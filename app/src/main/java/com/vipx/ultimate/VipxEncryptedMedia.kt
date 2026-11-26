package com.vipx.ultimate

import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.EOFException
import java.io.InputStream
import java.io.OutputStream
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * VIPXENC authenticated chunk container.
 *
 * v2 adds an authenticated end-of-file record. This detects truncation even
 * when complete encrypted chunks are removed. v1 remains readable for
 * compatibility, but new encryption always writes v2.
 */
object VipxEncryptedMedia {
    private val MAGIC = byteArrayOf(0x56, 0x49, 0x50, 0x58, 0x45, 0x4E, 0x43, 0x31)
    private const val VERSION = 2
    const val DEFAULT_CHUNK_SIZE = 256 * 1024
    private const val MIN_CHUNK_SIZE = 4 * 1024
    private const val MAX_CHUNK_SIZE = 1024 * 1024
    const val MAX_PLAINTEXT_BYTES = 512L * 1024L * 1024L
    private const val SALT_SIZE = 16
    private const val PREFIX_SIZE = 8
    private const val TAG_BITS = 128
    private const val TAG_BYTES = TAG_BITS / 8
    private const val PBKDF2_ITERATIONS = 310_000

    data class Header(
        val version: Int,
        val chunkSize: Int,
        val mimeType: String,
        val salt: ByteArray,
        val noncePrefix: ByteArray,
        val authenticatedBytes: ByteArray
    )

    fun encrypt(input: InputStream, output: OutputStream, password: CharArray,
                mimeType: String, chunkSize: Int = DEFAULT_CHUNK_SIZE,
                maxPlaintextBytes: Long = MAX_PLAINTEXT_BYTES) {
        require(password.isNotEmpty()) { "كلمة المرور فارغة" }
        require(chunkSize in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE) { "حجم القطعة غير صالح" }
        require(maxPlaintextBytes in 1..MAX_PLAINTEXT_BYTES) { "حد حجم الملف غير صالح" }
        require(mimeType.length in 1..255 && !mimeType.contains('\n')) { "نوع الوسائط غير صالح" }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val prefix = ByteArray(PREFIX_SIZE).also { SecureRandom().nextBytes(it) }
        val mime = mimeType.toByteArray(Charsets.UTF_8)
        require(mime.size in 1..255) { "بيانات MIME غير صالحة" }
        val headerBytes = java.io.ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write(MAGIC); out.writeByte(VERSION); out.writeInt(chunkSize)
                out.writeShort(mime.size); out.write(mime); out.write(salt); out.write(prefix)
            }
        }.toByteArray()
        val key = deriveKey(password, salt)
        val source = input
        val sink = DataOutputStream(BufferedOutputStream(output))
        sink.write(headerBytes)
        val buffer = ByteArray(chunkSize)
        var index = 0L
        var totalPlainBytes = 0L
        while (true) {
            var count = 0
            while (count < buffer.size) {
                val n = source.read(buffer, count, buffer.size - count)
                if (n < 0) break
                if (n == 0) {
                    // Some custom/provider streams may return zero for a non-empty read.
                    // Fall back to a single-byte read so this loop cannot spin forever.
                    val one = source.read()
                    if (one < 0) break
                    buffer[count++] = one.toByte()
                    continue
                }
                count += n
            }
            if (count == 0) break
            if (totalPlainBytes > maxPlaintextBytes - count.toLong()) {
                buffer.fill(0, 0, count)
                throw IllegalArgumentException("حجم الملف يتجاوز الحد الأقصى المسموح به")
            }
            require(index < 0xFFFF_FFFFL) { "الملف أكبر من حد صيغة VIPXENC" }
            val plainChunk = buffer.copyOf(count)
            try {
                val encrypted = crypt(Cipher.ENCRYPT_MODE, key, nonce(prefix, index),
                    aad(headerBytes, index, count), plainChunk)
                sink.writeInt(count)
                sink.write(encrypted)
            } finally {
                plainChunk.fill(0)
                buffer.fill(0, 0, count)
            }
            totalPlainBytes += count.toLong()
            index++
            if (count < buffer.size) break
        }
        // Authenticated terminator: makes missing/truncated trailing chunks detectable.
        val finalTag = crypt(Cipher.ENCRYPT_MODE, key, nonce(prefix, index),
            aad(headerBytes, index, 0), ByteArray(0))
        sink.writeInt(0)
        sink.write(finalTag)
        sink.flush()
    }

    /**
     * Decrypts to [output]. Callers must supply a temporary/unpublished destination and
     * publish it only after this method returns successfully: authenticated v2 end-of-file
     * validation happens after plaintext chunks have been streamed to the output.
     */
    fun decrypt(input: InputStream, output: OutputStream, password: CharArray,
                maxPlaintextBytes: Long = MAX_PLAINTEXT_BYTES): String {
        require(password.isNotEmpty()) { "كلمة المرور فارغة" }
        require(maxPlaintextBytes in 1..MAX_PLAINTEXT_BYTES) { "حد حجم الملف غير صالح" }
        val source = DataInputStream(BufferedInputStream(input))
        val header = readHeader(source)
        val key = deriveKey(password, header.salt)
        val sink = BufferedOutputStream(output)
        var index = 0L
        var totalPlainBytes = 0L
        var authenticatedEnd = false
        while (true) {
            val plainLength = if (header.version >= 2) {
                // Distinguish a clean record boundary from a partially truncated length.
                val first = source.read()
                if (first < 0) throw EOFException("ملف VIPXENC2 مبتور: علامة النهاية غير موجودة")
                val rest = ByteArray(3)
                source.readFully(rest)
                ((first and 0xff) shl 24) or ((rest[0].toInt() and 0xff) shl 16) or
                    ((rest[1].toInt() and 0xff) shl 8) or (rest[2].toInt() and 0xff)
            } else {
                // For legacy v1, EOF is clean only before a new record starts.
                // A partially written 4-byte length must not be mistaken for EOF.
                val first = source.read()
                if (first < 0) break
                val rest = ByteArray(3)
                source.readFully(rest)
                ((first and 0xff) shl 24) or ((rest[0].toInt() and 0xff) shl 16) or
                    ((rest[1].toInt() and 0xff) shl 8) or (rest[2].toInt() and 0xff)
            }

            if (plainLength == 0 && header.version >= 2) {
                val finalTag = ByteArray(TAG_BYTES)
                source.readFully(finalTag)
                crypt(Cipher.DECRYPT_MODE, key, nonce(header.noncePrefix, index),
                    aad(header.authenticatedBytes, index, 0), finalTag)
                require(source.read() == -1) { "بيانات إضافية بعد نهاية الملف" }
                authenticatedEnd = true
                break
            }

            require(plainLength in 1..header.chunkSize) { "قطعة تالفة أو غير صالحة" }
            require(index < 0xFFFF_FFFFL) { "تجاوز عدد القطع المدعوم" }
            val encrypted = ByteArray(plainLength + TAG_BYTES)
            source.readFully(encrypted)
            val plain = crypt(Cipher.DECRYPT_MODE, key, nonce(header.noncePrefix, index),
                aad(header.authenticatedBytes, index, plainLength), encrypted)
            try {
                require(plain.size == plainLength) { "طول القطعة غير مطابق" }
                require(totalPlainBytes <= maxPlaintextBytes - plain.size.toLong()) {
                    "حجم الملف المفكوك يتجاوز الحد الأقصى المسموح به"
                }
                sink.write(plain)
                totalPlainBytes += plain.size.toLong()
            } finally {
                plain.fill(0)
            }
            index++
        }
        if (header.version >= 2) require(authenticatedEnd) { "نهاية الملف غير موثّقة" }
        sink.flush()
        return header.mimeType
    }

    fun inspect(input: InputStream): Header = readHeader(DataInputStream(BufferedInputStream(input)))

    private fun readHeader(input: DataInputStream): Header {
        val captured = java.io.ByteArrayOutputStream()
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        require(magic.contentEquals(MAGIC)) { "ليس ملف VIPXENC" }
        captured.write(magic)
        val version = input.readUnsignedByte()
        captured.write(version)
        require(version in 1..VERSION) { "إصدار الحاوية غير مدعوم: $version" }
        val chunkSize = input.readInt()
        require(chunkSize in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE) { "حجم القطعة غير صالح" }
        captured.write(java.nio.ByteBuffer.allocate(4).putInt(chunkSize).array())
        val mimeLength = input.readUnsignedShort()
        require(mimeLength in 1..255) { "بيانات MIME غير صالحة" }
        captured.write(java.nio.ByteBuffer.allocate(2).putShort(mimeLength.toShort()).array())
        val mimeBytes = ByteArray(mimeLength).also(input::readFully)
        captured.write(mimeBytes)
        val mime = mimeBytes.toString(Charsets.UTF_8)
        require(mime.startsWith("image/") || mime.startsWith("video/")) { "الحاوية لا تحتوي صورة أو فيديو" }
        val salt = ByteArray(SALT_SIZE).also(input::readFully); captured.write(salt)
        val prefix = ByteArray(PREFIX_SIZE).also(input::readFully); captured.write(prefix)
        return Header(version, chunkSize, mime, salt, prefix, captured.toByteArray())
    }

    private fun deriveKey(password: CharArray, salt: ByteArray): SecretKey {
        val spec = PBEKeySpec(password, salt, PBKDF2_ITERATIONS, 256)
        return try {
            val bytes = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
                .generateSecret(spec).encoded
            SecretKeySpec(bytes, "AES").also { bytes.fill(0) }
        } finally { spec.clearPassword() }
    }

    private fun nonce(prefix: ByteArray, index: Long): ByteArray =
        prefix + java.nio.ByteBuffer.allocate(4).putInt(index.toInt()).array()

    private fun aad(header: ByteArray, index: Long, length: Int): ByteArray =
        header + java.nio.ByteBuffer.allocate(12).putLong(index).putInt(length).array()

    private fun crypt(mode: Int, key: SecretKey, nonce: ByteArray, aad: ByteArray, data: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(mode, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(aad)
        return cipher.doFinal(data)
    }
}
