package com.silva.vidtube

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.silva.vidtube.data.Library
import com.silva.vidtube.data.Repository
import com.silva.vidtube.player.NowPlaying
import com.silva.vidtube.player.PlayerHolder
import com.silva.vidtube.ui.VidtubeRoot
import com.silva.vidtube.ui.VidtubeTheme
import com.silva.vidtube.ui.pendingChannel
import org.schabi.newpipe.extractor.StreamingService

class MainActivity : ComponentActivity() {

    private val inPip = mutableStateOf(false)

    private val notificationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val storagePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        addOnPictureInPictureModeChangedListener { info ->
            inPip.value = info.isInPictureInPictureMode
        }

        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        if (Build.VERSION.SDK_INT < 29 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            storagePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        com.silva.vidtube.ui.lightTheme.value = Library.settings.value.lightMode
        setContent {
            VidtubeTheme {
                VidtubeRoot(isInPip = inPip.value)
            }
        }

        if (savedInstanceState == null) handleIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIntent(intent)
    }

    /** Abre links de YouTube compartidos o tocados en otras apps. */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.getBooleanExtra(EXTRA_OPEN_DOWNLOADS, false)) {
            com.silva.vidtube.ui.pendingRoute.value = "downloads"
            return
        }
        val raw = when (intent.action) {
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)
            Intent.ACTION_VIEW -> intent.dataString
            else -> null
        } ?: return
        val url = Regex("https?://\\S+").find(raw)?.value ?: return
        val type = runCatching { Repository.yt.getLinkTypeByUrl(url) }.getOrNull()
        when (type) {
            StreamingService.LinkType.STREAM -> NowPlaying.openUrl(this, url)
            StreamingService.LinkType.CHANNEL -> pendingChannel.value = url
            else -> {}
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        val player = PlayerHolder.playerOrNull ?: return
        val canPip = Library.settings.value.pictureInPicture &&
            NowPlaying.state.value != null &&
            player.isPlaying &&
            player.videoFormat != null &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE)
        if (canPip) {
            val size = player.videoSize
            val ratio = if (size.width > 0 && size.height > 0) {
                Rational(size.width, size.height).coerceRatio()
            } else Rational(16, 9)
            runCatching {
                enterPictureInPictureMode(
                    PictureInPictureParams.Builder().setAspectRatio(ratio).build()
                )
            }
        }
    }

    override fun onStop() {
        super.onStop()
        NowPlaying.saveCurrentPosition()
        // Los Shorts no suenan en segundo plano
        if (PlayerHolder.shortsMode && !isChangingConfigurations) PlayerHolder.playerOrNull?.pause()
        val keepPlaying = Library.settings.value.backgroundPlay || isInPictureInPictureMode
        if (!keepPlaying && !isChangingConfigurations) PlayerHolder.playerOrNull?.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        // Si la app se cierra del todo y no hay nada sonando, liberamos todo
        if (isFinishing && PlayerHolder.playerOrNull?.isPlaying != true) {
            NowPlaying.close(this)
        }
    }

    companion object {
        const val EXTRA_OPEN_DOWNLOADS = "open_downloads"
    }

    /** Android solo acepta proporciones entre 1:2,39 y 2,39:1 en imagen en imagen. */
    private fun Rational.coerceRatio(): Rational {
        val v = toFloat()
        return when {
            v > 2.39f -> Rational(239, 100)
            v < 1 / 2.39f -> Rational(100, 239)
            else -> this
        }
    }
}
