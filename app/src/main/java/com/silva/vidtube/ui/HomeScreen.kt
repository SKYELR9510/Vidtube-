package com.silva.vidtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silva.vidtube.data.FeedEntry
import com.silva.vidtube.data.Library
import com.silva.vidtube.data.PagedResult
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.VideoItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

data class HomeChip(val id: String, val label: String)

private val fixedChips = listOf(
    HomeChip("trending_music", "Música"),
    HomeChip("mixes", "Mixes"),
    HomeChip("live", "En vivo"),
    HomeChip("trending_gaming", "Videojuegos"),
    HomeChip("trending_movies_and_shows", "Películas"),
    HomeChip("trending_podcasts_episodes", "Podcasts"),
)

/** "Todo" + tus temas (según búsquedas y canales que más ves) + categorías fijas. */
fun buildHomeChips(): List<HomeChip> {
    val interests = Library.interests(5).map { HomeChip("i:" + it.query, it.label) }
    return listOf(HomeChip("todo", "Todo")) + interests + fixedChips
}

/** Contenido de una categoría del Inicio (todas menos "Todo"). */
suspend fun loadChip(id: String): PagedResult = when {
    id.startsWith("i:") -> Repository.freshSearch(id.removePrefix("i:"))
    id == "mixes" -> Repository.freshSearch(
        "mix " + (Library.interests(1).firstOrNull()?.query ?: "música") + " 2026",
        maxAgeDays = 365,
    )
    else -> Repository.kiosk(id)
}

/** Todas las tendencias: música, videojuegos, películas y podcasts mezclados. */
suspend fun loadAllTrending(): PagedResult = coroutineScope {
    val ids = listOf("trending_music", "trending_gaming", "trending_movies_and_shows", "trending_podcasts_episodes")
    val results = ids.map { async { runCatching { Repository.kiosk(it) } } }.map { it.await() }
    val lists = results.mapNotNull { it.getOrNull()?.items }
    if (lists.isEmpty()) throw results.first().exceptionOrNull() ?: Exception("Sin tendencias")
    val out = ArrayList<FeedEntry>()
    val queues = lists.map { ArrayDeque(it) }
    while (queues.any { it.isNotEmpty() }) queues.forEach { q -> q.removeFirstOrNull()?.let { out += it } }
    PagedResult(out.distinctBy { it.key }, null)
}

class HomeViewModel : ViewModel() {
    var selected by mutableStateOf("todo")
    var chips by mutableStateOf(buildHomeChips())
    var updateChecked = false
    private val lists = HashMap<String, Paged>()

    fun list(id: String): Paged = lists.getOrPut(id) {
        Paged(viewModelScope) { if (id == "todo") loadMix() else loadChip(id) }
    }

    /**
     * "Todo" = Para ti: recomendaciones según lo que has visto + videos nuevos de tus
     * suscripciones + algunas tendencias para descubrir cosas nuevas.
     */
    private suspend fun loadMix(): PagedResult = coroutineScope {
        val subsJob = async {
            runCatching { Repository.subscriptionFeed(Library.subscriptions.value.take(40)) }
                .getOrDefault(emptyList())
        }
        val musicJob = async { runCatching { Repository.kiosk("trending_music") } }
        val gamingJob = async { runCatching { Repository.kiosk("trending_gaming") } }

        // Lo más reciente de tus 2 temas principales (últimos ~4 meses)
        val freshJobs = Library.interests(2).map { interest ->
            async { runCatching { Repository.freshSearch(interest.query, maxAgeDays = 120).items }.getOrDefault(emptyList()) }
        }

        val watched = Library.history.value.map { it.url }.toHashSet()
        val forYou = Library.forYou(System.currentTimeMillis()).map { FeedEntry.Video(it) }
        val fresh = freshJobs.map { it.await() }.reduceOrNull { a, b -> interleave(a, b) }.orEmpty()
            .filter { e -> e !is FeedEntry.Video || e.video.url !in watched }
        val subs = subsJob.await().filter { it.url !in watched }.take(40).map { FeedEntry.Video(it) }
        val music = musicJob.await()
        val gaming = gamingJob.await()
        val trending = interleave(music.getOrNull()?.items.orEmpty(), gaming.getOrNull()?.items.orEmpty())

        if (forYou.isEmpty() && subs.isEmpty() && trending.isEmpty() && fresh.isEmpty()) {
            throw music.exceptionOrNull() ?: gaming.exceptionOrNull() ?: Exception("Sin contenido")
        }
        // 2 de "para ti", 1 de suscripciones… y cada tanto una tendencia
        // Primero lo nuevo de tus temas y suscripciones, luego recomendaciones
        val personal = interleave(interleave(fresh, subs), forYou, 2)
        val mixed = if (personal.isEmpty()) trending else interleave(personal, trending, 4)
        PagedResult(mixed.distinctBy { it.key }, null)
    }

    private fun interleave(a: List<FeedEntry>, b: List<FeedEntry>, everyA: Int = 1): List<FeedEntry> {
        val out = ArrayList<FeedEntry>(a.size + b.size)
        var i = 0
        var j = 0
        while (i < a.size || j < b.size) {
            repeat(everyA) { if (i < a.size) out.add(a[i++]) }
            if (j < b.size) out.add(b[j++])
        }
        return out
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onMore: (chipId: String, title: String) -> Unit,
    vm: HomeViewModel = viewModel(),
) {
    LaunchedEffect(Unit) { vm.chips = buildHomeChips() }
    val paged = vm.list(vm.selected)
    LaunchedEffect(vm.selected) { paged.loadIfNeeded() }
    // Al abrir la app revisamos (una vez) si hay versión nueva en GitHub
    LaunchedEffect(Unit) {
        if (!vm.updateChecked) {
            vm.updateChecked = true
            com.silva.vidtube.update.Updater.check(silent = true)
        }
    }

    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        VTopBar(leading = { VidtubeLogo() }) {
            TopIcon(Icons.Outlined.Search, "Buscar", onSearch)
            TopIcon(Icons.Outlined.Settings, "Configuración", onSettings)
        }
        UpdateBanner()
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            items(vm.chips, key = { it.id }) { chip ->
                val sel = chip.id == vm.selected
                Text(
                    chip.label,
                    color = if (sel) Color.White else VColors.Text,
                    fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(
                            if (sel) Modifier.background(VColors.RedGradient)
                            else Modifier.background(VColors.Chip)
                        )
                        .clickable { vm.selected = chip.id }
                        .padding(horizontal = 16.dp, vertical = 7.dp),
                )
            }
        }
        var scrollToFeed by androidx.compose.runtime.remember { mutableStateOf(0) }
        val showTip = vm.selected == "todo" && Library.subscriptions.value.isEmpty() && Library.history.value.size < 3
        // Destacado + fila de Tendencias arriba (cuando ya hay suficientes videos)
        val videos = paged.items.filterIsInstance<FeedEntry.Video>().map { it.video }
        val featured = paged.items.size >= 16 && paged.items.take(13).all { it is FeedEntry.Video }
        val tip: LazyListScope.() -> Unit = {
            if (showTip) {
                item("tip") {
                    Text(
                        "Mira algunos videos y suscríbete a canales: el Inicio se irá ajustando a tus gustos.",
                        color = VColors.Secondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                }
            }
            if (featured) {
                item("hero") { HeroCarousel(videos.take(5), onPlay = onVideo, onChannel = onChannel) }
                item("shelf_title") {
                    val shelfTitle = if (vm.selected == "todo") "Tendencias para ti"
                    else "Lo más visto · " + (vm.chips.firstOrNull { it.id == vm.selected }?.label ?: "")
                    SectionTitle(shelfTitle, onMore = { onMore(vm.selected, shelfTitle) })
                }
                item("shelf") { VideoShelf(videos.subList(5, 13), onVideo) }
                item("feed_title") { SectionTitle("Más videos") }
            }
        }
        PullToRefreshBox(
            isRefreshing = paged.refreshing,
            onRefresh = { paged.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            key(vm.selected) {
                val listState = rememberLazyListState()
                // "Ver más" baja hasta la lista completa de videos
                LaunchedEffect(scrollToFeed) {
                    if (scrollToFeed > 0) listState.animateScrollToItem(if (showTip) 4 else 3)
                }
                FeedList(
                    paged = paged,
                    onVideo = onVideo,
                    onChannel = onChannel,
                    state = listState,
                    emptyTitle = "Nada por aquí todavía",
                    emptySubtitle = "Desliza hacia abajo para recargar.",
                    header = tip,
                    skip = if (featured) 13 else 0,
                )
            }
        }
    }
}
