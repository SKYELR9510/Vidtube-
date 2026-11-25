package com.silva.vidtube

import com.silva.vidtube.data.ChannelItem
import com.silva.vidtube.data.FeedEntry
import com.silva.vidtube.data.VideoItem
import com.silva.vidtube.ui.Adaptive
import com.silva.vidtube.ui.feedRows
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdaptiveTest {

    @Test
    fun columnasSegunElAncho() {
        assertEquals(1, Adaptive.columnsFor(360))   // celular
        assertEquals(1, Adaptive.columnsFor(411))
        assertEquals(2, Adaptive.columnsFor(600))   // tablet pequeña vertical
        assertEquals(2, Adaptive.columnsFor(800))   // tablet vertical
        assertEquals(3, Adaptive.columnsFor(1024))
        assertEquals(4, Adaptive.columnsFor(1280))  // tablet horizontal
        assertFalse(Adaptive.isTablet(411))
        assertTrue(Adaptive.isTablet(600))
        assertFalse(Adaptive.twoPane(800))
        assertTrue(Adaptive.twoPane(1280))
    }

    private fun v(n: Int) = FeedEntry.Video(
        VideoItem(
            url = "https://youtube.com/watch?v=$n", title = "Video $n", thumbnail = null,
            channelName = "Canal", channelUrl = null, channelAvatar = null,
            durationSec = 100, views = 1, uploaded = null, isLive = false,
        )
    )

    @Test
    fun videosEnFilasYCanalesSolos() {
        val ch = FeedEntry.Channel(ChannelItem(url = "https://youtube.com/@c", name = "C", avatar = null, subscribers = -1))
        val rows = feedRows(listOf(v(1), v(2), v(3), ch, v(4), v(5)), columns = 2)
        assertEquals(listOf(2, 1, 1, 2), rows.map { it.size })
        assertTrue(rows[2][0] is FeedEntry.Channel)
        assertEquals(1, feedRows(listOf(v(1), v(2), v(3)), columns = 3).size)
    }
}
