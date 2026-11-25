package com.vipx.ultimate

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SharedMediaCaptureInstrumentedTest {
    @Test
    fun acceptsOnlyContentUrisExplicitlySharedByAnotherApp() {
        val shared = Uri.parse("content://example.provider/media/42")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("image/jpeg")
            .putExtra(Intent.EXTRA_STREAM, shared)

        assertEquals(listOf(shared), SharedMediaCapture.sharedContentUris(intent))
    }

    @Test
    fun supportsMultipleSharedItemsAndDeduplicatesClipData() {
        val first = Uri.parse("content://example.provider/media/1")
        val second = Uri.parse("content://example.provider/media/2")
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE)
            .setType("video/mp4")
            .putParcelableArrayListExtra(Intent.EXTRA_STREAM, arrayListOf(first, second))
            .setClipData(ClipData.newRawUri("first", first).apply {
                addItem(ClipData.Item(second))
            })

        assertEquals(listOf(first, second), SharedMediaCapture.sharedContentUris(intent))
    }

    @Test
    fun refusesFileAndWebUrisAsSharedFilePayloads() {
        val file = Uri.parse("file:///data/user/0/other.app/private.mp4")
        val web = Uri.parse("https://example.com/video.mp4")
        val intent = Intent(Intent.ACTION_SEND)
            .setType("video/mp4")
            .putExtra(Intent.EXTRA_STREAM, file)
            .setClipData(ClipData.newRawUri("web", web))

        assertTrue(SharedMediaCapture.sharedContentUris(intent).isEmpty())
    }

    @Test
    fun recognizesSupportedSharedWebStreamTypes() {
        assertEquals(SharedMediaKind.HLS, SharedMediaCapture.parse("https://example.com/live.m3u8")?.kind)
        assertEquals(SharedMediaKind.DASH, SharedMediaCapture.parse("https://example.com/live.mpd")?.kind)
        assertEquals(SharedMediaKind.MP4, SharedMediaCapture.parse("https://example.com/movie.mp4")?.kind)
        assertEquals(null, SharedMediaCapture.parse("http://example.com/movie.mp4"))
    }
}
