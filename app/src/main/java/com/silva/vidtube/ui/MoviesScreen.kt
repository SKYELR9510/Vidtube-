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
    private val loadedToken = HashMap<Boolean, String>()

    /** Carga las filas de películas (tv = false) o de series (tv = true). */
    fun load(tv: Boolean = false, force: Boolean = false) {
        val token = Tmdb.userToken.value + "|" + Tmdb.hasBuiltInToken()
        if (!Tmdb.isConfigured() || loading) return
        val list = if (tv) Tmdb.tvRows else Tmdb.rows
        val have = list.any { rows.containsKey(it.id) }
        if (!force && loadedToken[tv] == token && have) return
        loadedToken[tv] = token
        loading = true
        error = null
        viewModelScope.launch {
            var any = false
            for (row in list) {
                // Dos páginas por fila: unas 40 películas o series en cada una
                runCatching { (Tmdb.list(row) + runCatching { Tmdb.list(row, 2) }.getOrDefault(emptyList())).distinctBy { it.id } }
                    .onSuccess { rows[row.id] = it; any = true }
                    .onFailure { e -> if (!any) error = e.message ?: "No se pudo cargar TMDB." }
                // Si la clave es mala no tiene sentido seguir pidiendo filas
                if (error != null && !any) break
            }
            if (any) error = null
            loading = false
        }
    }
}

/** Encabezado de Películas o Series: filas de pósters de TMDB. */
fun LazyListScope.movieRows(
    vm: MoviesViewModel,
    onMovie: (Long, Boolean) -> Unit,
    onSettings: () -> Unit,
    tv: Boolean = false,
) {
    val tmdbRows = if (tv) Tmdb.tvRows else Tmdb.rows
    val hasRows = tmdbRows.any { !vm.rows[it.id].isNullOrEmpty() }
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
                    Text(if (tv) "Activa los pósters y fichas de series" else "Activa los pósters y fichas de películas", color = VColors.Text, fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                    Text("Pega tu clave gratuita de TMDB en Configuración.", color = VColors.Secondary, fontSize = 12.sp)
                }
            }
        }
        return
    }
    val err = vm.error
    if (err != null && !hasRows) {
        item("tmdb_error") {
            Text(
                "$err Toca para revisar la clave.",
                color = VColors.Secondary, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().clickable(onClick = onSettings).padding(16.dp),
            )
        }
        return
    }
    if (!hasRows && vm.loading) {
        item("tmdb_loading") {
            Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = VColors.Coral, strokeWidth = 3.dp)
            }
        }
    }
    for (row in tmdbRows) {
        val list = vm.rows[row.id].orEmpty()
        if (list.isEmpty()) continue
        item("tmdb_t_" + row.id) { SectionTitle(row.title) }
        item("tmdb_r_" + row.id) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(list, key = { it.id }) { m -> PosterCard(m) { onMovie(m.id, m.tv) } }
            }
        }
    }
    if (hasRows) {
        item("tmdb_yt_title") {
            SectionTitle(if (tv) "Series completas gratis en YouTube" else "Películas completas gratis en YouTube")
        }
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

/** Ficha de una película o serie: datos de TMDB + tráiler y lo que haya gratis en YouTube. */
@Composable
fun MovieScreen(
    id: Long,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onMovie: (Long, Boolean) -> Unit,
    tv: Boolean = false,
) {
    var detail by remember(id) { mutableStateOf<TmdbDetail?>(null) }
    var error by remember(id) { mutableStateOf<String?>(null) }
    var full by remember(id) { mutableStateOf<List<VideoItem>?>(null) }
    // Coinciden en todo pero el canal no está verificado: se muestran aparte
    var others by remember(id) { mutableStateOf<List<VideoItem>>(emptyList()) }
    var similar by remember(id) { mutableStateOf<List<TmdbMovie>>(emptyList()) }
    var attempt by remember(id) { mutableStateOf(0) }
    // Series: temporada elegida (los capítulos se buscan por temporada)
    var season by remember(id) { mutableStateOf(1) }

    LaunchedEffect(id, attempt) {
        error = null
        runCatching { Tmdb.detail(id, tv) }
            .onSuccess { d ->
                detail = d
                if (!tv) {
                    val m = runCatching { Repository.fullMovie(d) }.getOrNull()
                    others = m?.others.orEmpty()
                    full = m?.official.orEmpty()
                }
                else season = d.seasons.firstOrNull()?.number ?: 1
            }
            .onFailure { error = it.message ?: if (tv) "No se pudo cargar la serie." else "No se pudo cargar la película." }
    }
    LaunchedEffect(id, season, detail) {
        val d = detail ?: return@LaunchedEffect
        if (!tv) return@LaunchedEffect
        full = null
        others = emptyList()
        val eps = runCatching { Repository.seriesEpisodes(d.movie, season, d.runtimeMin) }.getOrDefault(emptyList())
        others = eps.filter { !it.verified }
        full = eps.filter { it.verified }
    }
    LaunchedEffect(id) {
        similar = runCatching { Tmdb.list(Tmdb.recommendationsRow(id, tv)) }.getOrDefault(emptyList()).take(15)
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
                        val other = others.firstOrNull()
                        // Si no está gratis en YouTube, el botón lleva a la plataforma donde sí está
                        val w = d.watch
                        val platform = w?.free?.firstOrNull() ?: w?.subscription?.firstOrNull()
                        val platformUrl = platform?.let { Tmdb.providerUrl(it, d.movie.title, w?.link) }
                        val context = androidx.compose.ui.platform.LocalContext.current
                        PillButton(
                            when {
                                first != null -> if (tv) "Ver capítulo" else "Ver película"
                                other != null && full != null && platformUrl == null -> if (tv) "Ver capítulo" else "Ver película"
                                platform != null && platformUrl != null && full != null ->
                                    (if (w?.free?.isNotEmpty() == true) "Gratis en " else "Ver en ") + platform.name
                                !Tmdb.mayBeFreeOnYoutube(d.movie) -> "Aún no está gratis"
                                Tmdb.isMajorStudio(d) -> "No está gratis"
                                full == null -> "Buscando…"
                                else -> "No disponible gratis"
                            },
                            Icons.Filled.PlayArrow, primary = true,
                            enabled = first != null || (full != null && (platformUrl != null || other != null)),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (first != null) onVideo(first)
                            else if (platformUrl != null) openLink(context, platformUrl)
                            else if (other != null) onVideo(other)
                        }
                        val key = d.trailerKey
                        PillButton("Tráiler", Icons.Outlined.Movie, primary = false, enabled = key != null, modifier = Modifier.weight(1f)) {
                            if (key != null) onVideo(trailerItem(d.movie, key))
                        }
                    }
                }
                d.watch?.let { w ->
                    item("watch") { WhereToWatch(w, d.movie.title) }
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
                                Text((if (tv) "Creada por: " else "Dirección: ") + it, color = VColors.Text, fontSize = 13.sp)
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
                item("full_t") { SectionTitle(if (tv) "Capítulos en YouTube" else "En YouTube") }
                if (tv && d.seasons.size > 1) {
                    item("seasons") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(d.seasons, key = { it.number }) { s ->
                                CategoryChip(
                                    "T${s.number}" + (if (s.episodes > 0) " · ${s.episodes} cap." else ""),
                                    null, s.number == season,
                                ) { season = s.number }
                            }
                        }
                    }
                }
                val f = full
                when {
                    f == null -> item("full_loading") {
                        Text(if (tv) "Buscando capítulos de la temporada $season en YouTube…" else "Buscando la película completa en YouTube…", color = VColors.Secondary, fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
                    }
                    f.isEmpty() && others.isEmpty() -> item("full_none") {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 6.dp)) {
                            Text(
                                if (tv && !Tmdb.mayBeFreeOnYoutube(d.movie))
                                    "Es una serie reciente: está en plataformas de pago, así que sus capítulos no están gratis " +
                                        "en YouTube. Los videos que dicen «capítulo completo» son resúmenes o engaños. Mira el tráiler."
                                else if (tv)
                                    "No encontramos capítulos completos en español de la temporada $season en un canal oficial de YouTube " +
                                        "(descartamos narraciones, resúmenes y videos en otros idiomas)."
                                else if (Tmdb.isMajorStudio(d) && Tmdb.mayBeFreeOnYoutube(d.movie))
                                    "Es de un gran estudio (" + d.studios.first() + "): sus películas no están gratis en YouTube. " +
                                        "Los videos con ese título son narraciones o copias. Mira arriba «Dónde ver»."
                                else if (!Tmdb.mayBeFreeOnYoutube(d.movie))
                                    "Es un estreno reciente: todavía está en cines o en plataformas de pago, así que no " +
                                        "está gratis en YouTube. Los videos que dicen «película completa» son reseñas o engaños. " +
                                        "Mira el tráiler mientras tanto."
                                else
                                    "No encontramos esta película completa, gratis y en español en un canal oficial de YouTube " +
                                        "(descartamos narraciones, reseñas, resúmenes y videos en otros idiomas). Mira «Dónde ver».",
                                color = VColors.Secondary, fontSize = 13.sp,
                            )
                        }
                    }
                    else -> items(f, key = { "full:" + it.url }) { v ->
                        VideoCard(v, onClick = { onVideo(v) }, onChannel = {}, reason = (if (tv) "Capítulo completo · " else "Duración completa · ") + v.channelName + (if (v.verified) " ✓ canal verificado" else ""))
                    }
                }
                if (f != null && others.isNotEmpty()) {
                    item("others_t") {
                        SectionTitle(
                            "De otros canales (sin verificar)",
                            subtitle = "Coinciden en título, duración e idioma, pero el canal no es oficial: " +
                                "puede ser la " + (if (tv) "serie" else "película") + " completa o no.",
                        )
                    }
                    items(others, key = { "other:" + it.url }) { v ->
                        VideoCard(v, onClick = { onVideo(v) }, onChannel = {}, reason = "Sin verificar · " + v.channelName)
                    }
                }
                if (similar.isNotEmpty()) {
                    item("sim_t") { SectionTitle("Si te gustó, mira también") }
                    item("sim") {
                        LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            items(similar, key = { it.id }) { m -> PosterCard(m) { onMovie(m.id, m.tv) } }
                        }
                    }
                }
                item("credit") {
                    Text(
                        "Datos e imágenes de películas y series: TMDB. Esta app usa la API de TMDB pero no está avalada ni certificada por TMDB.",
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
                if (m.tv) d.seasons.size.takeIf { it > 0 }?.let { if (it == 1) "1 temporada" else "$it temporadas" }
                else d.runtimeMin.takeIf { it > 0 }?.let { if (it >= 60) "${it / 60} h ${it % 60} min" else "$it min" },
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

/**
 * Mientras el usuario no haya movido la lista, la mantiene arriba cuando llegan filas
 * nuevas por encima (los pósters de TMDB suelen llegar después que los videos y la
 * lista se quedaba anclada más abajo).
 */
@Composable
fun KeepListAtTop(state: androidx.compose.foundation.lazy.LazyListState, trigger: Any?) {
    val flags = remember(state) { booleanArrayOf(false, false) } // [0] tocada por el usuario, [1] scroll automático
    LaunchedEffect(state) {
        androidx.compose.runtime.snapshotFlow { state.isScrollInProgress }.collect { moving ->
            if (moving && !flags[1]) flags[0] = true
        }
    }
    LaunchedEffect(trigger) {
        if (!flags[0] && (state.firstVisibleItemIndex > 0 || state.firstVisibleItemScrollOffset > 0)) {
            flags[1] = true
            state.scrollToItem(0)
            flags[1] = false
        }
    }
}

/** «Dónde ver»: plataformas en Perú (gratis, suscripción, alquiler o compra). */
@Composable
private fun WhereToWatch(w: com.silva.vidtube.data.TmdbWatch, title: String) {
    val context = androidx.compose.ui.platform.LocalContext.current
    Column(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
        SectionTitle("Dónde ver")
        val groups = listOf(
            "Gratis" to w.free,
            "Con suscripción" to w.subscription,
            "Alquiler o compra" to w.rentOrBuy,
        ).filter { it.second.isNotEmpty() }
        for ((label, list) in groups) {
            Text(
                label, color = VColors.Secondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 16.dp, top = 2.dp, bottom = 6.dp),
            )
            LazyRow(
                contentPadding = PaddingValues(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 10.dp),
            ) {
                items(list, key = { label + it.id }) { p ->
                    val url = Tmdb.providerUrl(p, title, w.link)
                    Column(
                        Modifier.width(68.dp).clip(RoundedCornerShape(10.dp))
                            .clickable(enabled = url != null, onClickLabel = "Abrir ${p.name}") { url?.let { openLink(context, it) } },
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        AsyncImage(
                            model = p.logo, contentDescription = p.name, contentScale = ContentScale.Crop,
                            modifier = Modifier.size(52.dp).clip(RoundedCornerShape(12.dp)).background(VColors.Card)
                                .border(1.dp, VColors.Divider, RoundedCornerShape(12.dp)),
                        )
                        Text(
                            p.name, color = VColors.Text, fontSize = 10.sp, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center, modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }
        Text(
            "Disponibilidad en Perú según JustWatch (vía TMDB). Al tocar una plataforma se abre su app o su página.",
            color = VColors.Muted, fontSize = 11.sp, modifier = Modifier.padding(horizontal = 16.dp),
        )
    }
}

/** Abre un enlace en la app que corresponda (Netflix, Prime…) o en el navegador. */
private fun openLink(context: android.content.Context, url: String) {
    runCatching {
        context.startActivity(
            android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
                .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }.onFailure {
        android.widget.Toast.makeText(context, "No se pudo abrir el enlace", android.widget.Toast.LENGTH_SHORT).show()
    }
}
