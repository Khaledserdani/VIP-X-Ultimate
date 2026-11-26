package com.vipx.ultimate

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException

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

    @Test fun actualPngImagePayloadRoundTripsWithoutChangingBytes() {
        // A fixed 1x1 PNG fixture; JVM unit tests do not provide java.awt/ImageIO on Android.
        val imageBytes = java.util.Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAAAAAA6fptVAAAACklEQVR4nGNgAAAAAgABSK+kcQAAAABJRU5ErkJggg=="
        )
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream(imageBytes), encrypted, "png-test".toCharArray(), "image/png", 4096)
        val recovered = ByteArrayOutputStream()
        assertEquals("image/png", VipxEncryptedMedia.decrypt(
            ByteArrayInputStream(encrypted.toByteArray()), recovered, "png-test".toCharArray()
        ))
        val decodedBytes = recovered.toByteArray()
        assertArrayEquals("PNG bytes must survive encryption and decryption unchanged", imageBytes, decodedBytes)
        assertEquals(0x89, decodedBytes[0].toInt() and 0xff)
        assertEquals('P'.code, decodedBytes[1].toInt())
        assertEquals('N'.code, decodedBytes[2].toInt())
        assertEquals('G'.code, decodedBytes[3].toInt())
        assertEquals(1, java.nio.ByteBuffer.wrap(decodedBytes, 16, 4).int)
        assertEquals(1, java.nio.ByteBuffer.wrap(decodedBytes, 20, 4).int)
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

    @Test fun trailingBytesAfterAuthenticatedEndAreRejected() {
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream("valid media".toByteArray()), encrypted,
            "secret".toCharArray(), "image/png", 4096)
        val damaged = encrypted.toByteArray() + byteArrayOf(0x55)
        try {
            VipxEncryptedMedia.decrypt(ByteArrayInputStream(damaged), ByteArrayOutputStream(), "secret".toCharArray())
            fail("Trailing bytes after the authenticated terminator must fail")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun unsupportedMimeHeaderIsRejected() {
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream("data".toByteArray()), encrypted,
            "secret".toCharArray(), "image/png", 4096)
        val damaged = encrypted.toByteArray()
        damaged[15] = 'x'.code.toByte()
        try {
            VipxEncryptedMedia.inspect(ByteArrayInputStream(damaged))
            fail("A non-image/video MIME type must be rejected")
        } catch (_: IllegalArgumentException) { }
    }

    @Test fun encryptedChunkTruncationIsRejected() {
        val encrypted = ByteArrayOutputStream()
        VipxEncryptedMedia.encrypt(ByteArrayInputStream(ByteArray(9000) { it.toByte() }), encrypted,
            "secret".toCharArray(), "video/mp4", 4096)
        val complete = encrypted.toByteArray()
        val truncated = complete.copyOf(complete.size - 24)
        try {
            VipxEncryptedMedia.decrypt(ByteArrayInputStream(truncated), ByteArrayOutputStream(), "secret".toCharArray())
            fail("Truncated ciphertext record must fail")
        } catch (_: EOFException) { }
    }
}
