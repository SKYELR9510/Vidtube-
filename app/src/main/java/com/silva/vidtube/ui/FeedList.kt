package com.silva.vidtube.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.silva.vidtube.data.ChannelItem
import com.silva.vidtube.data.FeedEntry
import com.silva.vidtube.data.VideoItem

@Composable
fun FeedList(
    paged: Paged,
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    modifier: Modifier = Modifier,
    state: LazyListState = rememberLazyListState(),
    showChannelAvatar: Boolean = true,
    emptyTitle: String = "No hay nada por aquí",
    emptySubtitle: String = "",
    header: (LazyListScope.() -> Unit)? = null,
    /** Cuántos de los primeros elementos ya se muestran en el encabezado (destacado, tendencias). */
    skip: Int = 0,
) {
    LazyColumn(modifier.fillMaxSize(), state = state) {
        header?.invoke(this)
        val items = paged.items
        itemsIndexed(items.drop(skip), key = { _, e -> e.key }) { i, entry ->
            val index = i + skip
            if (index >= items.size - 4 && paged.canLoadMore) {
                LaunchedEffect(items.size) { paged.loadMore() }
            }
            when (entry) {
                is FeedEntry.Video -> VideoCard(
                    entry.video,
                    onClick = { onVideo(entry.video) },
                    onChannel = onChannel,
                    showChannelAvatar = showChannelAvatar,
                )
                is FeedEntry.Channel -> ChannelEntry(entry.channel, onChannel)
            }
        }
        when {
            paged.loading && !paged.refreshing && items.isEmpty() -> item("loading") { SkeletonFeed() }
            paged.loading && !paged.refreshing -> item("loading") { LoadingView() }
            paged.error != null && items.isEmpty() -> item("error") {
                ErrorView(paged.error ?: "", onRetry = { paged.refresh() })
            }
            paged.loaded && items.isEmpty() -> item("empty") { EmptyView(emptyTitle, emptySubtitle) }
        }
    }
}

@Composable
private fun ChannelEntry(channel: ChannelItem, onChannel: (String) -> Unit) {
    ChannelResultRow(channel) { onChannel(channel.url) }
}
