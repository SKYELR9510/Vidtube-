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

    // Identidad "Coral Eléctrico": fondo azul-noche, superficies grafito y coral solo como acento
    val Background get() = if (light) Color(0xFFF6F7FA) else Color(0xFF090B12)
    val Surface get() = if (light) Color(0xFFFFFFFF) else Color(0xFF171B25)
    val Card get() = if (light) Color(0xFFFFFFFF) else Color(0xFF131722)
    val Raise get() = if (light) Color(0xFFFFFFFF) else Color(0xFF1C2130)
    /** Barra inferior y superficies de navegación (grafito oscuro). */
    val NavBar get() = if (light) Color(0xFFFFFFFF) else Color(0xFF10131B)
    val Chip get() = if (light) Color(0xFFECEEF2) else Color(0xFF171B25)
    val ChipSelected get() = Coral
    val Text get() = if (light) Color(0xFF12141B) else Color(0xFFF5F6FA)
    val Secondary get() = if (light) Color(0xFF555B66) else Color(0xFFA6ADBA)
    val Muted get() = if (light) Color(0xFF6E7480) else Color(0xFF7F8695)
    val Divider get() = if (light) Color(0xFFE2E4EA) else Color(0xFF292D38)
    val Silver get() = Color(0xFFA6ADBA)
    /** Velo oscuro para leer texto sobre miniaturas. */
    val Scrim = Color(0xFF090B12)

    val Coral = Color(0xFFFF405B)
    val Coral2 = Color(0xFFFF875E)
    // Nombres anteriores (se usan en toda la app): ahora son el coral
    val Red = Coral
    val RedDeep = Color(0xFFD92E48)
    val RedGlow = Color(0x40FF405B)

    /** Botón principal: coral con un toque del coral secundario (sutil). */
    val RedGradient get() = Brush.linearGradient(listOf(Coral, Color(0xFFFF5A5C)))
    val CoralGradient get() = RedGradient
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
