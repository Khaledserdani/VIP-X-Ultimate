package com.vipx.ultimate

import android.content.Context
import android.os.StatFs
import com.google.ai.edge.litertlm.Backend
import com.google.ai.edge.litertlm.Contents
import com.google.ai.edge.litertlm.Conversation
import com.google.ai.edge.litertlm.ConversationConfig
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * On-device Qwen3 0.6B fallback.
 * Model weights are downloaded by explicit user action and stay in app-private storage.
 * Inference is CPU-only for broad device compatibility; no API key or cloud call is used.
 */
internal object LocalLlmRuntime {
    private const val MODEL_NAME = "Qwen3-0.6B.litertlm"
    private const val MODEL_BYTES = 613722264L
    private const val MODEL_SHA256 = "c5f39b4191926783c6e6668b62af4d6eb15248dfc7a5348252a9b406f33f2108"
    private const val MODEL_URL =
        "https://huggingface.co/litert-community/Qwen3-0.6B/resolve/e6687177e00897731e7716267eebd4eba0dde7c2/Qwen3-0.6B.litertlm"

    private val lock = Any()
    @Volatile private var engine: Engine? = null
    @Volatile private var conversation: Conversation? = null

    fun isReady(): Boolean = engine != null && conversation != null

    fun modelFile(context: Context): File = File(context.filesDir, MODEL_NAME)

    fun isModelDownloaded(context: Context): Boolean =
        modelFile(context).let { it.isFile && it.length() == MODEL_BYTES }

    suspend fun downloadModel(context: Context, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val target = modelFile(context)
        if (target.isFile && target.length() == MODEL_BYTES && sha256(target) == MODEL_SHA256) {
            onProgress(100)
            return@withContext
        }
        val available = StatFs(context.filesDir.path).availableBytes
        require(available >= MODEL_BYTES + 100L * 1024L * 1024L) {
            "يلزم توفير نحو 700 ميغابايت مساحة فارغة لتنزيل النموذج"
        }
        val partial = File(context.filesDir, "$MODEL_NAME.part")
        var existing = partial.takeIf { it.isFile }?.length() ?: 0L
        if (existing > MODEL_BYTES) {
            partial.delete()
            existing = 0L
        }
        if (existing == MODEL_BYTES) {
            if (sha256(partial) == MODEL_SHA256) {
                check(partial.renameTo(target)) { "تعذر اعتماد ملف النموذج بعد التحقق" }
                onProgress(100)
                return@withContext
            } else {
                partial.delete()
                existing = 0L
            }
        }

        val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 30000
            readTimeout = 120000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "VIP-X-Ultimate-Android")
            if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
        }
        try {
            connection.connect()
            val code = connection.responseCode
            require(code == 200 || code == 206) { "فشل تنزيل النموذج: HTTP $code" }
            val append = existing > 0L && code == 206
            if (existing > 0L && code == 200) {
                partial.delete()
                existing = 0L
            }
            val declaredLength = connection.contentLengthLong
            if (declaredLength > 0 && !append) require(declaredLength == MODEL_BYTES) {
                "حجم ملف النموذج غير مطابق للنسخة المعتمدة"
            }
            var total = existing
            connection.inputStream.use { input ->
                partial.outputStream().buffered().use { output ->
                    val buffer = ByteArray(1024 * 1024)
                    while (true) {
                        if (!kotlin.coroutines.coroutineContext.isActive) throw CancellationException("أُلغي تنزيل النموذج")
                        val count = input.read(buffer)
                        if (count < 0) break
                        output.write(buffer, 0, count)
                        total += count
                        onProgress(((total * 100L) / MODEL_BYTES).toInt().coerceIn(0, 99))
                    }
                    output.flush()
                }
            }
            require(total == MODEL_BYTES) { "اكتمل تنزيل غير صحيح: الحجم لا يطابق النموذج الأصلي" }
            if (sha256(partial) != MODEL_SHA256) {
                partial.delete()
                throw IllegalStateException("فشل التحقق من سلامة النموذج (SHA-256)")
            }
            check(partial.renameTo(target)) { "تعذر حفظ النموذج في مساحة التطبيق" }
            onProgress(100)
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            // Keep a partial download so the next attempt can resume with HTTP Range.
            throw error
        } finally {
            connection.disconnect()
        }
    }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val model = modelFile(context)
            require(model.isFile && model.length() == MODEL_BYTES && sha256(model) == MODEL_SHA256) {
                "نزّل نموذج الذكاء المحلي أولاً"
            }
            if (conversation != null && engine != null) return@synchronized
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
            conversation = null
            engine = null

            val candidate = Engine(
                EngineConfig(
                    modelPath = model.absolutePath,
                    backend = Backend.CPU(),
                    cacheDir = context.cacheDir.absolutePath
                )
            )
            try {
                candidate.initialize()
                val chat = candidate.createConversation(
                    ConversationConfig(
                        systemInstruction = Contents.of(
                            "أنت مساعد VIP X المحلي، تعمل بالكامل دون إنترنت. أجب بالعربية الواضحة. " +
                                "لا تدّعِ أنك نفذت إجراءً أو استخدمت الإنترنت. اقترح خطوات آمنة فقط، " +
                                "واطلب تأكيد المستخدم قبل أي إجراء داخل التطبيقات."
                        )
                    )
                )
                engine = candidate
                conversation = chat
            } catch (error: Exception) {
                runCatching { candidate.close() }
                throw error
            }
        }
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val active = conversation ?: error("المساعد المحلي غير جاهز؛ حمّل النموذج وفعّله أولاً")
            active.sendMessage(prompt).toString().trim().ifBlank { "لم ينتج النموذج إجابة نصية." }
        }
    }

    fun close() {
        synchronized(lock) {
            runCatching { conversation?.close() }
            runCatching { engine?.close() }
            conversation = null
            engine = null
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered().use { input ->
            val buffer = ByteArray(1024 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
