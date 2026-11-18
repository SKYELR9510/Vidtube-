package com.silva.vidtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.silva.vidtube.data.LiveTv
import com.silva.vidtube.data.PanelChannel
import com.silva.vidtube.data.PanelLinks
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.TvChannel
import com.silva.vidtube.data.VideoItem
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Estado de cada canal: buscando, en vivo (con su video) o sin transmisión. */
sealed interface LiveState {
    data object Searching : LiveState
    data class OnAir(val video: VideoItem) : LiveState
    data object Off : LiveState
}

class TvViewModel : ViewModel() {
    val states = mutableStateMapOf<String, LiveState>()
    var refreshing by mutableStateOf(false)
    private var loadedAt = 0L

    fun load(force: Boolean = false) {
        if (refreshing) return
        // Las transmisiones cambian: se vuelven a buscar si pasaron más de 3 minutos
        if (!force && System.currentTimeMillis() - loadedAt < 180_000 && states.isNotEmpty()) return
        loadedAt = System.currentTimeMillis()
        refreshing = true
        LiveTv.peru.forEach { if (force || states[it.id] !is LiveState.OnAir) states[it.id] = LiveState.Searching }
        viewModelScope.launch {
            launch { PanelLinks.refresh() }
            val gate = Semaphore(3)
            LiveTv.peru.map { ch ->
                async {
                    gate.withPermit {
                        val v = runCatching { Repository.findLive(ch) }.getOrNull()
                        states[ch.id] = if (v != null) LiveState.OnAir(v) else LiveState.Off
                    }
                }
            }.awaitAll()
            refreshing = false
        }
    }
}

/** Canales de TV de señal abierta (YouTube oficial) y los del panel. */
@Composable
fun TvContent(onVideo: (VideoItem) -> Unit, vm: TvViewModel = viewModel()) {
    LaunchedEffect(Unit) { vm.load() }
    val panel by PanelLinks.channels.collectAsState()
    PullToRefreshBox(isRefreshing = vm.refreshing, onRefresh = { vm.load(force = true) }, modifier = Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "intro") {
                Text(
                    "Señal abierta de Perú desde sus canales oficiales de YouTube. Si un canal no está transmitiendo, " +
                        "aparece «Sin transmisión». Desliza hacia abajo para actualizar.",
                    color = VColors.Secondary, fontSize = 12.sp, modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
            if (panel.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "panel_t") { Header("De tu panel") }
                items(panel, key = { "p:" + it.url }) { c -> PanelChannelCard(c) { onVideo(liveItem(c)) } }
                item(span = { GridItemSpan(maxLineSpan) }, key = "yt_t") { Header("Canales de señal abierta") }
            }
            items(LiveTv.peru, key = { it.id }) { ch ->
                ChannelCard(ch, vm.states[ch.id] ?: LiveState.Searching) { v -> onVideo(v) }
            }
        }
    }
}

/** Pantalla propia (desde Explorar). */
@Composable
fun TvScreen(onBack: () -> Unit, onVideo: (VideoItem) -> Unit) {
    androidx.compose.foundation.layout.Column(Modifier.fillMaxSize().background(VColors.Background)) {
        VTopBar(title = "TV abierta", onBack = onBack)
        TvContent(onVideo)
    }
}

@Composable
private fun Header(t: String) {
    Text(t, color = VColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 4.dp, top = 6.dp))
}

private fun liveItem(c: PanelChannel) = VideoItem(
    url = c.url, title = c.name, thumbnail = c.logo, channelName = c.name, channelUrl = null,
    channelAvatar = c.logo, durationSec = 0, views = -1, uploaded = null, isLive = true,
)

@Composable
private fun ChannelCard(ch: TvChannel, state: LiveState, onPlay: (VideoItem) -> Unit) {
    val onAir = state as? LiveState.OnAir
    Column(
        Modifier.clip(RoundedCornerShape(12.dp))
            .clickable(enabled = onAir != null, onClickLabel = "Ver ${ch.name}") { onAir?.let { onPlay(it.video) } }
    ) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(listOf(Color(ch.color), Color(ch.color).copy(alpha = 0.35f))))
                .border(1.dp, VColors.Divider, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (onAir != null) {
                AsyncImage(
                    model = onAir.video.thumbnail, contentDescription = null, contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(ch.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
            }
            when (state) {
                is LiveState.OnAir -> LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
                LiveState.Searching -> CircularProgressIndicator(
                    color = Color.White, strokeWidth = 2.dp,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp).size(16.dp),
                )
                LiveState.Off -> Box(Modifier.fillMaxSize().background(Color(0x99000000)))
            }
        }
        Text(ch.name, color = VColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        Text(
            when (state) {
                is LiveState.OnAir -> state.video.title
                LiveState.Searching -> "Buscando señal…"
                LiveState.Off -> "Sin transmisión ahora"
            },
            color = VColors.Secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 2.dp),
        )
    }
}

@Composable
private fun PanelChannelCard(c: PanelChannel, onClick: () -> Unit) {
    Column(Modifier.clip(RoundedCornerShape(12.dp)).clickable(onClickLabel = "Ver ${c.name}", onClick = onClick)) {
        Box(
            Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp)).background(VColors.Card)
                .border(1.dp, VColors.Divider, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (c.logo != null) {
                AsyncImage(model = c.logo, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize().padding(18.dp))
            } else {
                Text(c.name, color = VColors.Text, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
            }
            LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Text(c.name, color = VColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        Text(com.silva.vidtube.data.UserLinks.hostOf(c.url), color = VColors.Secondary, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun LiveBadge(modifier: Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(VColors.Coral).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(4.dp))
        Text("EN VIVO", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
