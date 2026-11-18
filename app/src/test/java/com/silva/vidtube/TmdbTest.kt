package com.silva.vidtube

import com.silva.vidtube.data.Tmdb
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TmdbTest {

    private val listJson = """
        {"page":1,"results":[
          {"id":603,"title":"Matrix","original_title":"The Matrix","release_date":"1999-03-31",
           "overview":"Un hacker descubre…","poster_path":"/abc.jpg","backdrop_path":"/bd.jpg","vote_average":8.2},
          {"id":1,"title":"Sin póster","original_title":"No poster","release_date":"","poster_path":null,"vote_average":5.0},
          {"id":0,"title":"Id inválido","poster_path":"/x.jpg"}
        ]}
    """.trimIndent()

    @Test
    fun leeListaYDescartaSinPoster() {
        val list = Tmdb.parseList(JSONObject(listJson))
        assertEquals(1, list.size)
        val m = list[0]
        assertEquals(603L, m.id)
        assertEquals("Matrix", m.title)
        assertEquals("The Matrix", m.originalTitle)
        assertEquals(1999, m.year)
        assertEquals("https://image.tmdb.org/t/p/w342/abc.jpg", m.poster)
        assertEquals("https://image.tmdb.org/t/p/w780/bd.jpg", m.backdrop)
        assertEquals(8.2, m.rating, 0.001)
    }

    @Test
    fun leeFichaConRepartoDirectorYTrailerEnEspanol() {
        val json = JSONObject(
            """
            {"id":603,"title":"Matrix","original_title":"The Matrix","release_date":"1999-03-31","poster_path":"/abc.jpg",
             "runtime":136,"tagline":"Bienvenido al mundo real","genres":[{"id":28,"name":"Acción"},{"id":878,"name":"Ciencia ficción"}],
             "credits":{"cast":[{"name":"Keanu Reeves","character":"Neo","profile_path":"/k.jpg"}],
                        "crew":[{"name":"Alguien","job":"Producer"},{"name":"Lana Wachowski","job":"Director"}]},
             "videos":{"results":[
                {"site":"YouTube","key":"EN_TRAILER","type":"Trailer","iso_639_1":"en","official":true},
                {"site":"YouTube","key":"ES_TEASER","type":"Teaser","iso_639_1":"es","official":true},
                {"site":"YouTube","key":"ES_TRAILER","type":"Trailer","iso_639_1":"es","official":false},
                {"site":"Vimeo","key":"VIMEO","type":"Trailer","iso_639_1":"es"}
             ]}}
            """.trimIndent()
        )
        val d = Tmdb.parseDetail(json)
        assertEquals(136, d.runtimeMin)
        assertEquals(listOf("Acción", "Ciencia ficción"), d.genres)
        assertEquals("Keanu Reeves", d.cast[0].name)
        assertEquals("Lana Wachowski", d.director)
        assertEquals("ES_TRAILER", d.trailerKey)
    }

    @Test
    fun sinVideosNoHayTrailer() {
        assertNull(Tmdb.pickTrailer(null))
        assertNull(Tmdb.pickTrailer(JSONObject("""{"results":[{"site":"YouTube","key":"x","type":"Clip"}]}""")))
    }

    @Test
    fun reconoceElTituloEnVideosDeYoutube() {
        val m = Tmdb.parseList(JSONObject(listJson))[0]
        assertTrue(Tmdb.matches("MATRIX (1999) Película Completa en Español Latino", m))
        assertTrue(Tmdb.matches("The Matrix full movie HD", m))
        assertFalse(Tmdb.matches("Rápidos y furiosos película completa", m))
        assertEquals("accion y comedia", Tmdb.norm("¡Acción  y Comedia!"))
    }

    @Test
    fun descartaPeliculasCompletasFalsas() {
        val year = 2026
        // Los casos reales que aparecieron: reseñas con título de "película completa"
        assertTrue(Tmdb.looksFake("Spider-Man: Un Nuevo Día (2026) Película Completa en Español | Película", "Rehman Critica", year))
        assertTrue(Tmdb.looksFake("Allen 2026 Full Movie | T.J. Power, Edward A. O'Sullivan", "Harry Critic", year))
        assertTrue(Tmdb.looksFake("Pelea final COMPLETA + Final explicado", "Cine X", year))
        assertTrue(Tmdb.looksFake("Matrix resumida en 10 minutos", "Resumen Cine", year))
        // Una película antigua subida completa sí pasa
        assertFalse(Tmdb.looksFake("La noche de los muertos vivientes (1968) película completa", "Clásicos de siempre", year))
        assertFalse(Tmdb.looksFake("Harry Potter y las reliquias de la muerte parte 2 película completa", "Cine", year))
    }

    @Test
    fun estrenosNoSeBuscanYLaDuracionDebeCoincidir() {
        val m = Tmdb.parseList(JSONObject(listJson))[0] // Matrix, 1999
        assertTrue(Tmdb.mayBeFreeOnYoutube(m, 2026))
        assertFalse(Tmdb.mayBeFreeOnYoutube(m.copy(year = 2026), 2026))
        assertFalse(Tmdb.mayBeFreeOnYoutube(m.copy(year = 2025), 2026))
        val runtime = 136 * 60L
        assertTrue(Tmdb.looksLikeFullMovie("Matrix película completa en español", "Cine Clásico", runtime, runtime, m))
        assertFalse(Tmdb.looksLikeFullMovie("Matrix película completa en español", "Cine Clásico", runtime / 2, runtime, m))
        assertFalse(Tmdb.looksLikeFullMovie("Matrix reseña completa", "Cine Clásico", runtime, runtime, m))
    }

    @Test
    fun leeSeriesConTemporadasYCreadores() {
        val list = Tmdb.parseList(JSONObject("""{"results":[
            {"id":1396,"name":"Breaking Bad","original_name":"Breaking Bad","first_air_date":"2008-01-20","poster_path":"/bb.jpg","vote_average":8.9}
        ]}"""), tv = true)
        assertEquals(1, list.size)
        assertTrue(list[0].tv)
        assertEquals("Breaking Bad", list[0].title)
        assertEquals(2008, list[0].year)

        val d = Tmdb.parseDetail(JSONObject("""
            {"id":1396,"name":"Breaking Bad","first_air_date":"2008-01-20","poster_path":"/bb.jpg",
             "episode_run_time":[47],"created_by":[{"name":"Vince Gilligan"}],
             "seasons":[{"season_number":0,"name":"Especiales","episode_count":9},
                        {"season_number":1,"name":"Temporada 1","episode_count":7,"air_date":"2008-01-20"},
                        {"season_number":2,"name":"Temporada 2","episode_count":13,"air_date":"2009-03-08"}]}
        """.trimIndent()), tv = true)
        assertEquals(47, d.runtimeMin)
        assertEquals("Vince Gilligan", d.director)
        assertEquals(listOf(1, 2), d.seasons.map { it.number })
        assertEquals(13, d.seasons[1].episodes)
    }

    @Test
    fun busquedaMixtaIgnoraPersonas() {
        val list = Tmdb.parseList(JSONObject("""{"results":[
            {"id":5,"media_type":"person","name":"Keanu Reeves","profile_path":"/k.jpg"},
            {"id":603,"media_type":"movie","title":"Matrix","poster_path":"/m.jpg"},
            {"id":1396,"media_type":"tv","name":"Breaking Bad","poster_path":"/bb.jpg"}
        ]}"""))
        assertEquals(listOf(603L, 1396L), list.map { it.id })
        assertFalse(list[0].tv)
        assertTrue(list[1].tv)
    }

    @Test
    fun leeDondeVerEnPeru() {
        val w = Tmdb.parseWatch(JSONObject("""
            {"results":{
              "US":{"flatrate":[{"provider_id":1,"provider_name":"Solo EEUU"}]},
              "PE":{"link":"https://www.themoviedb.org/movie/603/watch?locale=PE",
                    "flatrate":[{"provider_id":8,"provider_name":"Netflix","logo_path":"/n.jpg","display_priority":2},
                                {"provider_id":1899,"provider_name":"Max","logo_path":"/m.jpg","display_priority":1}],
                    "ads":[{"provider_id":300,"provider_name":"Pluto TV","logo_path":"/p.jpg"}],
                    "rent":[{"provider_id":2,"provider_name":"Apple TV","logo_path":"/a.jpg"}],
                    "buy":[{"provider_id":2,"provider_name":"Apple TV","logo_path":"/a.jpg"}]}
            }}
        """.trimIndent()))!!
        assertEquals(listOf("Pluto TV"), w.free.map { it.name })
        assertEquals(listOf("Max", "Netflix"), w.subscription.map { it.name })
        assertEquals(listOf("Apple TV"), w.rentOrBuy.map { it.name })
        assertEquals("https://image.tmdb.org/t/p/w92/n.jpg", w.subscription[1].logo)
        assertNull(Tmdb.parseWatch(JSONObject("""{"results":{"US":{}}}""")))
        // Netflix abre su búsqueda; una plataforma desconocida usa la página de TMDB
        assertEquals("https://www.netflix.com/search?q=La+Odisea", Tmdb.providerUrl(w.subscription[1], "La Odisea", w.link))
        assertEquals(w.link, Tmdb.providerUrl(w.free[0], "La Odisea", w.link))
    }
}
