package com.silva.vidtube

import com.silva.vidtube.data.UserLink
import com.silva.vidtube.data.UserLinks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserLinksTest {
    @Test
    fun soloAceptaEnlacesWeb() {
        assertEquals("https://ejemplo.com/peli", UserLinks.normalize("ejemplo.com/peli"))
        assertEquals("http://ejemplo.com/a.mp4", UserLinks.normalize("  http://ejemplo.com/a.mp4 "))
        assertNull(UserLinks.normalize("javascript:alert(1)"))
        assertNull(UserLinks.normalize("intent://algo#Intent;end"))
        assertNull(UserLinks.normalize("file:///sdcard/a.mp4"))
        assertNull(UserLinks.normalize("https://localhost/x"))
        assertNull(UserLinks.normalize("https://ejemplo.com/a b"))
    }

    @Test
    fun reconoceElTipoDeEnlace() {
        assertEquals(UserLink.Kind.YOUTUBE, UserLinks.kindOf("https://youtu.be/dQw4w9WgXcQ"))
        assertEquals(UserLink.Kind.VIDEO_FILE, UserLinks.kindOf("https://cdn.ejemplo.com/pelicula.m3u8"))
        assertEquals(UserLink.Kind.WEB, UserLinks.kindOf("https://ejemplo.com/ver/123"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", UserLinks.youtubeWatchUrl("https://youtu.be/dQw4w9WgXcQ?t=10"))
        assertEquals("https://www.youtube.com/watch?v=dQw4w9WgXcQ", UserLinks.youtubeWatchUrl("https://m.youtube.com/watch?feature=x&v=dQw4w9WgXcQ"))
        assertEquals("https://www.youtube.com/watch?v=abcDEF12345", UserLinks.youtubeWatchUrl("https://www.youtube.com/shorts/abcDEF12345"))
    }
}
