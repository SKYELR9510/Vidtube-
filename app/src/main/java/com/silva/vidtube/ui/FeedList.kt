package com.silva.vidtube.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.unit.dp
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
    val columns = videoColumns()
    LazyColumn(modifier.fillMaxSize(), state = state) {
        header?.invoke(this)
        val items = paged.items
        val shown = items.drop(skip)
        if (columns <= 1) {
            itemsIndexed(shown, key = { _, e -> e.key }) { i, entry ->
                val index = i + skip
                if (index >= items.size - 4 && paged.canLoadMore) {
                    LaunchedEffect(items.size) { paged.loadMore() }
                }
                FeedEntryView(entry, onVideo, onChannel, showChannelAvatar)
            }
        } else {
            // Tablet: cuadrícula de videos (los canales ocupan toda la fila)
            val rows = feedRows(shown, columns)
            itemsIndexed(rows, key = { _, r -> r.first().key }) { i, row ->
                if (i >= rows.size - 2 && paged.canLoadMore) {
                    LaunchedEffect(items.size) { paged.loadMore() }
                }
                if (row.size == 1 && row[0] is FeedEntry.Channel) {
                    FeedEntryView(row[0], onVideo, onChannel, showChannelAvatar)
                } else {
                    Row(Modifier.fillMaxWidth().padding(horizontal = 6.dp)) {
                        row.forEach { e ->
                            Box(Modifier.weight(1f)) { FeedEntryView(e, onVideo, onChannel, showChannelAvatar) }
                        }
                        repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
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
private fun FeedEntryView(entry: FeedEntry, onVideo: (VideoItem) -> Unit, onChannel: (String) -> Unit, showChannelAvatar: Boolean) {
    when (entry) {
        is FeedEntry.Video -> VideoCard(
            entry.video,
            onClick = { onVideo(entry.video) },
            onChannel = onChannel,
            showChannelAvatar = showChannelAvatar,
            reason = entry.reason,
        )
        is FeedEntry.Channel -> ChannelEntry(entry.channel, onChannel)
    }
}

/** Agrupa los videos en filas de [columns]; cada canal va en su propia fila. */
internal fun feedRows(entries: List<FeedEntry>, columns: Int): List<List<FeedEntry>> {
    val rows = ArrayList<List<FeedEntry>>()
    var current = ArrayList<FeedEntry>()
    for (e in entries) {
        if (e is FeedEntry.Channel) {
            if (current.isNotEmpty()) { rows += current; current = ArrayList() }
            rows += listOf(e)
        } else {
            current += e
            if (current.size == columns) { rows += current; current = ArrayList() }
        }
    }
    if (current.isNotEmpty()) rows += current
    return rows
}

@Composable
private fun ChannelEntry(channel: ChannelItem, onChannel: (String) -> Unit) {
    ChannelResultRow(channel) { onChannel(channel.url) }
}
