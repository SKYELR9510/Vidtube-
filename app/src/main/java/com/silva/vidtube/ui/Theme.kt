package com.silva.vidtube.ui

import android.app.Activity
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.silva.vidtube.R
import com.silva.vidtube.data.Library

/** true = modo claro. Lo cambia Configuración › Apariencia. */
internal val lightTheme = mutableStateOf(false)

/**
 * Colores de Vidtube. Cambian solos entre oscuro (predeterminado) y claro:
 * cada pantalla que los usa se redibuja al cambiar de modo.
 */
object VColors {
    private val light get() = lightTheme.value

    val Background get() = if (light) Color(0xFFFFFFFF) else Color(0xFF09090B)
    val Surface get() = if (light) Color(0xFFF3F3F5) else Color(0xFF16161A)
    val Card get() = if (light) Color(0xFFF6F6F8) else Color(0xFF131317)
    val Chip get() = if (light) Color(0xFFEDEDF0) else Color(0xFF212127)
    val ChipSelected get() = Red
    val Text get() = if (light) Color(0xFF121215) else Color(0xFFF5F5F7)
    val Secondary get() = if (light) Color(0xFF606068) else Color(0xFFA7A7B0)
    val Divider get() = if (light) Color(0xFFE2E2E6) else Color(0xFF26262C)

    // Identidad: rojo neón del logo
    val Red = Color(0xFFFF1E2D)
    val RedDeep = Color(0xFFB00014)
    val RedGlow = Color(0x66FF1E2D)

    /** Degradado rojo de los botones principales. */
    val RedGradient get() = Brush.horizontalGradient(listOf(Color(0xFFFF3B3B), Red, RedDeep))
}

@Composable
fun VidtubeTheme(content: @Composable () -> Unit) {
    val settings by Library.settings.collectAsState()
    LaunchedEffect(settings.lightMode) { lightTheme.value = settings.lightMode }
    val light = lightTheme.value

    // Íconos de la barra de estado oscuros en modo claro
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as? Activity)?.window ?: return@SideEffect
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }

    val scheme = if (light) lightColorScheme(
        primary = VColors.Red,
        onPrimary = Color.White,
        secondary = VColors.Text,
        background = VColors.Background,
        onBackground = VColors.Text,
        surface = VColors.Background,
        onSurface = VColors.Text,
        surfaceVariant = VColors.Chip,
        onSurfaceVariant = VColors.Secondary,
        surfaceContainer = VColors.Surface,
        surfaceContainerHigh = VColors.Surface,
        surfaceContainerLow = VColors.Background,
        outline = VColors.Divider,
    ) else darkColorScheme(
        primary = VColors.Red,
        onPrimary = Color.White,
        secondary = VColors.Text,
        background = VColors.Background,
        onBackground = VColors.Text,
        surface = VColors.Background,
        onSurface = VColors.Text,
        surfaceVariant = VColors.Chip,
        onSurfaceVariant = VColors.Secondary,
        surfaceContainer = VColors.Surface,
        surfaceContainerHigh = VColors.Surface,
        surfaceContainerLow = VColors.Background,
        outline = VColors.Divider,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Logo de Vidtube (símbolo + nombre); en modo claro usa la versión con texto oscuro. */
@Composable
fun VidtubeLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(if (lightTheme.value) R.drawable.vidtube_wordmark_light else R.drawable.vidtube_wordmark),
        contentDescription = "Vidtube",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(30.dp),
    )
}
