package com.silva.vidtube.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.schabi.newpipe.extractor.Page
import org.schabi.newpipe.extractor.ServiceList
import org.schabi.newpipe.extractor.StreamingService
import org.schabi.newpipe.extractor.channel.ChannelInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabInfo
import org.schabi.newpipe.extractor.channel.tabs.ChannelTabs
import org.schabi.newpipe.extractor.comments.CommentsInfo
import org.schabi.newpipe.extractor.comments.CommentsInfoItem
import org.schabi.newpipe.extractor.exceptions.ContentNotAvailableException
import org.schabi.newpipe.extractor.feed.FeedInfo
import org.schabi.newpipe.extractor.kiosk.KioskInfo
import org.schabi.newpipe.extractor.linkhandler.ListLinkHandler
import org.schabi.newpipe.extractor.linkhandler.SearchQueryHandler
import org.schabi.newpipe.extractor.search.SearchInfo
import org.schabi.newpipe.extractor.services.youtube.linkHandler.YoutubeSearchQueryHandlerFactory
import org.schabi.newpipe.extractor.stream.StreamInfo
import org.schabi.newpipe.extractor.stream.StreamInfoItem

/** Una página de resultados y cómo pedir la siguiente. */
class PagedResult(
    val items: List<FeedEntry>,
    val loadMore: (suspend () -> PagedResult)?,
)

data class ChannelDetails(
    val channel: ChannelItem,
    val banner: String?,
    val description: String,
    val videos: PagedResult,
)

data class Comment(
    val author: String,
    val avatar: String?,
    val text: String,
    val date: String?,
    val likes: String,
    val pinned: Boolean,
    val replies: Int,
)

/** Todas las consultas a YouTube pasan por aquí (siempre en un hilo de fondo). */
object Repository {
    val yt: StreamingService get() = ServiceList.YouTube

    // ---------- Inicio / tendencias ----------
    /** Listas de Tendencias que YouTube ya no entrega: no se vuelven a pedir en esta sesión. */
    private val deadKiosks = java.util.Collections.synchronizedSet(HashSet<String>())

    suspend fun kiosk(kioskId: String): PagedResult = withContext(Dispatchers.IO) {
        val fromKiosk = if (kioskId in deadKiosks && kioskFallback.containsKey(kioskId)) Result.success(null) else runCatching {
            val extractor = yt.kioskList.getExtractorById(kioskId, null)
            extractor.fetchPage()
            val info = KioskInfo.getInfo(extractor)
            PagedResult(info.relatedItems.mapNotNull { it.toEntry() }, kioskNext(info.url, info.nextPage))
        }
        val ok = fromKiosk.getOrNull()
        if (ok != null && ok.items.size >= 6) return@withContext ok
        deadKiosks += kioskId
        // YouTube retiró su página de Tendencias: esas listas llegan vacías o fallan.
        // En ese caso armamos la categoría con búsquedas de lo más reciente.
        val queries = kioskFallback[kioskId] ?: return@withContext ok ?: throw fromKiosk.exceptionOrNull()!!
        val lists = queries.map { q ->
            async {
                runCatching {
                    if (kioskId == "live") liveSearch(q.first) else freshSearch(q.first, maxAgeDays = q.second).items
                }.getOrDefault(emptyList())
            }
        }.map { it.await() }
        val mixed = ArrayList<FeedEntry>()
        val queues = lists.map { ArrayDeque(it) }
        while (queues.any { it.isNotEmpty() }) queues.forEach { qu -> qu.removeFirstOrNull()?.let { mixed += it } }
        val out = mixed.filter { it !is FeedEntry.Channel }.distinctBy { it.key }
        if (out.isEmpty()) return@withContext ok ?: throw (fromKiosk.exceptionOrNull() ?: Exception("Sin resultados"))
        // Al llegar al final se sigue con la búsqueda principal (solo si el usuario baja tanto)
        val more: suspend () -> PagedResult = { search(queries.first().first) }
        PagedResult(out, more)
    }

    /** Transmisiones que están en vivo ahora mismo. */
    private fun liveSearch(query: String): List<FeedEntry> {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val info = SearchInfo.getInfo(yt, handler)
        return info.relatedItems.filterIsInstance<StreamInfoItem>().map { VideoItem.from(it) }
            .filter { it.isLive }.map { FeedEntry.Video(it) }
    }

    /** Búsquedas que reemplazan a cada lista de Tendencias (texto, antigüedad máxima en días). */
    private val kioskFallback = mapOf(
        "trending_music" to listOf(
            "música nueva video oficial" to 60L,
            "estrenos musicales latino" to 60L,
            "top canciones del momento" to 120L,
        ),
        "trending_gaming" to listOf("gameplay nuevo videojuego" to 30L, "videojuegos novedades" to 30L),
        "trending_podcasts_episodes" to listOf("podcast episodio completo" to 30L, "podcast entrevista" to 30L),
        "trending_movies_and_shows" to listOf("tráiler oficial español" to 30L),
        "live" to listOf("en vivo ahora" to 2L, "transmisión en vivo" to 2L),
    )

    private fun kioskNext(url: String, page: Page?): (suspend () -> PagedResult)? {
        if (page == null || !Page.isValid(page)) return null
        return {
            withContext(Dispatchers.IO) {
                val p = KioskInfo.getMoreItems(yt, url, page)
                PagedResult(p.items.mapNotNull { it.toEntry() }, kioskNext(url, p.nextPage))
            }
        }
    }

    // ---------- Búsqueda ----------
    suspend fun search(query: String): PagedResult = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query)
        val info = SearchInfo.getInfo(yt, handler)
        PagedResult(info.relatedItems.mapNotNull { it.toEntry() }, searchNext(handler, info.nextPage))
    }

    private fun searchNext(handler: SearchQueryHandler, page: Page?): (suspend () -> PagedResult)? {
        if (page == null || !Page.isValid(page)) return null
        return {
            withContext(Dispatchers.IO) {
                val p = SearchInfo.getMoreItems(yt, handler, page)
                PagedResult(p.items.mapNotNull { it.toEntry() }, searchNext(handler, p.nextPage))
            }
        }
    }

    /**
     * Búsqueda de lo más reciente sobre un tema: descarta shorts y videos de más de
     * [maxAgeDays] días, y pone primero lo del último mes.
     */
    suspend fun freshSearch(query: String, maxAgeDays: Long = 365): PagedResult = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val info = SearchInfo.getInfo(yt, handler)
        freshPage(handler, info.relatedItems, info.nextPage, maxAgeDays, pagesLeft = 2)
    }

    private val trailerWords = Regex("(?i)tr[aá]iler|teaser|avance oficial|reacci[oó]n|react|resumen de la pel|explicad")

    /**
     * Búsqueda para una categoría general (no depende de lo que ves): descarta Shorts, videos
     * más cortos que [minSec] y, si se pide, los tráileres (para películas y series completas).
     */
    suspend fun categorySearch(
        query: String,
        minSec: Long = 0,
        noTrailers: Boolean = false,
        maxAgeDays: Long = 3650,
        /** Con noTrailers: solo canales verificados (las listas); la ficha pide todos y los separa. */
        onlyVerified: Boolean = false,
    ): PagedResult = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val info = SearchInfo.getInfo(yt, handler)
        val keep: (VideoItem) -> Boolean = { v ->
            v.durationSec >= minSec && (!noTrailers ||
                // Películas y series completas: solo canales verificados, en español y sin reseñas ni narraciones
                (!trailerWords.containsMatchIn(v.title) && !Tmdb.looksFake(v.title, v.channelName) &&
                    (v.verified || !onlyVerified) && Tmdb.isSpanish(v.title)))
        }
        val page = freshPage(handler, info.relatedItems, info.nextPage, maxAgeDays, pagesLeft = if (noTrailers) 6 else if (minSec > 0) 4 else 2, keep = keep)
        // Películas y series completas: primero los canales verificados (distribuidoras oficiales)
        if (!noTrailers) page
        else PagedResult(
            page.items
                .sortedByDescending { (it as? FeedEntry.Video)?.video?.verified == true }
                .map { e -> if (e is FeedEntry.Video && !e.video.verified) FeedEntry.Video(e.video, "Canal sin verificar") else e },
            page.loadMore,
        )
    }

    private fun freshPage(
        handler: SearchQueryHandler,
        items: List<org.schabi.newpipe.extractor.InfoItem>,
        next: Page?,
        maxAgeDays: Long,
        pagesLeft: Int,
        keep: (VideoItem) -> Boolean = { true },
    ): PagedResult {
        var all = items
        var page = next
        var left = pagesLeft
        fun filtered() = all.filterIsInstance<StreamInfoItem>()
            .map { VideoItem.from(it) }
            .filter { !it.isShort && !it.isLive && (it.ageDays ?: 0) <= maxAgeDays && keep(it) }
        // Si salió muy poco reciente, traemos una o dos páginas más
        while (filtered().size < 12 && left > 0 && page != null && Page.isValid(page)) {
            val p = SearchInfo.getMoreItems(yt, handler, page)
            all = all + p.items
            page = p.nextPage
            left--
        }
        val videos = filtered().sortedBy {
            val age = it.ageDays ?: 400
            when { age <= 31 -> 0; age <= 180 -> 1; else -> 2 }
        }
        val more: (suspend () -> PagedResult)? = if (page != null && Page.isValid(page)) {
            val p0 = page
            ({
                withContext(Dispatchers.IO) {
                    val p = SearchInfo.getMoreItems(yt, handler, p0)
                    freshPage(handler, p.items, p.nextPage, maxAgeDays, pagesLeft = 1, keep = keep)
                }
            })
        } else null
        return PagedResult(videos.map { FeedEntry.Video(it) }, more)
    }

    // ---------- Películas (con datos de TMDB) ----------
    /**
     * Busca en YouTube la película completa gratuita. YouTube está lleno de videos con
     * "película completa" en el título que en realidad son reseñas, resúmenes o fragmentos,
     * así que el filtro es estricto:
     *  - Los estrenos (de este año o del anterior) no se buscan: nunca están gratis de verdad.
     *  - La duración tiene que parecerse a la de la película (±12 %).
     *  - Se descartan títulos y canales de reseñas, resúmenes, escenas, reacciones, etc.
     */
    /** Resultados de "película completa": de canales verificados y de otros canales. */
    class FullMatches(val official: List<VideoItem>, val others: List<VideoItem>) {
        val first get() = official.firstOrNull()
        val isEmpty get() = official.isEmpty() && others.isEmpty()
    }

    suspend fun fullMovie(detail: TmdbDetail): FullMatches = withContext(Dispatchers.IO) {
        val movie = detail.movie
        // Estrenos y grandes estudios también se buscan, pero todo lo que aparezca va a
        // "otros canales" (no es una copia oficial)
        val unofficial = !Tmdb.mayBeFreeOnYoutube(movie) || Tmdb.isMajorStudio(detail)
        val runtimeSec = if (detail.runtimeMin > 0) detail.runtimeMin * 60L else 0L
        val minSec = if (runtimeSec > 0) (runtimeSec * 0.88).toLong() else 3600L
        val year = movie.year?.toString().orEmpty()
        val found = LinkedHashMap<String, VideoItem>()
        for (q in listOf("${movie.title} $year película completa en español", "${movie.title} película completa latino")) {
            val r = runCatching { categorySearch(q, minSec = minSec, noTrailers = true, onlyVerified = false) }.getOrNull() ?: continue
            r.items.filterIsInstance<FeedEntry.Video>().map { it.video }
                .filter {
                    Tmdb.looksLikeFullMovie(it.title, it.channelName, it.durationSec, runtimeSec, movie, it.verified, requireVerified = false)
                }
                .forEach { found.putIfAbsent(it.url, it) }
            if (found.values.count { it.verified } >= 3) break
        }
        val all = found.values.toList()
        if (unofficial) FullMatches(emptyList(), all.take(10))
        else FullMatches(all.filter { it.verified }, all.filter { !it.verified }.take(8))
    }

    suspend fun seriesEpisodes(show: TmdbMovie, season: Int, episodeMin: Int): List<VideoItem> = withContext(Dispatchers.IO) {
        val epSec = if (episodeMin > 0) episodeMin * 60L else 0L
        val minSec = if (epSec > 0) (epSec * 0.7).toLong() else 1200L
        val queries = listOf(
            "${show.title} temporada $season capítulo completo en español",
            "${show.title} temporada $season episodio completo latino",
        )
        val found = LinkedHashMap<String, VideoItem>()
        for (q in queries) {
            val r = runCatching { categorySearch(q, minSec = minSec, noTrailers = true, onlyVerified = false) }.getOrNull() ?: continue
            r.items.filterIsInstance<FeedEntry.Video>().map { it.video }
                .filter { v ->
                    Tmdb.matches(v.title, show) && !Tmdb.looksFake(v.title, v.channelName) &&
                        Tmdb.isSpanish(v.title) &&
                        // Un capítulo no dura el triple de lo normal (eso son recopilaciones o películas)
                        (epSec == 0L || v.durationSec <= epSec * 3)
                }
                .forEach { found.putIfAbsent(it.url, it) }
            if (found.size >= 10) break
        }
        found.values.sortedByDescending { it.verified }
    }

    // ---------- Shorts ----------
    /** Shorts sobre un tema (búsqueda con #shorts). */
    suspend fun shortsSearch(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        // Búsqueda con el filtro "Shorts" de YouTube (trae shorts verdaderos, verticales)
        ShortsSearch.search(query)
    }

    /** Últimos shorts de un canal (pestaña Shorts del canal). */
    /**
     * Transmisión en vivo actual del canal oficial de YouTube (pestaña «En vivo»), o null si
     * no está transmitiendo.
     */
    suspend fun channelLive(url: String): VideoItem? = withContext(Dispatchers.IO) {
        runCatching {
            val info = ChannelInfo.getInfo(yt, url)
            val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.LIVESTREAMS) }
                ?: return@runCatching null
            ChannelTabInfo.getInfo(yt, tab).relatedItems
                .filterIsInstance<StreamInfoItem>()
                .firstOrNull {
                    it.streamType == org.schabi.newpipe.extractor.stream.StreamType.LIVE_STREAM ||
                        it.streamType == org.schabi.newpipe.extractor.stream.StreamType.AUDIO_LIVE_STREAM
                }
                ?.let { VideoItem.from(it).copy(channelName = info.name ?: "", channelUrl = url, isLive = true) }
        }.getOrNull()
    }

    suspend fun channelShorts(channel: ChannelItem, limit: Int = 8): List<VideoItem> = withContext(Dispatchers.IO) {
        runCatching {
            val info = ChannelInfo.getInfo(yt, channel.url)
            val tab = info.tabs.firstOrNull { it.contentFilters.contains(ChannelTabs.SHORTS) }
                ?: return@runCatching emptyList()
            ChannelTabInfo.getInfo(yt, tab).relatedItems
                .filterIsInstance<StreamInfoItem>()
                .take(limit)
                .map {
                    VideoItem.from(it).copy(
                        isShort = true,
                        channelName = it.uploaderName?.ifBlank { null } ?: channel.name,
                        channelUrl = channel.url,
                        channelAvatar = channel.avatar,
                    )
                }
        }.getOrDefault(emptyList())
    }

    suspend fun suggestions(query: String): List<String> = withContext(Dispatchers.IO) {
        runCatching { yt.suggestionExtractor.suggestionList(query) }.getOrDefault(emptyList())
    }

    // ---------- Video ----------
    /** Información de videos ya consultados (unos minutos): abrir de nuevo un video es inmediato. */
    private val streamCache = object : LinkedHashMap<String, Pair<Long, StreamInfo>>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Pair<Long, StreamInfo>>?) = size > 12
    }
    private const val STREAM_TTL_MS = 20 * 60_000L

    /** Olvida la información guardada de un video (por ejemplo, si falló al reproducir). */
    fun forget(url: String) = synchronized(streamCache) { streamCache.remove(url) }

    /** Carga por adelantado la información de un video (sin esperar el resultado). */
    suspend fun warm(url: String) {
        if (synchronized(streamCache) { streamCache[url] } != null) return
        runCatching { stream(url) }
    }

    suspend fun stream(url: String, fresh: Boolean = false): StreamInfo = withContext(Dispatchers.IO) {
        if (!fresh) {
            val hit = synchronized(streamCache) { streamCache[url] }
            if (hit != null && System.currentTimeMillis() - hit.first < STREAM_TTL_MS) return@withContext hit.second
        }
        loadStream(url).also { info ->
            synchronized(streamCache) { streamCache[url] = System.currentTimeMillis() to info }
        }
    }

    private suspend fun loadStream(url: String): StreamInfo = withContext(Dispatchers.IO) {
        try {
            StreamInfo.getInfo(yt, url)
        } catch (e: Exception) {
            // Videos "hechos para niños": YouTube los bloquea al cliente normal; probamos el plan B
            if (KidsFallback.applies(e)) {
                try {
                    KidsFallback.load(url)
                } catch (fallback: Exception) {
                    android.util.Log.w("Vidtube", "Plan B para videos de niños falló", fallback)
                    // Mostramos el motivo del plan B para poder diagnosticarlo
                    throw ContentNotAvailableException(
                        if (fallback is ContentNotAvailableException) fallback.message
                        else "Plan B (TV) también falló: ${fallback.javaClass.simpleName} ${fallback.message ?: ""}",
                        fallback,
                    )
                }
            } else {
                throw e
            }
        }
    }

    suspend fun comments(url: String): Pair<List<Comment>, Int> = withContext(Dispatchers.IO) {
        val info = CommentsInfo.getInfo(yt, url)
        if (info.isCommentsDisabled) return@withContext emptyList<Comment>() to -1
        info.relatedItems.map { it.toComment() } to info.commentsCount
    }

    private fun CommentsInfoItem.toComment() = Comment(
        author = uploaderName ?: "",
        avatar = bestImage(uploaderAvatars, 48),
        text = plainText(commentText),
        date = textualUploadDate,
        likes = textualLikeCount ?: "",
        pinned = isPinned,
        replies = replyCount,
    )

    /** Los comentarios con enlaces llegan en HTML: los pasamos a texto normal. */
    private fun plainText(d: org.schabi.newpipe.extractor.stream.Description): String {
        val raw = d.content()
        if (d.type() != org.schabi.newpipe.extractor.stream.Description.Type.HTML && !raw.contains('<')) return raw
        return android.text.Html.fromHtml(raw, android.text.Html.FROM_HTML_MODE_LEGACY)
            .toString()
            .trim()
    }

    // ---------- Canal ----------
    suspend fun channel(url: String): ChannelDetails = withContext(Dispatchers.IO) {
        val info = ChannelInfo.getInfo(yt, url)
        val videosTab: ListLinkHandler? = info.tabs.firstOrNull {
            it.contentFilters.contains(ChannelTabs.VIDEOS)
        } ?: info.tabs.firstOrNull()

        val videos = if (videosTab != null) {
            runCatching {
                val tab = ChannelTabInfo.getInfo(yt, videosTab)
                PagedResult(
                    tab.relatedItems.mapNotNull { it.toEntry() }.withChannel(info),
                    channelNext(videosTab, tab.nextPage, info),
                )
            }.getOrElse { PagedResult(emptyList(), null) }
        } else PagedResult(emptyList(), null)

        ChannelDetails(
            channel = ChannelItem(
                url = info.url,
                name = info.name ?: "",
                avatar = bestImage(info.avatars, 176),
                subscribers = info.subscriberCount,
            ),
            banner = bestImage(info.banners, 200),
            description = info.description ?: "",
            videos = videos,
        )
    }

    private fun channelNext(
        tab: ListLinkHandler,
        page: Page?,
        info: ChannelInfo,
    ): (suspend () -> PagedResult)? {
        if (page == null || !Page.isValid(page)) return null
        return {
            withContext(Dispatchers.IO) {
                val p = ChannelTabInfo.getMoreItems(yt, tab, page)
                PagedResult(
                    p.items.mapNotNull { it.toEntry() }.withChannel(info),
                    channelNext(tab, p.nextPage, info),
                )
            }
        }
    }

    /** En la pestaña de un canal los videos no traen el nombre/avatar del canal: los completamos. */
    private fun List<FeedEntry>.withChannel(info: ChannelInfo) = map { e ->
        if (e is FeedEntry.Video) {
            FeedEntry.Video(
                e.video.copy(
                    channelName = e.video.channelName.ifBlank { info.name ?: "" },
                    channelUrl = e.video.channelUrl ?: info.url,
                    channelAvatar = e.video.channelAvatar ?: bestImage(info.avatars, 68),
                )
            )
        } else e
    }

    // ---------- Suscripciones ----------
    /** Últimos videos de todos los canales suscritos, del más nuevo al más antiguo. */
    /** Novedades de suscripciones ya consultadas: se reutilizan unos minutos para no esperar cada vez. */
    @Volatile private var subsCache: Triple<Long, Set<String>, List<VideoItem>>? = null
    private const val SUBS_TTL_MS = 15 * 60_000L

    /** Al recargar a mano se vuelven a pedir las novedades. */
    fun clearSubscriptionCache() { subsCache = null }

    suspend fun subscriptionFeed(channels: List<ChannelItem>, fresh: Boolean = false): List<VideoItem> {
        val key = channels.map { it.url }.toSet()
        val c = subsCache
        if (!fresh && c != null && c.second == key && System.currentTimeMillis() - c.first < SUBS_TTL_MS) return c.third
        return loadSubscriptionFeed(channels).also { subsCache = Triple(System.currentTimeMillis(), key, it) }
    }

    private suspend fun loadSubscriptionFeed(channels: List<ChannelItem>): List<VideoItem> =
        withContext(Dispatchers.IO) {
            val limit = Semaphore(10)
            coroutineScope {
                channels.map { ch ->
                    async {
                        limit.withPermit {
                            // Un canal lento no detiene a los demás
                            kotlinx.coroutines.withTimeoutOrNull(5_000) { runCatching {
                                FeedInfo.getInfo(yt, ch.url).relatedItems.map { item: StreamInfoItem ->
                                    val sortKey = item.uploadDate?.offsetDateTime()?.toEpochSecond() ?: 0L
                                    sortKey to VideoItem.from(item).copy(
                                        channelName = item.uploaderName ?: ch.name,
                                        channelUrl = ch.url,
                                        channelAvatar = ch.avatar,
                                    )
                                }
                            }.getOrNull() }.orEmpty()
                        }
                    }
                }.awaitAll()
            }.flatten()
                .sortedByDescending { it.first }
                .map { it.second }
                .filterNot { it.isShort }
                .distinctBy { it.url }
        }
}
