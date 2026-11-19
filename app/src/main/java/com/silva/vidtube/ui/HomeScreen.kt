package com.silva.vidtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Podcasts
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.outlined.Sensors
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
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

data class HomeChip(val id: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector? = null)

// Categorías generales: muestran lo actual de YouTube para todos, no según lo que tú ves
private val fixedChips = listOf(
    HomeChip("c:peliculas", "Películas", Icons.Outlined.Movie),
    HomeChip("c:series", "Series", Icons.Outlined.Tv),
    HomeChip("trending_music", "Música", Icons.Outlined.MusicNote),
    HomeChip("c:conciertos", "Conciertos", Icons.Outlined.Mic),
    HomeChip("c:deportes", "Deportes", Icons.Outlined.SportsSoccer),
    HomeChip("c:noticias", "Noticias", Icons.Outlined.Newspaper),
    HomeChip("trending_gaming", "Videojuegos", Icons.Outlined.SportsEsports),
    HomeChip("c:infantil", "Infantil", Icons.Outlined.ChildCare),
    HomeChip("c:tecnologia", "Tecnología", Icons.Outlined.Memory),
    HomeChip("tvabierta", "TV abierta", Icons.Outlined.LiveTv),
    HomeChip("live", "En vivo", Icons.Outlined.Sensors),
    HomeChip("trending_podcasts_episodes", "Podcasts", Icons.Outlined.Podcasts),
)

/** Cómo se busca cada categoría general (texto, duración mínima, sin tráileres, antigüedad máxima en días). */
private data class CategoryDef(val query: String, val minSec: Long = 0, val noTrailers: Boolean = false, val maxAgeDays: Long = 3650)
private val categoryDefs = mapOf(
    "peliculas" to CategoryDef("película completa en español latino", minSec = 3600, noTrailers = true),
    "series" to CategoryDef("serie completa capítulo 1 español latino", minSec = 1200, noTrailers = true),
    "conciertos" to CategoryDef("concierto completo en vivo", minSec = 1800),
    "deportes" to CategoryDef("resumen deportes hoy", maxAgeDays = 7),
    "noticias" to CategoryDef("noticias de hoy", maxAgeDays = 3),
    "infantil" to CategoryDef("canciones infantiles"),
    "tecnologia" to CategoryDef("tecnología novedades", maxAgeDays = 60),
)

/** Tarjetas de la sección Descubre (abren la categoría correspondiente). */
val discoverTiles = listOf(
    DiscoverTile("c:peliculas", "Películas", Icons.Outlined.Movie, 0xFF7A1020),
    DiscoverTile("c:series", "Series", Icons.Outlined.Tv, 0xFF3B2A8A),
    DiscoverTile("trending_music", "Música", Icons.Outlined.MusicNote, 0xFF8A1F6E),
    DiscoverTile("c:deportes", "Deportes", Icons.Outlined.SportsSoccer, 0xFF16704A),
    DiscoverTile("c:infantil", "Infantil", Icons.Outlined.ChildCare, 0xFFA8611A),
    DiscoverTile("c:tecnologia", "Tecnología", Icons.Outlined.Memory, 0xFF1A5F8A),
)

/** "Todo" + categorías generales. */
fun buildHomeChips(): List<HomeChip> =
    listOf(HomeChip("todo", "Todo", Icons.Outlined.Apps), HomeChip("subs", "Suscripciones", Icons.Outlined.Subscriptions)) + fixedChips

/** Contenido de una categoría del Inicio (todas menos "Todo"). */
suspend fun loadChip(id: String): PagedResult = when {
    id == "tvabierta" -> PagedResult(emptyList(), null)
    id.startsWith("i:") -> Repository.freshSearch(id.removePrefix("i:"))
    id == "subs" -> PagedResult(
        Repository.subscriptionFeed(Library.subscriptions.value.take(60))
            .filter { !it.isShort }
            .map { FeedEntry.Video(it, "De tus suscripciones") },
        null,
    )
    id.startsWith("c:") -> categoryDefs[id.removePrefix("c:")]?.let {
        Repository.categorySearch(it.query, it.minSec, it.noTrailers, it.maxAgeDays)
    } ?: Repository.search(id.removePrefix("c:"))
    id.startsWith("q:") -> Repository.freshSearch(id.removePrefix("q:"), maxAgeDays = 60).let {
        if (it.items.size < 8) Repository.search(id.removePrefix("q:")) else it
    }
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
    /** Lo nuevo de tus suscripciones (fila "Novedades de tus suscripciones"). */
    var recentSubs by mutableStateOf<List<VideoItem>>(emptyList())
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
            async {
                runCatching { Repository.freshSearch(interest.query, maxAgeDays = 120).items }.getOrDefault(emptyList())
                    .map { e -> if (e is FeedEntry.Video) e.copy(reason = "Porque te interesa «${interest.label}»") else e }
            }
        }

        val watched = Library.history.value.map { it.url }.toHashSet()
        val forYou = Library.forYou(System.currentTimeMillis()).map { FeedEntry.Video(it, "Parecido a lo que viste") }
        val fresh = freshJobs.map { it.await() }.reduceOrNull { a, b -> interleave(a, b) }.orEmpty()
            .filter { e -> e !is FeedEntry.Video || e.video.url !in watched }
        val subsAll = subsJob.await().filter { it.url !in watched && !it.isShort }
        recentSubs = subsAll.take(12)
        val subs = subsAll.take(40).map { FeedEntry.Video(it, "De tus suscripciones") }
        val music = musicJob.await()
        val gaming = gamingJob.await()
        val trending = interleave(music.getOrNull()?.items.orEmpty(), gaming.getOrNull()?.items.orEmpty())
            .map { e -> if (e is FeedEntry.Video) e.copy(reason = "Tendencia") else e }

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
    onLibrary: () -> Unit = {},
    onMovie: (Long, Boolean) -> Unit = { _, _ -> },
    onWeb: (String, String) -> Unit = { _, _ -> },
    vm: HomeViewModel = viewModel(),
    moviesVm: MoviesViewModel = viewModel(),
) {
    val tmdbToken by com.silva.vidtube.data.Tmdb.userToken.collectAsState()
    val moviesChip = vm.selected == "c:peliculas"
    val seriesChip = vm.selected == "c:series"
    LaunchedEffect(moviesChip, seriesChip, tmdbToken) {
        if (moviesChip) moviesVm.load(tv = false)
        if (seriesChip) moviesVm.load(tv = true)
    }
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
        // Cabecera mínima: logo y perfil (la búsqueda vive en Explorar, al alcance del pulgar)
        VTopBar(leading = { VidtubeLogo() }) {
            ProfileButton(onLibrary = onLibrary, onSettings = onSettings)
        }
        UpdateBanner()
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            items(vm.chips, key = { it.id }) { chip ->
                CategoryChip(chip.label, chip.icon, chip.id == vm.selected) { vm.selected = chip.id }
            }
            item("more_cats") { CategoryChip("Más categorías", Icons.Outlined.Explore, false, onSearch) }
        }
        var scrollToFeed by androidx.compose.runtime.remember { mutableStateOf(0) }
        val subsNow by Library.subscriptions.collectAsState()
        val historyNow by Library.history.collectAsState()
        val showTip = vm.selected == "todo" && subsNow.isEmpty() && historyNow.size < 3
        // Destacado + fila de Tendencias arriba (cuando ya hay suficientes videos)
        val videos = paged.items.filterIsInstance<FeedEntry.Video>().map { it.video }
        val featured = paged.items.isNotEmpty()
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
            // Películas: pósters y fichas de TMDB arriba, películas completas de YouTube abajo
            if (moviesChip || seriesChip) movieRows(moviesVm, onMovie, onSettings, tv = seriesChip)
            // Lo útil primero: Seguir viendo, y después el feed (sin carrusel)
            if (vm.selected == "todo") {
                val cont = historyNow.filter { !it.isShort && progressOf(it) != null }.take(12)
                if (cont.isNotEmpty()) {
                    item("cont_title") { SectionTitle("Seguir viendo") }
                    item("cont") { VideoShelf(cont, onVideo, onChannel, resume = true) }
                }
                if (featured) item("feed_title") { SectionTitle("Para ti") }
            }
        }
        // TV abierta: canales en vivo en vez de la lista de videos
        if (vm.selected == "tvabierta") {
            TvContent(onVideo)
            return@Column
        }
        PullToRefreshBox(
            isRefreshing = paged.refreshing,
            onRefresh = { paged.refresh(); if (moviesChip || seriesChip) moviesVm.load(tv = seriesChip, force = true) },
            modifier = Modifier.fillMaxSize(),
        ) {
            key(vm.selected) {
                val listState = rememberLazyListState()
                if (moviesChip || seriesChip) KeepListAtTop(listState, moviesVm.rows.size)
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
                    skip = 0,
                )
            }
        }
    }
}

/** Filtro de categoría con ícono. */
@Composable
fun CategoryChip(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .then(
                if (selected) Modifier.background(VColors.Coral)
                else Modifier.background(VColors.Chip).border(1.dp, VColors.Divider, RoundedCornerShape(50))
            )
            .clickable(role = androidx.compose.ui.semantics.Role.Tab, onClick = onClick)
            .heightIn(min = 36.dp)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = if (selected) Color.White else VColors.Secondary, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(
            label,
            color = if (selected) Color.White else VColors.Secondary,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.SemiBold,
        )
    }
}

/** Botón de perfil: Biblioteca, tema y Configuración. */
@Composable
fun ProfileButton(onLibrary: () -> Unit, onSettings: () -> Unit) {
    var open by androidx.compose.runtime.remember { mutableStateOf(false) }
    val settings by Library.settings.collectAsState()
    Box(Modifier.padding(horizontal = 6.dp)) {
        Box(
            Modifier.size(38.dp).clip(CircleShape).background(VColors.Raise)
                .border(1.dp, VColors.Divider, CircleShape)
                .clickable(onClickLabel = "Abrir perfil") { open = true },
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Filled.Person, "Tu perfil", tint = VColors.Text, modifier = Modifier.size(21.dp)) }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text("Biblioteca") },
                leadingIcon = { Icon(Icons.Outlined.VideoLibrary, null) },
                onClick = { open = false; onLibrary() },
            )
            DropdownMenuItem(
                text = { Text(if (settings.lightMode) "Usar tema oscuro" else "Usar tema claro") },
                leadingIcon = { Icon(Icons.Outlined.LightMode, null) },
                onClick = { open = false; Library.updateSettings { it.copy(lightMode = !it.lightMode) } },
            )
            DropdownMenuItem(
                text = { Text("Configuración") },
                leadingIcon = { Icon(Icons.Outlined.Settings, null) },
                onClick = { open = false; onSettings() },
            )
        }
    }
}
