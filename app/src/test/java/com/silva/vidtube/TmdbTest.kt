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
}
