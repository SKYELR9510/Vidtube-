package com.silva.vidtube

import com.silva.vidtube.data.FeedCache
import com.silva.vidtube.data.FeedEntry
import com.silva.vidtube.data.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FeedCacheTest {
    private val video = FeedEntry.Video(
        VideoItem(
            url = "https://www.youtube.com/watch?v=abc", title = "Mix 2026", thumbnail = "https://i.ytimg.com/x.jpg",
            channelName = "Letras Rojas", channelUrl = "https://www.youtube.com/@x", channelAvatar = null,
            durationSec = 3190, views = 69000, uploaded = "hace 1 día", isLive = false,
        ),
        reason = "Tendencia",
    )

    @Test
    fun guardaYLeeLaLista() {
        val raw = FeedCache.encode(listOf(video), now = 1_000L)
        val back = FeedCache.decode(raw, now = 2_000L)!!
        assertEquals(1, back.size)
        val v = back[0] as FeedEntry.Video
        assertEquals("Mix 2026", v.video.title)
        assertEquals("Tendencia", v.reason)
        assertEquals(3190L, v.video.durationSec)
    }

    @Test
    fun listaViejaNoSeMuestra() {
        val raw = FeedCache.encode(listOf(video), now = 0L)
        assertNull(FeedCache.decode(raw, now = 4L * 24 * 3600_000))
    }
}
