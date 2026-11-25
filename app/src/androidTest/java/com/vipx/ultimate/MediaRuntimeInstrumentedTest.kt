package com.vipx.ultimate

import android.media.MediaCodec
import android.media.MediaCodecList
import android.media.MediaExtractor
import android.media.MediaFormat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

@RunWith(AndroidJUnit4::class)
class MediaRuntimeInstrumentedTest {
    @Test
    fun encryptedMp4DecryptsAndDecodesOnAndroidMediaCodec() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val original = android.util.Base64.decode("AAAAHGZ0eXBpc29tAAACAGlzb21pc28ybXA0MQAAA1xtb292AAAAbG12aGQAAAAAAAAAAAAAAAAAAAPoAAABkAABAAABAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAACh3RyYWsAAABcdGtoZAAAAAMAAAAAAAAAAAAAAAEAAAAAAAABkAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAQAAAAEAAAAAAACRlZHRzAAAAHGVsc3QAAAAAAAAAAQAAAZAAAAAAAAEAAAAAAf9tZGlhAAAAIG1kaGQAAAAAAAAAAAAAAAAAACgAAAAQAFXEAAAAAAAtaGRscgAAAAAAAAAAdmlkZQAAAAAAAAAAAAAAAFZpZGVvSGFuZGxlcgAAAAGqbWluZgAAABR2bWhkAAAAAQAAAAAAAAAAAAAAJGRpbmYAAAAcZHJlZgAAAAAAAAABAAAADHVybCAAAAABAAABanN0YmwAAADqc3RzZAAAAAAAAAABAAAA2m1wNHYAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAQABAAEgAAABIAAAAAAAAAAETTGF2YzYxLjE5LjEwMSBtcGVnNAAAAAAAAAAAAAAAAAAY//8AAABgZXNkcwAAAAADgICATwABAASAgIBBIBEAAAAAAw1AAAAH+AWAgIAvAAABsAEAAAG1iRMAAAEAAAABIADEjYgALQIECBRDAAABskxhdmM2MS4xOS4xMDEGgICAAQIAAAAQcGFzcAAAAAEAAAABAAAAFGJ0cnQAAAAAAAMNQAAAB/gAAAAYc3R0cwAAAAAAAAABAAAAAgAACAAAAAAUc3RzcwAAAAAAAAABAAAAAQAAABxzdHNjAAAAAAAAAAEAAAABAAAAAgAAAAEAAAAc3RzegAAAAAAAAAAAAAAAgAAAFIAAAAUAAAAFHN0Y28AAAAAAAAAAQAAA4gAAABhdWR0YQAAAFltZXRhAAAAAAAAACFoZGxyAAAAAAAAAABtZGlyYXBwbAAAAAAAAAAAAAAAACxpbHN0AAAAJKl0b28AAAAcZGF0YQAAAAEAAAAATGF2ZjYxLjcuMTAzAAAACGZyZWUAAABubWRhdAAAAbMAEAcAAAG2EYVGKm2EYGlG238bbfxtt+8AAKKRipthGBpRtt/G238bbfsAAMKRipthGBpRtt/G238bbfsAAOKRipthGBpRtt/G238bbfsAAAG2U8KPvwAAop4AAMKeAADing==", android.util.Base64.DEFAULT)
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream(original), encrypted, "android-runtime-test".toCharArray(), "video/mp4", 4096)
        val decrypted = ByteArrayOutputStream()
        val mime = VipxEncryptedMedia.decrypt(ByteArrayInputStream(encrypted.toByteArray()), decrypted, "android-runtime-test".toCharArray())
        assertEquals("video/mp4", mime)
        assertArrayEquals(original, decrypted.toByteArray())
        val mediaFile = File(context.cacheDir, "vipx-runtime-" + System.nanoTime() + ".mp4")
        mediaFile.writeBytes(decrypted.toByteArray())
        val extractor = MediaExtractor()
        var codec: MediaCodec? = null
        try {
            extractor.setDataSource(mediaFile.absolutePath)
            val videoTrack = (0 until extractor.trackCount).firstOrNull {
                extractor.getTrackFormat(it).getString(MediaFormat.KEY_MIME)?.startsWith("video/") == true
            } ?: fail("Android MediaExtractor could not find a video track")
            extractor.selectTrack(videoTrack)
            val format = extractor.getTrackFormat(videoTrack)
            val mimeType = format.getString(MediaFormat.KEY_MIME)!!
            val decoderName = MediaCodecList(MediaCodecList.REGULAR_CODECS).findDecoderForFormat(format)
            assertNotNull("No Android decoder for $mimeType", decoderName)
            codec = MediaCodec.createByCodecName(decoderName!!)
            codec.configure(format, null, null, 0)
            codec.start()
            val info = MediaCodec.BufferInfo()
            val deadline = System.currentTimeMillis() + 8000
            var inputEnded = false
            var decodedOutput = false
            while (System.currentTimeMillis() < deadline && !decodedOutput) {
                if (!inputEnded) {
                    val inputIndex = codec.dequeueInputBuffer(10000)
                    if (inputIndex >= 0) {
                        val inputBuffer = codec.getInputBuffer(inputIndex)!!
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            codec.queueInputBuffer(inputIndex, 0, 0, 0L, MediaCodec.BUFFER_FLAG_END_OF_STREAM)
                            inputEnded = true
                        } else {
                            codec.queueInputBuffer(inputIndex, 0, sampleSize, extractor.sampleTime, extractor.sampleFlags)
                            extractor.advance()
                        }
                    }
                }
                val outputIndex = codec.dequeueOutputBuffer(info, 10000)
                if (outputIndex >= 0) {
                    if (info.size > 0) decodedOutput = true
                    codec.releaseOutputBuffer(outputIndex, false)
                }
            }
            assertTrue("Android MediaCodec did not decode a video frame", decodedOutput)
        } finally {
            runCatching { codec?.stop() }
            codec?.release()
            extractor.release()
            mediaFile.delete()
        }
    }
}
