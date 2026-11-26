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
 * Entries expire 8 hours after being saved and are indexed by a one-way URL digest.
 * Legacy 24-hour entries are capped at 8 hours from their original save time when read or pruned.
 */
object AuthorizationVault {
    private const val PREFS = "vipx_authorization_vault"
    private const val KEY_ALIAS = "vipx_authorization_aes_gcm_v1"
    private const val ENTRY_PREFIX = "entry_"
    private const val ENTRY_VERSION = "2"
    private const val TTL_MILLIS = 8L * 60L * 60L * 1000L
    private const val LEGACY_TTL_REDUCTION_MILLIS = 16L * 60L * 60L * 1000L
    private const val GCM_TAG_BITS = 128
    private const val MAX_AUTHORIZATION_LENGTH = 8192

    @Synchronized
    fun save(context: Context, streamUrl: String, authorization: String) {
        if (authorization.isBlank()) {
            remove(context, streamUrl)
            return
        }
        require(isValidAuthorization(authorization)) {
            "Authorization غير صالح؛ الحد الأقصى 8192 حرفاً ومن دون أسطر جديدة"
        }
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        cipher.updateAAD(streamUrl.toByteArray(Charsets.UTF_8))
        val encrypted = cipher.doFinal(authorization.toByteArray(Charsets.UTF_8))
        val value = listOf(
            ENTRY_VERSION,
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
            val parts = stored.split(':', limit = 4)
            val isCurrentVersion = parts.size == 4 && parts[0] == ENTRY_VERSION
            val isLegacyVersion = parts.size == 3
            require(isCurrentVersion || isLegacyVersion)
            val expiresAt = if (isCurrentVersion) {
                parts[1].toLong()
            } else {
                parts[0].toLong() - LEGACY_TTL_REDUCTION_MILLIS
            }
            if (System.currentTimeMillis() >= expiresAt) {
                prefs.edit().remove(key).apply()
                return null
            }
            val ivIndex = if (isCurrentVersion) 2 else 1
            val encryptedIndex = if (isCurrentVersion) 3 else 2
            val iv = Base64.decode(parts[ivIndex], Base64.NO_WRAP)
            val encrypted = Base64.decode(parts[encryptedIndex], Base64.NO_WRAP)
            val cipher = Cipher.getInstance("AES/GCM/NoPadding")
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
            cipher.updateAAD(streamUrl.toByteArray(Charsets.UTF_8))
            val authorization = cipher.doFinal(encrypted).toString(Charsets.UTF_8)
                .takeIf { it.isNotBlank() && isValidAuthorization(it) }
            if (authorization != null && isLegacyVersion) {
                prefs.edit().putString(
                    key,
                    listOf(
                        ENTRY_VERSION,
                        expiresAt.toString(),
                        parts[1],
                        parts[2]
                    ).joinToString(":")
                ).apply()
            }
            authorization
        } catch (_: Exception) {
            prefs.edit().remove(key).apply()
            null
        }
    }

    /**
     * Accept one bounded HTTP header value only; CR/LF are rejected to prevent
     * header injection. Empty values remain valid for streams without auth.
     */
    fun isValidAuthorization(value: String): Boolean =
        value.length <= MAX_AUTHORIZATION_LENGTH &&
            value.none { it == '\\r' || it == '\\n' }

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
                val expiresAt = (value as? String)?.let(::storedExpiryMillis)
                if (expiresAt == null || now >= expiresAt) editor.remove(key)
            }
        }
        editor.apply()
    }

    private fun storedExpiryMillis(stored: String): Long? {
        val parts = stored.split(':', limit = 4)
        return when {
            parts.size == 4 && parts[0] == ENTRY_VERSION -> parts[1].toLongOrNull()
            parts.size == 3 -> parts[0].toLongOrNull()?.minus(LEGACY_TTL_REDUCTION_MILLIS)
            else -> null
        }
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
