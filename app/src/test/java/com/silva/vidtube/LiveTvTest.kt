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
}
