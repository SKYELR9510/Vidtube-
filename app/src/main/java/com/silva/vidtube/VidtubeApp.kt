package com.silva.vidtube

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.util.Log
import com.silva.vidtube.data.DownloaderImpl
import com.silva.vidtube.data.Library
import com.silva.vidtube.potoken.PoTokenProviderImpl
import io.reactivex.rxjava3.plugins.RxJavaPlugins
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization
import org.schabi.newpipe.extractor.services.youtube.extractors.YoutubeStreamExtractor
import java.util.Locale

class VidtubeApp : Application() {

    override fun onCreate() {
        super.onCreate()
        instance = this

        val locale = Locale.getDefault()
        val country = locale.country.ifBlank { "PE" }
        NewPipe.init(
            DownloaderImpl.init(),
            Localization(locale.language.ifBlank { "es" }, country),
            ContentCountry(country)
        )
        YoutubeStreamExtractor.setPoTokenProvider(PoTokenProviderImpl)

        // Evita que errores tardíos de RxJava (WebView del PoToken) cierren la app
        RxJavaPlugins.setErrorHandler { Log.w("Vidtube", "Error RxJava no entregado", it) }

        Library.init(this)
        com.silva.vidtube.download.Downloads.init(this)

        val channel = NotificationChannel(
            PLAYBACK_CHANNEL_ID,
            "Reproducción",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Controles de reproducción en segundo plano" }
        val downloads = NotificationChannel(
            DOWNLOAD_CHANNEL_ID,
            "Descargas",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Progreso de las descargas" }
        getSystemService(NotificationManager::class.java).apply {
            createNotificationChannel(channel)
            createNotificationChannel(downloads)
        }
    }

    companion object {
        const val PLAYBACK_CHANNEL_ID = "playback"
        const val DOWNLOAD_CHANNEL_ID = "downloads"

        lateinit var instance: VidtubeApp
            private set
    }
}
