package com.silva.vidtube.ui

import android.view.ScaleGestureDetector
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.silva.vidtube.player.PlayerHolder
import kotlinx.coroutines.delay

/** Vista de video de ExoPlayer conectada al reproductor compartido. */
@Composable
fun PlayerSurface(
    modifier: Modifier = Modifier,
    useController: Boolean = true,
    onFullscreen: (() -> Unit)? = null,
    zoom: Boolean = false,
    onZoomChange: ((Boolean) -> Unit)? = null,
) {
    val context = LocalContext.current
    val player = remember { PlayerHolder.player(context) }
    val zoomCallback = rememberUpdatedState(onZoomChange)
    AndroidView(
        factory = { ctx ->
            StyledPlayerView(ctx).apply {
                // Pellizcar con dos dedos: abrir = llenar pantalla, cerrar = ajustar
                var scale = 1f
                val detector = ScaleGestureDetector(ctx, object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
                    override fun onScaleBegin(d: ScaleGestureDetector): Boolean {
                        scale = 1f
                        return zoomCallback.value != null
                    }

                    override fun onScale(d: ScaleGestureDetector): Boolean {
                        scale *= d.scaleFactor
                        return true
                    }

                    override fun onScaleEnd(d: ScaleGestureDetector) {
                        if (scale > 1.08f) zoomCallback.value?.invoke(true)
                        else if (scale < 0.92f) zoomCallback.value?.invoke(false)
                    }
                })
                setOnTouchListener { _, ev ->
                    detector.onTouchEvent(ev)
                    // Mientras se pellizca no mostramos/ocultamos los controles
                    detector.isInProgress || ev.pointerCount > 1
                }
                layoutParams = FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                setBackgroundColor(android.graphics.Color.BLACK)
                resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                setShowNextButton(false)
                setShowPreviousButton(false)
                setShowShuffleButton(false)
                setShowSubtitleButton(false)
                // La ruedita la dibujan nuestros controles (si no, salían dos girando)
                setShowBuffering(StyledPlayerView.SHOW_BUFFERING_NEVER)
                keepScreenOn = true
                this.useController = useController
                this.player = player
                if (onFullscreen != null) {
                    setFullscreenButtonClickListener { onFullscreen() }
                }
            }
        },
        update = { view ->
            if (view.player !== player) view.player = player
            view.useController = useController
            view.resizeMode = if (zoom) AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            else AspectRatioFrameLayout.RESIZE_MODE_FIT
        },
        onRelease = { view -> view.player = null },
        modifier = modifier,
    )
}

/** Estado observable del reproductor (reproduciendo, progreso, error). */
class PlayerUiState {
    var isPlaying by mutableStateOf(false)
    var progress by mutableFloatStateOf(0f)
    var error by mutableStateOf<String?>(null)
}

@Composable
fun rememberPlayerUiState(): PlayerUiState {
    val context = LocalContext.current
    val player: ExoPlayer = remember { PlayerHolder.player(context) }
    val state = remember { PlayerUiState() }
    DisposableEffect(player) {
        state.isPlaying = player.isPlaying
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                state.isPlaying = isPlaying
            }

            override fun onPlayerError(error: PlaybackException) {
                state.error = "Error de reproducción (${error.errorCodeName})"
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) state.error = null
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }
    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            state.progress = if (d > 0) (player.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
            delay(500)
        }
    }
    return state
}
