package com.silva.vidtube.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.silva.vidtube.R

object VColors {
    val Background = Color(0xFF0F0F0F)
    val Surface = Color(0xFF212121)
    val Chip = Color(0xFF272727)
    val ChipSelected = Color(0xFFF1F1F1)
    val Text = Color(0xFFF1F1F1)
    val Secondary = Color(0xFFAAAAAA)
    val Red = Color(0xFFFF0033)
    val Divider = Color(0xFF303030)
}

private val scheme = darkColorScheme(
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

@Composable
fun VidtubeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = scheme, content = content)
}

/** Logo de Vidtube (símbolo + nombre). */
@Composable
fun VidtubeLogo(modifier: Modifier = Modifier) {
    Image(
        painter = painterResource(R.drawable.vidtube_wordmark),
        contentDescription = "Vidtube",
        contentScale = ContentScale.Fit,
        modifier = modifier.height(26.dp),
    )
}
