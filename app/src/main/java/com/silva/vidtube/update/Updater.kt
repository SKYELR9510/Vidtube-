package com.silva.vidtube.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import androidx.core.content.FileProvider
import com.silva.vidtube.BuildConfig
import com.silva.vidtube.data.DownloaderImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.IOException

/**
 * Actualizaciones de Vidtube desde GitHub.
 * Cada vez que GitHub compila una versión nueva la publica como "Release" con el APK;
 * aquí revisamos si hay una más nueva que la instalada y la descargamos e instalamos.
 */
object Updater {

    data class Release(
        val versionCode: Int,
        val versionName: String,
        val notes: String,
        val apkUrl: String,
        val sizeBytes: Long,
    )

    sealed interface State {
        data object Idle : State
        data object Checking : State
        data object UpToDate : State
        data class Available(val release: Release) : State
        data class Downloading(val release: Release, val progress: Float) : State
        data class ReadyToInstall(val release: Release, val file: File) : State
        data class Failed(val message: String, val release: Release? = null) : State
    }

    private val _state = MutableStateFlow<State>(State.Idle)
    val state: StateFlow<State> = _state.asStateFlow()

    /** Se activa cuando varios videos fallan seguidos: probablemente YouTube cambió algo. */
    private val _youtubeBroken = MutableStateFlow(false)
    val youtubeBroken: StateFlow<Boolean> = _youtubeBroken.asStateFlow()
    private var consecutiveFailures = 0

    val enabled: Boolean get() = BuildConfig.UPDATE_REPO.isNotBlank()

    fun reportPlaybackResult(success: Boolean, networkError: Boolean) {
        if (success) {
            consecutiveFailures = 0
            _youtubeBroken.value = false
        } else if (!networkError) {
            consecutiveFailures++
            if (consecutiveFailures >= 3) _youtubeBroken.value = true
        }
    }

    fun dismissBroken() {
        _youtubeBroken.value = false
        consecutiveFailures = 0
    }

    /** Revisa GitHub. [silent] = no mostrar "ya tienes la última" (revisión automática al abrir). */
    suspend fun check(silent: Boolean = true) {
        if (!enabled) return
        val current = _state.value
        if (current is State.Checking || current is State.Downloading) return
        _state.value = State.Checking
        _state.value = try {
            val release = fetchLatest()
            when {
                release == null -> if (silent) State.Idle else State.UpToDate
                release.versionCode > BuildConfig.VERSION_CODE -> State.Available(release)
                else -> if (silent) State.Idle else State.UpToDate
            }
        } catch (e: Exception) {
            Log.w("VidtubeUpdate", "No se pudo revisar actualizaciones", e)
            if (silent) State.Idle else State.Failed("No se pudo revisar. Revisa tu internet.")
        }
    }

    private suspend fun fetchLatest(): Release? = withContext(Dispatchers.IO) {
        val req = Request.Builder()
            .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Vidtube")
            .build()
        DownloaderImpl.getInstance().client.newCall(req).execute().use { resp ->
            if (resp.code == 404) return@withContext null
            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
            val json = JSONObject(resp.body?.string() ?: return@withContext null)
            // La etiqueta se publica como "build-<versionCode>"
            val code = json.optString("tag_name").substringAfter("build-", "").toIntOrNull()
                ?: return@withContext null
            val assets = json.optJSONArray("assets") ?: return@withContext null
            var url: String? = null
            var size = 0L
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name").endsWith(".apk")) {
                    url = a.optString("browser_download_url")
                    size = a.optLong("size")
                    break
                }
            }
            Release(
                versionCode = code,
                versionName = json.optString("name").ifBlank { "build $code" },
                notes = json.optString("body"),
                apkUrl = url ?: return@withContext null,
                sizeBytes = size,
            )
        }
    }

    suspend fun download(context: Context, release: Release) {
        val dir = File(context.cacheDir, "updates").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val file = File(dir, "Vidtube-${release.versionCode}.apk")
        _state.value = State.Downloading(release, 0f)
        try {
            withContext(Dispatchers.IO) {
                val req = Request.Builder().url(release.apkUrl).header("User-Agent", "Vidtube").build()
                DownloaderImpl.getInstance().client.newCall(req).execute().use { resp ->
                    if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                    val body = resp.body ?: throw IOException("Respuesta vacía")
                    val total = body.contentLength().takeIf { it > 0 } ?: release.sizeBytes
                    body.byteStream().use { input ->
                        file.outputStream().use { out ->
                            val buf = ByteArray(64 * 1024)
                            var done = 0L
                            var lastPct = -1
                            while (true) {
                                val n = input.read(buf)
                                if (n < 0) break
                                out.write(buf, 0, n)
                                done += n
                                if (total > 0) {
                                    val pct = (done * 100 / total).toInt()
                                    if (pct != lastPct) {
                                        lastPct = pct
                                        _state.value = State.Downloading(release, pct / 100f)
                                    }
                                }
                            }
                        }
                    }
                }
            }
            _state.value = State.ReadyToInstall(release, file)
            install(context, file)
        } catch (e: Exception) {
            Log.e("VidtubeUpdate", "Descarga fallida", e)
            file.delete()
            _state.value = State.Failed("No se pudo descargar la actualización.", release)
        }
    }

    /** Abre el instalador de Android con el APK descargado. */
    fun install(context: Context, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
        ) {
            // Primero hay que permitir que Vidtube instale apps (solo una vez)
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
            return
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, "application/vnd.android.package-archive")
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }

    fun dismiss() {
        _state.value = State.Idle
    }
}
