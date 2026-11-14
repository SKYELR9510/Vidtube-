package com.silva.vidtube.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.VideoLibrary
import androidx.compose.animation.core.animateFloat
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.silva.vidtube.data.ChannelItem
import com.silva.vidtube.data.Library
import com.silva.vidtube.data.VideoItem

/** Barra superior simple (sin Scaffold anidado). */
@Composable
fun VTopBar(
    title: String? = null,
    onBack: (() -> Unit)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(VColors.Background)
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = VColors.Text)
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        if (leading != null) leading()
        if (title != null) {
            Text(
                title,
                color = VColors.Text,
                fontSize = 21.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        actions()
    }
}

@Composable
fun Avatar(url: String?, name: String, size: Dp, modifier: Modifier = Modifier) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(VColors.Chip),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                name.take(1).uppercase(),
                color = VColors.Text,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
fun Thumbnail(video: VideoItem, modifier: Modifier = Modifier, rounded: Dp = 0.dp, smallBadge: Boolean = false) {
    Box(
        modifier
            .clip(RoundedCornerShape(rounded))
            .background(VColors.Chip)
    ) {
        AsyncImage(
            model = video.thumbnail,
            contentDescription = video.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
        val badge = if (video.isLive) "EN VIVO" else durationText(video.durationSec)
        val progress = progressOf(video)
        if (badge.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 6.dp, bottom = if (progress != null) 9.dp else 6.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(if (video.isLive) VColors.Red else Color(0xD108090B))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (video.isLive) {
                    Box(Modifier.size(5.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(4.dp))
                }
                Text(badge, color = Color.White, fontSize = if (smallBadge) 11.sp else 12.sp, fontWeight = FontWeight.Bold)
            }
        }
        // Barra roja con lo que ya viste del video
        if (progress != null) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color(0x55FFFFFF))) {
                Box(Modifier.fillMaxWidth(progress).height(3.dp).background(VColors.Red))
            }
        }
    }
}

/** Parte del video que ya se vio (0..1), o null si no hay posición guardada. */
fun progressOf(video: VideoItem): Float? {
    if (video.isLive || video.durationSec <= 0) return null
    val pos = Library.position(video.url)
    if (pos <= 0) return null
    return (pos / 1000f / video.durationSec).coerceIn(0.02f, 1f)
}

/** Tarjeta grande de video. */
@Composable
fun VideoCard(
    video: VideoItem,
    onClick: () -> Unit,
    onChannel: ((String) -> Unit)?,
    showChannelAvatar: Boolean = true,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Thumbnail(
            video,
            Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(16f / 9f),
            rounded = 14.dp,
        )
        Row(Modifier.padding(start = 14.dp, top = 10.dp, end = 0.dp, bottom = 18.dp)) {
            if (showChannelAvatar) {
                Avatar(
                    video.channelAvatar,
                    video.channelName,
                    36.dp,
                    Modifier.clickable(enabled = onChannel != null && video.channelUrl != null) {
                        video.channelUrl?.let { onChannel?.invoke(it) }
                    },
                )
                Spacer(Modifier.width(12.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    video.title,
                    color = VColors.Text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    lineHeight = 20.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    joinDot(
                        if (showChannelAvatar) video.channelName else null,
                        viewsText(video.views),
                        video.uploaded,
                    ),
                    color = VColors.Secondary,
                    fontSize = 12.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            VideoMenu(video, onChannel)
        }
    }
}

/** Fila compacta (historial, ver más tarde). */
@Composable
fun VideoRow(
    video: VideoItem,
    onClick: () -> Unit,
    onChannel: ((String) -> Unit)?,
    onRemove: (() -> Unit)? = null,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 12.dp, top = 6.dp, bottom = 6.dp),
    ) {
        Thumbnail(video, Modifier.width(160.dp).aspectRatio(16f / 9f), rounded = 8.dp, smallBadge = true)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                color = VColors.Text,
                fontSize = 14.sp,
                lineHeight = 18.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(3.dp))
            Text(
                joinDot(video.channelName, viewsText(video.views)),
                color = VColors.Secondary,
                fontSize = 12.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        VideoMenu(video, onChannel, onRemove)
    }
}

@Composable
fun VideoMenu(video: VideoItem, onChannel: ((String) -> Unit)?, onRemove: (() -> Unit)? = null) {
    val context = LocalContext.current
    var open by remember { mutableStateOf(false) }
    val watchLater by Library.watchLater.collectAsState()
    val saved = watchLater.any { it.url == video.url }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Filled.MoreVert, "Más opciones", tint = VColors.Text, modifier = Modifier.size(20.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(
                text = { Text(if (saved) "Quitar de Mi lista" else "Guardar en Mi lista") },
                onClick = {
                    open = false
                    val added = Library.toggleWatchLater(video)
                    Toast.makeText(
                        context,
                        if (added) "Guardado en Mi lista (Ver más tarde)" else "Quitado de Mi lista",
                        Toast.LENGTH_SHORT
                    ).show()
                },
            )
            DropdownMenuItem(
                text = { Text(if (Library.isLiked(video.url)) "Quitar de Me gusta" else "Agregar a Me gusta") },
                onClick = {
                    open = false
                    val added = Library.toggleLike(video)
                    Toast.makeText(context, if (added) "Agregado a Videos que me gustan" else "Quitado de Videos que me gustan", Toast.LENGTH_SHORT).show()
                },
            )
            DropdownMenuItem(
                text = { Text("Compartir") },
                onClick = {
                    open = false
                    shareUrl(context, video.title, video.url)
                },
            )
            if (onChannel != null && video.channelUrl != null) {
                DropdownMenuItem(
                    text = { Text("Ir al canal") },
                    onClick = {
                        open = false
                        onChannel(video.channelUrl)
                    },
                )
            }
            if (onRemove != null) {
                DropdownMenuItem(
                    text = { Text("Quitar de la lista") },
                    onClick = {
                        open = false
                        onRemove()
                    },
                )
            }
        }
    }
}

@Composable
fun SubscribeButton(channel: ChannelItem, modifier: Modifier = Modifier) {
    val subs by Library.subscriptions.collectAsState()
    val subscribed = remember(subs, channel.url) { Library.isSubscribed(channel.url) }
    Button(
        onClick = { Library.toggleSubscription(channel) },
        colors = ButtonDefaults.buttonColors(
            containerColor = if (subscribed) VColors.Chip else VColors.Red,
            contentColor = if (subscribed) VColors.Text else Color.White,
        ),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        modifier = modifier.height(36.dp),
    ) {
        Text(
            if (subscribed) "Suscrito" else "Suscribirse",
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
fun ChannelResultRow(channel: ChannelItem, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Avatar(channel.avatar, channel.name, 72.dp)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(channel.name, color = VColors.Text, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subs = subscribersText(channel.subscribers)
            if (subs.isNotEmpty()) Text(subs, color = VColors.Secondary, fontSize = 12.sp)
        }
        Spacer(Modifier.width(8.dp))
        SubscribeButton(channel)
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = VColors.Red, strokeWidth = 3.dp, modifier = Modifier.size(36.dp))
    }
}

@Composable
fun ErrorView(message: String, onRetry: (() -> Unit)?, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        StateIcon(Icons.Outlined.Info)
        Text("Algo salió mal", color = VColors.Text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(message, color = VColors.Secondary, textAlign = TextAlign.Center, fontSize = 14.sp)
        if (onRetry != null) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(containerColor = VColors.Red, contentColor = Color.White),
            ) { Text("Reintentar", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun EmptyView(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        StateIcon(Icons.Outlined.VideoLibrary)
        Text(title, color = VColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(subtitle, color = VColors.Secondary, fontSize = 14.sp, textAlign = TextAlign.Center)
    }
}

/** Píldora gris de acción (Compartir, Guardar, etc.). */
@Composable
fun ActionChip(text: String, icon: androidx.compose.ui.graphics.vector.ImageVector?, onClick: () -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(VColors.Chip)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = VColors.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, color = VColors.Text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

/** Ícono redondo de los estados vacíos y de error. */
@Composable
fun StateIcon(icon: androidx.compose.ui.graphics.vector.ImageVector) {
    Box(
        Modifier.padding(bottom = 14.dp).size(68.dp).clip(CircleShape).background(VColors.Red.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) { Icon(icon, null, tint = VColors.Red, modifier = Modifier.size(32.dp)) }
}

/** Bloques grises animados mientras carga la lista (en lugar del círculo girando). */
@Composable
fun SkeletonFeed(cards: Int = 3) {
    val t = androidx.compose.animation.core.rememberInfiniteTransition(label = "sk")
    val a by t.animateFloat(
        0.45f, 0.9f,
        androidx.compose.animation.core.infiniteRepeatable(
            androidx.compose.animation.core.tween(800),
            androidx.compose.animation.core.RepeatMode.Reverse,
        ),
        label = "a",
    )
    val c = VColors.Chip.copy(alpha = a)
    Column(Modifier.fillMaxWidth()) {
        repeat(cards) {
            Box(Modifier.padding(horizontal = 12.dp).fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(14.dp)).background(c))
            Row(Modifier.padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 22.dp)) {
                Box(Modifier.size(36.dp).clip(CircleShape).background(c))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.9f).height(14.dp).clip(RoundedCornerShape(6.dp)).background(c))
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.fillMaxWidth(0.55f).height(12.dp).clip(RoundedCornerShape(6.dp)).background(c))
                }
            }
        }
    }
}
