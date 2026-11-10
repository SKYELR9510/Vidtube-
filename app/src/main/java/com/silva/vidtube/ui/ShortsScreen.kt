package com.silva.vidtube.ui

import android.widget.Toast
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.ThumbUp
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.runtime.collectAsState
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.mutableFloatStateOf
import com.google.android.exoplayer2.Player
import kotlinx.coroutines.delay
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
    /** Shorts descartados (horizontales o que no cargan): no vuelven a aparecer. */
    private val rejected = HashSet<String>()
    private var round = 0
    private var current = 0
    private var lastError: String? = null

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
        current = 0
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
                val before = items.size
                var tries = 0
                // Si una ronda no trae nada nuevo, probamos con otros temas (hasta 3 veces)
                while (items.size == before && tries < 3) {
                    tries++
                    val pickedTopics = List(2) { i -> t[(round * 2 + i) % t.size] }.distinct()
                    val pickedSubs = if (subs.isEmpty()) emptyList()
                    else List(minOf(3, subs.size)) { i -> subs[(round * 3 + i) % subs.size] }.distinct()
                    round++
                    // Cada fuente se agrega apenas llega: así el primer short sale antes
                    kotlinx.coroutines.coroutineScope {
                        pickedTopics.forEach { q ->
                            launch {
                                runCatching { Repository.shortsSearch(q) }
                                    .onSuccess { add(it) }
                                    .onFailure { lastError = Paged.friendly(it) }
                            }
                        }
                        pickedSubs.forEach { ch -> launch { add(Repository.channelShorts(ch)) } }
                    }
                }
                if (items.isEmpty()) error = lastError ?: "No se encontraron Shorts. Toca Reintentar."
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                error = Paged.friendly(e)
            } finally {
                loading = false
            }
        }
    }

    private fun add(list: List<VideoItem>) {
        val seen = items.map { it.url }.toHashSet()
        val watched = Library.history.value.map { it.url }.toHashSet()
        val fresh = list.shuffled().filter { seen.add(it.url) && it.url !in watched && it.url !in rejected }
        if (fresh.isEmpty()) return
        val wasEmpty = items.isEmpty()
        items = items + fresh
        // Los primeros se empiezan a cargar ya, antes de que se muestren
        if (wasEmpty) prefetch(-1)
    }

    fun remove(item: VideoItem) {
        rejected += item.url
        items = items.filterNot { it.url == item.url }
        if (items.size - current < 5) loadMore()
    }

    private fun load(url: String) = viewModelScope.async {
        runCatching { Repository.stream(url) }
            .onFailure { lastError = Paged.friendly(it) }
            .getOrNull()
    }

    suspend fun info(item: VideoItem): StreamInfo? {
        cache[item.url]?.let { return it }
        val job = pending.getOrPut(item.url) { load(item.url) }
        val info = job.await()
        if (info != null) { cache[item.url] = info; enrich(item.url, info) }
        pending.remove(item.url)
        return info
    }

    /** Los shorts de la búsqueda no traen el canal: lo completamos al cargar el video. */
    private fun enrich(url: String, info: StreamInfo) {
        val cur = items.firstOrNull { it.url == url } ?: return
        if (cur.channelName.isNotBlank()) return
        val full = VideoItem.from(info)
        items = items.map {
            if (it.url == url) it.copy(
                channelName = full.channelName,
                channelUrl = full.channelUrl,
                channelAvatar = full.channelAvatar,
                title = full.title.ifBlank { it.title },
                views = if (full.views > 0) full.views else it.views,
                uploaded = if (full.views > 0) full.uploaded else it.uploaded,
            ) else it
        }
    }

    /** Carga por adelantado los próximos shorts y descarta los que no sirven antes de llegar a ellos. */
    fun prefetch(index: Int) {
        current = index.coerceAtLeast(0)
        items.drop(index + 1).take(4).forEach { v ->
            if (!cache.containsKey(v.url) && !pending.containsKey(v.url)) {
                val job = load(v.url)
                pending[v.url] = job
                viewModelScope.launch {
                    val info = job.await()
                    pending.remove(v.url)
                    if (info != null) { cache[v.url] = info; enrich(v.url, info) }
                    val idx = items.indexOfFirst { it.url == v.url }
                    // Solo quitamos los que aún no estás viendo
                    if ((info == null || isLandscape(info)) && idx > current) remove(v)
                }
            }
        }
        if (index >= items.size - 5) loadMore()
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
    var buffering by remember { mutableStateOf(false) }

    // Ruedita también mientras el video se está cargando (no solo mientras se busca)
    LaunchedEffect(Unit) {
        while (true) {
            val p = PlayerHolder.playerOrNull
            buffering = p != null && p.playbackState == Player.STATE_BUFFERING
            delay(150)
        }
    }

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
                    // No se pudo cargar: se salta (no vuelve a salir)
                    info == null -> vm.remove(item)
                    // Si es horizontal igual se reproduce (ajustado a la pantalla), sin hacerte esperar
                    pager.currentPage == page -> runCatching { PlayerHolder.playShort(context, info) }
                        .onFailure { vm.remove(item) }
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
                    ready = isCurrent && !loadingCurrent,
                    loading = isCurrent && (loadingCurrent || buffering),
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
    ready: Boolean,
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
        if (isCurrent && ready) {
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
            val likedList by Library.liked.collectAsState()
            val liked = likedList.any { it.url == item.url }
            ShortAction(
                if (liked) Icons.Filled.ThumbUp else Icons.Outlined.ThumbUp,
                "Me gusta",
                tint = if (liked) VColors.Red else Color.White,
            ) { Library.toggleLike(item) }
            ShortAction(Icons.Outlined.Share, "Compartir") { shareUrl(context, item.title, item.url) }
            ShortAction(Icons.Outlined.BookmarkBorder, "Guardar") {
                val added = Library.toggleWatchLater(item)
                Toast.makeText(
                    context,
                    if (added) "Guardado en Ver más tarde" else "Quitado de Ver más tarde",
                    Toast.LENGTH_SHORT,
                ).show()
            }
            ShortAction(Icons.Outlined.Download, "Descargar", onClick = onDownload)
        }

        // Canal y título abajo
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 14.dp, end = 80.dp, bottom = 24.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    Modifier.weight(1f, fill = false).clickable(enabled = item.channelUrl != null) { item.channelUrl?.let(onChannel) },
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

        // La barra se ve siempre en el short actual (en cero mientras carga)
        if (isCurrent) {
            ShortProgressBar(Modifier.align(Alignment.BottomCenter), active = ready)
        }
    }
}

/** Barra de progreso fina abajo, como en YouTube; se puede tocar o arrastrar para adelantar. */
@Composable
private fun ShortProgressBar(modifier: Modifier = Modifier, active: Boolean = true) {
    var progress by remember { mutableFloatStateOf(0f) }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    var widthPx by remember { mutableFloatStateOf(1f) }
    val isActive by androidx.compose.runtime.rememberUpdatedState(active)

    LaunchedEffect(Unit) {
        while (true) {
            val p = PlayerHolder.playerOrNull
            val dur = p?.duration ?: 0L
            if (!isActive) progress = 0f
            else if (p != null && dur > 0 && !dragging) {
                progress = (p.currentPosition.toFloat() / dur).coerceIn(0f, 1f)
            }
            delay(100)
        }
    }

    fun seekTo(fraction: Float) {
        val p = PlayerHolder.playerOrNull ?: return
        val dur = p.duration
        if (dur > 0) p.seekTo((dur * fraction.coerceIn(0f, 1f)).toLong())
    }

    val shown = if (dragging) dragValue else progress
    Box(
        modifier
            .fillMaxWidth()
            .height(22.dp)
            .onSizeChanged { widthPx = it.width.toFloat().coerceAtLeast(1f) }
            .pointerInput(Unit) {
                detectTapGestures { off ->
                    val f = off.x / widthPx
                    progress = f.coerceIn(0f, 1f)
                    seekTo(f)
                }
            }
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { off -> dragging = true; dragValue = (off.x / widthPx).coerceIn(0f, 1f) },
                    onDragEnd = { seekTo(dragValue); progress = dragValue; dragging = false },
                    onDragCancel = { dragging = false },
                    onHorizontalDrag = { change, _ ->
                        dragValue = (change.position.x / widthPx).coerceIn(0f, 1f)
                    },
                )
            },
        contentAlignment = Alignment.BottomStart,
    ) {
        val barHeight = if (dragging) 6.dp else 3.dp
        Box(Modifier.fillMaxWidth().height(barHeight).background(Color(0x88FFFFFF)))
        Box(Modifier.fillMaxWidth(shown).height(barHeight).background(VColors.Red))
    }
}

@Composable
private fun ShortAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    tint: Color = Color.White,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable(onClick = onClick)) {
        Box(
            Modifier
                .size(46.dp)
                .background(Color(0x40FFFFFF), shape = androidx.compose.foundation.shape.CircleShape)
                .border(1.dp, Color(0x55FFFFFF), androidx.compose.foundation.shape.CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, label, tint = tint, modifier = Modifier.size(26.dp))
        }
        Text(label, color = Color.White, fontSize = 11.sp)
    }
}
