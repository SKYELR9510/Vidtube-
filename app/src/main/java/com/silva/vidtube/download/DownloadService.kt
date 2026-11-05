package com.silva.vidtube.download

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentValues
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaMuxer
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.provider.MediaStore
import android.util.Log
import androidx.core.app.NotificationCompat
import com.silva.vidtube.MainActivity
import com.silva.vidtube.R
import com.silva.vidtube.VidtubeApp
import com.silva.vidtube.data.DownloaderImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.stream.Stream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer

/** Descarga en segundo plano con notificación de progreso. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var worker: Job? = null
    private lateinit var nm: NotificationManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        nm = getSystemService(NotificationManager::class.java)
        val n = notification("Preparando descarga…", 0, true)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, n)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (worker?.isActive != true) {
            worker = scope.launch { processQueue() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun processQueue() {
        while (true) {
            val job = synchronized(Downloads.queue) { Downloads.queue.removeFirstOrNull() } ?: break
            val task = Downloads.tasks.value.firstOrNull { it.id == job.taskId } ?: continue
            try {
                runJob(job, task)
            } catch (e: Exception) {
                Log.e(TAG, "Descarga fallida", e)
                Downloads.update(job.taskId) {
                    it.copy(status = DownloadStatus.FAILED, error = e.message ?: e.javaClass.simpleName)
                }
                nm.notify(
                    job.taskId.hashCode(),
                    doneNotification("No se pudo descargar", task.title)
                )
            }
        }
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun runJob(job: DownloadJob, task: DownloadTask) {
        val dir = File(cacheDir, "downloads").apply { mkdirs() }
        val opt = job.option
        Downloads.update(task.id) { it.copy(status = DownloadStatus.RUNNING, progress = 0f) }

        val streams = listOfNotNull(opt.video, opt.audio)
        val sizes = streams.map { it.itagItem?.contentLength ?: -1L }
        val total = sizes.sumOf { if (it > 0) it else 0L }.coerceAtLeast(1L)
        var doneBefore = 0L

        val parts = streams.mapIndexed { i, s ->
            val f = File(dir, "${task.id}_$i.part")
            fetch(s, f, sizes[i]) { written ->
                val p = ((doneBefore + written).toFloat() / total).coerceIn(0f, 1f)
                Downloads.update(task.id) { it.copy(progress = p) }
                nm.notify(NOTIF_ID, notification(task.title, (p * 100).toInt(), false))
            }
            doneBefore += f.length()
            f
        }

        val isAudio = opt.video == null
        val ext = if (isAudio) "m4a" else "mp4"
        val output: File
        if (parts.size == 2) {
            Downloads.update(task.id) { it.copy(status = DownloadStatus.MERGING) }
            nm.notify(NOTIF_ID, notification("Uniendo audio y video…", 0, true))
            output = File(dir, "${task.id}.mp4")
            mux(parts[0], parts[1], output)
            parts.forEach { it.delete() }
        } else {
            output = parts[0]
        }

        val name = safeName(task.title) + " (" + task.label.replace(" ", "_") + ")." + ext
        val uri = publish(output, name, isAudio)
        val size = output.length()
        output.delete()

        Downloads.update(task.id) {
            it.copy(status = DownloadStatus.DONE, progress = 1f, uri = uri.toString(), sizeBytes = size)
        }
        nm.notify(task.id.hashCode(), doneNotification("Descarga completa", task.title))
    }

    /**
     * Descarga un stream de YouTube por partes (como el reproductor), para evitar que
     * YouTube limite la velocidad.
     */
    private suspend fun fetch(stream: Stream, file: File, contentLength: Long, onProgress: (Long) -> Unit) {
        val client = DownloaderImpl.getInstance().client
        val base = stream.content
        val isWeb = YoutubeParsingHelper.isWebStreamingUrl(base)
        var pos = 0L
        var rn = 0
        var lastReport = 0L
        FileOutputStream(file).use { out ->
            while (contentLength <= 0 || pos < contentLength) {
                currentCoroutineContext().ensureActive()
                val end = if (contentLength > 0) minOf(pos + CHUNK - 1, contentLength - 1) else pos + CHUNK - 1
                val url = "$base&range=$pos-$end&rn=${rn++}"
                val req = Request.Builder()
                    .url(url)
                    .post(byteArrayOf(0x78, 0).toRequestBody())
                    .header("User-Agent", DownloaderImpl.USER_AGENT)
                    .header("Accept-Encoding", "identity")
                    .apply {
                        if (isWeb) {
                            header("Origin", "https://www.youtube.com")
                            header("Referer", "https://www.youtube.com/")
                            header("Sec-Fetch-Dest", "empty")
                            header("Sec-Fetch-Mode", "cors")
                            header("Sec-Fetch-Site", "cross-site")
                        }
                    }
                    .build()
                var read = 0L
                var attempt = 0
                while (true) {
                    try {
                        client.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw IOException("HTTP ${resp.code}")
                            val body = resp.body ?: throw IOException("Respuesta vacía")
                            body.byteStream().use { input ->
                                val buf = ByteArray(64 * 1024)
                                while (true) {
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    read += n
                                }
                            }
                        }
                        break
                    } catch (e: IOException) {
                        if (read > 0 || ++attempt >= 3) throw e
                    }
                }
                pos += read
                if (pos - lastReport > 512 * 1024) {
                    lastReport = pos
                    onProgress(pos)
                }
                if (read == 0L || (contentLength <= 0 && read < CHUNK)) break
            }
        }
        onProgress(pos)
        if (contentLength > 0 && file.length() < contentLength) {
            throw IOException("Descarga incompleta")
        }
    }

    /** Une la pista de video (MP4) con la de audio (M4A) sin recomprimir. */
    private fun mux(videoFile: File, audioFile: File, output: File) {
        val muxer = MediaMuxer(output.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
        val vEx = MediaExtractor().apply { setDataSource(videoFile.absolutePath) }
        val aEx = MediaExtractor().apply { setDataSource(audioFile.absolutePath) }
        try {
            val vTrack = (0 until vEx.trackCount).first {
                vEx.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("video/") == true
            }
            val aTrack = (0 until aEx.trackCount).first {
                aEx.getTrackFormat(it).getString(android.media.MediaFormat.KEY_MIME)?.startsWith("audio/") == true
            }
            vEx.selectTrack(vTrack)
            aEx.selectTrack(aTrack)
            val outV = muxer.addTrack(vEx.getTrackFormat(vTrack))
            val outA = muxer.addTrack(aEx.getTrackFormat(aTrack))
            muxer.start()
            val buffer = ByteBuffer.allocate(4 * 1024 * 1024)
            val info = MediaCodec.BufferInfo()
            copyTrack(vEx, muxer, outV, buffer, info)
            copyTrack(aEx, muxer, outA, buffer, info)
            muxer.stop()
        } finally {
            runCatching { muxer.release() }
            vEx.release()
            aEx.release()
        }
    }

    private fun copyTrack(
        ex: MediaExtractor,
        muxer: MediaMuxer,
        track: Int,
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
    ) {
        while (true) {
            buffer.clear()
            val size = ex.readSampleData(buffer, 0)
            if (size < 0) break
            info.offset = 0
            info.size = size
            info.presentationTimeUs = ex.sampleTime
            info.flags = if (ex.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC != 0) {
                MediaCodec.BUFFER_FLAG_KEY_FRAME
            } else 0
            muxer.writeSampleData(track, buffer, info)
            ex.advance()
        }
    }

    /** Guarda el archivo en la carpeta pública Descargas/Vidtube. */
    private fun publish(file: File, name: String, isAudio: Boolean): Uri {
        val mime = if (isAudio) "audio/mp4" else "video/mp4"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, mime)
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Vidtube")
                put(MediaStore.MediaColumns.IS_PENDING, 1)
            }
            val resolver = contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                ?: throw IOException("No se pudo crear el archivo")
            resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("No se pudo escribir el archivo")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
            return uri
        } else {
            @Suppress("DEPRECATION")
            val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Vidtube")
            dir.mkdirs()
            val dest = File(dir, name)
            file.copyTo(dest, overwrite = true)
            MediaScannerConnection.scanFile(this, arrayOf(dest.absolutePath), arrayOf(mime), null)
            return Uri.fromFile(dest)
        }
    }

    private fun safeName(title: String) =
        title.replace(Regex("[\\\\/:*?\"<>|\\n\\r]"), " ").trim().take(80).ifBlank { "video" }

    private fun openAppIntent() = PendingIntent.getActivity(
        this, 1,
        Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(MainActivity.EXTRA_OPEN_DOWNLOADS, true),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
    )

    private fun notification(title: String, progress: Int, indeterminate: Boolean): Notification =
        NotificationCompat.Builder(this, VidtubeApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(if (indeterminate) null else "$progress %")
            .setProgress(100, progress, indeterminate)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setContentIntent(openAppIntent())
            .build()

    private fun doneNotification(title: String, text: String): Notification =
        NotificationCompat.Builder(this, VidtubeApp.DOWNLOAD_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_vidtube)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(openAppIntent())
            .build()

    companion object {
        private const val TAG = "VidtubeDownload"
        private const val NOTIF_ID = 2001
        private const val CHUNK = 2L * 1024 * 1024
    }
}
