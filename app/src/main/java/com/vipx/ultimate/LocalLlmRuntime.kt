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
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.coroutines.isActive
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/** On-device Qwen3 0.6B fallback; no API key or cloud call is used. */
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
    fun partialFile(context: Context): File = File(context.filesDir, "$MODEL_NAME.part")
    fun isModelDownloaded(context: Context): Boolean =
        modelFile(context).let { it.isFile && it.length() == MODEL_BYTES }

    fun partialDownloadBytes(context: Context): Long =
        partialFile(context).takeIf { it.isFile }?.length()?.coerceAtMost(MODEL_BYTES) ?: 0L

    suspend fun downloadModel(context: Context, onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val target = modelFile(context)
        val partial = partialFile(context)
        if (target.isFile) {
            if (target.length() == MODEL_BYTES && sha256(target) == MODEL_SHA256) {
                onProgress(100)
                return@withContext
            }
            target.delete()
        }
        var existing = partial.length().coerceAtMost(MODEL_BYTES)
        if (partial.length() > MODEL_BYTES) {
            partial.delete()
            existing = 0L
        }
        if (existing == MODEL_BYTES) {
            if (sha256(partial) == MODEL_SHA256) {
                check(partial.renameTo(target)) { "تعذر اعتماد ملف النموذج بعد التحقق" }
                onProgress(100)
                return@withContext
            }
            partial.delete()
            existing = 0L
        }

        val requiredSpace = (MODEL_BYTES - existing).coerceAtLeast(0L) + 100L * 1024L * 1024L
        val available = StatFs(context.filesDir.path).availableBytes
        require(available >= requiredSpace) {
            "يلزم توفير مساحة فارغة إضافية لتنزيل النموذج والتحقق منه"
        }

        var attempt = 0
        var lastError: Exception? = null
        while (attempt < 4) {
            if (!kotlin.coroutines.coroutineContext.isActive) throw CancellationException("أُلغي تنزيل النموذج")
            existing = partial.length().coerceAtMost(MODEL_BYTES)
            val connection = (URL(MODEL_URL).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 30000
                readTimeout = 120000
                instanceFollowRedirects = true
                setRequestProperty("User-Agent", "VIP-X-Ultimate-Android")
                setRequestProperty("Accept-Encoding", "identity")
                if (existing > 0L) setRequestProperty("Range", "bytes=$existing-")
            }
            try {
                connection.connect()
                val code = connection.responseCode
                if (code != 200 && code != 206) throw IOException("فشل تنزيل النموذج: HTTP $code")
                val append = existing > 0L && code == 206
                if (append) {
                    val range = connection.getHeaderField("Content-Range").orEmpty()
                    val expectedPrefix = "bytes $existing-"
                    if (!range.startsWith(expectedPrefix) || !range.endsWith("/$MODEL_BYTES")) {
                        throw IOException("الخادم لم يؤكد موضع الاستكمال وحجم الملف؛ حُفظ الجزء السابق دون دمج بيانات غير مؤكدة")
                    }
                } else if (existing > 0L && code == 200) {
                    partial.delete()
                    existing = 0L
                }

                val declaredLength = connection.contentLengthLong
                val expectedBytes = MODEL_BYTES - existing
                if (declaredLength > 0L && declaredLength != expectedBytes) {
                    throw IOException("حجم الجزء المستلم غير مطابق؛ أُبقي الجزء السابق للاستكمال")
                }

                var total = existing
                connection.inputStream.use { input ->
                    FileOutputStream(partial, append).buffered().use { output ->
                        val buffer = ByteArray(1024 * 1024)
                        while (true) {
                            if (!kotlin.coroutines.coroutineContext.isActive) {
                                throw CancellationException("أُلغي تنزيل النموذج")
                            }
                            val count = input.read(buffer)
                            if (count < 0) break
                            output.write(buffer, 0, count)
                            total += count
                            onProgress(((total * 100L) / MODEL_BYTES).toInt().coerceIn(0, 99))
                        }
                        output.flush()
                    }
                }
                if (total != MODEL_BYTES) {
                    throw IOException("انقطع الاتصال عند ${total} من ${MODEL_BYTES} بايت؛ سيُستكمل من الموضع المحفوظ")
                }
                if (sha256(partial) != MODEL_SHA256) {
                    partial.delete()
                    throw IOException("فشل SHA-256؛ حُذف الجزء غير السليم وستبدأ محاولة نظيفة")
                }
                check(partial.renameTo(target)) { "تعذر حفظ ملف النموذج بعد التحقق" }
                onProgress(100)
                return@withContext
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                lastError = error
                attempt++
                if (attempt >= 4) {
                    val saved = partial.length()
                    throw IOException(
                        "تعذر إكمال الاتصال بعد 4 محاولات. حُفظ $saved من $MODEL_BYTES بايت؛ اضغط إعادة المحاولة للاستكمال دون إعادة الجزء المحفوظ. السبب: ${error.message}",
                        error
                    )
                }
                delay(1500L * attempt)
            } finally {
                connection.disconnect()
            }
        }
        throw IOException("تعذر تنزيل النموذج: ${lastError?.message ?: "خطأ اتصال غير معروف"}")
    }

    suspend fun initialize(context: Context) = withContext(Dispatchers.IO) {
        synchronized(lock) {
            val model = modelFile(context)
            require(model.isFile && model.length() == MODEL_BYTES && sha256(model) == MODEL_SHA256) {
                "ملف النموذج غير مكتمل أو فشل التحقق منه؛ أعد محاولة التنزيل"
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
                // Do not report the model as ready until a real on-device inference succeeds.
                val smokeReply = chat.sendMessage("أجب بكلمة واحدة: جاهز").toString().trim()
                check(smokeReply.isNotBlank()) { "تم تحميل النموذج لكن اختبار الاستدلال المحلي لم يُنتج إجابة" }
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
