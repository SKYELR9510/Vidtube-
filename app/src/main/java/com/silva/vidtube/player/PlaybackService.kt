package com.silva.vidtube.player

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.Bundle
import android.os.IBinder
import android.os.ResultReceiver
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ForwardingPlayer
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.ext.mediasession.MediaSessionConnector
import com.google.android.exoplayer2.ui.PlayerNotificationManager
import com.silva.vidtube.MainActivity
import com.silva.vidtube.R
import com.silva.vidtube.VidtubeApp
import com.silva.vidtube.data.DownloaderImpl
import com.silva.vidtube.data.Library
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/**
 * Mantiene la reproducción viva con la pantalla apagada y publica una "sesión multimedia":
 * así el Bluetooth del carro, los audífonos y la pantalla de bloqueo ven qué suena
 * y pueden pausar, adelantar o pasar al siguiente.
 */
class PlaybackService : Service() {

    private var notificationManager: PlayerNotificationManager? = null
    private var mediaSession: MediaSessionCompat? = null
    private var connector: MediaSessionConnector? = null
    private val imageExecutor = Executors.newSingleThreadExecutor()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var isForeground = false

    // Carátula del video actual (miniatura)
    @Volatile private var artUrl: String? = null
    @Volatile private var artBitmap: Bitmap? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        running = true
        val exo = PlayerHolder.player(this)
        val player = SkipAwarePlayer(exo)

        // ---------- Sesión multimedia (Bluetooth / carro / pantalla de bloqueo) ----------
        val session = MediaSessionCompat(this, "Vidtube").apply {
            setSessionActivity(contentIntent())
            isActive = true
        }
        mediaSession = session
        connector = MediaSessionConnector(session).apply {
            setPlayer(player)
            setQueueNavigator(SkipNavigator())
            setMediaMetadataProvider { p -> buildMetadata(p) }
        }

        // ---------- Notificación ----------
        val manager = PlayerNotificationManager.Builder(this, NOTIFICATION_ID, VidtubeApp.PLAYBACK_CHANNEL_ID)
            .setSmallIconResourceId(R.drawable.ic_stat_vidtube)
            .setMediaDescriptionAdapter(DescriptionAdapter())
            .setNotificationListener(object : PlayerNotificationManager.NotificationListener {
                override fun onNotificationPosted(
                    notificationId: Int,
                    notification: Notification,
                    ongoing: Boolean,
                ) {
                    if (ongoing && !isForeground) {
                        try {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                                startForeground(
                                    notificationId,
                                    notification,
                                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                                )
                            } else {
                                startForeground(notificationId, notification)
                            }
                            isForeground = true
                        } catch (e: Exception) {
                            Log.w("Vidtube", "No se pudo pasar a primer plano", e)
                        }
                    } else if (!ongoing && isForeground && !autoplayPending()) {
                        stopForeground(STOP_FOREGROUND_DETACH)
                        isForeground = false
                    }
                }

                override fun onNotificationCancelled(notificationId: Int, dismissedByUser: Boolean) {
                    if (dismissedByUser) PlayerHolder.playerOrNull?.pause()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    isForeground = false
                    stopSelf()
                }
            })
            .build()

        manager.setMediaSessionToken(session.sessionToken)
        manager.setUseNextAction(true)
        manager.setUsePreviousAction(true)
        manager.setUseFastForwardAction(false)
        manager.setUseRewindAction(false)
        manager.setUseStopAction(true)
        manager.setPlayer(player)
        notificationManager = manager

        // Cuando el carro (Bluetooth) se vuelve a conectar, le reenviamos la información
        audioManager = getSystemService(AudioManager::class.java)
        audioManager?.registerAudioDeviceCallback(btCallback, mainHandler)

        // Cuando cambia el video o llega el siguiente, actualizamos lo que muestra el carro
        scope.launch {
            combine(NowPlaying.state, NowPlaying.upNext) { s, n -> s?.video?.url to n?.url }
                .collect {
                    connector?.invalidateMediaSessionMetadata()
                    connector?.invalidateMediaSessionPlaybackState()
                    manager.invalidate()
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Si cierran la app desde recientes y está en pausa, terminamos
        if (PlayerHolder.playerOrNull?.isPlaying != true) stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        running = false
        audioManager?.unregisterAudioDeviceCallback(btCallback)
        mainHandler.removeCallbacksAndMessages(null)
        scope.cancel()
        notificationManager?.setPlayer(null)
        notificationManager = null
        connector?.setPlayer(null)
        connector = null
        mediaSession?.run {
            isActive = false
            release()
        }
        mediaSession = null
        imageExecutor.shutdownNow()
        super.onDestroy()
    }

    private val mainHandler = Handler(Looper.getMainLooper())
    private var audioManager: AudioManager? = null

    /**
     * Al prender el carro, su Bluetooth se conecta de nuevo y pregunta qué está sonando.
     * Muchos equipos solo leen la información cuando la sesión "cambia", así que la
     * reactivamos y reenviamos título, carátula y estado unos segundos después de conectar.
     */
    private val btCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) {
            val bt = addedDevices.any {
                it.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP || it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO
            }
            if (!bt) return
            mainHandler.removeCallbacks(refreshSession)
            mainHandler.postDelayed(refreshSession, 1_500)
            mainHandler.postDelayed(refreshSession, 5_000)
        }
    }

    private val refreshSession = Runnable {
        val session = mediaSession ?: return@Runnable
        session.isActive = false
        session.isActive = true
        connector?.invalidateMediaSessionPlaybackState()
        connector?.invalidateMediaSessionMetadata()
        notificationManager?.invalidate()
    }

    // ------------------------------------------------------------------

    /** Información que ve el carro: título, canal, duración y carátula. */
    private fun buildMetadata(player: Player): MediaMetadataCompat {
        val video = NowPlaying.state.value?.video
        val b = MediaMetadataCompat.Builder()
        if (video != null) {
            val title = video.title.ifBlank { "Cargando…" }
            b.putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            b.putString(MediaMetadataCompat.METADATA_KEY_ARTIST, video.channelName)
            b.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, video.channelName)
            b.putString(MediaMetadataCompat.METADATA_KEY_ALBUM, "Vidtube")
            b.putString(MediaMetadataCompat.METADATA_KEY_MEDIA_ID, video.url)
            val duration = player.duration
            if (duration != C.TIME_UNSET && duration > 0) {
                b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)
            } else if (video.durationSec > 0) {
                b.putLong(MediaMetadataCompat.METADATA_KEY_DURATION, video.durationSec * 1000)
            }
            val art = artFor(video.thumbnail)
            if (art != null) {
                b.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, art)
                b.putBitmap(MediaMetadataCompat.METADATA_KEY_DISPLAY_ICON, art)
            }
        }
        return b.build()
    }

    /** Devuelve la carátula si ya está descargada; si no, la descarga y avisa. */
    private fun artFor(url: String?): Bitmap? {
        if (url == null) return null
        if (url == artUrl) return artBitmap
        artUrl = url
        artBitmap = null
        imageExecutor.execute {
            runCatching {
                val request = okhttp3.Request.Builder().url(url).build()
                DownloaderImpl.getInstance().client.newCall(request).execute().use { r ->
                    val bytes = r.body?.bytes() ?: return@use
                    val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return@use
                    if (artUrl == url) {
                        artBitmap = bmp
                        scope.launch {
                            connector?.invalidateMediaSessionMetadata()
                            notificationManager?.invalidate()
                        }
                    }
                }
            }
        }
        return null
    }

    private fun contentIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    /** Al terminar un video con reproducción automática seguimos en primer plano mientras carga el siguiente. */
    private fun autoplayPending(): Boolean {
        val p = PlayerHolder.playerOrNull ?: return false
        return Library.settings.value.autoplay &&
            (p.playbackState == Player.STATE_ENDED || p.playbackState == Player.STATE_IDLE) &&
            NowPlaying.state.value != null
    }

    /**
     * Reproductor "envuelto": hace que los botones ⏮ ⏭ (notificación, carro, audífonos)
     * pasen al video anterior/siguiente aunque el reproductor tenga un solo video cargado.
     */
    private inner class SkipAwarePlayer(player: Player) : ForwardingPlayer(player) {
        override fun isCommandAvailable(command: Int): Boolean = when (command) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM ->
                NowPlaying.upNext.value != null
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
            else -> super.isCommandAvailable(command)
        }

        override fun getAvailableCommands(): Player.Commands {
            val b = super.getAvailableCommands().buildUpon()
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            if (NowPlaying.upNext.value != null) {
                b.add(Player.COMMAND_SEEK_TO_NEXT).add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
            }
            return b.build()
        }

        override fun seekToNext() = NowPlaying.playNext(this@PlaybackService)
        override fun seekToNextMediaItem() = NowPlaying.playNext(this@PlaybackService)
        override fun seekToPrevious() = NowPlaying.playPrevious(this@PlaybackService)
        override fun seekToPreviousMediaItem() = NowPlaying.playPrevious(this@PlaybackService)
    }

    /** Botones ⏮ ⏭ que llegan por la sesión multimedia (carro, audífonos, reloj). */
    private inner class SkipNavigator : MediaSessionConnector.QueueNavigator {
        override fun getSupportedQueueNavigatorActions(player: Player): Long =
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS

        override fun onTimelineChanged(player: Player) {}
        override fun getActiveQueueItemId(player: Player?): Long = MediaSessionCompat.QueueItem.UNKNOWN_ID.toLong()
        override fun onSkipToPrevious(player: Player) = NowPlaying.playPrevious(this@PlaybackService)
        override fun onSkipToQueueItem(player: Player, id: Long) {}
        override fun onSkipToNext(player: Player) = NowPlaying.playNext(this@PlaybackService)
        override fun onCommand(
            player: Player,
            command: String,
            extras: Bundle?,
            cb: ResultReceiver?,
        ): Boolean = false
    }

    private inner class DescriptionAdapter : PlayerNotificationManager.MediaDescriptionAdapter {
        override fun getCurrentContentTitle(player: Player): CharSequence =
            NowPlaying.state.value?.video?.title ?: "Vidtube"

        override fun getCurrentContentText(player: Player): CharSequence? =
            NowPlaying.state.value?.video?.channelName

        override fun createCurrentContentIntent(player: Player): PendingIntent = contentIntent()

        override fun getCurrentLargeIcon(
            player: Player,
            callback: PlayerNotificationManager.BitmapCallback,
        ): Bitmap? = artFor(NowPlaying.state.value?.video?.thumbnail)
    }

    companion object {
        private const val NOTIFICATION_ID = 1337

        /** Si el servicio está vivo (para volver a iniciarlo al reanudar). */
        @Volatile
        var running = false
            private set
    }
}
