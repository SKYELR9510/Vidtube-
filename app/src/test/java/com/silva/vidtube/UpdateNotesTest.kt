package com.silva.vidtube

import com.silva.vidtube.update.Updater
import org.junit.Assert.assertEquals
import org.junit.Test

/** El aviso de actualización muestra la lista "Por qué se actualiza" de la versión publicada. */
class UpdateNotesTest {
    private fun release(body: String) = Updater.Release(180, "Vidtube 1.3.80", body, "https://github.com/x.apk", 1)

    @Test fun leeLosMotivos() {
        val body = "## Por qué se actualiza\n- Nuevo diseño Coral\n- Arreglo de Shorts\n\n---\nInstala **Vidtube-180.apk**.\n- esto no es un motivo"
        assertEquals(listOf("Nuevo diseño Coral", "Arreglo de Shorts"), release(body).reasons)
    }

    @Test fun sinMotivos() {
        assertEquals(emptyList<String>(), release("Instala la app").reasons)
    }
}
