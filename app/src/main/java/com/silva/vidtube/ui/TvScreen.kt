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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.silva.vidtube.data.PanelChannel
import com.silva.vidtube.data.PanelLinks
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.VideoItem
import kotlinx.coroutines.launch

/** Canales de TV: solo los que agregas en tu panel web (sección «Canales de TV»). */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TvContent(onVideo: (VideoItem) -> Unit) {
    val panel by PanelLinks.channels.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    var refreshing by androidx.compose.runtime.remember { mutableStateOf(false) }
    // Transmisión en vivo en YouTube de cada canal que tenga su canal oficial (clave: dirección del canal)
    val ytLive = androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateMapOf<String, VideoItem?>() }
    suspend fun checkYoutube() {
        val gate = kotlinx.coroutines.sync.Semaphore(3)
        kotlinx.coroutines.coroutineScope {
            PanelLinks.channels.value.mapNotNull { it.youtube }.distinct().forEach { yt ->
                launch { gate.acquire(); try { ytLive[yt] = Repository.channelLive(yt) } finally { gate.release() } }
            }
        }
    }
    LaunchedEffect(Unit) { PanelLinks.refresh(); checkYoutube() }
    fun play(c: PanelChannel) {
        val live = c.youtube?.let { ytLive[it] }
        when {
            live != null -> onVideo(live)
            c.url.isNotEmpty() -> {
                com.silva.vidtube.player.NowPlaying.alternates[c.url] = c.backups
                onVideo(liveItem(c))
            }
            else -> android.widget.Toast.makeText(context, "${c.name} no está transmitiendo ahora en YouTube.", android.widget.Toast.LENGTH_LONG).show()
        }
    }
    PullToRefreshBox(
        isRefreshing = refreshing,
        onRefresh = { refreshing = true; scope.launch { PanelLinks.refresh(force = true); checkYoutube(); refreshing = false } },
        modifier = Modifier.fillMaxSize(),
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(160.dp),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (panel.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyView(
                        "Aún no hay canales",
                        if (PanelLinks.isConfigured()) "Agrégalos en tu panel web, en «Canales de TV», y desliza hacia abajo para actualizar."
                        else "Conecta tu panel en Configuración → Panel de enlaces y agrega canales en «Canales de TV».",
                    )
                }
            }
            items(panel, key = { "p:" + it.name + it.url }) { c ->
                val live = c.youtube?.let { ytLive[it] }
                PanelChannelCard(c, live, onAirKnown = c.youtube == null || ytLive.containsKey(c.youtube)) { play(c) }
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


private fun liveItem(c: PanelChannel) = VideoItem(
    url = c.url, title = c.name, thumbnail = c.logo, channelName = c.name, channelUrl = null,
    channelAvatar = c.logo, durationSec = 0, views = -1, uploaded = null, isLive = true,
)

@Composable
private fun PanelChannelCard(c: PanelChannel, ytLive: VideoItem?, onAirKnown: Boolean, onClick: () -> Unit) {
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
            if (ytLive != null || c.url.isNotEmpty()) LiveBadge(Modifier.align(Alignment.TopStart).padding(6.dp))
            if (ytLive != null) {
                Text(
                    "YouTube", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(6.dp).clip(RoundedCornerShape(6.dp))
                        .background(Color(0xCC0B0C10)).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
        }
        Text(c.name, color = VColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 6.dp, start = 2.dp))
        Text(
            when {
                ytLive != null -> "En vivo en YouTube"
                c.url.isNotEmpty() -> "En vivo"
                !onAirKnown -> "Buscando transmisión…"
                else -> "Sin transmisión ahora"
            },
            color = VColors.Secondary, fontSize = 11.sp, maxLines = 1, modifier = Modifier.padding(start = 2.dp))
    }
}

@Composable
private fun LiveBadge(modifier: Modifier, text: String = "EN VIVO") {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(VColors.Coral).padding(horizontal = 6.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
        Spacer(Modifier.width(4.dp))
        Text(text, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}
