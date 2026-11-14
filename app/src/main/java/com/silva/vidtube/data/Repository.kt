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
    suspend fun kiosk(kioskId: String): PagedResult = withContext(Dispatchers.IO) {
        val extractor = yt.kioskList.getExtractorById(kioskId, null)
        extractor.fetchPage()
        val info = KioskInfo.getInfo(extractor)
        PagedResult(info.relatedItems.mapNotNull { it.toEntry() }, kioskNext(info.url, info.nextPage))
    }

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
    ): PagedResult = withContext(Dispatchers.IO) {
        val handler = yt.searchQHFactory.fromQuery(query, listOf(YoutubeSearchQueryHandlerFactory.VIDEOS), "")
        val info = SearchInfo.getInfo(yt, handler)
        val keep: (VideoItem) -> Boolean = { v ->
            v.durationSec >= minSec && !(noTrailers && trailerWords.containsMatchIn(v.title))
        }
        freshPage(handler, info.relatedItems, info.nextPage, maxAgeDays, pagesLeft = if (minSec > 0) 4 else 2, keep = keep)
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

    // ---------- Shorts ----------
    /** Shorts sobre un tema (búsqueda con #shorts). */
    suspend fun shortsSearch(query: String): List<VideoItem> = withContext(Dispatchers.IO) {
        // Búsqueda con el filtro "Shorts" de YouTube (trae shorts verdaderos, verticales)
        ShortsSearch.search(query)
    }

    /** Últimos shorts de un canal (pestaña Shorts del canal). */
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
    suspend fun stream(url: String): StreamInfo = withContext(Dispatchers.IO) {
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
    suspend fun subscriptionFeed(channels: List<ChannelItem>): List<VideoItem> =
        withContext(Dispatchers.IO) {
            val limit = Semaphore(6)
            coroutineScope {
                channels.map { ch ->
                    async {
                        limit.withPermit {
                            runCatching {
                                FeedInfo.getInfo(yt, ch.url).relatedItems.map { item: StreamInfoItem ->
                                    val sortKey = item.uploadDate?.offsetDateTime()?.toEpochSecond() ?: 0L
                                    sortKey to VideoItem.from(item).copy(
                                        channelName = item.uploaderName ?: ch.name,
                                        channelUrl = ch.url,
                                        channelAvatar = ch.avatar,
                                    )
                                }
                            }.getOrDefault(emptyList())
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
