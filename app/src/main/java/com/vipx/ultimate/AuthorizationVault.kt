package com.vipx.ultimate

import android.content.Context
import android.util.Base64
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * Stores provider-issued Authorization values encrypted with an Android Keystore AES-GCM key.
 * Entries expire 24 hours after being saved and are indexed by a one-way URL digest.
 */
object AuthorizationVault {
    private const val PREFS = "vipx_authorization_vault"
    private const val KEY_ALIAS = "vipx_authorization_aes_gcm_v1"
    private const val ENTRY_PREFIX = "entry_"
    private const val TTL_MILLIS = 24L * 60L * 60L * 1000L
    private const val GCM_TAG_BITS = 128

    @Synchronized
    fun save(context: Context, streamUrl: String, authorization: String) {
        if (authorization.isBlank()) {
            remove(context, streamUrl)
            return
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(authorization.toByteArray(Charsets.UTF_8))
        val value = listOf(
            (System.currentTimeMillis() + TTL_MILLIS).toString(),
            Base64.encodeToString(cipher.iv, Base64.NO_WRAP),
            Base64.encodeToString(encrypted, Base64.NO_WRAP)
        ).joinToString(":")
        preferences(context).edit().putString(entryKey(streamUrl), value).apply()
    }

    @Synchronized
    fun load(context: Context, streamUrl: String): String? {
        val prefs = preferences(context)
        val key = entryKey(streamUrl)
        val stored = prefs.getString(key, null) ?: return null
        return try {
            val parts = stored.split(':', limit = 3)
            require(parts.size == 3)
            val expiresAt = parts[0].toLong()
            if (System.currentTimeMillis() >= expiresAt) {
                prefs.edit().remove(key).apply()
                return null
            }
            val iv = Base64.decode(parts[1], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[2], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.doFinal(encrypted).toString(Charsets.UTF_8).takeIf { it.isNotBlank() }
        } catch (_: Exception) {
            prefs.edit().remove(key).apply()
            null
        }
    }

    @Synchronized
    fun remove(context: Context, streamUrl: String) {
        preferences(context).edit().remove(entryKey(streamUrl)).apply()
    }

    @Synchronized
    fun clear(context: Context) {
        preferences(context).edit().clear().apply()
    }

    @Synchronized
    fun pruneExpired(context: Context) {
        val prefs = preferences(context)
        val editor = prefs.edit()
        val now = System.currentTimeMillis()
        prefs.all.forEach { (key, value) ->
            if (key.startsWith(ENTRY_PREFIX)) {
                val expiresAt = (value as? String)?.substringBefore(':')?.toLongOrNull()
                if (expiresAt == null || now >= expiresAt) editor.remove(key)
            }
        }
        editor.apply()
    }

    private fun preferences(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    private fun entryKey(streamUrl: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(streamUrl.toByteArray(Charsets.UTF_8))
        return ENTRY_PREFIX + digest.joinToString("") { "%02x".format(it) }
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(
            android.security.keystore.KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or
                    android.security.keystore.KeyProperties.PURPOSE_DECRYPT
            )
                .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        return generator.generateKey()
    }
}
