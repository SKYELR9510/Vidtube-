package com.silva.vidtube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL

/**
 * ¿Una señal está transmitiendo? Pide solo el comienzo (la lista .m3u8 o los primeros bytes)
 * como lo haría el reproductor: primero como navegador del celular y, si lo rechazan, como VLC.
 */
object LiveCheck {
    private val agents = listOf(
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36",
        "VLC/3.0.20 LibVLC/3.0.20",
    )

    suspend fun isUp(rawUrl: String): Boolean = withContext(Dispatchers.IO) {
        if (rawUrl.isBlank()) return@withContext false
        val (url, headers) = UserLinks.splitHeaders(UserLinks.directUrl(rawUrl))
        val tryAgents = headers["User-Agent"]?.let { listOf(it) } ?: agents
        for (ua in tryAgents) {
            val ok = runCatching {
                val c = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 7_000; readTimeout = 7_000
                    instanceFollowRedirects = true
                    setRequestProperty("User-Agent", ua)
                    setRequestProperty("Range", "bytes=0-2047")
                    headers.forEach { (k, v) -> if (k != "User-Agent") setRequestProperty(k, v) }
                }
                try {
                    val code = c.responseCode
                    if (code !in 200..399) return@runCatching false
                    val head = c.inputStream.use { s -> ByteArray(2048).let { b -> String(b, 0, s.read(b).coerceAtLeast(0)) } }
                    // Una lista HLS vacía o sin segmentos no es una señal en vivo
                    !head.startsWith("#EXTM3U") || head.contains("#EXTINF") || head.contains("#EXT-X-STREAM-INF")
                } finally { c.disconnect() }
            }.getOrDefault(false)
            if (ok) return@withContext true
        }
        false
    }
}
