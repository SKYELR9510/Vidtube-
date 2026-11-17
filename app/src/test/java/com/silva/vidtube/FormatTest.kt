package com.silva.vidtube

import com.silva.vidtube.ui.durationText
import com.silva.vidtube.ui.joinDot
import com.silva.vidtube.ui.viewsText
import org.junit.Assert.assertEquals
import org.junit.Test

/** Textos que se ven en las tarjetas (duración, vistas, metadatos). */
class FormatTest {
    @Test fun duracion() {
        assertEquals("", durationText(0))
        assertEquals("0:59", durationText(59))
        assertEquals("4:05", durationText(245))
        assertEquals("1:03:50", durationText(3830))
    }

    @Test fun vistas() {
        assertEquals("", viewsText(-1))
        assertEquals("1 vista", viewsText(1))
        assertEquals("999 vistas", viewsText(999))
        assertEquals("1,5 k vistas", viewsText(1_500))
        assertEquals("2,4 M vistas", viewsText(2_400_000))
    }

    @Test fun metadatosSinHuecos() {
        assertEquals("Canal · 10 k vistas", joinDot("Canal", null, "", "10 k vistas"))
    }
}
