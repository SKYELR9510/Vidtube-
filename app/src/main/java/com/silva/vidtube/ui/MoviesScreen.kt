package com.silva.vidtube.ui

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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import coil.compose.AsyncImage
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.Tmdb
import com.silva.vidtube.data.TmdbDetail
import com.silva.vidtube.data.TmdbMovie
import com.silva.vidtube.data.VideoItem
import kotlinx.coroutines.launch

/** Filas de películas de TMDB (se cargan una vez por sesión). */
class MoviesViewModel : ViewModel() {
    val rows = mutableStateMapOf<String, List<TmdbMovie>>()
    var error by mutableStateOf<String?>(null)
    var loading by mutableStateOf(false)
    private var loadedToken: String? = null

    fun load(force: Boolean = false) {
        val token = Tmdb.userToken.value + "|" + Tmdb.hasBuiltInToken()
        if (!Tmdb.isConfigured() || loading) return
        if (!force && loadedToken == token && rows.isNotEmpty()) return
        loadedToken = token
        loading = true
        error = null
        viewModelScope.launch {
            for (row in Tmdb.rows) {
                runCatching { Tmdb.list(row) }
                    .onSuccess { rows[row.id] = it }
                    .onFailure { e -> if (rows.isEmpty()) error = e.message ?: "No se pudo cargar TMDB." }
                // Si la clave es mala no tiene sentido seguir pidiendo filas
                if (error != null && rows.isEmpty()) break
            }
            loading = false
        }
    }
}

/** Encabezado de la categoría Películas: filas de pósters de TMDB. */
fun LazyListScope.movieRows(vm: MoviesViewModel, onMovie: (Long) -> Unit, onSettings: () -> Unit) {
    if (!Tmdb.isConfigured()) {
        item("tmdb_setup") {
            Row(
                Modifier.padding(horizontal = 12.dp, vertical = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp)).background(VColors.Card)
                    .border(1.dp, VColors.Divider, RoundedCornerShape(16.dp))
                    .clickable(onClick = onSettings).padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Movie, null, tint = VColors.Coral, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("Activa los pósters y fichas de películas", color = VColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Pega tu clave gratuita de TMDB en Configuración.", color = VColors.Secondary, fontSize = 12.sp)
                }
            }
        }
        return
    }
    val err = vm.error
    if (err != null && vm.rows.isEmpty()) {
        item("tmdb_error") {
            Text(
                "$err Toca para revisar la clave.",
                color = VColors.Secondary, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(16.dp),
            )
        }
        return
    }
    if (vm.rows.isEmpty() && vm.loading) {
        item("tmdb_loading") {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VColors.Coral, strokeWidth = 3.dp)
            }
        }
    }
    for (row in Tmdb.rows) {
        val list = vm.rows[row.id].orEmpty()
        if (list.isEmpty()) continue
        item("tmdb_t_" + row.id) { SectionTitle(row.title) }
        item("tmdb_r_" + row.id) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { m -> PosterCard(m) { onMovie(m.id) } }
            }
        }
    }
    if (vm.rows.isNotEmpty()) {
        item("tmdb_yt_title") { SectionTitle("Películas completas gratis en YouTube") }
    }
}

@Composable
fun PosterCard(m: TmdbMovie, width: androidx.compose.ui.unit.Dp = 116.dp, onClick: () -> Unit) {
    Column(Modifier.width(width).clip(RoundedCornerShape(10.dp)).clickable(onClickLabel = "Ver ${m.title}", onClick = onClick)) {
        Box {
            AsyncImage(
                model = m.poster, contentDescription = m.title, contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp)).background(VColors.Card),
            )
            if (m.rating > 0) RatingBadge(m.rating, Modifier.align(Alignment.TopStart).padding(6.dp))
        }
        Text(
            m.title, color = VColors.Text, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 6.dp, start = 2.dp, end = 2.dp),
        )
        m.year?.let { Text(it.toString(), color = VColors.Secondary, fontSize = 11.sp, modifier = Modifier.padding(start = 2.dp)) }
    }
}

@Composable
private fun RatingBadge(rating: Double, modifier: Modifier = Modifier) {
    Row(
        modifier.clip(RoundedCornerShape(6.dp)).background(Color(0xCC0B0C10)).padding(horizontal = 5.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Filled.Star, null, tint = Color(0xFFFFC94D), modifier = Modifier.size(11.dp))
        Spacer(Modifier.width(3.dp))
        Text(String.format(java.util.Locale.US, "%.1f", rating), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

/** Ficha de una película: datos de TMDB + tráiler y película completa desde YouTube. */
@Composable
fun MovieScreen(id: Long, onBack: () -> Unit, onVideo: (VideoItem) -> Unit, onMovie: (Long) -> Unit) {
    var detail by remember(id) { mutableStateOf<TmdbDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var full by remember(id) { mutableStateOf<List<VideoItem>?>(null) }
    var similar by remember(id) { mutableStateOf<List<TmdbMovie>>(emptyList()) }
    var attempt by remember(id) { mutableStateOf(0) }

    LaunchedEffect(id, attempt) {
        error = null
        runCatching { Tmdb.detail(id) }
            .onSuccess { d ->
                detail = d
                full = runCatching { Repository.fullMovie(d.movie, d.runtimeMin) }.getOrDefault(emptyList())
            }
            .onFailure { error = it.message ?: "No se pudo cargar la película." }
    }
    LaunchedEffect(id) {
        similar = runCatching {
            Tmdb.list(com.silva.vidtube.data.TmdbRow("sim", "", "movie/$id/recommendations"))
        }.getOrDefault(emptyList()).take(15)
    }

    Box(Modifier.fillMaxSize().background(VColors.Background)) {
        val d = detail
        when {
            error != null && d == null -> ErrorView(error!!, onRetry = { attempt++ })
            d == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VColors.Coral, strokeWidth = 3.dp)
            }
            else -> LazyColumn(Modifier.fillMaxSize()) {
                item("hero") { MovieHero(d) }
                item("actions") {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        val first = full?.firstOrNull()
                        PillButton(
                            if (first != null) "Ver película" else if (full == null) "Buscando…" else "No disponible gratis",
                            Icons.Filled.PlayArrow, primary = true, enabled = first != null,
                            modifier = Modifier.weight(1f),
                        ) { first?.let(onVideo) }
                        val key = d.trailerKey
                        PillButton("Tráiler", Icons.Outlined.Movie, primary = false, enabled = key != null, modifier = Modifier.weight(1f)) {
                            if (key != null) onVideo(trailerItem(d.movie, key))
                        }
                    }
                }
                if (d.movie.overview.isNotBlank()) {
                    item("overview") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                            if (d.tagline.isNotBlank()) {
                                Text(d.tagline, color = VColors.Coral2, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                Spacer(Modifier.height(6.dp))
                            }
                            Text(d.movie.overview, color = VColors.Secondary, fontSize = 14.sp, lineHeight = 20.sp)
                            d.director?.let {
                                Spacer(Modifier.height(8.dp))
                                Text("Dirección: $it", color = VColors.Text, fontSize = 13.sp)
                            }
                        }
                    }
                }
                if (d.cast.isNotEmpty()) {
                    item("cast_t") { SectionTitle("Reparto") }
                    item("cast") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(d.cast, key = { it.name + it.character }) { p ->
                                Column(Modifier.width(76.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    AsyncImage(
                                        model = p.photo, contentDescription = null, contentScale = ContentScale.Crop,
                                        modifier = Modifier.size(64.dp).clip(CircleShape).background(VColors.Card),
                                    )
                                    Text(p.name, color = VColors.Text, fontSize = 11.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(top = 4.dp), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                    if (p.character.isNotBlank()) Text(p.character, color = VColors.Muted, fontSize = 10.sp, maxLines = 1,
                                        overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                                }
                            }
                        }
                    }
                }
                item("full_t") { SectionTitle("En YouTube") }
                val f = full
                when {
                    f == null -> item("full_loading") {
                        Text("Buscando la película completa en YouTube…", color = VColors.Secondary, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    f.isEmpty() -> item("full_none") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(
                                "No encontramos esta película completa y gratis en YouTube. Puedes ver el tráiler " +
                                    "o buscarla tú mismo.",
                                color = VColors.Secondary, fontSize = 13.sp,
                            )
                        }
                    }
                    else -> items(f, key = { "full:" + it.url }) { v ->
                        VideoCard(v, onClick = { onVideo(v) }, onChannel = {}, reason = "Duración completa · ${v.channelName}")
                    }
                }
                if (similar.isNotEmpty()) {
                    item("sim_t") { SectionTitle("Si te gustó, mira también") }
                    item("sim") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(similar, key = { it.id }) { m -> PosterCard(m) { onMovie(m.id) } }
                        }
                    }
                }
                item("credit") {
                    Text(
                        "Datos e imágenes de películas: TMDB. Esta app usa la API de TMDB pero no está avalada ni certificada por TMDB.",
                        color = VColors.Muted, fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 20.dp).navigationBarsPadding(),
                    )
                }
            }
        }
        // Volver
        Box(
            Modifier.statusBarsPadding().padding(8.dp).size(42.dp).clip(CircleShape).background(Color(0x990B0C10))
                .clickable(onClickLabel = "Volver", onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White) }
    }
}

@Composable
private fun MovieHero(d: TmdbDetail) {
    val m = d.movie
    Box(Modifier.fillMaxWidth()) {
        AsyncImage(
            model = m.backdrop ?: m.poster, contentDescription = null, contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 10f).background(VColors.Card),
        )
        Box(
            Modifier.matchParentSize().background(
                Brush.verticalGradient(0f to Color(0x660B0C10), 0.55f to Color.Transparent, 1f to VColors.Background)
            )
        )
    }
    Row(Modifier.padding(horizontal = 16.dp).padding(top = 0.dp), verticalAlignment = Alignment.Bottom) {
        AsyncImage(
            model = m.poster, contentDescription = m.title, contentScale = ContentScale.Crop,
            modifier = Modifier.width(100.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))
                .border(1.dp, VColors.Divider, RoundedCornerShape(10.dp)).background(VColors.Card),
        )
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f).heightIn(min = 60.dp)) {
            Text(m.title, color = VColors.Text, fontSize = 22.sp, fontWeight = FontWeight.Bold, lineHeight = 26.sp)
            if (m.originalTitle.isNotBlank() && Tmdb.norm(m.originalTitle) != Tmdb.norm(m.title)) {
                Text(m.originalTitle, color = VColors.Muted, fontSize = 12.sp)
            }
            Spacer(Modifier.height(6.dp))
            val meta = listOfNotNull(
                m.year?.toString(),
                d.runtimeMin.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min" },
                d.genres.take(2).joinToString(", ").ifBlank { null },
            ).joinToString(" · ")
            Text(meta, color = VColors.Secondary, fontSize = 13.sp)
            if (m.rating > 0) {
                Spacer(Modifier.height(6.dp))
                RatingBadge(m.rating)
            }
        }
    }
}

@Composable
private fun PillButton(
    text: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg = when {
        !enabled -> VColors.Chip
        primary -> VColors.Coral
        else -> VColors.Raise
    }
    Row(
        modifier.height(46.dp).clip(RoundedCornerShape(23.dp)).background(bg)
            .then(if (!primary || !enabled) Modifier.border(1.dp, VColors.Divider, RoundedCornerShape(23.dp)) else Modifier)
            .clickable(enabled = enabled, onClick = onClick).padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (primary && enabled) Color.White else if (enabled) VColors.Text else VColors.Muted
        Icon(icon, null, tint = fg, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, color = fg, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Video de YouTube del tráiler. */
private fun trailerItem(m: TmdbMovie, key: String) = VideoItem(
    url = "https://www.youtube.com/watch?v=$key",
    title = "${m.title} · Tráiler",
    thumbnail = "https://i.ytimg.com/vi/$key/hqdefault.jpg",
    channelName = "Tráiler",
    channelUrl = null,
    channelAvatar = null,
    durationSec = 0,
    views = -1,
    uploaded = null,
    isLive = false,
)
