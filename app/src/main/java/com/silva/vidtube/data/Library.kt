package com.silva.vidtube.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Todo lo personal se guarda solo en el celular: suscripciones, historial, ver más tarde y ajustes. */
object Library {
    private lateinit var prefs: SharedPreferences

    private val _subscriptions = MutableStateFlow<List<ChannelItem>>(emptyList())
    val subscriptions: StateFlow<List<ChannelItem>> = _subscriptions.asStateFlow()

    private val _history = MutableStateFlow<List<VideoItem>>(emptyList())
    val history: StateFlow<List<VideoItem>> = _history.asStateFlow()

    private val _watchLater = MutableStateFlow<List<VideoItem>>(emptyList())
    val watchLater: StateFlow<List<VideoItem>> = _watchLater.asStateFlow()

    private val _settings = MutableStateFlow(Settings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _searches = MutableStateFlow<List<String>>(emptyList())
    val searches: StateFlow<List<String>> = _searches.asStateFlow()

    private val positions = LinkedHashMap<String, Long>()

    /** Grupos de videos relacionados a lo último que viste (para el Inicio "Para ti"). */
    private var recommendationBatches: List<List<VideoItem>> = emptyList()

    data class Settings(
        val quality: Int = 720,
        val backgroundPlay: Boolean = true,
        val pictureInPicture: Boolean = true,
        val saveHistory: Boolean = true,
        val autoplay: Boolean = true,
    )

    fun init(context: Context) {
        prefs = context.getSharedPreferences("vidtube_library", Context.MODE_PRIVATE)
        _subscriptions.value = readArray("subscriptions").map { ChannelItem.fromJson(it) }
        _history.value = readArray("history").map { VideoItem.fromJson(it) }
        _watchLater.value = readArray("watch_later").map { VideoItem.fromJson(it) }
        _searches.value = runCatching {
            val arr = JSONArray(prefs.getString("searches", "[]") ?: "[]")
            (0 until arr.length()).map { arr.getString(it) }
        }.getOrDefault(emptyList())
        _settings.value = Settings(
            quality = prefs.getInt("quality", 720),
            backgroundPlay = prefs.getBoolean("background_play", true),
            pictureInPicture = prefs.getBoolean("pip", true),
            saveHistory = prefs.getBoolean("save_history", true),
            autoplay = prefs.getBoolean("autoplay", true),
        )
        recommendationBatches = runCatching {
            val arr = JSONArray(prefs.getString("recommendations", "[]") ?: "[]")
            (0 until arr.length()).map { i ->
                val b = arr.getJSONArray(i)
                (0 until b.length()).map { VideoItem.fromJson(b.getJSONObject(it)) }
            }
        }.getOrDefault(emptyList())
        runCatching {
            val o = JSONObject(prefs.getString("positions", "{}") ?: "{}")
            o.keys().forEach { positions[it] = o.getLong(it) }
        }
    }

    // ---------- Suscripciones ----------
    fun isSubscribed(url: String?) = url != null && _subscriptions.value.any { sameChannel(it.url, url) }

    fun toggleSubscription(channel: ChannelItem) {
        val current = _subscriptions.value
        _subscriptions.value = if (current.any { sameChannel(it.url, channel.url) }) {
            current.filterNot { sameChannel(it.url, channel.url) }
        } else {
            (current + channel).sortedBy { it.name.lowercase() }
        }
        writeArray("subscriptions", _subscriptions.value.map { it.toJson() })
    }

    private fun sameChannel(a: String, b: String) =
        a.trimEnd('/').substringAfterLast('/') == b.trimEnd('/').substringAfterLast('/')

    // ---------- Historial ----------
    fun addToHistory(video: VideoItem) {
        if (!_settings.value.saveHistory) return
        _history.value = (listOf(video) + _history.value.filterNot { it.url == video.url }).take(300)
        writeArray("history", _history.value.map { it.toJson() })
    }

    fun removeFromHistory(url: String) {
        _history.value = _history.value.filterNot { it.url == url }
        writeArray("history", _history.value.map { it.toJson() })
    }

    fun clearHistory() {
        _history.value = emptyList()
        _searches.value = emptyList()
        recommendationBatches = emptyList()
        prefs.edit().putString("searches", "[]").putString("recommendations", "[]").apply()
        positions.clear()
        writeArray("history", emptyList())
        prefs.edit().putString("positions", "{}").apply()
    }

    // ---------- Recomendaciones "Para ti" ----------
    fun addRecommendations(related: List<VideoItem>) {
        if (!_settings.value.saveHistory) return
        val batch = related.filter { !it.isLive && !it.isShort && it.durationSec != 0L }.take(15)
        if (batch.isEmpty()) return
        recommendationBatches = (listOf(batch) + recommendationBatches).take(12)
        val arr = JSONArray()
        recommendationBatches.forEach { b ->
            val inner = JSONArray()
            b.forEach { inner.put(it.toJson()) }
            arr.put(inner)
        }
        prefs.edit().putString("recommendations", arr.toString()).apply()
    }

    /**
     * Mezcla las recomendaciones: toma por turnos de cada grupo (los más recientes primero)
     * y quita lo que ya viste.
     */
    fun forYou(seed: Long): List<VideoItem> {
        val watched = _history.value.map { it.url }.toHashSet()
        val random = java.util.Random(seed)
        val queues = recommendationBatches.map { b ->
            ArrayDeque(b.filter { it.url !in watched }.shuffled(random))
        }
        val out = LinkedHashMap<String, VideoItem>()
        while (queues.any { it.isNotEmpty() } && out.size < 80) {
            queues.forEach { q -> q.removeFirstOrNull()?.let { out.putIfAbsent(it.url, it) } }
        }
        return out.values.toList()
    }

    // ---------- Búsquedas recientes ----------
    fun addSearch(query: String) {
        if (!_settings.value.saveHistory) return
        _searches.value = (listOf(query) + _searches.value.filterNot { it.equals(query, true) }).take(15)
        val arr = JSONArray()
        _searches.value.forEach { arr.put(it) }
        prefs.edit().putString("searches", arr.toString()).apply()
    }

    // ---------- Ver más tarde ----------
    fun isInWatchLater(url: String) = _watchLater.value.any { it.url == url }

    fun toggleWatchLater(video: VideoItem): Boolean {
        val added = !isInWatchLater(video.url)
        _watchLater.value = if (added) listOf(video) + _watchLater.value
        else _watchLater.value.filterNot { it.url == video.url }
        writeArray("watch_later", _watchLater.value.map { it.toJson() })
        return added
    }

    // ---------- Posición de reproducción (continuar donde te quedaste) ----------
    fun position(url: String): Long = positions[url] ?: 0L

    fun savePosition(url: String, positionMs: Long, durationMs: Long) {
        // Si ya casi terminó, al volver empieza desde el inicio
        if (durationMs > 0 && positionMs > durationMs - 15_000) positions.remove(url)
        else if (positionMs > 5_000) positions[url] = positionMs
        while (positions.size > 400) positions.remove(positions.keys.first())
        val o = JSONObject()
        positions.forEach { (k, v) -> o.put(k, v) }
        prefs.edit().putString("positions", o.toString()).apply()
    }

    // ---------- Ajustes ----------
    fun updateSettings(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        _settings.value = s
        prefs.edit()
            .putInt("quality", s.quality)
            .putBoolean("background_play", s.backgroundPlay)
            .putBoolean("pip", s.pictureInPicture)
            .putBoolean("save_history", s.saveHistory)
            .putBoolean("autoplay", s.autoplay)
            .apply()
    }

    private fun readArray(key: String): List<JSONObject> = runCatching {
        val arr = JSONArray(prefs.getString(key, "[]") ?: "[]")
        (0 until arr.length()).map { arr.getJSONObject(it) }
    }.getOrDefault(emptyList())

    private fun writeArray(key: String, items: List<JSONObject>) {
        val arr = JSONArray()
        items.forEach { arr.put(it) }
        prefs.edit().putString(key, arr.toString()).apply()
    }
}
