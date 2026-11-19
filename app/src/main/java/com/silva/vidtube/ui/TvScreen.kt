package com.silva.vidtube.ui

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.LiveTv
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.TheaterComedy
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.silva.vidtube.data.LiveCheck
import com.silva.vidtube.data.PanelChannel
import com.silva.vidtube.data.PanelLinks
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.VideoItem
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/** Ícono de cada categoría (las mismas del panel). */
private fun categoryIcon(c: String): ImageVector = when (c) {
    "Noticias" -> Icons.Outlined.Newspaper
    "Deportes" -> Icons.Outlined.SportsSoccer
    "Música" -> Icons.Outlined.MusicNote
    "Infantil" -> Icons.Outlined.ChildCare
    "Películas" -> Icons.Outlined.Movie
    "Entretenimiento" -> Icons.Outlined.TheaterComedy
    else -> Icons.Outlined.Tv
}
private val categoryOrder = listOf("Noticias", "Deportes", "Música", "Infantil", "Películas", "Entretenimiento", "General")

/** Favoritos de TV (solo en este celular). */
private object TvFavorites {
    private fun prefs(c: Context) = c.getSharedPreferences("vidtube_tv", Context.MODE_PRIVATE)
    fun load(c: Context): Set<String> = prefs(c).getStringSet("favs", emptySet()).orEmpty().toSet()
    fun save(c: Context, s: Set<String>) { prefs(c).edit().putStringSet("favs", s).apply() }
}

/** Contenido de TV abierta: filtros y cuadrícula de canales. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun TvContent(onVideo: (VideoItem) -> Unit, query: String = "") {
    val panel by PanelLinks.channels.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var refreshing by remember { mutableStateOf(false) }
    var filter by remember { mutableStateOf("Todos") }
    var favs by remember { mutableStateOf(TvFavorites.load(context)) }
    // Transmisión de YouTube encontrada por canal, y si cada canal está en vivo (sin dato = revisando)
    val ytLive = remember { mutableStateMapOf<String, VideoItem?>() }
    val live = remember { mutableStateMapOf<String, Boolean>() }

    suspend fun checkAll() {
        val gate = Semaphore(4)
        coroutineScope {
            PanelLinks.channels.value.forEach { c ->
                launch {
                    gate.withPermit {
                        val yt = c.youtube?.let { Repository.channelLive(it) }
                        if (c.youtube != null) ytLive[c.youtube] = yt
                        live[c.key] = yt != null ||
                            (listOf(c.url) + c.backups).filter { it.isNotBlank() }.any { LiveCheck.isUp(it) }
                    }
                }
            }
        }
    }
    LaunchedEffect(Unit) { PanelLinks.refresh(); checkAll() }

    fun play(c: PanelChannel) {
        val yt = c.youtube?.let { ytLive[it] }
        when {
            yt != null -> onVideo(yt)
            c.url.isNotEmpty() -> {
                com.silva.vidtube.player.NowPlaying.alternates[c.url] = c.backups
                onVideo(
                    VideoItem(
                        url = c.url, title = c.name, thumbnail = c.cover ?: c.logo, channelName = c.name, channelUrl = null,
                        channelAvatar = c.logo, durationSec = 0, views = -1, uploaded = null, isLive = true,
                    )
                )
            }
            else -> android.widget.Toast.makeText(context, "${c.name} no está transmitiendo ahora.", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    // Filtros: Todos, Favoritos (si hay), Nacionales (si hay) y las categorías que existan
    val filters = buildList {
        add("Todos")
        if (panel.any { it.key in favs }) add("Favoritos")
        if (panel.any { it.national }) add("Nacionales")
        categoryOrder.filter { cat -> panel.any { it.category == cat } }.forEach { add(it) }
    }
    if (filter !in filters) filter = "Todos"
    val q = com.silva.vidtube.data.Tmdb.norm(query)
    val shown = panel.filter { c ->
        when (filter) {
            "Todos" -> true
            "Favoritos" -> c.key in favs
            "Nacionales" -> c.national
            else -> c.category == filter
        }
    }.filter { q.isBlank() || com.silva.vidtube.data.Tmdb.norm(it.name).contains(q) }
        // Los que están en vivo primero; dentro de cada grupo, el orden del panel
        .sortedBy { if (live[it.key] == false) 1 else 0 }

    Column(Modifier.fillMaxSize()) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(bottom = 14.dp),
        ) {
            items(filters, key = { it }) { f -> TvChip(f, f == filter) { filter = f } }
        }
        PullToRefreshBox(
            isRefreshing = refreshing,
            onRefresh = {
                refreshing = true
                scope.launch { PanelLinks.refresh(force = true); live.clear(); checkAll(); refreshing = false }
            },
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(160.dp),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                if (shown.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                        EmptyView(
                            if (panel.isEmpty()) "Aún no hay canales" else "Nada por aquí",
                            if (panel.isEmpty()) "Pronto habrá canales disponibles." else "Prueba con otro filtro o búsqueda.",
                        )
                    }
                }
                items(shown, key = { it.key + it.url }) { c ->
                    TvCard(
                        c, live[c.key], c.key in favs,
                        onFav = {
                            favs = if (c.key in favs) favs - c.key else favs + c.key
                            TvFavorites.save(context, favs)
                        },
                    ) { play(c) }
                }
            }
        }
    }
}

/** Pantalla TV abierta (desde Inicio y Explorar). */
@Composable
fun TvScreen(onBack: () -> Unit, onVideo: (VideoItem) -> Unit) {
    var searching by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    BackHandler(enabled = searching) { searching = false; query = "" }
    Box(Modifier.fillMaxSize().background(VColors.Background)) {
        // Brillo rojo de la esquina superior derecha
        Canvas(Modifier.fillMaxWidth().height(260.dp)) {
            drawRect(
                Brush.radialGradient(
                    listOf(VColors.Coral.copy(alpha = 0.30f), Color.Transparent),
                    center = Offset(size.width * 0.98f, 0f), radius = size.width * 0.8f,
                )
            )
        }
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 10.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = { if (searching) { searching = false; query = "" } else onBack() }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = VColors.Text)
                }
                if (searching) {
                    Row(
                        Modifier.weight(1f).height(46.dp).clip(RoundedCornerShape(23.dp)).background(VColors.Raise)
                            .border(1.dp, VColors.Divider, RoundedCornerShape(23.dp)).padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        BasicTextField(
                            value = query, onValueChange = { query = it }, singleLine = true,
                            textStyle = TextStyle(color = VColors.Text, fontSize = 16.sp), cursorBrush = SolidColor(VColors.Coral),
                            modifier = Modifier.weight(1f).focusRequester(focus),
                            decorationBox = { inner ->
                                if (query.isEmpty()) Text("Buscar canal", color = VColors.Muted, fontSize = 16.sp)
                                inner()
                            },
                        )
                        if (query.isNotEmpty()) {
                            Icon(
                                Icons.Outlined.Close, "Borrar", tint = VColors.Muted,
                                modifier = Modifier.size(20.dp).clickable { query = "" },
                            )
                        }
                    }
                    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
                } else {
                    Column(Modifier.weight(1f).padding(start = 6.dp)) {
                        Text("TV abierta", color = VColors.Text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                        Text("Canales en directo", color = VColors.Secondary, fontSize = 15.sp)
                    }
                    IconButton(onClick = { searching = true }) {
                        Icon(Icons.Outlined.Search, "Buscar canal", tint = VColors.Text, modifier = Modifier.size(26.dp))
                    }
                }
            }
            TvContent(onVideo, query)
        }
    }
}

@Composable
private fun TvChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val fg = if (selected) Color.White else VColors.Text
    Row(
        Modifier.height(44.dp).clip(shape)
            .then(
                if (selected) Modifier.background(Brush.horizontalGradient(listOf(VColors.Coral, VColors.Coral2)))
                else Modifier.background(VColors.Card).border(1.dp, VColors.Divider, shape)
            )
            .clickable(onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when (label) {
            "Nacionales" -> PeruFlag(Modifier.size(20.dp))
            "Favoritos" -> Icon(Icons.Filled.Favorite, null, tint = if (selected) Color.White else VColors.Coral, modifier = Modifier.size(20.dp))
            "Todos" -> Icon(Icons.Outlined.LiveTv, null, tint = fg, modifier = Modifier.size(20.dp))
            else -> Icon(categoryIcon(label), null, tint = fg, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
    }
}

/** Bandera del Perú en círculo (rojo, blanco, rojo). */
@Composable
private fun PeruFlag(modifier: Modifier) {
    Canvas(modifier.clip(CircleShape)) {
        val w = size.width / 3
        val red = Color(0xFFD91023)
        drawRect(red, size = androidx.compose.ui.geometry.Size(w, size.height))
        drawRect(Color.White, topLeft = Offset(w, 0f), size = androidx.compose.ui.geometry.Size(w, size.height))
        drawRect(red, topLeft = Offset(2 * w, 0f), size = androidx.compose.ui.geometry.Size(w + 1f, size.height))
    }
}

/**
 * Tarjeta de canal: portada (o el logo sobre un degradado de su color), «EN VIVO» solo si la
 * señal está transmitiendo, corazón de favorito, nombre, categoría y botón de reproducir.
 */
@Composable
private fun TvCard(c: PanelChannel, isLive: Boolean?, fav: Boolean, onFav: () -> Unit, onPlay: () -> Unit) {
    val context = LocalContext.current
    val off = isLive == false
    val tint by produceState<Color?>(null, c.logo, c.cover) {
        value = if (c.cover == null) ambientColorOf(context, c.logo) else null
    }
    val base = tint ?: VColors.Raise
    Column(
        Modifier.clip(RoundedCornerShape(18.dp)).background(VColors.Card)
            .border(1.dp, VColors.Divider, RoundedCornerShape(18.dp))
            .clickable(onClickLabel = "Ver ${c.name}", onClick = onPlay)
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).alpha(if (off) 0.55f else 1f)) {
            if (c.cover != null) {
                AsyncImage(model = c.cover, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            } else {
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(listOf(base.copy(alpha = 0.95f), base.copy(alpha = 0.35f), Color(0xFF0B0C10)))
                    )
                )
                if (c.logo != null) {
                    AsyncImage(
                        model = c.logo, contentDescription = c.name, contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxSize().padding(horizontal = 22.dp, vertical = 18.dp),
                    )
                } else {
                    Text(
                        c.name, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.align(Alignment.Center),
                    )
                }
            }
            // «EN VIVO» solo cuando la señal (o su YouTube oficial) está transmitiendo
            if (isLive == true) {
                Row(
                    Modifier.align(Alignment.TopStart).padding(10.dp).clip(RoundedCornerShape(50))
                        .background(VColors.Coral).padding(horizontal = 9.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(6.dp).clip(CircleShape).background(Color.White))
                    Spacer(Modifier.width(5.dp))
                    Text("EN VIVO", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
            Icon(
                if (fav) Icons.Filled.Favorite else Icons.Outlined.FavoriteBorder,
                if (fav) "Quitar de favoritos" else "Agregar a favoritos",
                tint = if (fav) VColors.Coral else Color.White,
                modifier = Modifier.align(Alignment.TopEnd).padding(6.dp).size(36.dp).clip(CircleShape)
                    .clickable(onClick = onFav).padding(6.dp),
            )
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 10.dp, top = 10.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(c.name, color = VColors.Text, fontSize = 16.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(3.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(categoryIcon(c.category), null, tint = VColors.Secondary, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(5.dp))
                    Text(
                        if (off) "Sin señal ahora" else c.category,
                        color = if (off) VColors.Muted else VColors.Secondary, fontSize = 13.sp,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box(
                Modifier.size(38.dp).clip(CircleShape)
                    .border(2.dp, Brush.linearGradient(listOf(VColors.Coral, VColors.Coral2)), CircleShape),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.PlayArrow, "Reproducir", tint = VColors.Coral, modifier = Modifier.size(22.dp)) }
        }
    }
}
