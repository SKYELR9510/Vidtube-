package com.silva.vidtube.ui

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
            else -> FeedList(
                paged = vm.videos,
                onVideo = onVideo,
                onChannel = {},
                showChannelAvatar = false,
                emptyTitle = "Este canal no tiene videos",
                header = { item("header") { ChannelHeader(d) } },
            )
        }
    }
}

@Composable
private fun ChannelHeader(d: ChannelDetails) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth()) {
        if (d.banner != null) {
            AsyncImage(
                model = d.banner,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .fillMaxWidth()
                    .aspectRatio(4.6f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(VColors.Chip),
            )
        }
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Avatar(d.channel.avatar, d.channel.name, 72.dp)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    d.channel.name,
                    color = VColors.Text,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val subs = subscribersText(d.channel.subscribers)
                if (subs.isNotEmpty()) Text(subs, color = VColors.Secondary, fontSize = 13.sp)
            }
        }
        if (d.description.isNotBlank()) {
            Text(
                d.description,
                color = VColors.Secondary,
                fontSize = 13.sp,
                maxLines = if (expanded) Int.MAX_VALUE else 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .clickable { expanded = !expanded },
            )
            Spacer(Modifier.height(12.dp))
        }
        SubscribeButton(
            d.channel,
            Modifier
                .padding(horizontal = 16.dp)
                .fillMaxWidth()
        )
        Spacer(Modifier.height(16.dp))
        Text(
            "Videos",
            color = VColors.Text,
            fontSize = 15.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider(color = VColors.Divider)
        Spacer(Modifier.height(8.dp))
    }
}
