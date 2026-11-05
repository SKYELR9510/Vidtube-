package com.silva.vidtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
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

class SubscriptionsViewModel : ViewModel() {
    var lastCount = -1
    val feed = Paged(viewModelScope) {
        val videos = Repository.subscriptionFeed(Library.subscriptions.value)
        PagedResult(videos.map { FeedEntry.Video(it) }, null)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubscriptionsScreen(
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    onSearch: () -> Unit,
    vm: SubscriptionsViewModel = viewModel(),
) {
    val subs by Library.subscriptions.collectAsState()
    LaunchedEffect(subs.size) {
        if (subs.isNotEmpty() && vm.lastCount != subs.size) {
            vm.lastCount = subs.size
            if (vm.feed.loaded) vm.feed.refresh() else vm.feed.loadIfNeeded()
        }
    }

    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        VTopBar(title = "Suscripciones") {
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Buscar", tint = VColors.Text) }
        }
        if (subs.isEmpty()) {
            EmptyView(
                "Aún no tienes suscripciones",
                "Busca un canal y toca \"Suscribirse\". Sus videos nuevos aparecerán aquí. " +
                    "Tus suscripciones se guardan solo en este celular.",
            )
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(subs, key = { it.url }) { ch ->
                Column(
                    Modifier
                        .width(64.dp)
                        .clickable { onChannel(ch.url) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Avatar(ch.avatar, ch.name, 56.dp)
                    Text(
                        ch.name,
                        color = VColors.Secondary,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
        HorizontalDivider(color = VColors.Divider)
        PullToRefreshBox(
            isRefreshing = vm.feed.refreshing,
            onRefresh = { vm.feed.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            FeedList(
                paged = vm.feed,
                onVideo = onVideo,
                onChannel = onChannel,
                emptyTitle = "Sin videos nuevos",
                emptySubtitle = "Desliza hacia abajo para recargar.",
            )
        }
    }
}
