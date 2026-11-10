package com.silva.vidtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import com.silva.vidtube.data.VideoItem

/** Página completa de "Ver más": todas las tendencias o todo lo de una categoría. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MoreScreen(
    chipId: String,
    title: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val paged = remember(chipId) {
        Paged(scope) { if (chipId == "todo") loadAllTrending() else loadChip(chipId) }
    }
    LaunchedEffect(paged) { paged.loadIfNeeded() }
    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        VTopBar(title = title, onBack = onBack)
        PullToRefreshBox(
            isRefreshing = paged.refreshing,
            onRefresh = { paged.refresh() },
            modifier = Modifier.fillMaxSize(),
        ) {
            FeedList(
                paged = paged,
                onVideo = onVideo,
                onChannel = onChannel,
                emptyTitle = "Nada por aquí todavía",
                emptySubtitle = "Desliza hacia abajo para recargar.",
            )
        }
    }
}
