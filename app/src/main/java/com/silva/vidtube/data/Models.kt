package com.silva.vidtube.data

import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.InfoItem
import org.schabi.newpipe.extractor.channel.ChannelInfoItem
import org.schabi.newpipe.extractor.localization.DateWrapper
import java.time.Duration
import java.time.Instant
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem
import org.schabi.newpipe.extractor.stream.StreamType

/** Video tal como lo muestra la interfaz (y como se guarda en historial/ver más tarde). */
data class VideoItem(
    val url: String,
    val title: String,
    val thumbnail: String?,
    val channelName: String,
    val channelUrl: String?,
    val channelAvatar: String?,
    val durationSec: Long,
    val views: Long,
    val uploaded: String?,
    val isLive: Boolean,
    val isShort: Boolean = false,
    /** Fecha de publicación (segundos desde 1970) si se conoce; sirve para mostrar lo reciente. */
    val publishedAt: Long? = null,
) {
    /** Días desde que se publicó (null si no se sabe). */
    val ageDays: Long?
        get() = publishedAt?.let { (System.currentTimeMillis() / 1000 - it) / 86_400 }

    fun toJson(): JSONObject = JSONObject()
        .put("url", url)
        .put("title", title)
        .put("thumbnail", thumbnail ?: "")
        .put("channelName", channelName)
        .put("channelUrl", channelUrl ?: "")
        .put("channelAvatar", channelAvatar ?: "")
        .put("duration", durationSec)
        .put("views", views)
        .put("uploaded", uploaded ?: "")
        .put("live", isLive)
        .put("short", isShort)
        .put("published", publishedAt ?: 0L)

    companion object {
        fun fromJson(o: JSONObject) = VideoItem(
            url = o.getString("url"),
            title = o.optString("title"),
            thumbnail = o.optString("thumbnail").ifBlank { null },
            channelName = o.optString("channelName"),
            channelUrl = o.optString("channelUrl").ifBlank { null },
            channelAvatar = o.optString("channelAvatar").ifBlank { null },
            durationSec = o.optLong("duration", -1),
            views = o.optLong("views", -1),
            uploaded = o.optString("uploaded").ifBlank { null },
            isLive = o.optBoolean("live", false),
            isShort = o.optBoolean("short", false),
            publishedAt = o.optLong("published", 0L).takeIf { it > 0 },
        )

        fun from(item: StreamInfoItem) = VideoItem(
            url = item.url,
            title = item.name ?: "",
            thumbnail = bestImage(item.thumbnails, 360),
            channelName = item.uploaderName ?: "",
            channelUrl = item.uploaderUrl,
            channelAvatar = bestImage(item.uploaderAvatars, 68),
            durationSec = item.duration,
            views = item.viewCount,
            uploaded = friendlyDate(item.textualUploadDate, item.uploadDate),
            isLive = item.streamType == StreamType.LIVE_STREAM ||
                item.streamType == StreamType.AUDIO_LIVE_STREAM,
            isShort = item.isShortFormContent,
            publishedAt = item.uploadDate?.instant?.epochSecond
                ?: parseAgo(item.textualUploadDate),
        )

        fun from(info: StreamInfo) = VideoItem(
            url = info.url,
            title = info.name ?: "",
            thumbnail = bestImage(info.thumbnails, 360),
            channelName = info.uploaderName ?: "",
            channelUrl = info.uploaderUrl,
            channelAvatar = bestImage(info.uploaderAvatars, 68),
            durationSec = info.duration,
            views = info.viewCount,
            uploaded = friendlyDate(info.textualUploadDate, info.uploadDate),
            isLive = info.streamType == StreamType.LIVE_STREAM ||
                info.streamType == StreamType.AUDIO_LIVE_STREAM,
            publishedAt = info.uploadDate?.instant?.epochSecond,
        )
    }
}

data class ChannelItem(
    val url: String,
    val name: String,
    val avatar: String?,
    val subscribers: Long = -1,
) {
    fun toJson(): JSONObject = JSONObject()
        .put("url", url).put("name", name).put("avatar", avatar ?: "").put("subs", subscribers)

    companion object {
        fun fromJson(o: JSONObject) = ChannelItem(
            url = o.getString("url"),
            name = o.optString("name"),
            avatar = o.optString("avatar").ifBlank { null },
            subscribers = o.optLong("subs", -1),
        )

        fun from(item: ChannelInfoItem) = ChannelItem(
            url = item.url,
            name = item.name ?: "",
            avatar = bestImage(item.thumbnails, 68),
            subscribers = item.subscriberCount,
        )
    }
}

/** Elemento de una lista mixta (búsqueda, inicio). */
sealed interface FeedEntry {
    val key: String

    data class Video(val video: VideoItem) : FeedEntry {
        override val key get() = "v:" + video.url
    }

    data class Channel(val channel: ChannelItem) : FeedEntry {
        override val key get() = "c:" + channel.url
    }
}

fun InfoItem.toEntry(): FeedEntry? = when (this) {
    is StreamInfoItem -> FeedEntry.Video(VideoItem.from(this))
    is ChannelInfoItem -> FeedEntry.Channel(ChannelItem.from(this))
    else -> null
}

/** Elige la imagen más pequeña que cumpla la altura mínima (o la más grande disponible). */
fun bestImage(images: List<Image>?, minHeight: Int): String? {
    if (images.isNullOrEmpty()) return null
    val known = images.filter { it.height > 0 }
    if (known.isEmpty()) return images.last().url
    return known.filter { it.height >= minHeight }.minByOrNull { it.height }?.url
        ?: known.maxByOrNull { it.height }?.url
}

private val isoLike = Regex("^\\d{4}-\\d{2}-\\d{2}")

/** Convierte fechas como "2026-07-05T15:00:06-07:00" en "hace 4 meses". */
fun friendlyDate(textual: String?, date: DateWrapper?): String? {
    if (!textual.isNullOrBlank() && !isoLike.containsMatchIn(textual)) return textual
    val instant = date?.instant ?: runCatching {
        java.time.OffsetDateTime.parse(textual).toInstant()
    }.getOrNull() ?: return textual
    return relativeTime(instant)
}

fun relativeTime(instant: Instant): String {
    val secs = Duration.between(instant, Instant.now()).seconds.coerceAtLeast(0)
    fun plural(n: Long, one: String, many: String) = "hace $n " + if (n == 1L) one else many
    return when {
        secs < 60 -> "hace un momento"
        secs < 3600 -> plural(secs / 60, "minuto", "minutos")
        secs < 86_400 -> plural(secs / 3600, "hora", "horas")
        secs < 7 * 86_400 -> plural(secs / 86_400, "día", "días")
        secs < 30 * 86_400 -> plural(secs / (7 * 86_400), "semana", "semanas")
        secs < 365 * 86_400 -> plural(secs / (30 * 86_400), "mes", "meses")
        else -> plural(secs / (365 * 86_400), "año", "años")
    }
}

/** Convierte "hace 3 años" / "3 years ago" en una fecha aproximada (segundos). */
fun parseAgo(text: String?): Long? {
    if (text.isNullOrBlank()) return null
    val t = text.lowercase()
    val n = Regex("(\\d+)").find(t)?.groupValues?.get(1)?.toLongOrNull() ?: 1L
    val unit = when {
        "seg" in t || "second" in t -> 1L
        "min" in t -> 60L
        "hora" in t || "hour" in t -> 3_600L
        "día" in t || "dia" in t || "day" in t -> 86_400L
        "semana" in t || "week" in t -> 7 * 86_400L
        "mes" in t || "month" in t -> 30 * 86_400L
        "año" in t || "ano" in t || "year" in t -> 365 * 86_400L
        else -> return null
    }
    return System.currentTimeMillis() / 1000 - n * unit
}
