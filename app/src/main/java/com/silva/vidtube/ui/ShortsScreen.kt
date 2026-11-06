package com.silva.vidtube.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import com.silva.vidtube.data.ChannelItem
import com.silva.vidtube.data.Library
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.VideoItem
import com.silva.vidtube.download.Downloads
import com.silva.vidtube.player.NowPlaying
import com.silva.vidtube.player.PlayerHolder
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.stream.StreamInfo

class ShortsViewModel : ViewModel() {
    var items by mutableStateOf<List<VideoItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    /** Info ya cargada de cada short (para que el siguiente empiece al instante). */
    private val cache = HashMap<String, StreamInfo>()
    private val pending = HashMap<String, kotlinx.coroutines.Deferred<StreamInfo?>>()
    private var round = 0

    private fun topics(): List<String> {
        val interests = Library.interests(6).map { it.query }
        return (interests + listOf("música", "humor", "fútbol", "recetas", "baile", "viral")).distinct()
    }

    fun loadIfNeeded() {
        if (items.isEmpty() && !loading) loadMore()
    }

    fun refresh() {
        items = emptyList()
        round = 0
        cache.clear()
        pending.clear()
        loadMore()
    }

    /** Mezcla shorts de tus suscripciones y de tus temas; cada ronda trae temas distintos. */
    fun loadMore() {
        if (loading) return
        loading = true
        error = null
        viewModelScope.launch {
            try {
                val t = topics()
                val subs = Library.subscriptions.value
                var tries = 0
                var added = 0
                // Si una ronda no trae nada nuevo, probamos con otros temas (hasta 3 veces)
                while (added == 0 && tries < 3) {
                    tries++
                    val pickedTopics = List(2) { i -> t[(round * 2 + i) % t.size] }.distinct()
                    val pickedSubs = if (subs.isEmpty()) emptyList()
                    else List(minOf(3, subs.size)) { i -> subs[(round * 3 + i) % subs.size] }.distinct()
                    round++

                    val results = (pickedTopics.map { q -> async { Repository.shortsSearch(q) } } +
                        pickedSubs.map { ch -> async { Repository.channelShorts(ch) } }).awaitAll()

                    // Intercalar las fuentes para que no salgan 10 seguidos del mismo
                    val mixed = ArrayList<VideoItem>()
                    val queues = results.map { ArrayDeque(it.shuffled()) }
                    while (queues.any { it.isNotEmpty() }) {
                        queues.forEach { q -> q.removeFirstOrNull()?.let { mixed += it } }
                    }

                    val seen = items.map { it.url }.toHashSet()
                    val watched = Library.history.value.map { it.url }.toHashSet()
                    val fresh = mixed.filter { seen.add(it.url) && it.url !in watched }
                    items = items + fresh
                    added = fresh.size
                }
                if (items.isEmpty()) error = "No se encontraron Shorts. Toca Reintentar."
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = Paged.friendly(e)
            } finally {
                loading = false
            }
        }
    }

    fun remove(item: VideoItem) {
        items = items.filterNot { it.url == item.url }
        if (items.size < 4) loadMore()
    }

    suspend fun info(item: VideoItem): StreamInfo? {
        cache[item.url]?.let { return it }
        val job = pending.getOrPut(item.url) {
            viewModelScope.async { runCatching { Repository.stream(item.url) }.getOrNull() }
        }
        val info = job.await()
        if (info != null) cache[item.url] = info
        pending.remove(item.url)
        return info
    }

    /** Carga por adelantado los próximos shorts. */
    fun prefetch(index: Int) {
        items.drop(index + 1).take(3).forEach { v ->
            if (!cache.containsKey(v.url) && !pending.containsKey(v.url)) {
                pending[v.url] = viewModelScope.async { runCatching { Repository.stream(v.url) }.getOrNull() }
            }
        }
        if (index >= items.size - 4) loadMore()
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ShortsScreen(
    onChannel: (String) -> Unit,
    vm: ShortsViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // Al entrar a Shorts se cierra el video normal; al salir se detiene el short
    DisposableEffect(Unit) {
        NowPlaying.close(context)
        onDispose { PlayerHolder.stopShorts(context) }
    }
    LaunchedEffect(Unit) { vm.loadIfNeeded() }

    val pager = rememberPagerState { vm.items.size }
    var playing by remember { mutableStateOf(true) }
    var loadingCurrent by remember { mutableStateOf(false) }
    var playJob by remember { mutableStateOf<Job?>(null) }

    // Cada vez que cambia el short visible, se reproduce ese
    LaunchedEffect(vm.items.isNotEmpty()) {
        if (vm.items.isEmpty()) return@LaunchedEffect
        snapshotFlow { pager.currentPage to vm.items.getOrNull(pager.currentPage)?.url }
            .distinctUntilChanged().collect { (page, _) ->
            val item = vm.items.getOrNull(page) ?: return@collect
            playJob?.cancel()
            playing = true
            loadingCurrent = true
            PlayerHolder.playerOrNull?.stop()
            playJob = scope.launch {
                val info = vm.info(item)
                when {
                    // Videos horizontales que se colaron: se quitan y pasa el siguiente
                    info != null && isLandscape(info) -> vm.remove(item)
                    // No se pudo cargar: también se salta
                    info == null -> vm.remove(item)
                    pager.currentPage == page -> runCatching { PlayerHolder.playShort(context, info) }
                }
                loadingCurrent = false
            }
            vm.prefetch(page)
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            vm.items.isEmpty() && vm.loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VColors.Red)
            }
            vm.items.isEmpty() -> ErrorView(
                vm.error ?: "No hay Shorts por ahora.",
                onRetry = { vm.refresh() },
                modifier = Modifier.align(Alignment.Center),
            )
            else -> VerticalPager(
                state = pager,
                modifier = Modifier.fillMaxSize(),
                beyondViewportPageCount = 1,
                key = { vm.items[it].url },
            ) { page ->
                val item = vm.items[page]
                val isCurrent = pager.currentPage == page
                ShortPage(
                    item = item,
                    isCurrent = isCurrent,
                    loading = isCurrent && loadingCurrent,
                    paused = isCurrent && !playing,
                    onTogglePlay = {
                        val p = PlayerHolder.playerOrNull ?: return@ShortPage
                        if (p.isPlaying) p.pause() else p.play()
                        playing = p.playWhenReady
                    },
                    onChannel = onChannel,
                    onDownload = {
                        scope.launch {
                            val info = vm.info(item)
                            val opt = info?.let { Downloads.options(it).firstOrNull { o -> !o.isAudioOnly } }
                            if (info != null && opt != null) {
                                Downloads.enqueue(context, info, opt)
                                Toast.makeText(context, "Descargando… míralo en Tú › Descargas", Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, "No se pudo descargar este short", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Shorts", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { vm.refresh() }) {
                Icon(Icons.Outlined.Refresh, "Actualizar", tint = Color.White)
            }
        }
    }
}

/** ¿El video es horizontal? (los Shorts son verticales) */
private fun isLandscape(info: StreamInfo): Boolean {
    val v = (info.videoOnlyStreams + info.videoStreams).maxByOrNull { it.height } ?: return false
    val w = v.width
    val h = v.height
    return w > 0 && h > 0 && w > h
}

@Composable
private fun ShortPage(
    item: VideoItem,
    isCurrent: Boolean,
    loading: Boolean,
    paused: Boolean,
    onTogglePlay: () -> Unit,
    onChannel: (String) -> Unit,
    onDownload: () -> Unit,
) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize().background(Color.Black)) {
        // Miniatura de fondo mientras carga (y en las páginas vecinas)
        AsyncImage(
            model = item.thumbnail,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        if (isCurrent && !loading) {
            PlayerSurface(Modifier.fillMaxSize(), useController = false)
        }
        if (loading) {
            CircularProgressIndicator(color = Color.White, modifier = Modifier.align(Alignment.Center))
        }
        // Capa transparente: tocar = pausar / reanudar (deslizar sigue cambiando de short)
        Box(
            Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = onTogglePlay,
                )
        )
        if (paused) {
            Icon(
                Icons.Filled.PlayArrow,
                "Reproducir",
                tint = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.align(Alignment.Center).size(84.dp),
            )
        }

        // Degradado inferior para leer el texto
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(260.dp)
                .background(Brush.verticalGradient(listOf(Color.Transparent, Color(0xCC000000))))
        )

        // Acciones a la derecha
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 8.dp, bottom = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            ShortAction(Icons.Outlined.Share, "Compartir") { shareUrl(context, item.title, item.url) }
            ShortAction(Icons.Outlined.BookmarkBorder, "Guardar") {
                val added = Library.toggleWatchLater(item)
                Toast.makeText(
                    context,
                    if (added) "Guardado en Ver más tarde" else "Quitado de Ver más tarde",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            ShortAction(Icons.Outlined.Download, "Descargar", onDownload)
        }

        // Canal y título abajo
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 80.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.clickable(enabled = item.channelUrl != null) { item.channelUrl?.let(onChannel) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(item.channelAvatar, item.channelName, 32.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        item.channelName,
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (item.channelUrl != null) {
                    Spacer(Modifier.width(10.dp))
                    SubscribeButton(
                        ChannelItem(item.channelUrl, item.channelName, item.channelAvatar),
                        Modifier.height(32.dp),
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                item.title,
                color = Color.White,
                fontSize = 14.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            val meta = joinDot(viewsText(item.views), item.uploaded)
            if (meta.isNotBlank()) Text(meta, color = Color(0xFFDDDDDD), fontSize = 12.sp)
        }
    }
}

@Composable
private fun ShortAction(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(46.dp)
                .background(Color(0x55000000), shape = androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = Color.White, modifier = Modifier.size(26.dp))
        }
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
