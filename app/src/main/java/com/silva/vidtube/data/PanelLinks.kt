package com.silva.vidtube.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Título con enlaces en el panel web. */
data class PanelTitle(
    val tv: Boolean,
    val tmdbId: Long,
    val title: String,
    val year: Int?,
    val poster: String?,
    val links: List<UserLink>,
) {
    fun asMovie() = TmdbMovie(tmdbId, title, title, year, "", poster, null, 0.0, tv)
}

/** Canal de TV agregado en el panel (enlace de su señal en vivo). */
data class PanelChannel(val name: String, val url: String, val logo: String?)

/**
 * Enlaces del panel web (PHP en tu hosting). La app solo los lee con la clave;
 * se guardan en el celular para que funcionen sin conexión al panel.
 */
object PanelLinks {
    private var prefs: SharedPreferences? = null
    private val _titles = MutableStateFlow<List<PanelTitle>>(emptyList())
    val titles: StateFlow<List<PanelTitle>> = _titles
    private val _channels = MutableStateFlow<List<PanelChannel>>(emptyList())
    /** Canales de TV del panel. */
    val channels: StateFlow<List<PanelChannel>> = _channels
    private val _status = MutableStateFlow<String?>(null)
    /** Último resultado de la sincronización (para Configuración). */
    val status: StateFlow<String?> = _status
    private val _settings = MutableStateFlow("" to "")
    /** (dirección del panel, clave) puestas por el usuario. */
    val settings: StateFlow<Pair<String, String>> = _settings
    @Volatile private var lastFetch = 0L

    fun init(context: Context) {
        prefs = context.getSharedPreferences("vidtube_panel", Context.MODE_PRIVATE)
        _settings.value = (prefs?.getString("url", "") ?: "") to (prefs?.getString("key", "") ?: "")
        prefs?.getString("cache", null)?.let {
            runCatching { val o = JSONObject(it); _titles.value = parse(o); _channels.value = parseChannels(o) }
        }
    }

    private val builtInUrl get() = runCatching { com.silva.vidtube.BuildConfig.PANEL_URL }.getOrDefault("")
    private val builtInKey get() = runCatching { com.silva.vidtube.BuildConfig.PANEL_KEY }.getOrDefault("")
    private fun url() = _settings.value.first.ifBlank { builtInUrl }.trim()
    private fun key() = _settings.value.second.ifBlank { builtInKey }.trim()

    fun isConfigured() = url().isNotEmpty() && key().isNotEmpty()
    fun hasBuiltIn() = builtInUrl.isNotBlank() && builtInKey.isNotBlank()

    fun save(url: String, key: String) {
        _settings.value = url.trim() to key.trim()
        prefs?.edit()?.putString("url", url.trim())?.putString("key", key.trim())?.apply()
        lastFetch = 0
    }

    fun linksFor(id: Long, tv: Boolean): List<UserLink> =
        _titles.value.firstOrNull { it.tmdbId == id && it.tv == tv }?.links.orEmpty()

    /** Dirección de api.php a partir de lo que escribió el usuario (con o sin /api.php). */
    fun apiUrl(base: String): String? {
        val b = UserLinks.normalize(base) ?: return null
        if (!b.startsWith("https://")) return null // la clave nunca viaja sin cifrar
        val clean = b.substringBefore('?').trimEnd('/')
        return if (clean.endsWith("api.php")) clean else "$clean/api.php"
    }

    /** Descarga los enlaces del panel (como mucho una vez cada 2 minutos, salvo force). */
    suspend fun refresh(force: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!isConfigured()) { _status.value = null; return@withContext false }
        if (!force && System.currentTimeMillis() - lastFetch < 120_000) return@withContext true
        val api = apiUrl(url()) ?: run { _status.value = "La dirección del panel debe empezar con https://"; return@withContext false }
        try {
            val conn = (URL(api + "?key=" + URLEncoder.encode(key(), "UTF-8")).openConnection() as HttpURLConnection).apply {
                connectTimeout = 12_000; readTimeout = 15_000
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = false
            }
            try {
                when (val code = conn.responseCode) {
                    200 -> {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val json = JSONObject(body)
                        val list = parse(json)
                        _titles.value = list
                        _channels.value = parseChannels(json)
                        prefs?.edit()?.putString("cache", body)?.apply()
                        lastFetch = System.currentTimeMillis()
                        _status.value = "Conectado · ${list.sumOf { it.links.size }} enlaces en ${list.size} títulos"
                        true
                    }
                    401 -> { _status.value = "La clave del panel no es correcta"; false }
                    404 -> { _status.value = "No se encontró api.php en esa dirección"; false }
                    else -> { _status.value = "El panel respondió HTTP $code"; false }
                }
            } finally { conn.disconnect() }
        } catch (e: Exception) {
            _status.value = "No se pudo conectar con el panel (${e.javaClass.simpleName})"
            false
        }
    }

    fun parseChannels(o: JSONObject): List<PanelChannel> {
        val arr = o.optJSONArray("channels") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val c = arr.optJSONObject(i) ?: return@mapNotNull null
            val url = UserLinks.normalize(c.optString("url")) ?: return@mapNotNull null
            val name = c.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            PanelChannel(name, url, c.optString("logo").takeIf { it.startsWith("https://") })
        }
    }

    fun parse(o: JSONObject): List<PanelTitle> {
        val arr = o.optJSONArray("titles") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            val t = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = t.optLong("tmdb_id", -1).takeIf { it > 0 } ?: return@mapNotNull null
            val title = t.optString("title").takeIf { it.isNotBlank() } ?: return@mapNotNull null
            val la = t.optJSONArray("links")
            val links = (0 until (la?.length() ?: 0)).mapNotNull { j ->
                val l = la!!.optJSONObject(j) ?: return@mapNotNull null
                val url = UserLinks.normalize(l.optString("url")) ?: return@mapNotNull null
                UserLink(l.optString("name").ifBlank { UserLinks.hostOf(url) }, url, fromPanel = true)
            }
            if (links.isEmpty()) return@mapNotNull null
            val poster = t.optString("poster").takeIf { it.startsWith("/") }?.let { "https://image.tmdb.org/t/p/w342$it" }
            PanelTitle(t.optString("kind") == "tv", id, title, t.optInt("year", 0).takeIf { it > 0 }, poster, links)
        }
    }
}
