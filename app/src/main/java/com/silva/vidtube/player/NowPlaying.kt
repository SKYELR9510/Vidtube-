package com.silva.vidtube.player

import android.content.Context
import android.os.PowerManager
import android.util.Log
import com.silva.vidtube.data.Library
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.AgeRestrictedContentException
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.exceptions.GeographicRestrictionException
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import org.schabi.newpipe.extractor.exceptions.SignInConfirmNotBotException
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.Timeline
import java.io.IOException

/** Qué se está viendo ahora y si la pantalla del video está abierta o minimizada. */
object NowPlaying {

    data class State(
        val video: VideoItem,
        val info: StreamInfo? = null,
        val loading: Boolean = true,
        val error: String? = null,
        val quality: Int = 720,
        val qualities: List<Int> = emptyList(),
    )

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var loadJob: Job? = null
    private var saveJob: Job? = null

    private val _state = MutableStateFlow<State?>(null)
    val state: StateFlow<State?> = _state.asStateFlow()

    private val _expanded = MutableStateFlow(false)
    val expanded: StateFlow<Boolean> = _expanded.asStateFlow()

    /** Videos ya reproducidos en esta sesión, para que la reproducción automática no repita. */
    private val played = LinkedHashSet<String>()
    private var listenerAttached = false

    private val _upNext = MutableStateFlow<VideoItem?>(null)
    /** Video que sonará a continuación (reproducción automática). */
    val upNext: StateFlow<VideoItem?> = _upNext.asStateFlow()

    /** Info del siguiente video ya cargada por adelantado, para que el cambio sea inmediato. */
    private var prefetched: Pair<String, StreamInfo>? = null
    private var prefetchJob: Job? = null
    private var lastInfo: StreamInfo? = null
    private var autoFailures = 0

    fun open(context: Context, video: VideoItem, expand: Boolean = true, auto: Boolean = false) {
        val appContext = context.applicationContext
        attachListener(appContext)
        val current = _state.value
        if (expand) _expanded.value = true
        if (current != null && current.video.url == video.url && current.error == null) return

        saveCurrentPosition()
        // Recordamos el video anterior para el botón ⏮
        if (current != null && current.video.url != video.url && current.video.title.isNotBlank()) {
            if (backStack.lastOrNull()?.url != current.video.url) backStack.addLast(current.video)
            while (backStack.size > 30) backStack.removeFirst()
        }
        PlayerHolder.playerOrNull?.stop()
        val quality = Library.settings.value.quality
        _state.value = State(video = video, quality = quality)
        _upNext.value = null

        loadJob?.cancel()
        loadJob = scope.launch {
            try {
                val cached = prefetched?.takeIf { it.first == video.url }?.second
                prefetched = null
                val info = cached ?: Repository.stream(video.url)
                val fromInfo = VideoItem.from(info)
                // Si la respuesta trae pocos datos, completamos con lo que ya mostraba la lista
                val full = fromInfo.copy(
                    title = fromInfo.title.ifBlank { video.title },
                    thumbnail = fromInfo.thumbnail ?: video.thumbnail,
                    channelName = fromInfo.channelName.ifBlank { video.channelName },
                    channelUrl = fromInfo.channelUrl ?: video.channelUrl,
                    channelAvatar = fromInfo.channelAvatar ?: video.channelAvatar,
                    views = if (fromInfo.views >= 0) fromInfo.views else video.views,
                    uploaded = fromInfo.uploaded ?: video.uploaded,
                )
                _state.value = State(
                    video = full,
                    info = info,
                    loading = false,
                    quality = quality,
                    qualities = PlayerHolder.availableQualities(info),
                )
                Library.addToHistory(full)
                played.remove(full.url)
                played.add(full.url)
                while (played.size > 100) played.remove(played.first())
                lastInfo = info
                Library.addRecommendations(
                    info.relatedItems.filterIsInstance<StreamInfoItem>().map { VideoItem.from(it) }
                )
                val next = pickNext(info)
                _upNext.value = next
                PlayerHolder.play(appContext, info, quality, Library.position(full.url))
                startStallWatch(appContext, info, quality)
                autoFailures = 0
                com.silva.vidtube.update.Updater.reportPlaybackResult(success = true, networkError = false)
                releaseWakeLock()
                startPositionSaver()
                prefetch(next)
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("Vidtube", "No se pudo cargar el video", e)
                _state.value = _state.value?.copy(loading = false, error = friendlyError(e))
                com.silva.vidtube.update.Updater.reportPlaybackResult(
                    success = false,
                    networkError = e is IOException || e is ReCaptchaException ||
                        e is AgeRestrictedContentException || e is GeographicRestrictionException,
                )
                // En reproducción automática, si un video falla probamos con el siguiente
                if (auto && autoFailures < 3) {
                    autoFailures++
                    played.add(video.url)
                    val fallback = lastInfo?.let { pickNext(it) }
                    if (fallback != null) open(appContext, fallback, expand = false, auto = true)
                    else releaseWakeLock()
                } else {
                    releaseWakeLock()
                }
            }
        }
    }

    private fun attachListener(context: Context) {
        if (listenerAttached) return
        listenerAttached = true
        PlayerHolder.player(context).addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED && !PlayerHolder.shortsMode) onEnded(context)
            }

            // El botón ■ de la notificación vacía el reproductor: cerramos el video
            // para no dejar una pantalla de reproductor sin nada que reproducir.
            override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                if (PlayerHolder.shortsMode) return
                val s = _state.value ?: return
                if (timeline.isEmpty && !s.loading && s.info != null) close(context)
            }
        })
    }

    private var transitionLock: PowerManager.WakeLock? = null

    /** Con la pantalla bloqueada, evita que el celular se duerma mientras carga el siguiente video. */
    private fun holdWakeLock(context: Context) {
        val lock = transitionLock ?: context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Vidtube:autoplay")
            .also { it.setReferenceCounted(false); transitionLock = it }
        lock.acquire(90_000)
    }

    private fun releaseWakeLock() {
        transitionLock?.let { if (it.isHeld) it.release() }
    }

    private fun onEnded(context: Context) {
        val url = _state.value?.video?.url
        if (url != null) Library.savePosition(url, Long.MAX_VALUE, Long.MAX_VALUE)
        if (!Library.settings.value.autoplay) return
        val next = _upNext.value ?: return
        holdWakeLock(context)
        open(context, next, expand = false, auto = true)
    }

    private var stallJob: Job? = null

    /**
     * Si el video se queda "cargando" sin avanzar, probamos otra forma de cargarlo;
     * si ninguna funciona, mostramos el error real en vez de girar para siempre.
     */
    private fun startStallWatch(context: Context, info: StreamInfo, quality: Int) {
        stallJob?.cancel()
        stallJob = scope.launch {
            var stuck = 0
            var lastPos = -1L
            while (isActive) {
                delay(1_000)
                val p = PlayerHolder.playerOrNull ?: break
                if (_state.value?.info !== info) break
                val pos = p.currentPosition
                val waiting = p.playWhenReady && p.playbackState == Player.STATE_BUFFERING
                if (waiting && pos == lastPos) stuck++ else stuck = 0
                lastPos = pos
                if (stuck >= 12) {
                    stuck = 0
                    Log.w("Vidtube", "Video atascado (${PlayerHolder.lastLoadError}); probando otro modo")
                    val retried = runCatching { PlayerHolder.retryWithFallback(context, info, quality) }
                        .getOrDefault(false)
                    if (!retried) {
                        val detail = PlayerHolder.lastLoadError?.let { " ($it)" } ?: ""
                        _state.value = _state.value?.copy(
                            error = "El video no termina de cargar$detail. Revisa tu internet o prueba otra calidad."
                        )
                        break
                    }
                }
            }
        }
    }

    private fun prefetch(next: VideoItem?) {
        prefetchJob?.cancel()
        if (next == null || !Library.settings.value.autoplay) return
        prefetchJob = scope.launch {
            runCatching { Repository.stream(next.url) }
                .onSuccess { prefetched = next.url to it }
        }
    }

    /** Siguiente video: el primer relacionado que no se haya visto en esta sesión. */
    private fun pickNext(info: StreamInfo): VideoItem? =
        info.relatedItems
            .filterIsInstance<StreamInfoItem>()
            .map { VideoItem.from(it) }
            .firstOrNull { !it.isLive && !it.isShort && it.url !in played && it.durationSec > 0 }

    private val backStack = ArrayDeque<VideoItem>()

    /** ⏮: si ya avanzó unos segundos vuelve al inicio; si no, al video anterior. */
    fun playPrevious(context: Context) {
        val p = PlayerHolder.playerOrNull
        if (p != null && p.currentPosition > 5_000) {
            p.seekTo(0)
            return
        }
        val prev = backStack.removeLastOrNull()
        if (prev == null) {
            p?.seekTo(0)
            return
        }
        // Evita que el video actual vuelva a meterse en la pila
        val currentUrl = _state.value?.video?.url
        open(context, prev, expand = false)
        if (backStack.lastOrNull()?.url == currentUrl) backStack.removeLast()
    }

    fun playNext(context: Context) {
        val next = _upNext.value ?: return
        open(context, next, expand = false)
    }

    fun openUrl(context: Context, url: String) {
        open(
            context,
            VideoItem(
                url = url, title = "", thumbnail = null, channelName = "", channelUrl = null,
                channelAvatar = null, durationSec = -1, views = -1, uploaded = null, isLive = false,
            )
        )
    }

    fun retry(context: Context) {
        val video = _state.value?.video ?: return
        _state.value = null
        open(context, video)
    }

    fun setQuality(context: Context, quality: Int) {
        val s = _state.value ?: return
        val info = s.info ?: return
        _state.value = s.copy(quality = quality)
        val appContext = context.applicationContext
        scope.launch {
            runCatching { PlayerHolder.changeQuality(appContext, info, quality) }
                .onFailure { Log.e("Vidtube", "No se pudo cambiar la calidad", it) }
        }
    }

    fun expand() {
        if (_state.value != null) _expanded.value = true
    }

    fun minimize() {
        _expanded.value = false
    }

    fun close(context: Context) {
        saveCurrentPosition()
        loadJob?.cancel()
        saveJob?.cancel()
        stallJob?.cancel()
        prefetchJob?.cancel()
        prefetched = null
        // Primero limpiamos el estado para que el aviso del reproductor no vuelva a llamar aquí
        _expanded.value = false
        _state.value = null
        _upNext.value = null
        PlayerHolder.stop(context.applicationContext)
        releaseWakeLock()
    }

    fun saveCurrentPosition() {
        val url = _state.value?.video?.url ?: return
        val p = PlayerHolder.playerOrNull ?: return
        if (_state.value?.video?.isLive == true) return
        Library.savePosition(url, p.currentPosition, p.duration)
    }

    private fun startPositionSaver() {
        saveJob?.cancel()
        saveJob = scope.launch {
            while (isActive) {
                delay(10_000)
                if (PlayerHolder.playerOrNull?.isPlaying == true) saveCurrentPosition()
            }
        }
    }

    private fun friendlyError(e: Throwable): String = when (e) {
        is SignInConfirmNotBotException -> "YouTube está pidiendo confirmar que no eres un bot. Espera un rato o cambia de red (WiFi/datos)."
        is AgeRestrictedContentException -> "Este video tiene restricción de edad y no se puede ver sin iniciar sesión."
        is GeographicRestrictionException -> "Este video no está disponible en tu país."
        is ReCaptchaException -> "YouTube pidió verificación (captcha). Espera unos minutos e intenta de nuevo."
        is ContentNotAvailableException -> e.message ?: "Este video no está disponible."
        is IOException -> "Sin conexión. Revisa tu internet e intenta de nuevo."
        else -> "No se pudo reproducir este video. (${e.javaClass.simpleName})"
    }
}
