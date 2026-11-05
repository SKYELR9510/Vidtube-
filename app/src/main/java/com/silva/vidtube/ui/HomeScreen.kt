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

val homeChips = listOf(
    HomeChip("todo", "Todo"),
    HomeChip("trending_music", "Música"),
    HomeChip("trending_gaming", "Videojuegos"),
    HomeChip("trending_movies_and_shows", "Películas"),
    HomeChip("trending_podcasts_episodes", "Podcasts"),
    HomeChip("live", "En vivo"),
)

class HomeViewModel : ViewModel() {
    var selected by mutableStateOf(homeChips.first().id)
    var updateChecked = false
    private val lists = HashMap<String, Paged>()

    fun list(id: String): Paged = lists.getOrPut(id) {
        Paged(viewModelScope) { if (id == "todo") loadMix() else Repository.kiosk(id) }
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

        val watched = Library.history.value.map { it.url }.toHashSet()
        val forYou = Library.forYou(System.currentTimeMillis()).map { FeedEntry.Video(it) }
        val subs = subsJob.await().filter { it.url !in watched }.take(40).map { FeedEntry.Video(it) }
        val music = musicJob.await()
        val gaming = gamingJob.await()
        val trending = interleave(music.getOrNull()?.items.orEmpty(), gaming.getOrNull()?.items.orEmpty())

        if (forYou.isEmpty() && subs.isEmpty() && trending.isEmpty()) {
            throw music.exceptionOrNull() ?: gaming.exceptionOrNull() ?: Exception("Sin contenido")
        }
        // 2 de "para ti", 1 de suscripciones… y cada tanto una tendencia
        val personal = interleave(forYou, subs, 2)
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
    vm: HomeViewModel = viewModel(),
) {
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
            IconButton(onClick = onSearch) {
                Icon(Icons.Outlined.Search, "Buscar", tint = VColors.Text)
            }
        }
        UpdateBanner()
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        ) {
            items(homeChips, key = { it.id }) { chip ->
                val sel = chip.id == vm.selected
                Text(
                    chip.label,
                    color = if (sel) Color.Black else VColors.Text,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (sel) VColors.ChipSelected else VColors.Chip)
                        .clickable { vm.selected = chip.id }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        val tip: (LazyListScope.() -> Unit)? =
            if (vm.selected == "todo" && Library.subscriptions.value.isEmpty() && Library.history.value.size < 3) {
                {
                    item("tip") {
                        Text(
                            "Mira algunos videos y suscríbete a canales: el Inicio se irá ajustando a tus gustos.",
                            color = VColors.Secondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                    }
                }
            } else null
        PullToRefreshBox(
            isRefreshing = paged.refreshing,
            onRefresh = { paged.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            key(vm.selected) {
                FeedList(
                    paged = paged,
                    onVideo = onVideo,
                    onChannel = onChannel,
                    state = rememberLazyListState(),
                    emptyTitle = "Nada por aquí todavía",
                    emptySubtitle = "Desliza hacia abajo para recargar.",
                    header = tip,
                )
            }
        }
    }
}
