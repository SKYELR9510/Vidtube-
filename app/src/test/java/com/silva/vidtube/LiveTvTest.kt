package com.silva.vidtube

import com.silva.vidtube.data.PanelLinks
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class LiveTvTest {
    @Test
    fun leeCanalesDelPanel() {
        val o = JSONObject("""{"titles":[],"channels":[
            {"name":"TV Perú","url":"https://ejemplo.pe/live/tvperu.m3u8","logo":"https://ejemplo.pe/logo.png"},
            {"name":"Malo","url":"javascript:alert(1)","logo":null},
            {"name":"Sin logo","url":"https://ejemplo.pe/b.m3u8","logo":"http://inseguro.pe/l.png"}]}""")
        val list = PanelLinks.parseChannels(o)
        assertEquals(listOf("TV Perú", "Sin logo"), list.map { it.name })
        assertEquals(null, list[1].logo)
    }

    @Test
    fun canalConEnlacesDeRespaldo() {
        val o = JSONObject("""{"channels":[{"name":"ESPN","url":"https://a.pe/e.m3u8",
            "backups":["https://b.pe/e2.m3u8","javascript:x","https://a.pe/e.m3u8","https://c.pe/e3.m3u8"],"logo":null}]}""")
        val c = PanelLinks.parseChannels(o).single()
        assertEquals("https://a.pe/e.m3u8", c.url)
        // Se descartan los inválidos y los repetidos; el orden se respeta
        assertEquals(listOf("https://b.pe/e2.m3u8", "https://c.pe/e3.m3u8"), c.backups)
    }

    @Test
    fun canalSoloConYoutubeOficial() {
        val o = JSONObject("""{"channels":[
            {"name":"Willax","url":"","backups":[],"youtube":"https://www.youtube.com/@WillaxTV","logo":null},
            {"name":"Falso","url":"","backups":[],"youtube":"https://evil.com/@x","logo":null}]}""")
        val list = PanelLinks.parseChannels(o)
        assertEquals(listOf("Willax"), list.map { it.name })
        assertEquals("", list[0].url)
        assertEquals("https://www.youtube.com/@WillaxTV", list[0].youtube)
    }

    @Test
    fun canalConCategoriaNacionalYPortada() {
        val o = JSONObject("""{"channels":[
            {"name":"TV Perú","url":"https://a.pe/1.m3u8","backups":[],"category":"Noticias","national":true,"cover":"https://img.pe/tvp.jpg","logo":null},
            {"name":"Viejo","url":"https://a.pe/2.m3u8","logo":null}]}""")
        val list = PanelLinks.parseChannels(o)
        assertEquals("Noticias", list[0].category)
        assertEquals(true, list[0].national)
        assertEquals("https://img.pe/tvp.jpg", list[0].cover)
        // Paneles anteriores (sin estos datos): General, no nacional, sin portada
        assertEquals("General", list[1].category)
        assertEquals(false, list[1].national)
        assertEquals(null, list[1].cover)
        assertEquals("tv perú", list[0].key)
    }
}
