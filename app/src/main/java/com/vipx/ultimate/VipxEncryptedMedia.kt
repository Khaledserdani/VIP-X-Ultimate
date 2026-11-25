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
 * VIPXENC v1: chunk-authenticated AES-256-GCM container.
 *
 * Layout: magic[8], version[1], chunkSize[4], mimeLength[2], mime[UTF-8],
 * salt[16], noncePrefix[8], then repeated records [plainLength:int][ciphertext+tag].
 * Each chunk has its own GCM tag and nonce (prefix + monotonically increasing
 * 32-bit counter); the header and chunk index/length are authenticated as AAD.
 * Passwords are never persisted. This format is for user-owned/authorized files,
 * not a replacement for provider DRM such as Widevine.
 */
object VipxEncryptedMedia {
    private val MAGIC = byteArrayOf(0x56, 0x49, 0x50, 0x58, 0x45, 0x4E, 0x43, 0x31) // VIPXENC1
    private const val VERSION = 1
    const val DEFAULT_CHUNK_SIZE = 256 * 1024
    private const val MIN_CHUNK_SIZE = 4 * 1024
    private const val MAX_CHUNK_SIZE = 1024 * 1024
    private const val SALT_SIZE = 16
    private const val PREFIX_SIZE = 8
    private const val TAG_BITS = 128
    private const val PBKDF2_ITERATIONS = 310_000

    data class Header(
        val chunkSize: Int,
        val mimeType: String,
        val salt: ByteArray,
        val noncePrefix: ByteArray,
        val authenticatedBytes: ByteArray
    )

    fun encrypt(input: InputStream, output: OutputStream, password: CharArray,
                mimeType: String, chunkSize: Int = DEFAULT_CHUNK_SIZE) {
        require(password.isNotEmpty()) { "كلمة المرور فارغة" }
        require(chunkSize in MIN_CHUNK_SIZE..MAX_CHUNK_SIZE) { "حجم القطعة غير صالح" }
        require(mimeType.length in 1..255 && !mimeType.contains('\n')) { "نوع الوسائط غير صالح" }
        val salt = ByteArray(SALT_SIZE).also { SecureRandom().nextBytes(it) }
        val prefix = ByteArray(PREFIX_SIZE).also { SecureRandom().nextBytes(it) }
        val mime = mimeType.toByteArray(Charsets.UTF_8)
        val headerBytes = java.io.ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.write(MAGIC); out.writeByte(VERSION); out.writeInt(chunkSize)
                out.writeShort(mime.size); out.write(mime); out.write(salt); out.write(prefix)
            }
        }.toByteArray()
        val key = deriveKey(password, salt)
        val source = BufferedInputStream(input)
        val sink = DataOutputStream(BufferedOutputStream(output))
        sink.write(headerBytes)
        val buffer = ByteArray(chunkSize)
        var index = 0L
        while (true) {
            var count = 0
            while (count < buffer.size) {
                val n = source.read(buffer, count, buffer.size - count)
                if (n < 0) break
                if (n == 0) continue
                count += n
            }
            if (count == 0) break
            require(index <= 0xFFFF_FFFFL) { "الملف أكبر من حد صيغة VIPXENC1" }
            val encrypted = crypt(Cipher.ENCRYPT_MODE, key, nonce(prefix, index),
                aad(headerBytes, index, count), buffer.copyOf(count))
            sink.writeInt(count)
            sink.write(encrypted)
            index++
            if (count < buffer.size) break
        }
        sink.flush()
    }

    fun decrypt(input: InputStream, output: OutputStream, password: CharArray): String {
        require(password.isNotEmpty()) { "كلمة المرور فارغة" }
        val source = DataInputStream(BufferedInputStream(input))
        val header = readHeader(source)
        val key = deriveKey(password, header.salt)
        val sink = BufferedOutputStream(output)
        var index = 0L
        while (true) {
            val plainLength = try { source.readInt() } catch (_: EOFException) { break }
            require(plainLength in 1..header.chunkSize) { "قطعة تالفة أو غير صالحة" }
            require(index <= 0xFFFF_FFFFL) { "تجاوز عدد القطع المدعوم" }
            val encrypted = ByteArray(plainLength + 16)
            source.readFully(encrypted)
            val plain = crypt(Cipher.DECRYPT_MODE, key, nonce(header.noncePrefix, index),
                aad(header.authenticatedBytes, index, plainLength), encrypted)
            require(plain.size == plainLength) { "طول القطعة غير مطابق" }
            sink.write(plain)
            index++
        }
        sink.flush()
        return header.mimeType
    }

    fun inspect(input: InputStream): Header = readHeader(DataInputStream(BufferedInputStream(input)))

    private fun readHeader(input: DataInputStream): Header {
        val captured = java.io.ByteArrayOutputStream()
        val magic = ByteArray(MAGIC.size).also(input::readFully)
        require(magic.contentEquals(MAGIC)) { "ليس ملف VIPXENC1" }
        captured.write(magic)
        val version = input.readUnsignedByte()
        captured.write(version)
        require(version == VERSION) { "إصدار الحاوية غير مدعوم: $version" }
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
        return Header(chunkSize, mime, salt, prefix, captured.toByteArray())
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
