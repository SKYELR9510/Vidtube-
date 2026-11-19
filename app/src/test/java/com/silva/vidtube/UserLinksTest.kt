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

    @Test
    fun driveYDropboxSeVenEnElReproductor() {
        val drive = "https://drive.google.com/file/d/1AbCdEfGhIjKlMnOp/view?usp=sharing"
        assertEquals(UserLink.Kind.VIDEO_FILE, UserLinks.kindOf(drive))
        assertEquals("https://drive.usercontent.google.com/download?id=1AbCdEfGhIjKlMnOp&export=download&confirm=t", UserLinks.directUrl(drive))
        assertEquals("1AbCdEfGhIjKlMnOp", UserLinks.driveId("https://drive.google.com/open?id=1AbCdEfGhIjKlMnOp"))
        val dbx = "https://www.dropbox.com/s/abc123/pelicula.mp4?dl=0"
        assertEquals(UserLink.Kind.VIDEO_FILE, UserLinks.kindOf(dbx))
        assertEquals("https://www.dropbox.com/s/abc123/pelicula.mp4?raw=1", UserLinks.directUrl(dbx))
        assertEquals("https://cdn.x.com/a.mp4", UserLinks.directUrl("https://cdn.x.com/a.mp4"))
        org.junit.Assert.assertTrue(UserLinks.isYoutube("https://m.youtube.com/watch?v=x"))
        org.junit.Assert.assertFalse(UserLinks.isYoutube(drive))
    }

    @Test
    fun enlacesIptvConCabeceras() {
        val raw = "https://93.ejemplo.com/espn/mono.m3u8?token=abc|User-Agent=VLC/3.0.20 LibVLC&Referer=https://sitio.com/"
        val n = UserLinks.normalize(raw)
        assertEquals(raw, n)
        val (url, h) = UserLinks.splitHeaders(n!!)
        assertEquals("https://93.ejemplo.com/espn/mono.m3u8?token=abc", url)
        assertEquals("VLC/3.0.20 LibVLC", h["User-Agent"])
        assertEquals("https://sitio.com/", h["Referer"])
        assertEquals("93.ejemplo.com", UserLinks.hostOf(n))
        assertEquals(UserLink.Kind.VIDEO_FILE, UserLinks.kindOf(n))
        // Cabeceras no permitidas se ignoran; el enlace sigue siendo válido
        assertEquals("https://a.pe/x.m3u8", UserLinks.normalize("https://a.pe/x.m3u8|Authorization=secreto"))
        // Con espacios en el enlace (antes del «|») no es válido
        assertNull(UserLinks.normalize("https://a.pe/x y.m3u8|User-Agent=VLC"))
    }
}
