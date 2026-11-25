package com.vipx.ultimate

import android.content.Intent
import android.net.Uri
import android.util.Patterns

enum class SharedMediaKind { HLS, DASH, MP4, AUDIO, UNKNOWN }

data class SharedMediaCandidate(
    val url: String,
    val kind: SharedMediaKind,
    val host: String
)

object SharedMediaCapture {
    private val urlRegex = Regex("https://[^\\s<>\\\"']+", RegexOption.IGNORE_CASE)

    fun fromIntent(intent: Intent?): SharedMediaCandidate? {
        if (intent == null) return null
        val candidates = linkedSetOf<String>()
        intent.data?.toString()?.let(candidates::add)
        intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()?.let { text ->
            urlRegex.findAll(text).forEach { candidates.add(it.value.trimEnd('.', ',', ';', ')', ']', '}')) }
            if (candidates.isEmpty() && Patterns.WEB_URL.matcher(text.trim()).matches()) {
                candidates.add(if (text.trim().startsWith("http", true)) text.trim() else "https://${text.trim()}")
            }
        }
        return candidates.asSequence().mapNotNull(::parse).firstOrNull()
    }

    fun parse(raw: String): SharedMediaCandidate? {
        val cleaned = raw.trim().trimEnd('.', ',', ';', ')', ']', '}')
        val uri = runCatching { Uri.parse(cleaned) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host.isNullOrBlank()) return null
        val path = uri.path.orEmpty().lowercase()
        val kind = when {
            path.endsWith(".m3u8") -> SharedMediaKind.HLS
            path.endsWith(".mpd") -> SharedMediaKind.DASH
            path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".webm") -> SharedMediaKind.MP4
            path.endsWith(".mp3") || path.endsWith(".m4a") || path.endsWith(".aac") || path.endsWith(".ogg") -> SharedMediaKind.AUDIO
            else -> SharedMediaKind.UNKNOWN
        }
        return SharedMediaCandidate(cleaned, kind, uri.host.orEmpty())
    }

    fun safeDisplay(raw: String): String {
        val uri = Uri.parse(raw)
        val base = buildString {
            append(uri.scheme).append("://").append(uri.host)
            if (uri.port != -1) append(":").append(uri.port)
            append(uri.encodedPath.orEmpty())
        }
        return if (uri.encodedQuery.isNullOrBlank()) base else "$base?…"
    }

    fun label(kind: SharedMediaKind): String = when (kind) {
        SharedMediaKind.HLS -> "HLS • بث m3u8"
        SharedMediaKind.DASH -> "DASH • بث mpd"
        SharedMediaKind.MP4 -> "فيديو مباشر/ملف فيديو"
        SharedMediaKind.AUDIO -> "ملف/بث صوتي"
        SharedMediaKind.UNKNOWN -> "رابط ويب • نوع الوسيط غير مؤكد"
    }
}
