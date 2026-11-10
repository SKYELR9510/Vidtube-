package com.silva.vidtube.ui

import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.silva.vidtube.data.ChannelDetails
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.VideoItem
import kotlinx.coroutines.launch

class ChannelViewModel(private val url: String) : ViewModel() {
    var details by mutableStateOf<ChannelDetails?>(null)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val videos = Paged(viewModelScope) { Repository.channel(url).also { details = it }.videos }
    var tab by mutableStateOf(0)
    var shorts by mutableStateOf<List<VideoItem>?>(null)
        private set

    fun loadShorts() {
        val d = details ?: return
        if (shorts != null) return
        viewModelScope.launch { shorts = Repository.channelShorts(d.channel, limit = 60) }
    }

    fun load() {
        if (details != null) return
        error = null
        viewModelScope.launch {
            try {
                val d = Repository.channel(url)
                details = d
                videos.setInitial(d.videos)
            } catch (e: Exception) {
                error = Paged.friendly(e)
            }
        }
    }
}

@Composable
fun ChannelScreen(
    url: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onSearch: () -> Unit,
) {
    val vm: ChannelViewModel = viewModel(key = url) { ChannelViewModel(url) }
    val context = LocalContext.current
    LaunchedEffect(url) { vm.load() }
    val d = vm.details

    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        VTopBar(title = d?.channel?.name ?: "", onBack = onBack) {
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Buscar", tint = VColors.Text) }
            if (d != null) {
                IconButton(onClick = { shareUrl(context, d.channel.name, d.channel.url) }) {
                    Icon(Icons.Outlined.Share, "Compartir", tint = VColors.Text)
                }
            }
        }
        when {
            d == null && vm.error != null -> ErrorView(vm.error ?: "", onRetry = { vm.load() })
            d == null -> LoadingView()
            vm.tab == 1 -> {
                LaunchedEffect(Unit) { vm.loadShorts() }
                val list = vm.shorts
                LazyColumn(Modifier.fillMaxSize()) {
                    item("header") { ChannelHeader(d!!, vm.tab) { vm.tab = it } }
                    when {
                        list == null -> item("loading") { LoadingView() }
                        list.isEmpty() -> item("empty") { EmptyView("Sin Shorts", "Este canal no tiene Shorts.") }
                        else -> items(list.chunked(3), key = { "s_" + it.first().url }) { row ->
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                row.forEach { v -> ShortTile(v, Modifier.weight(1f)) { onVideo(v) } }
                                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            else -> FeedList(
                paged = vm.videos,
                onVideo = onVideo,
                onChannel = {},
                showChannelAvatar = false,
                emptyTitle = "Este canal no tiene videos",
                header = { item("header") { ChannelHeader(d!!, vm.tab) { vm.tab = it } } },
            )
        }
    }
}

@Composable
private fun ShortTile(v: VideoItem, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .aspectRatio(9f / 16f)
            .clip(RoundedCornerShape(12.dp))
            .background(VColors.Chip)
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = v.thumbnail, contentDescription = v.title,
            contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize(),
        )
        Box(
            Modifier.fillMaxSize().background(
                androidx.compose.ui.graphics.Brush.verticalGradient(
                    0.55f to androidx.compose.ui.graphics.Color.Transparent,
                    1f to androidx.compose.ui.graphics.Color(0xCC000000),
                )
            )
        )
        Text(
            v.title, color = androidx.compose.ui.graphics.Color.White, fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp),
        )
    }
}

@Composable
private fun ChannelHeader(d: ChannelDetails, tab: Int, onTab: (Int) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        if (d.banner != null) {
            AsyncImage(
                model = d.banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(4.6f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(VColors.Chip),
            )
        }
        Spacer(Modifier.height(if (d.banner != null) 14.dp else 8.dp))
        // Foto con aro rojo brillante
        Box(
            Modifier
                .size(108.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(
                    androidx.compose.ui.graphics.Brush.sweepGradient(
                        listOf(VColors.Red, androidx.compose.ui.graphics.Color(0xFFFF7A45), VColors.RedDeep, VColors.Red)
                    )
                )
                .padding(3.dp)
                .clip(androidx.compose.foundation.shape.CircleShape)
                .background(VColors.Background)
                .padding(3.dp),
            contentAlignment = Alignment.Center,
        ) {
            Avatar(d.channel.avatar, d.channel.name, 96.dp)
        }
        Spacer(Modifier.height(10.dp))
        Text(
            d.channel.name,
            color = VColors.Text,
            fontSize = 24.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        val subs = subscribersText(d.channel.subscribers)
        if (subs.isNotEmpty()) Text(subs, color = VColors.Secondary, fontSize = 14.sp)
        if (d.description.isNotBlank()) {
            Text(
                d.description,
                color = VColors.Secondary,
                fontSize = 13.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 24.dp, vertical = 8.dp)
                    .clickable { expanded = !expanded },
            )
        } else {
            Spacer(Modifier.height(10.dp))
        }
        SubscribeButton(d.channel, Modifier.width(220.dp).height(42.dp))
        Spacer(Modifier.height(16.dp))
        // Pestañas
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            listOf("Videos", "Shorts").forEachIndexed { i, label ->
                val sel = tab == i
                Text(
                    label,
                    color = if (sel) androidx.compose.ui.graphics.Color.White else VColors.Text,
                    fontSize = 14.sp,
                    fontWeight = if (sel) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .then(if (sel) Modifier.background(VColors.RedGradient) else Modifier.background(VColors.Chip))
                        .clickable { onTab(i) }
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                )
            }
        }
        Spacer(Modifier.height(12.dp))
    }
}
