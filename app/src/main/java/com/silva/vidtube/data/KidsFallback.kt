package com.silva.vidtube.data

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.Image
import org.schabi.newpipe.extractor.MediaFormat
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.services.youtube.InnertubeClientRequestInfo
import org.schabi.newpipe.extractor.services.youtube.ItagItem
import org.schabi.newpipe.extractor.services.youtube.YoutubeParsingHelper
import org.schabi.newpipe.extractor.services.youtube.YoutubeJavaScriptPlayerManager
import org.schabi.newpipe.extractor.stream.AudioStream
import org.schabi.newpipe.extractor.stream.DeliveryMethod
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamType
import org.schabi.newpipe.extractor.stream.VideoStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Plan B para videos "hechos para niños".
 *
 * Desde fines de octubre de 2026 YouTube ya no entrega esos videos al cliente que usa
 * NewPipe Extractor (visionOS) y responde "UNPLAYABLE: This video is not available".
 * yt-dlp lo resolvió pidiéndolos como si fuera un Smart TV Samsung (cliente TVHTML5),
 * y aquí hacemos lo mismo solo cuando falla el camino normal.
 *
 * Cuando NewPipe Extractor lo arregle, el camino normal volverá a funcionar
 * y este plan B dejará de usarse solo.
 */
object KidsFallback {
    private const val TAG = "VidtubeKids"

    const val TV_USER_AGENT =
        "Mozilla/5.0 (SMART-TV; Linux; Tizen 2.4.0) AppleWebKit/538.1 (KHTML, like Gecko) " +
            "Version/2.4.0 TV Safari/538.1"
    private const val CLIENT_NAME = "TVHTML5"
    private const val CLIENT_VERSION = "5.20260707"
    private const val CLIENT_ID = "7"

    /** Videos que se están reproduciendo con este plan B (el reproductor usa otro "navegador"). */
    private val tvVideos = ConcurrentHashMap.newKeySet<String>()

    fun isTvStream(videoId: String?) = videoId != null && tvVideos.contains(videoId)

    /** ¿Vale la pena intentar el plan B con este error? */
    fun applies(e: Throwable): Boolean =
        e is ContentNotAvailableException &&
            e.javaClass == ContentNotAvailableException::class.java &&
            (e.message ?: "").contains("UNPLAYABLE", ignoreCase = true)

    /** Pide el video como Smart TV y arma un StreamInfo como el que da el extractor. */
    fun load(url: String): StreamInfo {
        val yt = ServiceList.YouTube
        val videoId = yt.streamLHFactory.getId(url)
        val loc = NewPipe.getPreferredLocalization()
        val country = NewPipe.getPreferredContentCountry()

        // Identificador de visitante (como hace yt-dlp): sin él YouTube a veces rechaza la TV
        val visitorData = runCatching {
            val web = InnertubeClientRequestInfo.ofWebClient()
            web.clientInfo.clientVersion = YoutubeParsingHelper.getClientVersion()
            YoutubeParsingHelper.getVisitorDataFromInnertube(
                web, loc, country, YoutubeParsingHelper.getYouTubeHeaders(),
                YoutubeParsingHelper.YOUTUBEI_V1_URL, null, false,
            )
        }.onFailure { Log.w(TAG, "Sin visitorData", it) }.getOrNull()

        val sts = runCatching { YoutubeJavaScriptPlayerManager.getSignatureTimestamp(videoId) }
            .onFailure { Log.w(TAG, "Sin signatureTimestamp", it) }
            .getOrNull()

        val body = JSONObject()
            .put(
                "context",
                JSONObject().put(
                    "client",
                    JSONObject()
                        .put("clientName", CLIENT_NAME)
                        .put("clientVersion", CLIENT_VERSION)
                        .put("deviceMake", "Samsung")
                        .put("deviceModel", "UKS9800")
                        .put("userAgent", TV_USER_AGENT)
                        .put("osName", "Tizen")
                        .put("osVersion", "2.4.0")
                        .put("hl", loc.localizationCode)
                        .put("gl", country.countryCode)
                        .apply { if (visitorData != null) put("visitorData", visitorData) }
                )
            )
            .put("videoId", videoId)
            .put("contentCheckOk", true)
            .put("racyCheckOk", true)
            .apply {
                if (sts != null) {
                    put(
                        "playbackContext",
                        JSONObject().put(
                            "contentPlaybackContext",
                            JSONObject()
                                .put("html5Preference", "HTML5_PREF_WANTS")
                                .put("signatureTimestamp", sts)
                        )
                    )
                }
            }

        val headers = mutableMapOf(
            "User-Agent" to listOf(TV_USER_AGENT),
            "Content-Type" to listOf("application/json"),
            "Origin" to listOf("https://www.youtube.com"),
            "X-YouTube-Client-Name" to listOf(CLIENT_ID),
            "X-YouTube-Client-Version" to listOf(CLIENT_VERSION),
        )
        if (visitorData != null) headers["X-Goog-Visitor-Id"] = listOf(visitorData)
        val resp = DownloaderImpl.getInstance().post(
            "https://www.youtube.com/youtubei/v1/player?prettyPrint=false",
            headers,
            body.toString().toByteArray(),
        )
        if (resp.responseCode() != 200) {
            throw ContentNotAvailableException("Plan B (TV): YouTube respondió HTTP ${resp.responseCode()}")
        }
        val json = JSONObject(resp.responseBody() ?: throw ContentNotAvailableException("Plan B (TV): respuesta vacía"))

        val playability = json.optJSONObject("playabilityStatus")
        val status = playability?.optString("status").orEmpty()
        if (!status.equals("OK", ignoreCase = true)) {
            throw ContentNotAvailableException(
                "Plan B (TV) también falló: $status ${playability?.optString("reason").orEmpty()}"
            )
        }

        val details = json.optJSONObject("videoDetails") ?: JSONObject()
        val streaming = json.optJSONObject("streamingData")
            ?: throw ContentNotAvailableException("Plan B (TV): sin formatos")

        val info = StreamInfo(
            yt.serviceId,
            "https://www.youtube.com/watch?v=$videoId",
            url,
            if (details.optBoolean("isLive")) StreamType.LIVE_STREAM else StreamType.VIDEO_STREAM,
            videoId,
            details.optString("title"),
            0,
        )
        val duration = details.optString("lengthSeconds").toLongOrNull() ?: 0L
        info.duration = duration
        info.viewCount = details.optString("viewCount").toLongOrNull() ?: -1
        info.uploaderName = details.optString("author")
        details.optString("channelId").takeIf { it.isNotBlank() }?.let {
            info.uploaderUrl = "https://www.youtube.com/channel/$it"
        }
        info.description = Description(details.optString("shortDescription"), Description.Type.PLAIN_TEXT)
        info.thumbnails = images(details.optJSONObject("thumbnail")?.optJSONArray("thumbnails"))

        val videos = ArrayList<VideoStream>()
        val videoOnly = ArrayList<VideoStream>()
        val audios = ArrayList<AudioStream>()
        val all = ArrayList<JSONObject>()
        streaming.optJSONArray("formats")?.let { a -> for (i in 0 until a.length()) all += a.getJSONObject(i) }
        streaming.optJSONArray("adaptiveFormats")?.let { a -> for (i in 0 until a.length()) all += a.getJSONObject(i) }

        for (f in all) {
            val itag = f.optInt("itag")
            if (!ItagItem.isSupported(itag)) continue
            val streamUrl = runCatching { resolveUrl(videoId, f) }
                .onFailure { Log.w(TAG, "No se pudo descifrar itag $itag", it) }
                .getOrNull() ?: continue
            val item = runCatching { ItagItem(ItagItem.getItag(itag)) }.getOrNull() ?: continue
            fillItag(item, f, duration)
            val mime = f.optString("mimeType")
            val format = when {
                mime.startsWith("video/mp4") -> MediaFormat.MPEG_4
                mime.startsWith("video/webm") -> MediaFormat.WEBM
                mime.startsWith("audio/mp4") -> MediaFormat.M4A
                mime.startsWith("audio/webm") -> MediaFormat.WEBMA_OPUS
                else -> item.mediaFormat
            }
            when (item.itagType) {
                ItagItem.ItagType.AUDIO -> audios += AudioStream.Builder()
                    .setId(itag.toString())
                    .setContent(streamUrl, true)
                    .setMediaFormat(format)
                    .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                    .setAverageBitrate(f.optInt("averageBitrate", f.optInt("bitrate")) / 1000)
                    .setItagItem(item)
                    .build()
                ItagItem.ItagType.VIDEO, ItagItem.ItagType.VIDEO_ONLY -> {
                    val onlyVideo = item.itagType == ItagItem.ItagType.VIDEO_ONLY
                    val stream = VideoStream.Builder()
                        .setId(itag.toString())
                        .setContent(streamUrl, true)
                        .setMediaFormat(format)
                        .setDeliveryMethod(DeliveryMethod.PROGRESSIVE_HTTP)
                        .setIsVideoOnly(onlyVideo)
                        .setResolution(f.optString("qualityLabel").ifBlank { item.resolutionString ?: "" })
                        .setItagItem(item)
                        .build()
                    if (onlyVideo) videoOnly += stream else videos += stream
                }
                else -> {}
            }
        }
        if (videos.isEmpty() && videoOnly.isEmpty() && audios.isEmpty()) {
            throw ContentNotAvailableException("Plan B (TV): no se pudo leer ningún formato")
        }
        info.videoStreams = videos
        info.videoOnlyStreams = videoOnly
        info.audioStreams = audios
        tvVideos += videoId
        Log.i(TAG, "Video $videoId cargado con el plan B (TV): ${videoOnly.size} video, ${audios.size} audio")
        return info
    }

    /** URL directa del formato: descifra la firma y el parámetro "n" anti-estrangulamiento. */
    private fun resolveUrl(videoId: String, f: JSONObject): String {
        var url = f.optString("url")
        if (url.isBlank()) {
            val cipher = f.optString("signatureCipher").ifBlank { f.optString("cipher") }
            if (cipher.isBlank()) error("sin url")
            val params = cipher.split("&").associate {
                val k = it.substringBefore("=")
                k to java.net.URLDecoder.decode(it.substringAfter("=", ""), "UTF-8")
            }
            val base = params["url"] ?: error("cipher sin url")
            val s = params["s"] ?: error("cipher sin firma")
            val sp = params["sp"] ?: "signature"
            url = "$base&$sp=" + java.net.URLEncoder.encode(
                YoutubeJavaScriptPlayerManager.deobfuscateSignature(videoId, s), "UTF-8"
            )
        }
        return YoutubeJavaScriptPlayerManager.getUrlWithThrottlingParameterDeobfuscated(videoId, url)
    }

    private fun fillItag(item: ItagItem, f: JSONObject, durationSec: Long) {
        f.optString("contentLength").toLongOrNull()?.let { item.contentLength = it }
        f.optString("approxDurationMs").toLongOrNull()?.let { item.approxDurationMs = it }
            ?: run { if (durationSec > 0) item.approxDurationMs = durationSec * 1000 }
        if (f.has("bitrate")) item.bitrate = f.optInt("bitrate")
        if (f.has("width")) item.width = f.optInt("width")
        if (f.has("height")) item.height = f.optInt("height")
        if (f.has("fps")) item.fps = f.optInt("fps")
        f.optJSONObject("initRange")?.let {
            item.initStart = it.optString("start").toIntOrNull() ?: 0
            item.initEnd = it.optString("end").toIntOrNull() ?: 0
        }
        f.optJSONObject("indexRange")?.let {
            item.indexStart = it.optString("start").toIntOrNull() ?: 0
            item.indexEnd = it.optString("end").toIntOrNull() ?: 0
        }
        f.optString("quality").takeIf { it.isNotBlank() }?.let { item.quality = it }
        Regex("codecs=\"([^\"]+)\"").find(f.optString("mimeType"))?.groupValues?.get(1)?.let { item.codec = it }
        if (f.has("audioSampleRate")) f.optString("audioSampleRate").toIntOrNull()?.let { item.sampleRate = it }
        if (f.has("audioChannels")) item.audioChannels = f.optInt("audioChannels")
    }

    private fun images(arr: JSONArray?): List<Image> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val h = o.optInt("height", Image.HEIGHT_UNKNOWN)
            Image(
                o.optString("url"),
                h,
                o.optInt("width", Image.WIDTH_UNKNOWN),
                Image.ResolutionLevel.fromHeight(h),
            )
        }
    }
}
