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
import androidx.compose.material.icons.filled.Bolt
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
                                    .size(width = 46.dp, height = 30.dp)
                                    .clip(RoundedCornerShape(15.dp))
                                    .background(VColors.RedGlow.copy(alpha = 0.22f))
                            )
                        }
                        Icon(
                            if (sel) tab.selected else tab.normal,
                            tab.label,
                            tint = if (sel) VColors.Red else VColors.Text,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                    Spacer(Modifier.height(2.dp))
                    Text(
                        tab.label,
                        color = if (sel) VColors.Red else VColors.Secondary,
                        fontSize = 11.sp,
                        fontWeight = if (sel) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/** Título de sección con "Ver más ›" opcional. */
@Composable
fun SectionTitle(title: String, onMore: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 10.dp, top = 14.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = VColors.Text, fontSize = 19.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onMore != null) {
            Row(Modifier.clip(RoundedCornerShape(8.dp)).clickable(onClick = onMore).padding(6.dp),
                verticalAlignment = Alignment.CenterVertically) {
                Text("Ver más", color = VColors.Secondary, fontSize = 13.sp)
                Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VColors.Secondary, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Video destacado grande del Inicio, con botones Reproducir y Guardar. */
@Composable
fun HeroCard(video: VideoItem, onPlay: () -> Unit, onChannel: (String) -> Unit) {
    val later by Library.watchLater.collectAsState()
    val saved = later.any { it.url == video.url }
    Box(
        Modifier
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .fillMaxWidth()
            .aspectRatio(16f / 10.5f)
            .clip(RoundedCornerShape(18.dp))
            .border(1.dp, Brush.linearGradient(listOf(VColors.Red.copy(alpha = 0.7f), Color.Transparent, VColors.Red.copy(alpha = 0.35f))), RoundedCornerShape(18.dp))
            .clickable(onClick = onPlay)
    ) {
        Thumbnail(video, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(0.35f to Color.Transparent, 1f to Color(0xEE000000))
            )
        )
        Column(Modifier.align(Alignment.BottomStart).padding(16.dp)) {
            Text(
                video.title,
                color = Color.White,
                fontSize = 21.sp,
                fontWeight = FontWeight.ExtraBold,
                lineHeight = 25.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                joinDot(video.channelName, viewsText(video.views)),
                color = Color(0xFFDADADF),
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
                        .background(Color.White)
                        .clickable(onClick = onPlay)
                        .padding(horizontal = 18.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.PlayArrow, null, tint = Color.Black, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reproducir", color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                }
                Row(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(Color(0x55FFFFFF))
                        .border(1.dp, Color(0x55FFFFFF), RoundedCornerShape(50))
                        .clickable { Library.toggleWatchLater(video) }
                        .padding(horizontal = 16.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        if (saved) Icons.Filled.BookmarkAdded else Icons.Outlined.BookmarkAdd,
                        null, tint = Color.White, modifier = Modifier.size(19.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(if (saved) "Guardado" else "Guardar", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** Fila horizontal de tarjetas (Tendencias, Historial…). */
@Composable
fun VideoShelf(videos: List<VideoItem>, onVideo: (VideoItem) -> Unit) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        items(videos, key = { it.url }) { v ->
            Column(Modifier.width(210.dp).clip(RoundedCornerShape(12.dp)).clickable { onVideo(v) }) {
                Thumbnail(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f), rounded = 12.dp, smallBadge = true)
                Text(
                    v.title,
                    color = VColors.Text,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 17.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
                )
                Text(
                    joinDot(v.channelName, viewsText(v.views)),
                    color = VColors.Secondary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 2.dp),
                )
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
