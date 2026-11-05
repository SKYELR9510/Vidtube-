package com.silva.vidtube.ui

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.BookmarkAdded
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.ZoomInMap
import androidx.compose.material.icons.outlined.ZoomOutMap
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import com.silva.vidtube.download.Downloads
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.fromHtml
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.silva.vidtube.data.ChannelItem
import com.silva.vidtube.data.Comment
import com.silva.vidtube.data.FeedEntry
import com.silva.vidtube.data.Library
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.VideoItem
import com.silva.vidtube.data.bestImage
import com.silva.vidtube.data.toEntry
import com.silva.vidtube.player.NowPlaying
import com.silva.vidtube.player.PlayerHolder
import org.schabi.newpipe.extractor.stream.Description
import org.schabi.newpipe.extractor.stream.StreamInfo

fun Context.findActivity(): Activity? {
    var c = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Pantalla del video abierto (reproductor arriba, detalles y relacionados abajo). */
@Composable
fun VideoScreen(
    state: NowPlaying.State,
    onChannel: (String) -> Unit,
) {
    val context = LocalContext.current
    val activity = context.findActivity()
    val isLandscape = LocalConfiguration.current.orientation == Configuration.ORIENTATION_LANDSCAPE
    var fullscreenRequested by rememberSaveable { mutableStateOf(false) }
    val fullscreen = fullscreenRequested || isLandscape
    val playerState = rememberPlayerUiState()

    fun toggleFullscreen() {
        if (fullscreen) {
            fullscreenRequested = false
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            fullscreenRequested = true
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        }
    }

    // Ocultar barras del sistema en pantalla completa
    DisposableEffect(fullscreen) {
        val window = activity?.window
        if (window != null) {
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            if (fullscreen) {
                controller.systemBarsBehavior =
                    WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                controller.hide(WindowInsetsCompat.Type.systemBars())
            } else {
                controller.show(WindowInsetsCompat.Type.systemBars())
            }
        }
        onDispose {}
    }
    DisposableEffect(Unit) {
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
            activity?.window?.let {
                WindowCompat.getInsetsController(it, it.decorView).show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    BackHandler {
        if (fullscreen) toggleFullscreen() else NowPlaying.minimize()
    }

    if (fullscreen) {
        var zoom by rememberSaveable { mutableStateOf(false) }
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            PlayerSurface(
                Modifier.fillMaxSize(),
                onFullscreen = { toggleFullscreen() },
                zoom = zoom,
                onZoomChange = { zoom = it },
            )
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color(0x99000000))
                    .clickable { zoom = !zoom }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    if (zoom) Icons.Outlined.ZoomInMap else Icons.Outlined.ZoomOutMap,
                    null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(if (zoom) "Ajustar" else "Llenar pantalla", color = Color.White, fontSize = 12.sp)
            }
        }
        return
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(VColors.Background)
            .statusBarsPadding()
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Color.Black)) {
            PlayerSurface(Modifier.fillMaxSize(), onFullscreen = { toggleFullscreen() })
            IconButton(
                onClick = { NowPlaying.minimize() },
                modifier = Modifier.align(Alignment.TopStart),
            ) {
                Icon(Icons.Outlined.KeyboardArrowDown, "Minimizar", tint = Color.White)
            }
            val err = playerState.error
            if (err != null && !state.loading) {
                ErrorOverlay(err) { NowPlaying.retry(context) }
            }
        }

        when {
            state.loading -> LoadingView()
            state.error != null -> ErrorView(state.error, onRetry = { NowPlaying.retry(context) })
            state.info != null -> VideoDetails(state, state.info, onChannel)
        }
    }
}

@Composable
private fun ErrorOverlay(message: String, onRetry: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color(0xCC000000)).clickable(onClick = onRetry),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(message, color = Color.White, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Text("Toca para reintentar", color = VColors.Secondary, fontSize = 12.sp)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VideoDetails(state: NowPlaying.State, info: StreamInfo, onChannel: (String) -> Unit) {
    val context = LocalContext.current
    val video = state.video
    var showDescription by remember { mutableStateOf(false) }
    var showComments by remember { mutableStateOf(false) }
    var showQuality by remember { mutableStateOf(false) }
    var showDownload by remember { mutableStateOf(false) }
    val settings by Library.settings.collectAsState()
    val upNext by NowPlaying.upNext.collectAsState()
    val watchLater by Library.watchLater.collectAsState()
    val saved = watchLater.any { it.url == video.url }

    var comments by remember(video.url) { mutableStateOf<List<Comment>?>(null) }
    var commentCount by remember(video.url) { mutableStateOf(-1) }
    LaunchedEffect(video.url) {
        runCatching { Repository.comments(video.url) }
            .onSuccess { (list, count) ->
                comments = list
                commentCount = count
            }
            .onFailure { comments = emptyList() }
    }

    val related = remember(info) {
        info.relatedItems.mapNotNull { it.toEntry() }.filterIsInstance<FeedEntry.Video>().map { it.video }
    }
    val channel = ChannelItem(
        url = info.uploaderUrl ?: "",
        name = info.uploaderName ?: "",
        avatar = bestImage(info.uploaderAvatars, 68),
        subscribers = info.uploaderSubscriberCount,
    )

    LazyColumn(Modifier.fillMaxSize()) {
        item("title") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .clickable { showDescription = true }
                    .padding(horizontal = 12.dp, vertical = 10.dp)
            ) {
                Text(
                    video.title,
                    color = VColors.Text,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 24.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    joinDot(viewsText(video.views), video.uploaded) + "  ...más",
                    color = VColors.Secondary,
                    fontSize = 12.sp,
                )
            }
        }
        item("channel") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable(enabled = channel.url.isNotEmpty()) { onChannel(channel.url) },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Avatar(channel.avatar, channel.name, 36.dp)
                    Spacer(Modifier.width(10.dp))
                    Column {
                        Text(
                            channel.name,
                            color = VColors.Text,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val subs = compactSubs(channel.subscribers)
                        if (subs.isNotEmpty()) Text(subs, color = VColors.Secondary, fontSize = 12.sp)
                    }
                }
                if (channel.url.isNotEmpty()) SubscribeButton(channel)
            }
        }
        item("actions") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (info.likeCount >= 0) {
                    item { ActionChip(compactNumber(info.likeCount), Icons.Outlined.ThumbUp) {} }
                }
                item {
                    ActionChip("Compartir", Icons.Outlined.Share) { shareUrl(context, video.title, video.url) }
                }
                if (!video.isLive) {
                    item { ActionChip("Descargar", Icons.Outlined.Download) { showDownload = true } }
                }
                item {
                    ActionChip(
                        if (saved) "Guardado" else "Ver más tarde",
                        if (saved) Icons.Outlined.BookmarkAdded else Icons.Outlined.BookmarkBorder,
                    ) {
                        val added = Library.toggleWatchLater(video)
                        Toast.makeText(
                            context,
                            if (added) "Guardado en Ver más tarde" else "Quitado de Ver más tarde",
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
                if (state.qualities.isNotEmpty() && !video.isLive) {
                    item {
                        ActionChip("Calidad ${state.quality}p", Icons.Outlined.Settings) { showQuality = true }
                    }
                }
            }
        }
        item("comments") {
            CommentsTeaser(comments, commentCount) { if (!comments.isNullOrEmpty()) showComments = true }
        }
        item("upnext") {
            Row(
                Modifier.fillMaxWidth().padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Reproducción automática", color = VColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    val next = upNext
                    Text(
                        when {
                            !settings.autoplay -> "Desactivada"
                            next != null -> "A continuación: ${next.title}"
                            else -> "No hay un video siguiente para este"
                        },
                        color = VColors.Secondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (upNext != null) {
                    IconButton(onClick = { NowPlaying.playNext(context) }) {
                        Icon(Icons.Outlined.SkipNext, "Siguiente", tint = VColors.Text)
                    }
                }
                Switch(
                    checked = settings.autoplay,
                    onCheckedChange = { v -> Library.updateSettings { it.copy(autoplay = v) } },
                    colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = VColors.Red),
                )
            }
        }
        items(related, key = { "rel:" + it.url }) { v ->
            VideoCard(v, onClick = { NowPlaying.open(context, v) }, onChannel = onChannel)
        }
        item("bottom") { Spacer(Modifier.navigationBarsPadding().height(16.dp)) }
    }

    if (showDescription) {
        ModalBottomSheet(
            onDismissRequest = { showDescription = false },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = VColors.Surface,
        ) {
            DescriptionSheet(video, info)
        }
    }
    if (showComments) {
        ModalBottomSheet(
            onDismissRequest = { showComments = false },
            containerColor = VColors.Surface,
        ) {
            CommentsList(comments.orEmpty(), commentCount)
        }
    }
    if (showDownload) {
        val options = remember(info) { Downloads.options(info) }
        ModalBottomSheet(
            onDismissRequest = { showDownload = false },
            containerColor = VColors.Surface,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Descargar",
                    color = VColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                if (options.isEmpty()) {
                    Text(
                        "Este video no tiene formatos descargables.",
                        color = VColors.Secondary,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(20.dp),
                    )
                }
                options.forEach { opt ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                Downloads.enqueue(context, info, opt)
                                showDownload = false
                                Toast.makeText(context, "Descargando… míralo en Tú › Descargas", Toast.LENGTH_SHORT).show()
                            }
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (opt.isAudioOnly) Icons.Outlined.MusicNote else Icons.Outlined.Movie,
                            null,
                            tint = VColors.Text,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text(opt.label, color = VColors.Text, fontSize = 15.sp)
                            Text(opt.detail, color = VColors.Secondary, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
    if (showQuality) {
        ModalBottomSheet(
            onDismissRequest = { showQuality = false },
            containerColor = VColors.Surface,
        ) {
            Column(Modifier.padding(bottom = 24.dp)) {
                Text(
                    "Calidad del video",
                    color = VColors.Text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
                state.qualities.forEach { q ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                NowPlaying.setQuality(context, q)
                                showQuality = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp),
                    ) {
                        Text(
                            "${q}p" + if (q == state.quality) "  ✓" else "",
                            color = if (q == state.quality) VColors.Red else VColors.Text,
                            fontSize = 15.sp,
                        )
                    }
                }
            }
        }
    }
}

private fun compactSubs(n: Long) = if (n < 0) "" else compactNumber(n) + " suscriptores"

@Composable
private fun CommentsTeaser(comments: List<Comment>?, count: Int, onClick: () -> Unit) {
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VColors.Chip)
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Comentarios", color = VColors.Text, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            if (count > 0) {
                Spacer(Modifier.width(8.dp))
                Text(compactNumber(count.toLong()), color = VColors.Secondary, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(8.dp))
        when {
            comments == null -> Text("Cargando…", color = VColors.Secondary, fontSize = 13.sp)
            comments.isEmpty() -> Text(
                if (count == -1) "Los comentarios están desactivados." else "No hay comentarios.",
                color = VColors.Secondary,
                fontSize = 13.sp,
            )
            else -> {
                val c = comments.first()
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Avatar(c.avatar, c.author, 24.dp)
                    Spacer(Modifier.width(8.dp))
                    Text(c.text, color = VColors.Text, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

@Composable
private fun CommentsList(comments: List<Comment>, count: Int) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            if (count > 0) "Comentarios · ${compactNumber(count.toLong())}" else "Comentarios",
            color = VColors.Text,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        HorizontalDivider(color = VColors.Divider)
        LazyColumn(Modifier.fillMaxWidth()) {
            items(comments) { c ->
                Row(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Avatar(c.avatar, c.author, 32.dp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (c.pinned) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.PushPin, null, tint = VColors.Secondary, modifier = Modifier.size(12.dp))
                                Text(" Fijado", color = VColors.Secondary, fontSize = 11.sp)
                            }
                        }
                        Text(joinDot(c.author, c.date), color = VColors.Secondary, fontSize = 12.sp)
                        Spacer(Modifier.height(2.dp))
                        Text(c.text, color = VColors.Text, fontSize = 14.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ThumbUp, null, tint = VColors.Secondary, modifier = Modifier.size(14.dp))
                            Text(" ${c.likes}", color = VColors.Secondary, fontSize = 12.sp)
                            if (c.replies > 0) {
                                Text(
                                    "   ${c.replies} respuestas",
                                    color = Color(0xFF3EA6FF),
                                    fontSize = 12.sp,
                                )
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.navigationBarsPadding().height(24.dp)) }
        }
    }
}

@Composable
private fun DescriptionSheet(video: VideoItem, info: StreamInfo) {
    val desc = info.description
    val text = remember(desc) {
        if (desc.type() == Description.Type.HTML) {
            AnnotatedString.fromHtml(
                desc.content(),
                linkStyles = TextLinkStyles(style = SpanStyle(color = Color(0xFF3EA6FF))),
            )
        } else {
            AnnotatedString(desc.content())
        }
    }
    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp)
            .navigationBarsPadding()
    ) {
        Text("Descripción", color = VColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(12.dp))
        Text(video.title, color = VColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            if (info.likeCount >= 0) Stat(compactNumber(info.likeCount), "Me gusta")
            if (info.viewCount >= 0) Stat(compactNumber(info.viewCount), "Vistas")
            Stat(video.uploaded ?: "", "Publicado")
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text,
            color = VColors.Text,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(VColors.Chip)
                .padding(12.dp)
                .fillMaxWidth(),
        )
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = VColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Text(label, color = VColors.Secondary, fontSize = 12.sp)
    }
}

/** Barra del mini reproductor sobre el menú inferior. */
@Composable
fun MiniPlayer(state: NowPlaying.State) {
    val context = LocalContext.current
    val ui = rememberPlayerUiState()
    Column(Modifier.fillMaxWidth().background(VColors.Surface)) {
        LinearProgressIndicator(
            progress = { ui.progress },
            color = VColors.Red,
            trackColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth().height(2.dp),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(58.dp)
                .clickable { NowPlaying.expand() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.width(103.dp).height(58.dp).background(Color.Black)) {
                PlayerSurface(Modifier.fillMaxSize(), useController = false)
            }
            Column(Modifier.weight(1f).padding(horizontal = 10.dp)) {
                Text(
                    state.video.title.ifBlank { "Cargando…" },
                    color = VColors.Text,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    state.video.channelName,
                    color = VColors.Secondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = {
                val p = PlayerHolder.player(context)
                if (p.isPlaying) p.pause() else p.play()
            }) {
                Icon(
                    if (ui.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    if (ui.isPlaying) "Pausar" else "Reproducir",
                    tint = VColors.Text,
                )
            }
            IconButton(onClick = { NowPlaying.close(context) }) {
                Icon(Icons.Filled.Close, "Cerrar", tint = VColors.Text)
            }
        }
    }
}
