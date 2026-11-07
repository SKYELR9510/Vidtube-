package com.silva.vidtube.data

import org.json.JSONArray
import org.json.JSONObject
import org.schabi.newpipe.extractor.NewPipe
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Búsqueda de Shorts con el filtro "Shorts" de YouTube.
 * El extractor no lee los Shorts de la búsqueda (vienen en un formato aparte), así que
 * pedimos la búsqueda directamente y sacamos cada short de ahí.
 */
object ShortsSearch {
    // Filtro de búsqueda "Tipo: Shorts"
    private const val SHORTS_FILTER = "EgIQCQ=="
    private const val CLIENT_VERSION = "2.20251105.01.00"

    fun search(query: String): List<VideoItem> {
        val loc = NewPipe.getPreferredLocalization()
        val country = NewPipe.getPreferredContentCountry()
        val body = JSONObject()
            .put("context", JSONObject().put("client", JSONObject()
                .put("clientName", "WEB")
                .put("clientVersion", CLIENT_VERSION)
                .put("hl", loc.localizationCode.ifBlank { "es" })
                .put("gl", country.countryCode.ifBlank { "PE" })))
            .put("query", query)
            .put("params", SHORTS_FILTER)
            .toString()
        val request = okhttp3.Request.Builder()
            .url("https://www.youtube.com/youtubei/v1/search?prettyPrint=false")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("User-Agent", DownloaderImpl.USER_AGENT)
            .header("X-YouTube-Client-Name", "1")
            .header("X-YouTube-Client-Version", CLIENT_VERSION)
            .header("Origin", "https://www.youtube.com")
            .build()
        val client = (DownloaderImpl.getInstance() ?: DownloaderImpl.init()).client
        val text = client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) throw java.io.IOException("YouTube respondió ${r.code}")
            r.body?.string().orEmpty()
        }
        val out = ArrayList<VideoItem>()
        collect(JSONObject(text), out, HashSet())
        return out
    }

    /** Recorre la respuesta buscando cada "shortsLockupViewModel". */
    private fun collect(node: Any?, out: MutableList<VideoItem>, seen: MutableSet<String>) {
        when (node) {
            is JSONObject -> {
                node.optJSONObject("shortsLockupViewModel")?.let { parse(it) }?.let {
                    if (seen.add(it.url)) out += it
                }
                node.keys().forEach { k -> collect(node.opt(k), out, seen) }
            }
            is JSONArray -> for (i in 0 until node.length()) collect(node.opt(i), out, seen)
        }
    }

    private fun parse(vm: JSONObject): VideoItem? {
        val id = vm.optJSONObject("onTap")?.optJSONObject("innertubeCommand")
            ?.optJSONObject("reelWatchEndpoint")?.optString("videoId")?.takeIf { it.length == 11 }
            ?: vm.optString("entityId").substringAfterLast('-', "").takeIf { it.length == 11 }
            ?: return null
        val overlay = vm.optJSONObject("overlayMetadata")
        val title = overlay?.optJSONObject("primaryText")?.optString("content")?.ifBlank { null }
            ?: vm.optString("accessibilityText").substringBefore(", ").ifBlank { "Short" }
        val views = overlay?.optJSONObject("secondaryText")?.optString("content")?.ifBlank { null }
            ?.replace("visualizaciones", "vistas")?.replace("views", "vistas")
        val sources = vm.optJSONObject("thumbnail")?.optJSONArray("sources")
        var thumb: String? = null
        var bestW = 0
        if (sources != null) for (i in 0 until sources.length()) {
            val s = sources.optJSONObject(i) ?: continue
            val w = s.optInt("width")
            if (thumb == null || (w in (bestW + 1)..720)) { thumb = s.optString("url"); bestW = w }
        }
        return VideoItem(
            url = "https://www.youtube.com/shorts/$id",
            title = title,
            thumbnail = thumb?.ifBlank { null } ?: "https://i.ytimg.com/vi/$id/oar2.jpg",
            channelName = "",
            channelUrl = null,
            channelAvatar = null,
            durationSec = 0,
            views = -1,
            uploaded = views,
            isLive = false,
            isShort = true,
        )
    }
}
