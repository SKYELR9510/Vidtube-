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
    /** true = serie de TV; false = película. */
    val tv: Boolean = false,
)

/** Plataforma donde se puede ver un título (datos de JustWatch vía TMDB). */
data class TmdbProvider(val id: Int, val name: String, val logo: String?)

/** Dónde ver un título en Perú. */
data class TmdbWatch(
    /** Página de TMDB con todas las opciones (enlaces de JustWatch). */
    val link: String?,
    /** Gratis (con o sin anuncios). */
    val free: List<TmdbProvider>,
    /** Incluido en una suscripción (Netflix, Prime, Disney+…). */
    val subscription: List<TmdbProvider>,
    /** Alquiler o compra. */
    val rentOrBuy: List<TmdbProvider>,
) {
    val isEmpty get() = free.isEmpty() && subscription.isEmpty() && rentOrBuy.isEmpty()
}

/** Temporada de una serie. */
data class TmdbSeason(val number: Int, val name: String, val episodes: Int, val poster: String?, val year: Int?)

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
    /** Solo series: temporadas (sin "especiales"). */
    val seasons: List<TmdbSeason> = emptyList(),
    /** Plataformas donde está en Perú. */
    val watch: TmdbWatch? = null,
)

/** Fila de la sección de películas (Tendencias, En cines, géneros…). */
data class TmdbRow(val id: String, val title: String, val path: String, val extra: String = "", val tv: Boolean = false)

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

    /** Filas de la sección Series. */
    val tvRows = listOf(
        TmdbRow("tv_trending", "Tendencias de la semana", "trending/tv/week", tv = true),
        TmdbRow("tv_air", "En emisión", "tv/on_the_air", tv = true),
        TmdbRow("tv_popular", "Populares", "tv/popular", tv = true),
        TmdbRow("tv_top", "Mejor valoradas", "tv/top_rated", tv = true),
        TmdbRow("tv_g10759", "Acción y aventura", "discover/tv", "&with_genres=10759&sort_by=popularity.desc", tv = true),
        TmdbRow("tv_g35", "Comedia", "discover/tv", "&with_genres=35&sort_by=popularity.desc", tv = true),
        TmdbRow("tv_g18", "Drama", "discover/tv", "&with_genres=18&sort_by=popularity.desc", tv = true),
        TmdbRow("tv_g80", "Crimen", "discover/tv", "&with_genres=80&sort_by=popularity.desc", tv = true),
        TmdbRow("tv_g10765", "Ciencia ficción y fantasía", "discover/tv", "&with_genres=10765&sort_by=popularity.desc", tv = true),
        TmdbRow("tv_g16", "Animación", "discover/tv", "&with_genres=16&sort_by=popularity.desc", tv = true),
        TmdbRow("tv_g10762", "Infantiles", "discover/tv", "&with_genres=10762&sort_by=popularity.desc", tv = true),
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
        parseList(get("${row.path}?language=$LANG&page=$page&include_adult=false${row.extra}"), row.tv)
    }

    /** Busca películas y series a la vez. */
    suspend fun search(query: String): List<TmdbMovie> = withContext(Dispatchers.IO) {
        parseList(get("search/multi?language=$LANG&include_adult=false&query=" + enc(query)))
    }

    suspend fun detail(id: Long, tv: Boolean = false): TmdbDetail = withContext(Dispatchers.IO) {
        val kind = if (tv) "tv" else "movie"
        parseDetail(
            get("$kind/$id?language=$LANG&append_to_response=videos,credits,watch/providers&include_video_language=es,en,null"),
            tv,
        )
    }

    /** "Si te gustó, mira también". */
    fun recommendationsRow(id: Long, tv: Boolean) =
        TmdbRow("rec", "", (if (tv) "tv" else "movie") + "/$id/recommendations", tv = tv)

    // ---------- Lectura del JSON (separada para poder probarla) ----------

    fun image(path: String?, size: String): String? =
        path?.takeIf { it.isNotBlank() && it != "null" }?.let { IMG + size + it }

    fun parseList(json: JSONObject, tv: Boolean = false): List<TmdbMovie> {
        val arr = json.optJSONArray("results") ?: return emptyList()
        return (0 until arr.length()).mapNotNull { i -> parseMovie(arr.optJSONObject(i) ?: return@mapNotNull null, tv) }
            .filter { it.poster != null }
    }

    /** Lee una película o una serie (las series usan "name" y "first_air_date"). */
    fun parseMovie(o: JSONObject, forceTv: Boolean = false): TmdbMovie? {
        val type = o.optString("media_type")
        if (type == "person") return null
        val tv = forceTv || type == "tv" || (!o.has("title") && o.has("name"))
        val id = o.optLong("id", -1)
        val title = o.optString(if (tv) "name" else "title").takeIf { it.isNotBlank() } ?: return null
        if (id <= 0) return null
        return TmdbMovie(
            id = id,
            title = title,
            originalTitle = o.optString(if (tv) "original_name" else "original_title", title),
            year = o.optString(if (tv) "first_air_date" else "release_date").take(4).toIntOrNull(),
            overview = o.optString("overview"),
            poster = image(o.optString("poster_path"), "w342"),
            backdrop = image(o.optString("backdrop_path"), "w780"),
            rating = o.optDouble("vote_average", 0.0).let { if (it.isNaN()) 0.0 else it },
            tv = tv,
        )
    }

    fun parseDetail(o: JSONObject, tv: Boolean = false): TmdbDetail {
        val movie = parseMovie(o, tv) ?: throw TmdbException(if (tv) "No se encontró la serie." else "No se encontró la película.")
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
        val director = if (tv) {
            o.optJSONArray("created_by")?.let { a ->
                (0 until a.length()).mapNotNull { a.optJSONObject(it)?.optString("name")?.takeIf { n -> n.isNotBlank() } }
                    .take(2).joinToString(", ").ifBlank { null }
            }
        } else credits?.optJSONArray("crew")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .firstOrNull { it.optString("job") == "Director" }?.optString("name")
        }
        val runtime = if (tv) {
            o.optJSONArray("episode_run_time")?.let { a -> (0 until a.length()).map { a.optInt(it) }.firstOrNull { it > 0 } }
                ?: o.optJSONObject("last_episode_to_air")?.optInt("runtime", 0) ?: 0
        } else o.optInt("runtime", 0)
        val seasons = if (!tv) emptyList() else o.optJSONArray("seasons")?.let { a ->
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }
                .filter { it.optInt("season_number", 0) > 0 }
                .map {
                    TmdbSeason(
                        number = it.optInt("season_number"),
                        name = it.optString("name").ifBlank { "Temporada ${it.optInt("season_number")}" },
                        episodes = it.optInt("episode_count", 0),
                        poster = image(it.optString("poster_path"), "w185"),
                        year = it.optString("air_date").take(4).toIntOrNull(),
                    )
                }
        }.orEmpty()
        return TmdbDetail(
            movie = movie,
            watch = parseWatch(o.optJSONObject("watch/providers")),
            seasons = seasons,
            runtimeMin = runtime,
            genres = genres,
            tagline = o.optString("tagline"),
            cast = cast,
            director = director,
            trailerKey = pickTrailer(o.optJSONObject("videos")),
        )
    }

    /** Plataformas en Perú (TMDB las obtiene de JustWatch). */
    fun parseWatch(o: JSONObject?, country: String = REGION): TmdbWatch? {
        val pe = o?.optJSONObject("results")?.optJSONObject(country) ?: return null
        fun list(vararg keys: String): List<TmdbProvider> = keys.flatMap { k ->
            val a = pe.optJSONArray(k) ?: return@flatMap emptyList<Pair<Int, TmdbProvider>>()
            (0 until a.length()).mapNotNull { a.optJSONObject(it) }.mapNotNull { p ->
                val id = p.optInt("provider_id", -1)
                val name = p.optString("provider_name").takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (id < 0) return@mapNotNull null
                p.optInt("display_priority", 99) to TmdbProvider(id, name, image(p.optString("logo_path"), "w92"))
            }
        }.sortedBy { it.first }.map { it.second }.distinctBy { it.id }
        val free = list("free", "ads")
        val subs = list("flatrate").filter { s -> free.none { it.id == s.id } }
        val rent = list("rent", "buy").filter { r -> (free + subs).none { it.id == r.id } }
        return TmdbWatch(pe.optString("link").takeIf { it.startsWith("https://") }, free, subs, rent)
            .takeIf { !it.isEmpty }
    }

    /**
     * Enlace para abrir la plataforma buscando el título (en Android abre la app si está
     * instalada). Si no conocemos la plataforma, se usa la página de TMDB con sus enlaces.
     */
    fun providerUrl(p: TmdbProvider, title: String, fallback: String?): String? {
        val q = enc(title)
        val n = p.name.lowercase()
        return when {
            n.startsWith("netflix") -> "https://www.netflix.com/search?q=$q"
            n.contains("amazon prime") || n.contains("prime video") -> "https://www.primevideo.com/search?phrase=$q"
            n.startsWith("disney") -> "https://www.disneyplus.com/search?q=$q"
            n == "max" || n.startsWith("hbo max") -> "https://play.max.com/search?q=$q"
            n.startsWith("apple tv") -> "https://tv.apple.com/search?term=$q"
            n.startsWith("crunchyroll") -> "https://www.crunchyroll.com/search?q=$q"
            n.startsWith("mubi") -> "https://mubi.com/search/films?query=$q"
            n.startsWith("tubi") -> "https://tubitv.com/search/$q"
            n.startsWith("youtube") -> "https://www.youtube.com/results?search_query=$q"
            else -> fallback
        }
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

    /** Palabras de videos que dicen "película completa" pero no lo son. */
    private val fakeWords = Regex(
        "\\b(resena|review|reviews|critica|critic|criticas|resumen|resumida|recap|explicad[ao]|explicacion|" +
            "analisis|reaccion|reaction|reacciona|escena|escenas|pelea|clip|clips|fragmento|teaser|trailer|" +
            "podcast|en minutos|en \\d+ minutos|curiosidades|easter eggs|teoria|teorias|final explicado|" +
            "spoilers?|opinion|comentario|comentamos|ending|recapped|movie recap)\\b"
    )

    /**
     * Las películas de este año o del anterior siguen en cines o plataformas de pago:
     * en YouTube no están gratis de forma legítima, solo hay engaños con ese título.
     */
    fun mayBeFreeOnYoutube(movie: TmdbMovie, currentYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)): Boolean {
        val y = movie.year ?: return true
        return y <= currentYear - 2
    }

    /**
     * Video con título de "película completa" que no lo es: reseñas, resúmenes, escenas,
     * o un estreno de este año o del anterior (que no puede estar gratis de verdad).
     */
    fun looksFake(title: String, channel: String, currentYear: Int = java.util.Calendar.getInstance().get(java.util.Calendar.YEAR)): Boolean {
        val t = norm(title)
        if (fakeWords.containsMatchIn(t) || fakeWords.containsMatchIn(norm(channel))) return true
        val recent = Regex("\\b(${currentYear}|${currentYear - 1})\\b")
        return recent.containsMatchIn(t) &&
            Regex("pelicula completa|full movie|capitulo completo|episodio completo|full episode").containsMatchIn(t)
    }

    /** ¿Este video de YouTube parece de verdad la película completa? */
    fun looksLikeFullMovie(title: String, channel: String, durationSec: Long, runtimeSec: Long, movie: TmdbMovie): Boolean {
        if (!matches(title, movie)) return false
        if (looksFake(title, channel)) return false
        if (runtimeSec > 0) {
            val ratio = durationSec.toDouble() / runtimeSec
            if (ratio < 0.88 || ratio > 1.12) return false
        } else if (durationSec < 3600) {
            return false
        }
        return true
    }

    fun norm(s: String): String = java.text.Normalizer.normalize(s.lowercase(), java.text.Normalizer.Form.NFD)
        .replace(Regex("\\p{Mn}+"), "")
        .replace(Regex("[^a-z0-9ñ ]"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
}
