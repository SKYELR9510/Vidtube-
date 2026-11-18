package com.silva.vidtube

import com.silva.vidtube.data.PanelLinks
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PanelLinksTest {
    @Test
    fun leeLaRespuestaDelPanel() {
        // Respuesta real del panel probada con PHP
        val body = """{"version":"2026-11-18 17:45:42","count":3,"titles":[
          {"kind":"tv","tmdb_id":1396,"title":"Breaking Bad","year":2008,"poster":null,"links":[{"name":"cdn.ejemplo.com","url":"https://cdn.ejemplo.com/bb1.mp4"}]},
          {"kind":"movie","tmdb_id":603,"title":"Matrix","year":1999,"poster":"/abc.jpg","links":[
             {"name":"Latino","url":"https://drive.google.com/file/d/1AbCdEfGhIjKlMnOp/view"},
             {"name":"malo","url":"javascript:alert(1)"}]}
        ]}"""
        val list = PanelLinks.parse(JSONObject(body))
        assertEquals(2, list.size)
        assertTrue(list[0].tv)
        val matrix = list[1]
        assertEquals(603L, matrix.tmdbId)
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", matrix.poster)
        assertEquals(1, matrix.links.size) // el enlace javascript: se descarta
        assertTrue(matrix.links[0].fromPanel)
    }

    @Test
    fun direccionDelPanel() {
        assertEquals("https://midominio.com/vidtube-panel/api.php", PanelLinks.apiUrl("https://midominio.com/vidtube-panel/"))
        assertEquals("https://midominio.com/p/api.php", PanelLinks.apiUrl("midominio.com/p/api.php"))
        assertNull(PanelLinks.apiUrl("http://midominio.com/p")) // la clave no viaja sin https
    }
}
