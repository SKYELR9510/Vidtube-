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

/** Película de TMDB (solo información: póster, sinopsis, puntuación). */
data class TmdbMovie(
    val id: Long,
    val title: String,
    val originalTitle: String,
    val year: Int?,
    val overview: String,
    val poster: String?,
    val backdrop: String?,
    val rating: Double,
)

data class TmdbPerson(val name: String, val character: String, val photo: String?)

data class TmdbDetail(
    val movie: TmdbMovie,
    val runtimeMin: Int,
    val genres: List<String>,
    val tagline: String,
    val cast: List<TmdbPerson>,
    val director: String?,
    /** Clave de YouTube del tráiler (en español si existe). */
    val trailerKey: String?,
)

/** Fila de la sección de películas (Tendencias, En cines, géneros…). */
data class TmdbRow(val id: String, val title: String, val path: String, val extra: String = "")

/**
 * Cliente de The Movie Database (TMDB). Solo trae información de las películas;
 * los videos se siguen viendo desde YouTube (tráiler o película completa gratuita).
 */
object Tmdb {
    private const val API = "https://api.themoviedb.org/3/"
    private const val IMG = "https://image.tmdb.org/t/p/"
    private const val LANG = "es-MX"
    private const val REGION = "PE"

    val rows = listOf(
        TmdbRow("trending", "Tendencias de la semana", "trending/movie/week"),
        TmdbRow("now", "En cines", "movie/now_playing", "&region=$REGION"),
        TmdbRow("popular", "Populares", "movie/popular", "&region=$REGION"),
        TmdbRow("top", "Mejor valoradas", "movie/top_rated"),
        TmdbRow("g28", "Acción", "discover/movie", "&with_genres=28&sort_by=popularity.desc"),
        TmdbRow("g35", "Comedia", "discover/movie", "&with_genres=35&sort_by=popularity.desc"),
        TmdbRow("g27", "Terror", "discover/movie", "&with_genres=27&sort_by=popularity.desc"),
        TmdbRow("g16", "Animación", "discover/movie", "&with_genres=16&sort_by=popularity.desc"),
        TmdbRow("g10751", "En familia", "discover/movie", "&with_genres=10751&sort_by=popularity.desc"),
        TmdbRow("g18", "Drama", "discover/movie", "&with_genres=18&sort_by=popularity.desc"),
    )

    private var prefs: SharedPreferences? = null
    private val _userToken = MutableStateFlow("")
    /** Clave pegada por el usuario en Configuración (tiene prioridad). */
    val userToken: StateFlow<String> = _userToken

    fun init(context: Context) {
        prefs = context.getSharedPreferences("vidtube_tmdb", Context.MODE_PRIVATE)
        _userToken.value = prefs?.getString("token", "") ?: ""
    }

    fun setUserToken(token: String) {
        val t = token.trim()
        _userToken.value = t
        prefs?.edit()?.putString("token", t)?.apply()
    }

    private val builtInToken: String
        get() = runCatching { com.silva.vidtube.BuildConfig.TMDB_TOKEN }.getOrDefault("")

    private fun token(): String = _userToken.value.ifBlank { builtInToken }.trim()

    /** true si hay una clave (de la app o del usuario). */
    fun isConfigured(): Boolean = token().isNotEmpty()

    /** true si la clave viene incluida en la app. */
    fun hasBuiltInToken(): Boolean = builtInToken.isNotBlank()

    class TmdbException(message: String) : Exception(message)

    private val cache = HashMap<String, Pair<Long, String>>()

    private fun get(pathAndQuery: String): JSONObject {
        val t = token()
        if (t.isEmpty()) throw TmdbException("Falta la clave de TMDB. Agrégala en Configuración.")
        // Token de lectura (v4, empieza con "eyJ") va en la cabecera; clave corta (v3) va en la URL
        val bearer = t.startsWith("eyJ")
        val sep = if ('?' in pathAndQuery) '&' else '?'
        val url = API + pathAndQuery + (if (bearer) "" else "${sep}api_key=" + enc(t))
        val now = System.currentTimeMillis()
        synchronized(cache) {
            cache[url]?.let { (at, body) -> if (now - at < 30 * 60_000) return JSONObject(body) }
        }
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 12_000
            readTimeout = 15_000
            setRequestProperty("Accept", "application/json")
            if (bearer) setRequestProperty("Authorization", "Bearer $t")
        }
        try {
            val code = conn.responseCode
            if (code == 401) throw TmdbException("La clave de TMDB no es válida. Revísala en Configuración.")
            if (code == 429) throw TmdbException("TMDB recibió demasiadas consultas. Intenta en un momento.")
            if (code !in 200..299) throw TmdbException("TMDB no respondió (HTTP $code).")
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            synchronized(cache) { cache[url] = now to body }
            return JSONObject(body)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    suspend fun list(row: TmdbRow, page: Int = 1): List<TmdbMovie> = withContext(Dispatchers.IO) {
        parseList(get("${row.path}?language=$LANG&page=$page&include_adult=false${row.extra}"))
    }

    suspend fun search(query: String): List<TmdbMovie> = withContext(Dispatchers.IO) {
        parseList(get("search/movie?language=$LANG&include_adult=false&query=" + enc(query)))
    }

    suspend fun detail(id: Long): TmdbDetail = withContext(Dispatchers.IO) {
        parseDetail(
            get("movie/$id?language=$LANG&append_to_response=videos,credits&include_video_language=es,en,null")
        )
    }

    // ---------- Lectura del JSON (separada para poder probarla) ----------

    fun image(path: String?, size: String): String? =
        path?.takeIf { it.isNotBlank() && it != "null" }?.let { IMG + size + it }

    fun parseList(json: JSONObject): List<TmdbMovie> {
        val arr = json.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> parseMovie(arr.optJSONObject(i) ?: return@mapNotNull null) }
            .filter { it.poster != null }
    }

    fun parseMovie(o: JSONObject): TmdbMovie? {
        val id = o.optLong("id", -1)
        val title = o.optString("title").takeIf { it.isNotBlank() } ?: return null
        if (id <= 0) return null
        return TmdbMovie(
            id = id,
            title = title,
            originalTitle = o.optString("original_title", title),
            year = o.optString("release_date").take(4).toIntOrNull(),
            overview = o.optString("overview"),
            poster = image(o.optString("poster_path"), "w342"),
            backdrop = image(o.optString("backdrop_path"), "w780"),
            rating = o.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it },
        )
    }

    fun parseDetail(o: JSONObject): TmdbDetail {
        val movie = parseMovie(o) ?: throw TmdbException("No se encontró la película.")
        val genres = o.optJSONArray("genres")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
        }.orEmpty()
        val credits = o.optJSONObject("credits")
        val cast = credits?.optJSONArray("cast")?.let { a ->
            (0 until minOf(a.length(), 12)).mapNotNull { i ->
                val c = a.optJSONObject(i) ?: return@mapNotNull null
                val name = c.optString("name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                TmdbPerson(name, c.optString("character"), image(c.optString("profile_path"), "w185"))
            }
        }.orEmpty()
        val director = credits?.optJSONArray("crew")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .firstOrNull { it.optString("job") == "Director" }?.optString("name")
        }
        return TmdbDetail(
            movie = movie,
            runtimeMin = o.optInt("runtime", 0),
            genres = genres,
            tagline = o.optString("tagline"),
            cast = cast,
            director = director,
            trailerKey = pickTrailer(o.optJSONObject("videos")),
        )
    }

    /** Tráiler de YouTube: primero en español, primero los oficiales. */
    fun pickTrailer(videos: JSONObject?): String? {
        val a = videos?.optJSONArray("results") ?: return null
        val list = (0 until a.length()).mapNotNull { a.optJSONObject(it) }
            .filter { it.optString("site") == "YouTube" && it.optString("key").isNotBlank() }
            .filter { it.optString("type") == "Trailer" || it.optString("type") == "Teaser" }
        return list.sortedWith(
            compareBy<JSONObject>(
                { if (it.optString("iso_639_1") == "es") 0 else 1 },
                { if (it.optString("type") == "Trailer") 0 else 1 },
                { if (it.optBoolean("official", false)) 0 else 1 },
            )
        ).firstOrNull()?.optString("key")
    }

    /**
     * ¿El título de un video de YouTube corresponde a esta película?
     * Se compara sin tildes ni signos, con el título en español o el original.
     */
    fun matches(videoTitle: String, movie: TmdbMovie): Boolean {
        val v = norm(videoTitle)
        return listOf(movie.title, movie.originalTitle)
            .map { norm(it) }
            .filter { it.length >= 2 }
            .any { t -> v.contains(t) }
    }

    fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9ñ ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
