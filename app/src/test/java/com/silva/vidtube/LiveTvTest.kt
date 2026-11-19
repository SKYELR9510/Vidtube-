package com.silva.vidtube

import com.silva.vidtube.data.LiveTv
import com.silva.vidtube.data.PanelLinks
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LiveTvTest {
    private fun ch(id: String) = LiveTv.peru.first { it.id == id }

    @Test
    fun reconoceLosCanalesOficiales() {
        assertTrue(LiveTv.matches("TVPerú Noticias", ch("tvperu")))
        assertTrue(LiveTv.matches("TV Perú", ch("tvperu")))
        assertTrue(LiveTv.matches("Panamericana Televisión", ch("panamericana")))
        assertTrue(LiveTv.matches("Willax Televisión", ch("willax")))
        assertTrue(LiveTv.matches("ATV", ch("atv")))
        assertTrue(LiveTv.matches("Canal N", ch("canaln")))
        // Retransmisiones o canales parecidos no cuentan
        assertFalse(LiveTv.matches("Satv Gaming", ch("atv")))
        assertFalse(LiveTv.matches("Canal Nueve", ch("canaln")))
        assertFalse(LiveTv.matches("Fans de Exitos", ch("exitosa")))
    }

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
    fun canalesConSenalOficial24h() {
        assertEquals("https://tvgo.americatv.com.pe/", ch("america").liveUrl)
        assertEquals("https://www.latina.pe/tvenvivo", ch("latina").liveUrl)
        assertEquals("https://panamericana.pe/tvenvivo/hd", ch("panamericana").liveUrl)
        // Todas las direcciones son https
        LiveTv.peru.forEach { c -> listOfNotNull(c.liveUrl, c.siteUrl).forEach { assertTrue(it.startsWith("https://")) } }
    }
}
