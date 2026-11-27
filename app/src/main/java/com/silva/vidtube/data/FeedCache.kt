package com.silva.vidtube.data

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Última versión de cada lista (Inicio, categorías): al abrir la app se muestra al instante
 * mientras se actualiza en segundo plano, en vez de esperar con la pantalla vacía.
 */
object FeedCache {
    private var prefs: SharedPreferences? = null
    /** Más viejo que esto ya no se muestra (mejor esperar que enseñar algo desactualizado). */
    private const val MAX_AGE_MS = 3L * 24 * 3600_000

    fun init(context: Context) {
        prefs = context.getSharedPreferences("vidtube_feed_cache", Context.MODE_PRIVATE)
    }

    fun save(key: String, entries: List<FeedEntry>) {
        val p = prefs ?: return
        val videos = entries.filterIsInstance<FeedEntry.Video>().take(40)
        if (videos.isEmpty()) return
        runCatching { p.edit().putString(key, encode(videos, System.currentTimeMillis())).apply() }
    }

    fun load(key: String): List<FeedEntry>? {
        val raw = prefs?.getString(key, null) ?: return null
        return runCatching { decode(raw, System.currentTimeMillis()) }.getOrNull()
    }

    internal fun encode(videos: List<FeedEntry.Video>, now: Long): String {
        val arr = JSONArray()
        videos.forEach { v -> arr.put(JSONObject().put("v", v.video.toJson()).put("r", v.reason ?: "")) }
        return JSONObject().put("t", now).put("items", arr).toString()
    }

    internal fun decode(raw: String, now: Long): List<FeedEntry>? {
        val o = JSONObject(raw)
        if (now - o.optLong("t") > MAX_AGE_MS) return null
        val arr = o.optJSONArray("items") ?: return null
        val out = (0 until arr.length()).map { i ->
            val e = arr.getJSONObject(i)
            FeedEntry.Video(VideoItem.fromJson(e.getJSONObject("v")), e.optString("r").ifBlank { null })
        }
        return out.ifEmpty { null }
    }
}
