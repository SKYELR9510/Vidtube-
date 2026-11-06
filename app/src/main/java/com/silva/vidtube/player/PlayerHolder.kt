package com.silva.vidtube.player

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.MergingMediaSource
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.source.dash.DashMediaSource
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource
import com.google.android.exoplayer2.source.dash.manifest.DashManifestParser
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeOtfDashManifestCreator
import org.schabi.newpipe.extractor.services.youtube.dashmanifestcreators.YoutubeProgressiveDashManifestCreator
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Stream
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.io.ByteArrayInputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Un único reproductor para toda la app (pantalla de video, mini reproductor,
 * imagen en imagen y segundo plano comparten el mismo).
 */
object PlayerHolder {
    private const val TAG = "VidtubePlayer"

    private var _player: ExoPlayer? = null

    fun player(context: Context): ExoPlayer {
        _player?.let { return it }
        val p = ExoPlayer.Builder(context.applicationContext)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            // Mantiene CPU y WiFi despiertos con la pantalla apagada mientras reproduce
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        // Si el servicio (notificación + sesión del carro) se cerró mientras estaba en pausa,
        // lo volvemos a levantar en cuanto se reanuda la reproducción con ▶.
        val appContext = context.applicationContext
        p.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && !shortsMode && !PlaybackService.running) startService(appContext)
            }
        })
        p.addAnalyticsListener(object : com.google.android.exoplayer2.analytics.AnalyticsListener {
            override fun onLoadError(
                eventTime: com.google.android.exoplayer2.analytics.AnalyticsListener.EventTime,
                loadEventInfo: com.google.android.exoplayer2.source.LoadEventInfo,
                mediaLoadData: com.google.android.exoplayer2.source.MediaLoadData,
                error: java.io.IOException,
                wasCanceled: Boolean,
            ) {
                lastLoadError = describe(error)
                Log.w(TAG, "Error de carga: $lastLoadError", error)
            }
        })
        _player = p
        return p
    }

    val playerOrNull: ExoPlayer? get() = _player

    // Fábricas de datos específicas de YouTube (evitan el estrangulamiento de velocidad)
    private val ytDash = YoutubeHttpDataSource.Factory()
        .setRangeParameterEnabled(true)
        .setRnParameterEnabled(true)
    private val ytProgressive = YoutubeHttpDataSource.Factory()
        .setRangeParameterEnabled(false)
        .setRnParameterEnabled(true)
    private val tvSource = com.google.android.exoplayer2.upstream.DefaultHttpDataSource.Factory()
        .setUserAgent(com.silva.vidtube.data.KidsFallback.TV_USER_AGENT)
        .setAllowCrossProtocolRedirects(true)
    private val ytHls = YoutubeHttpDataSource.Factory()
        .setRangeParameterEnabled(false)
        .setRnParameterEnabled(false)

    /** Alturas disponibles para el menú de calidad. */
    fun availableQualities(info: StreamInfo): List<Int> {
        val heights = (info.videoOnlyStreams + info.videoStreams)
            .map { it.height }
            .filter { it > 0 }
        return heights.distinct().sortedDescending()
    }

    /**
     * Prepara y reproduce el video con la calidad pedida.
     * La fuente se arma en segundo plano porque algunos formatos necesitan consultar a YouTube.
     */
    /** true mientras se ven Shorts: sin notificación ni segundo plano, en bucle. */
    @Volatile
    var shortsMode = false

    /** Reproduce un Short: en bucle, sin servicio en segundo plano. */
    suspend fun playShort(context: Context, info: StreamInfo) {
        val source = withContext(Dispatchers.IO) { buildSource(info, 720) }
        val p = player(context)
        shortsMode = true
        p.repeatMode = Player.REPEAT_MODE_ONE
        p.setMediaSource(source, 0L)
        p.prepare()
        p.playWhenReady = true
    }

    fun stopShorts(context: Context) {
        if (!shortsMode) return
        shortsMode = false
        _player?.let {
            it.repeatMode = Player.REPEAT_MODE_OFF
            it.stop()
            it.clearMediaItems()
        }
    }

    suspend fun play(context: Context, info: StreamInfo, quality: Int, startMs: Long) {
        sourceMode = 0
        lastLoadError = null
        val source = withContext(Dispatchers.IO) { buildSource(info, quality) }
        val p = player(context)
        shortsMode = false
        p.repeatMode = Player.REPEAT_MODE_OFF
        p.setMediaSource(source, startMs)
        p.prepare()
        p.playWhenReady = true
        startService(context)
    }

    /** Cambia la calidad manteniendo la posición. */
    suspend fun changeQuality(context: Context, info: StreamInfo, quality: Int) {
        val source = withContext(Dispatchers.IO) { buildSource(info, quality) }
        val p = player(context)
        val pos = p.currentPosition
        val wasPlaying = p.playWhenReady
        p.setMediaSource(source, pos)
        p.prepare()
        p.playWhenReady = wasPlaying
    }

    fun stop(context: Context) {
        _player?.let {
            it.stop()
            it.clearMediaItems()
        }
        context.stopService(Intent(context, PlaybackService::class.java))
    }

    private fun startService(context: Context) {
        runCatching {
            context.startService(Intent(context, PlaybackService::class.java))
        }.onFailure { Log.w(TAG, "No se pudo iniciar el servicio de reproducción", it) }
    }

    private fun describe(e: Throwable): String {
        var c: Throwable? = e
        while (c != null) {
            if (c is com.google.android.exoplayer2.upstream.HttpDataSource.InvalidResponseCodeException) {
                return "HTTP ${c.responseCode}"
            }
            c = c.cause
        }
        return "${e.javaClass.simpleName}: ${e.message ?: ""}".take(120)
    }

    // ------------------------------------------------------------------
    // Construcción de la fuente de medios (basado en el resolvedor de NewPipe)
    // ------------------------------------------------------------------

    /**
     * Modo de armado de la fuente:
     * 0 = normal (video + audio por separado, con manifiesto DASH)
     * 1 = video + audio por separado, como descarga directa (sin manifiesto)
     * 2 = solo el formato con audio incluido (360p, el más compatible)
     */
    @Volatile
    var sourceMode = 0
        private set

    /** Último error de red al cargar el video (para mostrarlo si no arranca). */
    @Volatile
    var lastLoadError: String? = null
        private set

    /** Prueba el siguiente modo de carga. Devuelve false si ya no quedan modos. */
    suspend fun retryWithFallback(context: Context, info: StreamInfo, quality: Int): Boolean {
        if (sourceMode >= 2) return false
        sourceMode++
        val source = withContext(Dispatchers.IO) { buildSource(info, quality) }
        val p = player(context)
        val pos = p.currentPosition
        p.setMediaSource(source, pos)
        p.prepare()
        p.playWhenReady = true
        return true
    }

    private fun buildSource(info: StreamInfo, quality: Int): MediaSource {
        val type = info.streamType
        if (type == StreamType.LIVE_STREAM || type == StreamType.AUDIO_LIVE_STREAM) {
            if (info.hlsUrl.isNotEmpty()) {
                return HlsMediaSource.Factory(ytHls)
                    .createMediaSource(MediaItem.fromUri(info.hlsUrl))
            }
            if (info.dashMpdUrl.isNotEmpty()) {
                return DashMediaSource.Factory(DefaultDashChunkSource.Factory(ytDash), ytDash)
                    .createMediaSource(MediaItem.fromUri(info.dashMpdUrl))
            }
            throw IllegalStateException("Esta transmisión en vivo no se puede reproducir")
        }

        val video = if (sourceMode >= 2) null else pickVideoOnly(info.videoOnlyStreams, quality)
        val audio = pickAudio(info.audioStreams)

        if (video != null && audio != null) {
            return MergingMediaSource(
                true,
                youtubeSource(video, info),
                youtubeSource(audio, info),
            )
        }

        // Respaldo: video con audio incluido (normalmente 360p)
        val muxed = info.videoStreams
            .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP }
            .sortedByDescending { it.height }
            .firstOrNull { it.height <= quality }
            ?: info.videoStreams.firstOrNull()
        if (muxed != null) return youtubeSource(muxed, info)

        if (audio != null) return youtubeSource(audio, info)

        if (sourceMode >= 2 && info.hlsUrl.isEmpty()) {
            val v = pickVideoOnly(info.videoOnlyStreams, quality)
            if (v != null) return youtubeSource(v, info)
        }
        if (info.hlsUrl.isNotEmpty()) {
            return HlsMediaSource.Factory(ytHls).createMediaSource(MediaItem.fromUri(info.hlsUrl))
        }
        throw IllegalStateException("No se encontró un formato reproducible para este video")
    }

    private fun pickVideoOnly(streams: List<VideoStream>, quality: Int): VideoStream? {
        val usable = streams.filter { it.height > 0 }
        if (usable.isEmpty()) return null
        // H.264 en MP4 funciona en todos los celulares; luego VP9/AV1 como respaldo
        val avc = usable.filter {
            it.format == MediaFormat.MPEG_4 && (it.codec?.startsWith("avc") ?: true)
        }
        val pool = avc.ifEmpty { usable.filter { it.format == MediaFormat.WEBM }.ifEmpty { usable } }
        val underLimit = pool.filter { it.height <= quality }
        val best = if (underLimit.isNotEmpty()) underLimit.maxOf { it.height } else pool.minOf { it.height }
        return pool.filter { it.height == best }
            .maxByOrNull { it.fps * 10_000 + it.bitrate / 1000 }
    }

    private fun pickAudio(streams: List<AudioStream>): AudioStream? {
        if (streams.isEmpty()) return null
        // Prefiere la pista original (no dobladas) y M4A por compatibilidad
        val original = streams.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { streams }
        val m4a = original.filter { it.format == MediaFormat.M4A }.ifEmpty { original }
        return m4a.maxByOrNull { it.averageBitrate }
    }

    private fun youtubeSource(stream: Stream, info: StreamInfo): MediaSource {
        val item = MediaItem.Builder()
            .setUri(Uri.parse(stream.content))
            .setCustomCacheKey(info.id + ":" + stream.id)
            .build()

        // Videos de niños cargados como Smart TV: se piden con el "navegador" de la TV
        if (com.silva.vidtube.data.KidsFallback.isTvStream(info.id)) {
            return ProgressiveMediaSource.Factory(tvSource).createMediaSource(item)
        }

        val isAdaptive = (stream is VideoStream && stream.isVideoOnly) || stream is AudioStream
        val itag = stream.itagItem

        return when (stream.deliveryMethod) {
            DeliveryMethod.PROGRESSIVE_HTTP -> {
                if (isAdaptive && itag != null && sourceMode == 0) {
                    try {
                        val manifest = YoutubeProgressiveDashManifestCreator
                            .fromProgressiveStreamingUrl(stream.content, itag, info.duration)
                        dashFromString(manifest, item)
                    } catch (e: Exception) {
                        Log.w(TAG, "Fallo el manifiesto DASH, uso progresivo", e)
                        ProgressiveMediaSource.Factory(ytProgressive).createMediaSource(item)
                    }
                } else {
                    ProgressiveMediaSource.Factory(ytProgressive).createMediaSource(item)
                }
            }
            DeliveryMethod.DASH -> {
                requireNotNull(itag) { "Falta información del formato" }
                val manifest = YoutubeOtfDashManifestCreator
                    .fromOtfStreamingUrl(stream.content, itag, info.duration)
                dashFromString(manifest, item)
            }
            DeliveryMethod.HLS -> HlsMediaSource.Factory(ytHls).createMediaSource(item)
            else -> throw IllegalStateException("Formato no soportado: ${stream.deliveryMethod}")
        }
    }

    private fun dashFromString(manifest: String, item: MediaItem): MediaSource {
        val uri = item.localConfiguration?.uri ?: Uri.EMPTY
        val parsed = DashManifestParser().parse(uri, ByteArrayInputStream(manifest.toByteArray()))
        return DashMediaSource.Factory(DefaultDashChunkSource.Factory(ytDash), ytDash)
            .createMediaSource(parsed, item)
    }
}
