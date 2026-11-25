package com.vipx.ultimate

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.EOFException
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
}
