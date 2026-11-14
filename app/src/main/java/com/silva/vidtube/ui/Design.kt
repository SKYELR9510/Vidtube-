package com.silva.vidtube.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.filled.Check
import coil.compose.AsyncImage
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkAdded
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Subscriptions
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silva.vidtube.R
import com.silva.vidtube.data.Library
import com.silva.vidtube.data.VideoItem

/** Pantalla de bienvenida al abrir la app (siempre oscura, como la marca). */
@Composable
fun SplashScreen() {
    val pulse = rememberInfiniteTransition(label = "pulse")
    val glow by pulse.animateFloat(
        0.85f, 1.05f,
        infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Reverse),
        label = "glow",
    )
    Box(
        Modifier
            .fillMaxSize()
            .background(Color(0xFF050506))
            .background(
                Brush.radialGradient(
                    listOf(Color(0x99C4001A), Color(0x33600010), Color.Transparent),
                    radius = 1100f,
                )
            ),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painterResource(R.drawable.vidtube_mark),
                contentDescription = null,
                modifier = Modifier.size(170.dp).scale(glow),
            )
            Spacer(Modifier.height(18.dp))
            Image(
                painterResource(R.drawable.vidtube_wordmark),
                contentDescription = "Vidtube",
                modifier = Modifier.height(52.dp),
            )
            Spacer(Modifier.height(14.dp))
            Text(
                "Tu mundo de videos,\nen un solo lugar",
                color = Color(0xFFE6E6EA),
                fontSize = 16.sp,
                fontStyle = FontStyle.Italic,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(48.dp))
            CircularProgressIndicator(color = VColors.Red, strokeWidth = 2.5.dp, modifier = Modifier.size(30.dp))
        }
    }
}

data class NavTab(val route: String, val label: String, val selected: ImageVector, val normal: ImageVector)

val navTabs = listOf(
    NavTab("home", "Inicio", Icons.Filled.Home, Icons.Outlined.Home),
    NavTab("shorts", "Shorts", Icons.Filled.Bolt, Icons.Outlined.Bolt),
    NavTab("search", "Explorar", Icons.Filled.Explore, Icons.Outlined.Explore),
    NavTab("subs", "Suscripciones", Icons.Filled.Subscriptions, Icons.Outlined.Subscriptions),
    NavTab("library", "Biblioteca", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
)

/** Barra inferior: la pestaña activa se ilumina en rojo. */
@Composable
fun VBottomBar(current: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(VColors.Background)) {
        HorizontalDivider(color = VColors.Divider, thickness = 0.5.dp)
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navTabs.forEach { tab ->
                val sel = current == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onSelect(tab.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (sel) {
                            Box(
                                Modifier
                                    .size(width = 52.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(VColors.RedGradient)
                            )
                        }
                        Icon(
                            if (sel) tab.selected else tab.normal,
                            tab.label,
                            tint = if (sel) Color.White else VColors.Secondary,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        color = if (sel) VColors.Text else VColors.Secondary,
                        fontSize = 10.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Título de sección con subtítulo y "Ver todo ›" opcionales. */
@Composable
fun SectionTitle(title: String, onMore: (() -> Unit)? = null, subtitle: String? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 20.dp, bottom = 10.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, color = VColors.Text, fontSize = 19.sp, fontWeight = FontWeight.ExtraBold)
            if (subtitle != null) Text(subtitle, color = VColors.Muted, fontSize = 12.sp)
        }
        if (onMore != null) {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onMore).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Ver todo", color = VColors.Secondary, fontSize = 13.sp)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VColors.Secondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Banner cinematográfico del Inicio: Reproducir, Mi lista e indicador EN VIVO. */
@Composable
fun HeroCard(video: VideoItem, onPlay: () -> Unit, onChannel: (String) -> Unit) {
    val later by Library.watchLater.collectAsState()
    val saved = later.any { it.url == video.url }
    Box(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 11f)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, Color(0x14BFC3CA), RoundedCornerShape(20.dp))
            .clickable(onClick = onPlay)
    ) {
        AsyncImage(
            model = video.thumbnail?.replace(Regex("hq720|hqdefault|mqdefault"), "maxresdefault"),
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize().background(Color.Black),
            error = coil.compose.rememberAsyncImagePainter(video.thumbnail),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0f to Color.Transparent, 0.35f to Color(0x2208090B), 0.62f to Color(0xC708090B), 1f to Color(0xF708090B))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            if (video.isLive) {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(VColors.Red).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(5.dp))
                    Text("EN VIVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
            } else {
                Text("DESTACADO", color = VColors.Silver, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp)
            }
            Spacer(Modifier.height(6.dp))
            Text(
                video.title,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 25.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                joinDot(video.channelName, viewsText(video.views), video.uploaded, if (video.isLive) null else durationText(video.durationSec).ifEmpty { null }),
                color = VColors.Silver,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = video.channelUrl != null) { video.channelUrl?.let(onChannel) },
            )
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VColors.RedGradient)
                        .clickable(onClick = onPlay)
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reproducir", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x33FFFFFF))
                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(50))
                        .clickable { Library.toggleWatchLater(video) }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (saved) Icons.Filled.Check else Icons.Filled.Add,
                        null, tint = Color.White, modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (saved) "En Mi lista" else "Mi lista", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/**
 * Fila horizontal de tarjetas (Tendencias, Historial…).
 * Con [resume] muestra el % visto y el tiempo que falta (Continuar viendo).
 */
@Composable
fun VideoShelf(
    videos: List<VideoItem>,
    onVideo: (VideoItem) -> Unit,
    onChannel: ((String) -> Unit)? = null,
    resume: Boolean = false,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(videos, key = { it.url }) { v ->
            Column(Modifier.width(if (resume) 240.dp else 220.dp).clip(RoundedCornerShape(14.dp)).clickable { onVideo(v) }) {
                Box {
                    Thumbnail(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f), rounded = 14.dp, smallBadge = true)
                    if (resume) {
                        Box(
                            Modifier.align(Alignment.Center).size(42.dp).clip(CircleShape).background(VColors.RedGradient),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Filled.PlayArrow, "Reanudar", tint = Color.White, modifier = Modifier.size(26.dp)) }
                    }
                }
                Row(Modifier.padding(top = 6.dp)) {
                    Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        Text(
                            v.title,
                            color = VColors.Text,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            lineHeight = 17.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        val p = if (resume) progressOf(v) else null
                        if (p != null) {
                            val left = ((1f - p) * v.durationSec).toLong()
                            val leftTxt = if (left >= 3600) "Quedan ${left / 3600} h ${(left % 3600) / 60} min" else "Quedan ${maxOf(1, left / 60)} min"
                            Row {
                                Text("${(p * 100).toInt()}%", color = VColors.Red, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(" · $leftTxt", color = VColors.Secondary, fontSize = 11.sp, maxLines = 1)
                            }
                        } else {
                            Text(
                                joinDot(v.channelName, viewsText(v.views)),
                                color = VColors.Secondary,
                                fontSize = 11.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    if (!resume) Box(Modifier.size(width = 30.dp, height = 36.dp), contentAlignment = Alignment.TopCenter) { VideoMenu(v, onChannel) }
                }
            }
        }
    }
}

/** Categorías visuales del Inicio (sección Descubre). */
data class DiscoverTile(val id: String, val label: String, val icon: ImageVector, val color: Long)

@Composable
fun DiscoverGrid(tiles: List<DiscoverTile>, onPick: (DiscoverTile) -> Unit) {
    Column(Modifier.padding(horizontal = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        tiles.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { t ->
                    val c = Color(t.color)
                    Box(
                        Modifier
                            .weight(1f)
                            .height(92.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Brush.linearGradient(listOf(c, Color(0xFF101114))))
                            .border(1.dp, Color(0x10FFFFFF), RoundedCornerShape(14.dp))
                            .clickable { onPick(t) }
                            .padding(10.dp),
                    ) {
                        Icon(t.icon, null, tint = Color.White.copy(alpha = 0.35f), modifier = Modifier.align(Alignment.TopEnd).size(40.dp))
                        Column(Modifier.align(Alignment.BottomStart)) {
                            Text(t.label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
                            Text("Explorar ›", color = Color(0xBFFFFFFF), fontSize = 11.sp)
                        }
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Ícono redondo para las barras superiores. */
@Composable
fun TopIcon(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier.size(42.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = VColors.Text, modifier = Modifier.size(24.dp))
    }
}

/** Destacados del Inicio: pasan solos cada 3 segundos (se puede deslizar con el dedo). */
@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun HeroCarousel(videos: List<VideoItem>, onPlay: (VideoItem) -> Unit, onChannel: (String) -> Unit) {
    val items = videos.take(5)
    if (items.isEmpty()) return
    val pager = androidx.compose.foundation.pager.rememberPagerState { items.size }
    androidx.compose.runtime.LaunchedEffect(items.size) {
        while (true) {
            kotlinx.coroutines.delay(3000)
            if (!pager.isScrollInProgress && items.size > 1) {
                pager.animateScrollToPage((pager.currentPage + 1) % items.size)
            }
        }
    }
    Box {
        androidx.compose.foundation.pager.HorizontalPager(state = pager) { page ->
            HeroCard(items[page], onPlay = { onPlay(items[page]) }, onChannel = onChannel)
        }
        Row(
            Modifier.align(Alignment.BottomEnd).padding(end = 28.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items.indices.forEach { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier
                        .size(width = if (on) 22.dp else 8.dp, height = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (on) VColors.Red else Color(0x88FFFFFF))
                )
            }
        }
    }
}
