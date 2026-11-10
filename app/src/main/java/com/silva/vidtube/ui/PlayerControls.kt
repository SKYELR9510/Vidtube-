package com.silva.vidtube.ui

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.outlined.ClosedCaption
import androidx.compose.material.icons.outlined.ClosedCaptionDisabled
import androidx.compose.material.icons.outlined.Forward10
import androidx.compose.material.icons.outlined.Fullscreen
import androidx.compose.material.icons.outlined.FullscreenExit
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Replay10
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ZoomInMap
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.android.exoplayer2.Player
import com.silva.vidtube.player.PlayerHolder
import kotlinx.coroutines.delay

/**
 * Controles del reproductor: botones de cristal flotantes, ±10 s, barra de progreso neón,
 * subtítulos, calidad y pantalla completa. Toque = mostrar/ocultar; doble toque a los lados = ±10 s.
 */
@Composable
fun PlayerControls(
    fullscreen: Boolean,
    title: String,
    qualityLabel: String?,
    onClose: () -> Unit,
    onFullscreen: () -> Unit,
    onQuality: (() -> Unit)?,
    onPrevious: (() -> Unit)?,
    onNext: (() -> Unit)?,
    zoom: Boolean = false,
    onZoom: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val player = remember { PlayerHolder.player(context) }
    var visible by remember { mutableStateOf(true) }
    var touchedAt by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }
    var buffered by remember { mutableLongStateOf(0L) }
    var playing by remember { mutableStateOf(player.isPlaying) }
    var wantsPlay by remember { mutableStateOf(player.playWhenReady) }
    var buffering by remember { mutableStateOf(false) }
    var seekFlash by remember { mutableStateOf<String?>(null) }
    var seekFlashLeft by remember { mutableStateOf(false) }
    val subsOn by PlayerHolder.subtitlesOn.collectAsState()
    val subsLang by PlayerHolder.subtitleLanguage.collectAsState()
    val zoomCb by rememberUpdatedState(onZoom)

    fun poke() { touchedAt = System.currentTimeMillis() }

    LaunchedEffect(player) {
        while (true) {
            position = player.currentPosition.coerceAtLeast(0)
            duration = player.duration.takeIf { it > 0 } ?: 0L
            buffered = player.bufferedPosition
            playing = player.isPlaying
            wantsPlay = player.playWhenReady
            buffering = player.playbackState == Player.STATE_BUFFERING
            // Se ocultan solos a los 3,5 s mientras reproduce
            if (visible && playing && System.currentTimeMillis() - touchedAt > 3500) visible = false
            delay(200)
        }
    }
    LaunchedEffect(seekFlash) { if (seekFlash != null) { delay(650); seekFlash = null } }

    fun seekBy(ms: Long) {
        val target = (player.currentPosition + ms).coerceIn(0, if (duration > 0) duration else Long.MAX_VALUE)
        player.seekTo(target)
        seekFlashLeft = ms < 0
        seekFlash = if (ms < 0) "−10 s" else "+10 s"
        poke()
    }

    Box(
        modifier
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { visible = !visible; poke() },
                    onDoubleTap = { off -> seekBy(if (off.x < size.width / 2) -10_000 else 10_000) },
                )
            }
            .pointerInput(fullscreen) {
                if (!fullscreen) return@pointerInput
                // Pellizcar: abrir = llenar pantalla, cerrar = ajustar
                var scale = 1f
                detectTransformGestures { _, _, z, _ ->
                    scale *= z
                    if (scale > 1.12f) { zoomCb?.invoke(true); scale = 1f }
                    else if (scale < 0.88f) { zoomCb?.invoke(false); scale = 1f }
                }
            }
    ) {
        // Aviso de ±10 s
        seekFlash?.let { msg ->
            Text(
                msg,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                modifier = Modifier
                    .align(if (seekFlashLeft) Alignment.CenterStart else Alignment.CenterEnd)
                    .padding(horizontal = 36.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x66000000))
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }
        if (buffering && !visible) {
            CircularProgressIndicator(
                color = VColors.Red, strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(42.dp),
            )
        }

        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0f to Color(0x99000000), 0.3f to Color(0x33000000),
                            0.7f to Color(0x33000000), 1f to Color(0xB3000000),
                        )
                    )
            ) {
                // Arriba
                Row(
                    Modifier.fillMaxWidth().align(Alignment.TopCenter)
                        .padding(horizontal = if (fullscreen) 20.dp else 4.dp, vertical = if (fullscreen) 12.dp else 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconCircle(
                        if (fullscreen) Icons.AutoMirrored.Filled.ArrowBack else Icons.Outlined.KeyboardArrowDown,
                        if (fullscreen) "Salir" else "Minimizar",
                    ) { onClose() }
                    if (fullscreen) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            title, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f),
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                    if (fullscreen && onZoom != null) {
                        IconCircle(if (zoom) Icons.Outlined.ZoomInMap else Icons.Outlined.ZoomOutMap, "Zoom") {
                            onZoom(!zoom); poke()
                        }
                    }
                    IconCircle(
                        if (subsOn) Icons.Outlined.ClosedCaption else Icons.Outlined.ClosedCaptionDisabled,
                        "Subtítulos",
                        tint = if (subsOn) VColors.Red else Color.White,
                    ) {
                        poke()
                        if (subsLang == null) {
                            Toast.makeText(context, "Este video no tiene subtítulos", Toast.LENGTH_SHORT).show()
                        } else {
                            PlayerHolder.setSubtitles(!subsOn)
                            Toast.makeText(
                                context,
                                if (!subsOn) "Subtítulos: $subsLang" else "Subtítulos desactivados",
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    }
                    if (onQuality != null) IconCircle(Icons.Outlined.Settings, "Calidad") { onQuality(); poke() }
                }

                // Centro: botones de cristal
                Row(
                    Modifier.align(Alignment.Center),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (fullscreen) 36.dp else 22.dp),
                ) {
                    if (fullscreen && onPrevious != null) GlassButton(Icons.Filled.SkipPrevious, "Anterior", 46.dp) { onPrevious(); poke() }
                    GlassButton(Icons.Outlined.Replay10, "Retroceder 10 s", 50.dp) { seekBy(-10_000) }
                    if (buffering && wantsPlay) {
                        Box(Modifier.size(68.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = VColors.Red, strokeWidth = 3.dp, modifier = Modifier.size(44.dp))
                        }
                    } else {
                        GlassButton(
                            if (playing) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                            if (playing) "Pausa" else "Reproducir", 68.dp,
                        ) {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                            if (player.isPlaying) player.pause() else player.play()
                            poke()
                        }
                    }
                    GlassButton(Icons.Outlined.Forward10, "Adelantar 10 s", 50.dp) { seekBy(10_000) }
                    if (fullscreen && onNext != null) GlassButton(Icons.Filled.SkipNext, "Siguiente", 46.dp) { onNext(); poke() }
                }

                // Abajo: tiempo, calidad y barra neón
                Column(
                    Modifier.fillMaxWidth().align(Alignment.BottomCenter)
                        .padding(horizontal = if (fullscreen) 24.dp else 10.dp, vertical = if (fullscreen) 14.dp else 0.dp)
                ) {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "${clock(position)} / ${clock(duration)}",
                            color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium,
                        )
                        Spacer(Modifier.weight(1f))
                        if (qualityLabel != null) {
                            Text(
                                qualityLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                                modifier = Modifier.clip(RoundedCornerShape(4.dp)).background(VColors.Red)
                                    .padding(horizontal = 5.dp, vertical = 1.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        IconCircle(
                            if (fullscreen) Icons.Outlined.FullscreenExit else Icons.Outlined.Fullscreen,
                            "Pantalla completa", size = 34.dp,
                        ) { onFullscreen() }
                    }
                    NeonSeekBar(position, buffered, duration, thick = fullscreen) { ms ->
                        player.seekTo(ms); poke()
                    }
                }
            }
        }

        // Con los controles ocultos queda una línea fina de progreso (solo en vista normal)
        if (!visible && !fullscreen && duration > 0) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(2.dp).background(Color(0x40FFFFFF)))
            Box(
                Modifier.align(Alignment.BottomStart)
                    .fillMaxWidth((position.toFloat() / duration).coerceIn(0f, 1f))
                    .height(2.dp)
                    .background(VColors.Red)
            )
        }
    }
}

/** Barra de progreso roja con brillo; se toca o arrastra para adelantar. */
@Composable
fun NeonSeekBar(position: Long, buffered: Long, duration: Long, thick: Boolean, onSeek: (Long) -> Unit) {
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var width by remember { mutableFloatStateOf(1f) }
    val fraction = if (dragging) dragFraction
    else if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    val bufferedFraction = if (duration > 0) (buffered.toFloat() / duration).coerceIn(0f, 1f) else 0f

    Box(
        Modifier
            .fillMaxWidth()
            .height(28.dp)
            .pointerInput(duration) {
                width = size.width.toFloat().coerceAtLeast(1f)
                detectTapGestures { off ->
                    if (duration > 0) onSeek((duration * (off.x / width).coerceIn(0f, 1f)).toLong())
                }
            }
            .pointerInput(duration) {
                width = size.width.toFloat().coerceAtLeast(1f)
                detectHorizontalDragGestures(
                    onDragStart = { off -> dragging = true; dragFraction = (off.x / width).coerceIn(0f, 1f) },
                    onDragEnd = {
                        if (duration > 0) onSeek((duration * dragFraction).toLong())
                        dragging = false
                    },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ -> dragFraction = (change.position.x / width).coerceIn(0f, 1f) },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxWidth().height(20.dp)) {
            val y = size.height / 2
            val stroke = (if (thick || dragging) 4.dp else 3.dp).toPx()
            val end = size.width * fraction
            drawLine(Color(0x40FFFFFF), Offset(0f, y), Offset(size.width, y), stroke, StrokeCap.Round)
            drawLine(Color(0x66FFFFFF), Offset(0f, y), Offset(size.width * bufferedFraction, y), stroke, StrokeCap.Round)
            if (end > 0f) {
                val neon = Brush.horizontalGradient(listOf(Color(0xFFFF4D5A), Color(0xFFFF1E2D)), 0f, end.coerceAtLeast(1f))
                // Brillo (capas anchas y transparentes) + línea sólida
                drawLine(VColors.Red.copy(alpha = 0.18f), Offset(0f, y), Offset(end, y), stroke * 4, StrokeCap.Round)
                drawLine(VColors.Red.copy(alpha = 0.35f), Offset(0f, y), Offset(end, y), stroke * 2.2f, StrokeCap.Round)
                drawLine(neon, Offset(0f, y), Offset(end, y), stroke, StrokeCap.Round)
            }
            val r = (if (dragging) 9.dp else 6.dp).toPx()
            drawCircle(VColors.Red.copy(alpha = 0.35f), r * 2f, Offset(end, y))
            drawCircle(VColors.Red, r, Offset(end, y))
            drawCircle(Color.White, r * 0.38f, Offset(end, y))
        }
    }
}

/** Botón redondo de cristal (fondo translúcido con borde claro). */
@Composable
private fun GlassButton(icon: ImageVector, label: String, size: Dp, onClick: () -> Unit) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .background(Brush.verticalGradient(listOf(Color(0x40FFFFFF), Color(0x1AFFFFFF))))
            .border(1.dp, Color(0x55FFFFFF), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color.White, modifier = Modifier.size(size * 0.56f))
    }
}

@Composable
private fun IconCircle(
    icon: ImageVector,
    label: String,
    tint: Color = Color.White,
    size: Dp = 42.dp,
    onClick: () -> Unit,
) {
    Box(Modifier.size(size).clip(CircleShape).clickable(onClick = onClick), contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size * 0.57f))
    }
}

private fun clock(ms: Long): String {
    val t = ms / 1000
    val h = t / 3600
    val m = (t % 3600) / 60
    val s = t % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/** Etiqueta HD / 4K según la calidad. */
fun qualityBadge(q: Int): String? = when {
    q >= 2160 -> "4K"
    q >= 1440 -> "2K"
    q >= 720 -> "HD"
    else -> null
}
