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
