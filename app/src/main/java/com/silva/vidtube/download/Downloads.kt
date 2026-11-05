package com.silva.vidtube.download

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.net.Uri
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.AudioTrackType
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.UUID

/** Una opción del menú "Descargar" (por ejemplo "720p" o "Solo audio"). */
data class DownloadOption(
    val label: String,
    val detail: String,
    val video: VideoStream?,
    val audio: AudioStream?,
) {
    val isAudioOnly get() = video == null
}

enum class DownloadStatus { QUEUED, RUNNING, MERGING, DONE, FAILED }

data class DownloadTask(
    val id: String,
    val title: String,
    val channel: String,
    val thumbnail: String?,
    val label: String,
    val audioOnly: Boolean,
    val status: DownloadStatus,
    val progress: Float = 0f,
    val uri: String? = null,
    val sizeBytes: Long = 0,
    val error: String? = null,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("id", id).put("title", title).put("channel", channel)
        .put("thumbnail", thumbnail ?: "").put("label", label).put("audioOnly", audioOnly)
        .put("uri", uri ?: "").put("size", sizeBytes)

    companion object {
        fun fromJson(o: JSONObject) = DownloadTask(
            id = o.getString("id"),
            title = o.optString("title"),
            channel = o.optString("channel"),
            thumbnail = o.optString("thumbnail").ifBlank { null },
            label = o.optString("label"),
            audioOnly = o.optBoolean("audioOnly"),
            status = DownloadStatus.DONE,
            progress = 1f,
            uri = o.optString("uri").ifBlank { null },
            sizeBytes = o.optLong("size"),
        )
    }
}

/** Trabajo pendiente con los streams elegidos (no se guarda en disco). */
class DownloadJob(
    val taskId: String,
    val option: DownloadOption,
    val durationSec: Long,
    /** true si el video se cargó con el plan B de Smart TV (videos de niños). */
    val tvClient: Boolean = false,
)

object Downloads {
    private lateinit var prefs: SharedPreferences

    private val _tasks = MutableStateFlow<List<DownloadTask>>(emptyList())
    val tasks: StateFlow<List<DownloadTask>> = _tasks.asStateFlow()

    internal val queue = ArrayDeque<DownloadJob>()

    fun init(context: Context) {
        prefs = context.getSharedPreferences("vidtube_downloads", Context.MODE_PRIVATE)
        _tasks.value = runCatching {
            val arr = JSONArray(prefs.getString("done", "[]") ?: "[]")
            (0 until arr.length()).map { DownloadTask.fromJson(arr.getJSONObject(it)) }
        }.getOrDefault(emptyList())
    }

    /** Calidades que se pueden descargar para este video. */
    fun options(info: StreamInfo): List<DownloadOption> {
        val audio = bestAudio(info.audioStreams)
        val out = ArrayList<DownloadOption>()

        // Video sin audio en MP4 (H.264) + audio M4A, unidos en el celular
        if (audio != null) {
            info.videoOnlyStreams
                .filter {
                    it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP &&
                        it.format == MediaFormat.MPEG_4 && (it.codec?.startsWith("avc") ?: true) &&
                        it.height in 144..1080
                }
                .groupBy { it.height }
                .map { (_, list) -> list.maxBy { it.fps * 10_000 + it.bitrate / 1000 } }
                .sortedByDescending { it.height }
                .forEach { v ->
                    val size = sizeOf(v.itagItem?.contentLength) + sizeOf(audio.itagItem?.contentLength)
                    out += DownloadOption(
                        label = "${v.height}p" + if (v.fps > 30) "${v.fps}" else "",
                        detail = "Video MP4" + sizeText(size),
                        video = v,
                        audio = audio,
                    )
                }
        }

        // Respaldo: video que ya trae audio (normalmente 360p)
        if (out.isEmpty()) {
            info.videoStreams
                .filter { it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.MPEG_4 }
                .sortedByDescending { it.height }
                .forEach { v ->
                    out += DownloadOption("${v.height}p", "Video MP4" + sizeText(sizeOf(v.itagItem?.contentLength)), v, null)
                }
        }

        if (audio != null) {
            out += DownloadOption(
                label = "Solo audio",
                detail = "M4A · ${audio.averageBitrate.coerceAtLeast(0)} kbps" + sizeText(sizeOf(audio.itagItem?.contentLength)),
                video = null,
                audio = audio,
            )
        }
        return out
    }

    private fun bestAudio(streams: List<AudioStream>): AudioStream? {
        val usable = streams.filter {
            it.isUrl && it.deliveryMethod == DeliveryMethod.PROGRESSIVE_HTTP && it.format == MediaFormat.M4A
        }
        val original = usable.filter {
            it.audioTrackType == null || it.audioTrackType == AudioTrackType.ORIGINAL
        }.ifEmpty { usable }
        return original.maxByOrNull { it.averageBitrate }
    }

    private fun sizeOf(len: Long?) = if (len != null && len > 0) len else 0L

    private fun sizeText(bytes: Long): String =
        if (bytes <= 0) "" else " · " + String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0).replace('.', ',')

    fun enqueue(context: Context, info: StreamInfo, option: DownloadOption) {
        val id = UUID.randomUUID().toString()
        val task = DownloadTask(
            id = id,
            title = info.name ?: "video",
            channel = info.uploaderName ?: "",
            thumbnail = com.silva.vidtube.data.bestImage(info.thumbnails, 180),
            label = option.label,
            audioOnly = option.isAudioOnly,
            status = DownloadStatus.QUEUED,
        )
        _tasks.value = listOf(task) + _tasks.value
        synchronized(queue) { queue.addLast(
            DownloadJob(id, option, info.duration, com.silva.vidtube.data.KidsFallback.isTvStream(info.id))
        ) }
        ContextCompat.startForegroundService(context, Intent(context, DownloadService::class.java))
    }

    internal fun update(id: String, transform: (DownloadTask) -> DownloadTask) {
        _tasks.value = _tasks.value.map { if (it.id == id) transform(it) else it }
        if (_tasks.value.firstOrNull { it.id == id }?.status == DownloadStatus.DONE) persist()
    }

    fun remove(context: Context, task: DownloadTask) {
        task.uri?.let { u ->
            runCatching { context.contentResolver.delete(Uri.parse(u), null, null) }
            runCatching { if (u.startsWith("file://")) java.io.File(Uri.parse(u).path!!).delete() }
        }
        _tasks.value = _tasks.value.filterNot { it.id == task.id }
        persist()
    }

    private fun persist() {
        val arr = JSONArray()
        _tasks.value.filter { it.status == DownloadStatus.DONE }.forEach { arr.put(it.toJson()) }
        prefs.edit().putString("done", arr.toString()).apply()
    }
}
