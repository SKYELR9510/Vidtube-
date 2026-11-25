package com.silva.vidtube.ui

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tamaños de pantalla: celular, tablet y pantallas grandes (tablet horizontal, Chromebook…).
 * Así la app se acomoda a cada equipo en lugar de estirar todo a lo ancho.
 */
object Adaptive {
    /** Cuántas columnas de videos caben en [widthDp] (cada tarjeta mide unos 300–420 dp). */
    fun columnsFor(widthDp: Int): Int = when {
        widthDp >= 1280 -> 4
        widthDp >= 900 -> 3
        widthDp >= 600 -> 2
        else -> 1
    }

    /** Tablet o pantalla grande (el lado más corto mide 600 dp o más). */
    fun isTablet(smallestWidthDp: Int): Boolean = smallestWidthDp >= 600

    /** Ancho suficiente para mostrar el video y la lista de sugeridos lado a lado. */
    fun twoPane(widthDp: Int): Boolean = widthDp >= 900
}

@Composable
fun videoColumns(): Int = Adaptive.columnsFor(LocalConfiguration.current.screenWidthDp)

@Composable
fun isTablet(): Boolean = Adaptive.isTablet(LocalConfiguration.current.smallestScreenWidthDp)

/** Contenido de lectura (fichas, ajustes, listas): centrado y con un ancho máximo cómodo. */
fun Modifier.readableWidth(max: Dp = 840.dp): Modifier =
    fillMaxWidth().wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = max)
