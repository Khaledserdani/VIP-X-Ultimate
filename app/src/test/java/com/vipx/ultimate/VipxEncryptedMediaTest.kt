package com.vipx.ultimate

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.File
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

class VipxEncryptedMediaTest {
    @Test fun roundTripImageAndVideoPayload() {
        val payload = ByteArray(700_123) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream(payload), encrypted, "test-pass".toCharArray(), "image/jpeg", 16 * 1024)
        val recovered = ByteArrayOutputStream()
        val mime = VipxEncryptedMedia.decrypt(ByteArrayInputStream(encrypted.toByteArray()), recovered, "test-pass".toCharArray())
        assertEquals("image/jpeg", mime)
        assertArrayEquals(payload, recovered.toByteArray())
    }

    @Test fun actualPngImageRoundTripsAndRemainsDecodable() {
        val original = BufferedImage(24, 18, BufferedImage.TYPE_INT_ARGB)
        for (y in 0 until original.height) for (x in 0 until original.width) {
            original.setRGB(x, y, (0xFF shl 24) or ((x * 9) shl 16) or ((y * 13) shl 8) or ((x + y) * 3))
        }
        val imageBytes = ByteArrayOutputStream().also { check(ImageIO.write(original, "png", it)) }.toByteArray()
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream(imageBytes), encrypted, "png-test".toCharArray(), "image/png", 4096)
        val recovered = ByteArrayOutputStream()
        assertEquals("image/png", VipxEncryptedMedia.decrypt(
            ByteArrayInputStream(encrypted.toByteArray()), recovered, "png-test".toCharArray()
        ))
        val decoded = ImageIO.read(ByteArrayInputStream(recovered.toByteArray()))
        org.junit.Assert.assertNotNull("Decrypted file must be a real decodable PNG", decoded)
        assertEquals(original.width, decoded.width)
        assertEquals(original.height, decoded.height)
        for (y in 0 until original.height) for (x in 0 until original.width) {
            assertEquals(original.getRGB(x, y), decoded.getRGB(x, y))
        }
    }

    @Test fun actualMp4VideoContainerRoundTripsWithoutChangingBytes() {
        // A tiny generated MP4 fixture (64x64 red test clip, 0.4 seconds).
        val mp4 = java.util.Base64.getDecoder().decode("AAAAHGZ0eXBpc29tAAACAGlzb21pc28ybXA0MQAAA1xtb292AAAAbG12aGQAAAAAAAAAAAAAAAAAAAPoAAABkAABAAABAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAACAAACh3RyYWsAAABcdGtoZAAAAAMAAAAAAAAAAAAAAAEAAAAAAAABkAAAAAAAAAAAAAAAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAEAAAAAAAAAAAAAAAAAAEAAAAAAQAAAAEAAAAAAACRlZHRzAAAAHGVsc3QAAAAAAAAAAQAAAZAAAAAAAAEAAAAAAf9tZGlhAAAAIG1kaGQAAAAAAAAAAAAAAAAAACgAAAAQAFXEAAAAAAAtaGRscgAAAAAAAAAAdmlkZQAAAAAAAAAAAAAAAFZpZGVvSGFuZGxlcgAAAAGqbWluZgAAABR2bWhkAAAAAQAAAAAAAAAAAAAAJGRpbmYAAAAcZHJlZgAAAAAAAAABAAAADHVybCAAAAABAAABanN0YmwAAADqc3RzZAAAAAAAAAABAAAA2m1wNHYAAAAAAAAAAQAAAAAAAAAAAAAAAAAAAAAAQABAAEgAAABIAAAAAAAAAAETTGF2YzYxLjE5LjEwMSBtcGVnNAAAAAAAAAAAAAAAAAAY//8AAABgZXNkcwAAAAADgICATwABAASAgIBBIBEAAAAAAw1AAAAH+AWAgIAvAAABsAEAAAG1iRMAAAEAAAABIADEjYgALQIECBRDAAABskxhdmM2MS4xOS4xMDEGgICAAQIAAAAQcGFzcAAAAAEAAAABAAAAFGJ0cnQAAAAAAAMNQAAAB/gAAAAYc3R0cwAAAAAAAAABAAAAAgAACAAAAAAUc3RzcwAAAAAAAAABAAAAAQAAABxzdHNjAAAAAAAAAAEAAAABAAAAAgAAAAEAAAAcc3RzegAAAAAAAAAAAAAAAgAAAFIAAAAUAAAAFHN0Y28AAAAAAAAAAQAAA4gAAABhdWR0YQAAAFltZXRhAAAAAAAAACFoZGxyAAAAAAAAAABtZGlyYXBwbAAAAAAAAAAAAAAAACxpbHN0AAAAJKl0b28AAAAcZGF0YQAAAAEAAAAATGF2ZjYxLjcuMTAzAAAACGZyZWUAAABubWRhdAAAAbMAEAcAAAG2EYVGKm2EYGlG238bbfxtt+8AAKKRipthGBpRtt/G238bbfsAAMKRipthGBpRtt/G238bbfsAAOKRipthGBpRtt/G238bbfsAAAG2U8KPvwAAop4AAMKeAADing==")
        val originalText = mp4.toString(Charsets.ISO_8859_1)
        org.junit.Assert.assertTrue("Fixture must contain an MP4 ftyp box", originalText.startsWith("\u0000\u0000\u0000\u001Cftyp"))
        org.junit.Assert.assertTrue("Fixture must contain movie metadata", originalText.contains("moov"))
        org.junit.Assert.assertTrue("Fixture must contain media data", originalText.contains("mdat"))

        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream(mp4), encrypted, "mp4-test".toCharArray(), "video/mp4", 4096)
        val recovered = ByteArrayOutputStream()
        assertEquals("video/mp4", VipxEncryptedMedia.decrypt(
            ByteArrayInputStream(encrypted.toByteArray()), recovered, "mp4-test".toCharArray()
        ))
        assertArrayEquals("Decrypted MP4 bytes must exactly match the source clip", mp4, recovered.toByteArray())
    }

    @Test fun emptyMediaHasAuthenticatedEndMarker() {
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream(ByteArray(0)), encrypted, "empty-pass".toCharArray(), "video/mp4")
        val recovered = ByteArrayOutputStream()
        assertEquals("video/mp4", VipxEncryptedMedia.decrypt(
            ByteArrayInputStream(encrypted.toByteArray()), recovered, "empty-pass".toCharArray()
        ))
        assertArrayEquals(ByteArray(0), recovered.toByteArray())
    }

    @Test fun wrongPasswordIsRejected() {
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream("owned test media".toByteArray()), encrypted, "right".toCharArray(), "video/mp4")
        try {
            VipxEncryptedMedia.decrypt(ByteArrayInputStream(encrypted.toByteArray()), ByteArrayOutputStream(), "wrong".toCharArray())
            fail("Wrong password must fail authentication")
        } catch (_: javax.crypto.AEADBadTagException) { }
    }

    @Test fun tamperingIsRejected() {
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream(ByteArray(50_000) { it.toByte() }), encrypted, "secret".toCharArray(), "video/mp4", 4096)
        val damaged = encrypted.toByteArray()
        damaged[damaged.size - 1] = (damaged.last().toInt() xor 1).toByte()
        try {
            VipxEncryptedMedia.decrypt(ByteArrayInputStream(damaged), ByteArrayOutputStream(), "secret".toCharArray())
            fail("Tampered ciphertext must fail authentication")
        } catch (_: javax.crypto.AEADBadTagException) { }
    }

    @Test fun removingAuthenticatedEndMarkerIsRejected() {
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream(ByteArray(20_000) { it.toByte() }), encrypted, "secret".toCharArray(), "video/mp4", 4096)
        val complete = encrypted.toByteArray()
        // The final record is a 4-byte zero length plus a 16-byte GCM tag.
        val truncated = complete.copyOf(complete.size - 20)
        try {
            VipxEncryptedMedia.decrypt(ByteArrayInputStream(truncated), ByteArrayOutputStream(), "secret".toCharArray())
            fail("Removing the authenticated end marker must fail")
        } catch (_: EOFException) { }
    }

    @Test fun partialEndMarkerIsRejected() {
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream("video".toByteArray()), encrypted, "secret".toCharArray(), "video/mp4")
        val complete = encrypted.toByteArray()
        val truncated = complete.copyOf(complete.size - 1)
        try {
            VipxEncryptedMedia.decrypt(ByteArrayInputStream(truncated), ByteArrayOutputStream(), "secret".toCharArray())
            fail("Truncated end marker must fail")
        } catch (_: EOFException) { }
    }

    @Test fun encryptionRejectsPayloadAboveConfiguredLimit() {
        val output = ByteArrayOutputStream()
        try {
            VipxEncryptedMedia.encrypt(
                ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
                output,
                "limit-pass".toCharArray(),
                "video/mp4",
                maxPlaintextBytes = 4
            )
            fail("Encryption must reject payloads above the configured limit")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun decryptionRejectsPayloadAboveConfiguredLimit() {
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(
            ByteArrayInputStream(byteArrayOf(1, 2, 3, 4, 5)),
            encrypted,
            "limit-pass".toCharArray(),
            "video/mp4"
        )
        try {
            VipxEncryptedMedia.decrypt(
                ByteArrayInputStream(encrypted.toByteArray()),
                ByteArrayOutputStream(),
                "limit-pass".toCharArray(),
                maxPlaintextBytes = 4
            )
            fail("Decryption must reject payloads above the configured limit")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun encryptionHandlesStreamsThatTemporarilyReturnZero() {
        val payload = byteArrayOf(11, 22, 33, 44, 55)
        val unusualStream = object : java.io.InputStream() {
            private val delegate = ByteArrayInputStream(payload)
            private var returnZero = true

            override fun read(): Int = delegate.read()

            override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
                if (returnZero) {
                    returnZero = false
                    return 0
                }
                return delegate.read(buffer, offset, length)
            }
        }
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(unusualStream, encrypted, "zero-read".toCharArray(), "video/mp4")
        val recovered = ByteArrayOutputStream()
        VipxEncryptedMedia.decrypt(
            ByteArrayInputStream(encrypted.toByteArray()),
            recovered,
            "zero-read".toCharArray()
        )
        assertArrayEquals(payload, recovered.toByteArray())
    }

}
