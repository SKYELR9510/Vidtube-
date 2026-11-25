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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.runtime.remember
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
            .background(Color(0xFF090B12))
            .background(
                Brush.radialGradient(
                    listOf(Color(0x40FF405B), Color(0x14FF875E), Color.Transparent),
                    radius = 1000f,
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
    NavTab("library", "Biblioteca", Icons.Filled.Bookmark, Icons.Outlined.BookmarkBorder),
)

/** Barra inferior: grafito oscuro; la pestaña activa con un indicador coral compacto. */
@Composable
fun VBottomBar(current: String?, onSelect: (String) -> Unit) {
    Column(Modifier.fillMaxWidth().background(VColors.NavBar)) {
        HorizontalDivider(color = VColors.Divider, thickness = 0.5.dp)
        Row(
            // En tablet las pestañas no se separan de extremo a extremo
            Modifier.readableWidth(640.dp).navigationBarsPadding().height(62.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            navTabs.forEach { tab ->
                val sel = current == tab.route
                Column(
                    Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .clickable(role = androidx.compose.ui.semantics.Role.Tab) { onSelect(tab.route) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        if (sel) {
                            Box(
                                Modifier
                                    .size(width = 48.dp, height = 28.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(VColors.Coral.copy(alpha = 0.16f))
                            )
                        }
                        Icon(
                            if (sel) tab.selected else tab.normal,
                            tab.label,
                            tint = if (sel) VColors.Coral else VColors.Secondary,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        color = if (sel) VColors.Coral else VColors.Secondary,
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
            // ~20 % más bajo que antes (16:11 → 16:8.8)
            .aspectRatio(16f / 8.8f)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, VColors.Divider.copy(alpha = 0.6f), RoundedCornerShape(18.dp))
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
                // Degradado solo en la parte de abajo, donde va el texto
                Brush.verticalGradient(0f to Color.Transparent, 0.38f to Color.Transparent, 0.7f to VColors.Scrim.copy(alpha = 0.72f), 1f to VColors.Scrim.copy(alpha = 0.94f))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = 14.dp, vertical = 12.dp)) {
            if (video.isLive) {
                Row(
                    Modifier.clip(RoundedCornerShape(6.dp)).background(VColors.Red).padding(horizontal = 8.dp, vertical = 3.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(5.dp))
                    Text("EN VIVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                }
                Spacer(Modifier.height(6.dp))
            }
            Text(
                video.title,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                lineHeight = 22.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                joinDot(video.channelName, viewsText(video.views), video.uploaded, if (video.isLive) null else durationText(video.durationSec).ifEmpty { null }),
                color = Color(0xFFD4D8E0),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clickable(enabled = video.channelUrl != null) { video.channelUrl?.let(onChannel) },
            )
            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(VColors.Coral)
                        .clickable(onClick = onPlay)
                        .padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.White, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reproducir", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x2EFFFFFF))
                        .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(50))
                        .clickable { Library.toggleWatchLater(video) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
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
                // Miniatura limpia: duración y barra de progreso coral en el borde inferior
                Thumbnail(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f), rounded = 12.dp, smallBadge = true)
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
                            Text(
                                "$leftTxt · ${(p * 100).toInt()} % visto",
                                color = VColors.Secondary, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                            )
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
                    Box(Modifier.size(width = 30.dp, height = 36.dp), contentAlignment = Alignment.TopCenter) { VideoMenu(v, onChannel) }
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
                            .background(Brush.linearGradient(listOf(c.copy(alpha = 0.55f), VColors.Surface)))
                            .border(1.dp, VColors.Divider, RoundedCornerShape(14.dp))
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
    // Con "quitar animaciones" activado en Android, el carrusel no avanza solo
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val reduceMotion = remember {
        android.provider.Settings.Global.getFloat(ctx.contentResolver, android.provider.Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    androidx.compose.runtime.LaunchedEffect(items.size, reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
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
            Modifier.align(Alignment.BottomEnd).padding(end = 26.dp, bottom = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            items.indices.forEach { i ->
                val on = i == pager.currentPage
                Box(
                    Modifier
                        .size(width = if (on) 16.dp else 6.dp, height = 6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(if (on) VColors.Coral else Color(0x66FFFFFF))
                )
            }
        }
    }
}
