package com.silva.vidtube.data

/**
 * Canales de TV de señal abierta de Perú. Se busca su transmisión en vivo oficial en YouTube:
 * solo cuenta si el canal está verificado y su nombre coincide (así se evitan retransmisiones).
 */
data class TvChannel(
    val id: String,
    val name: String,
    val query: String,
    /** Palabras que debe tener el nombre del canal de YouTube (sin tildes, en minúsculas). */
    val match: List<String>,
    val color: Long,
    /** Página oficial con la señal en vivo las 24 horas (confirmada). */
    val liveUrl: String? = null,
    /** Web oficial del canal (tiene su botón «En vivo»), si no hay página directa confirmada. */
    val siteUrl: String? = null,
)

object LiveTv {
    val peru = listOf(
        TvChannel("america", "América TV", "america tv en vivo", listOf("america tv", "america television", "america noticias"), 0xFFE53935,
            liveUrl = "https://tvgo.americatv.com.pe/"),
        TvChannel("latina", "Latina", "latina television en vivo", listOf("latina"), 0xFF1F6FEB,
            liveUrl = "https://www.latina.pe/tvenvivo"),
        TvChannel("panamericana", "Panamericana", "panamericana television en vivo", listOf("panamericana"), 0xFF0057B8,
            liveUrl = "https://panamericana.pe/tvenvivo/hd"),
        TvChannel("tvperu", "TV Perú", "tv peru en vivo", listOf("tv peru", "tvperu"), 0xFFD7263D,
            siteUrl = "https://www.tvperu.gob.pe/"),
        TvChannel("atv", "ATV", "atv en vivo", listOf("atv"), 0xFFF2A900, siteUrl = "https://www.atv.pe/"),
        TvChannel("willax", "Willax", "willax en vivo", listOf("willax"), 0xFF6A1B9A),
        TvChannel("exitosa", "Exitosa", "exitosa en vivo", listOf("exitosa"), 0xFFE65100),
        TvChannel("rpp", "RPP", "rpp en vivo", listOf("rpp"), 0xFFC62828),
        TvChannel("canaln", "Canal N", "canal n en vivo", listOf("canal n"), 0xFF263238, siteUrl = "https://canaln.pe/"),
        TvChannel("nativa", "Nativa", "nativa tv en vivo", listOf("nativa"), 0xFF2E7D32),
    )

    /** ¿El nombre del canal de YouTube corresponde a este canal de TV? */
    fun matches(channelName: String, ch: TvChannel): Boolean {
        val n = " " + Tmdb.norm(channelName) + " "
        return ch.match.any { m -> n.contains(" $m ") }
    }
}
