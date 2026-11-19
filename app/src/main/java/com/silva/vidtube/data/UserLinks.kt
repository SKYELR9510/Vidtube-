package com.silva.vidtube.data

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

/** Enlace que el usuario agregó a una película o serie (se guarda solo en este celular). */
data class UserLink(val name: String, val url: String, val fromPanel: Boolean = false) {
    /** Tipo de enlace, para saber cómo abrirlo. */
    val kind: Kind get() = UserLinks.kindOf(url)

    enum class Kind { YOUTUBE, VIDEO_FILE, WEB }
}

object UserLinks {
    private var prefs: SharedPreferences? = null
    private val _all = MutableStateFlow<Map<String, List<UserLink>>>(emptyMap())
    val all: StateFlow<Map<String, List<UserLink>>> = _all

    fun key(id: Long, tv: Boolean) = (if (tv) "tv:" else "movie:") + id

    fun init(context: Context) {
        prefs = context.getSharedPreferences("vidtube_links", Context.MODE_PRIVATE)
        _all.value = runCatching {
            val o = JSONObject(prefs?.getString("links", "{}") ?: "{}")
            o.keys().asSequence().associateWith { k ->
                val a = o.getJSONArray(k)
                (0 until a.length()).mapNotNull { i ->
                    val l = a.optJSONObject(i) ?: return@mapNotNull null
                    val url = normalize(l.optString("url")) ?: return@mapNotNull null
                    UserLink(l.optString("name").ifBlank { hostOf(url) }, url)
                }
            }.filterValues { it.isNotEmpty() }
        }.getOrDefault(emptyMap())
    }

    /** Agrega un enlace. Devuelve un mensaje de error o null si se guardó. */
    fun add(key: String, rawUrl: String, rawName: String): String? {
        val url = normalize(rawUrl) ?: return "El enlace debe empezar con https:// o http://"
        val current = _all.value[key].orEmpty()
        if (current.any { it.url == url }) return "Ese enlace ya está guardado"
        if (current.size >= 20) return "Ya tienes 20 enlaces en este título"
        val name = rawName.trim().take(60).ifBlank { hostOf(url) }
        save(_all.value + (key to current + UserLink(name, url)))
        return null
    }

    fun remove(key: String, link: UserLink) {
        val left = _all.value[key].orEmpty().filterNot { it.url == link.url }
        save(if (left.isEmpty()) _all.value - key else _all.value + (key to left))
    }

    private fun save(map: Map<String, List<UserLink>>) {
        _all.value = map
        val o = JSONObject()
        map.forEach { (k, list) ->
            o.put(k, JSONArray().apply { list.forEach { put(JSONObject().put("name", it.name).put("url", it.url)) } })
        }
        prefs?.edit()?.putString("links", o.toString())?.apply()
    }

    /**
     * Solo enlaces web (http/https) con un dominio: nada de javascript:, intent:, file: ni
     * content:, que podrían abrir cosas del celular en vez de una página.
     */
    /** Cabeceras que se aceptan en los enlaces de listas IPTV (formato `enlace|User-Agent=…&Referer=…`). */
    private val allowedHeaders = mapOf(
        "user-agent" to "User-Agent", "referer" to "Referer", "referrer" to "Referer",
        "origin" to "Origin", "cookie" to "Cookie",
    )

    /**
     * Separa el enlace de sus cabeceras al estilo IPTV/Kodi: `https://…/canal.m3u8|User-Agent=VLC&Referer=https://…`.
     * Devuelve (enlace, cabeceras). Solo se aceptan User-Agent, Referer, Origin y Cookie.
     */
    fun splitHeaders(raw: String): Pair<String, Map<String, String>> {
        val i = raw.indexOf('|')
        if (i < 0) return raw to emptyMap()
        val headers = LinkedHashMap<String, String>()
        raw.substring(i + 1).split('&').forEach { part ->
            val eq = part.indexOf('=')
            if (eq <= 0) return@forEach
            val key = allowedHeaders[part.substring(0, eq).trim().lowercase()] ?: return@forEach
            val value = runCatching { java.net.URLDecoder.decode(part.substring(eq + 1), "UTF-8") }
                .getOrDefault(part.substring(eq + 1)).trim()
            if (value.isNotEmpty() && value.length <= 400 && value.none { it.code < 0x20 }) headers[key] = value
        }
        return raw.substring(0, i) to headers
    }

    fun normalize(raw: String): String? {
        val trimmed = raw.trim()
        // Las cabeceras IPTV (después de «|») pueden tener espacios; el enlace en sí no
        val pipe = trimmed.indexOf('|')
        val options = if (pipe >= 0) trimmed.substring(pipe + 1).trim() else ""
        if (options.length > 600 || options.any { it.code < 0x20 }) return null
        val s = if (pipe >= 0) trimmed.substring(0, pipe).trim() else trimmed
        if (s.isEmpty() || s.length > 2000 || s.any { it.isWhitespace() || it.code < 0x20 }) return null
        val withScheme = if (Regex("^[a-zA-Z][a-zA-Z0-9+.-]*:").containsMatchIn(s)) s else "https://$s"
        val uri = runCatching { java.net.URI(withScheme) }.getOrNull() ?: return null
        val scheme = uri.scheme?.lowercase() ?: return null
        if (scheme != "https" && scheme != "http") return null
        val host = uri.host ?: return null
        if (!host.contains('.')) return null
        return if (options.isNotEmpty() && splitHeaders("x|$options").second.isNotEmpty()) "$withScheme|$options" else withScheme
    }

    fun hostOf(url: String): String =
        runCatching { java.net.URI(url.substringBefore('|')).host.removePrefix("www.").removePrefix("m.") }.getOrNull() ?: url

    fun kindOf(rawUrl: String): UserLink.Kind {
        val url = rawUrl.substringBefore('|')
        val host = hostOf(url).lowercase()
        // Google Drive y Dropbox se convierten en enlaces directos al archivo
        if (driveId(url) != null || host == "dropbox.com" || host.endsWith(".dropbox.com") ||
            host == "dropboxusercontent.com" || host.endsWith(".dropboxusercontent.com")
        ) return UserLink.Kind.VIDEO_FILE
        if (host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com") || host == "music.youtube.com") {
            return UserLink.Kind.YOUTUBE
        }
        val path = runCatching { java.net.URI(url).path.orEmpty().lowercase() }.getOrDefault("")
        if (Regex("\\.(mp4|m4v|webm|mkv|mov|m3u8|mpd|avi|3gp)$").containsMatchIn(path)) return UserLink.Kind.VIDEO_FILE
        return UserLink.Kind.WEB
    }

    /** Id del archivo de un enlace de Google Drive (…/file/d/ID/…, …?id=ID). */
    fun driveId(url: String): String? {
        val host = hostOf(url).lowercase()
        if (host != "drive.google.com" && host != "docs.google.com" && host != "drive.usercontent.google.com") return null
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        val fromPath = Regex("/file/d/([A-Za-z0-9_-]{10,})").find(uri.path.orEmpty())?.groupValues?.get(1)
        val fromQuery = uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("id=") }?.removePrefix("id=")
        return (fromPath ?: fromQuery)?.takeIf { Regex("^[A-Za-z0-9_-]{10,}$").matches(it) }
    }

    /**
     * Enlace que el reproductor puede leer directamente:
     *  - Google Drive: descarga directa (el archivo debe estar compartido con "cualquier persona con el enlace").
     *  - Dropbox: ?raw=1 en vez de la página de vista previa.
     *  - Lo demás queda igual.
     */
    fun directUrl(rawUrl: String): String {
        val suffix = rawUrl.indexOf('|').let { if (it >= 0) rawUrl.substring(it) else "" }
        return directBase(rawUrl.substringBefore('|')) + suffix
    }

    private fun directBase(url: String): String {
        driveId(url)?.let { return "https://drive.usercontent.google.com/download?id=$it&export=download&confirm=t" }
        val host = hostOf(url).lowercase()
        if (host == "dropbox.com" || host.endsWith(".dropbox.com")) {
            val base = url.replace(Regex("([?&])dl=[01]"), "$1raw=1")
            return if (base.contains("raw=1")) base else base + (if ('?' in base) "&" else "?") + "raw=1"
        }
        return url
    }

    /** ¿Es un enlace de YouTube? */
    fun isYoutube(url: String): Boolean {
        val host = hostOf(url).lowercase()
        return host == "youtube.com" || host == "youtu.be" || host.endsWith(".youtube.com")
    }

    /** Convierte cualquier enlace de YouTube al formato que entiende el reproductor. */
    fun youtubeWatchUrl(url: String): String? {
        val uri = runCatching { java.net.URI(url) }.getOrNull() ?: return null
        val host = hostOf(url).lowercase()
        val id = when {
            host == "youtu.be" -> uri.path.trim('/').substringBefore('/')
            uri.path.startsWith("/shorts/") || uri.path.startsWith("/live/") || uri.path.startsWith("/embed/") ->
                uri.path.split('/').getOrNull(2)
            else -> uri.rawQuery.orEmpty().split('&').firstOrNull { it.startsWith("v=") }?.removePrefix("v=")
        }?.takeIf { Regex("^[A-Za-z0-9_-]{6,20}$").matches(it) } ?: return null
        return "https://www.youtube.com/watch?v=$id"
    }
}
