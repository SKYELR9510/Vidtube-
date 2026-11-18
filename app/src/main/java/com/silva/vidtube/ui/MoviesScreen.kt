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
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
    /** Abrir una página dentro de Vidtube (enlaces propios que no son archivos de video). */
    onWeb: (url: String, title: String) -> Unit = { _, _ -> },
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
    // Enlaces que agregó el usuario a este título (solo en este celular)
    val linkKey = com.silva.vidtube.data.UserLinks.key(id, tv)
    val allLinks by com.silva.vidtube.data.UserLinks.all.collectAsState()
    val myLinks = allLinks[linkKey].orEmpty()
    var addLink by remember(id) { mutableStateOf(false) }
    val context = androidx.compose.ui.platform.LocalContext.current

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
        // Series recientes: lo que aparezca va a "otros canales"
        val recent = !Tmdb.mayBeFreeOnYoutube(d.movie)
        others = if (recent) eps else eps.filter { !it.verified }
        full = if (recent) emptyList() else eps.filter { it.verified }
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
                        val mine = myLinks.firstOrNull()
                        // Si no está gratis en YouTube, el botón lleva a la plataforma donde sí está
                        val w = d.watch
                        val platform = w?.free?.firstOrNull() ?: w?.subscription?.firstOrNull()
                        val platformUrl = platform?.let { Tmdb.providerUrl(it, d.movie.title, w?.link) }
                        PillButton(
                            when {
                                first != null -> if (tv) "Ver capítulo" else "Ver película"
                                mine != null -> "Ver: " + mine.name
                                other != null && full != null && platformUrl == null -> if (tv) "Ver capítulo" else "Ver película"
                                platform != null && platformUrl != null && full != null ->
                                    (if (w?.free?.isNotEmpty() == true) "Gratis en " else "Ver en ") + platform.name
                                full == null -> "Buscando…"
                                else -> "No disponible gratis"
                            },
                            Icons.Filled.PlayArrow, primary = true,
                            enabled = first != null || mine != null || (full != null && (platformUrl != null || other != null)),
                            modifier = Modifier.weight(1f),
                        ) {
                            if (first != null) onVideo(first)
                            else if (mine != null) openUserLink(context, mine, d.movie, onVideo, onWeb)
                            else if (platformUrl != null) openLink(context, platformUrl)
                            else if (other != null) onVideo(other)
                        }
                        val key = d.trailerKey
                        PillButton("Tráiler", Icons.Outlined.Movie, primary = false, enabled = key != null, modifier = Modifier.weight(1f)) {
                            if (key != null) onVideo(trailerItem(d.movie, key))
                        }
                    }
                }
                item("mylinks") {
                    MyLinks(myLinks, onOpen = { l -> openUserLink(context, l, d.movie, onVideo, onWeb) },
                        onRemove = { l -> com.silva.vidtube.data.UserLinks.remove(linkKey, l) }, onAdd = { addLink = true })
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
                                (if (tv) "No encontramos capítulos completos en español de la temporada $season en YouTube"
                                else "No encontramos esta película completa en español en YouTube") +
                                    " (se descartan narraciones, reseñas, resúmenes y videos en otros idiomas). " +
                                    "Mira «Dónde ver» o agrega tu propio enlace en «Tus enlaces».",
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
                            subtitle = "Coinciden en título, duración e idioma; el canal no es oficial: " +
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
        if (addLink) AddLinkDialog(onDismiss = { addLink = false }) { url, name ->
            com.silva.vidtube.data.UserLinks.add(linkKey, url, name)
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

/** «Tus enlaces»: enlaces que el usuario guardó para este título. */
@Composable
private fun MyLinks(
    links: List<com.silva.vidtube.data.UserLink>,
    onOpen: (com.silva.vidtube.data.UserLink) -> Unit,
    onRemove: (com.silva.vidtube.data.UserLink) -> Unit,
    onAdd: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        SectionTitle("Tus enlaces", subtitle = if (links.isEmpty()) "Agrega un enlace a este título: se guarda solo en tu celular." else null)
        for (l in links) {
            Row(
                Modifier.fillMaxWidth().clickable(onClickLabel = "Abrir ${l.name}") { onOpen(l) }
                    .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    when (l.kind) {
                        com.silva.vidtube.data.UserLink.Kind.YOUTUBE -> Icons.Filled.PlayArrow
                        com.silva.vidtube.data.UserLink.Kind.VIDEO_FILE -> Icons.Outlined.Movie
                        else -> Icons.Outlined.Link
                    },
                    null, tint = VColors.Coral, modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(l.name, color = VColors.Text, fontSize = 14.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        when (l.kind) {
                            com.silva.vidtube.data.UserLink.Kind.YOUTUBE -> "YouTube · se ve en Vidtube"
                            com.silva.vidtube.data.UserLink.Kind.VIDEO_FILE -> com.silva.vidtube.data.UserLinks.hostOf(l.url) + " · en el reproductor de Vidtube"
                            else -> com.silva.vidtube.data.UserLinks.hostOf(l.url) + " · página, se abre dentro de Vidtube"
                        },
                        color = VColors.Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                }
                androidx.compose.material3.IconButton(onClick = { onRemove(l) }) {
                    Icon(Icons.Outlined.Close, "Quitar enlace", tint = VColors.Muted)
                }
            }
        }
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(20.dp))
                .border(1.dp, VColors.Divider, RoundedCornerShape(20.dp)).clickable(onClick = onAdd)
                .padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Add, null, tint = VColors.Text, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Agregar enlace", color = VColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun AddLinkDialog(onDismiss: () -> Unit, onSave: (url: String, name: String) -> String?) {
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Agregar enlace") },
        text = {
            Column {
                Text(
                    "Pega un enlace de YouTube, Google Drive, Dropbox, un archivo de video (.mp4, .m3u8…) " +
                        "o una página con reproductor. Todo se ve dentro de Vidtube. Se guarda solo en este celular.",
                    color = VColors.Secondary, fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = url, onValueChange = { url = it; error = null }, singleLine = true,
                    label = { Text("Enlace") }, placeholder = { Text("https://…") }, isError = error != null,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                androidx.compose.material3.OutlinedTextField(
                    value = name, onValueChange = { name = it }, singleLine = true,
                    label = { Text("Nombre (opcional)") }, placeholder = { Text("Ej.: Versión latino") },
                    modifier = Modifier.fillMaxWidth(),
                )
                error?.let { Text(it, color = VColors.Coral, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) }
            }
        },
        confirmButton = {
            androidx.compose.material3.TextButton(onClick = {
                val e = onSave(url, name)
                if (e == null) onDismiss() else error = e
            }) { Text("Guardar", color = VColors.Coral) }
        },
        dismissButton = { androidx.compose.material3.TextButton(onClick = onDismiss) { Text("Cancelar") } },
    )
}

/**
 * Abre un enlace del usuario dentro de Vidtube:
 *  - YouTube, Google Drive, Dropbox y archivos de video → el reproductor de Vidtube.
 *  - Otras páginas → el reproductor de páginas (dentro de la app, sin ventanas emergentes).
 */
@Suppress("UNUSED_PARAMETER")
private fun openUserLink(
    context: android.content.Context,
    link: com.silva.vidtube.data.UserLink,
    movie: TmdbMovie,
    onVideo: (VideoItem) -> Unit,
    onWeb: (String, String) -> Unit,
) {
    fun item(url: String) = VideoItem(
        url = url, title = movie.title, thumbnail = movie.backdrop ?: movie.poster, channelName = link.name,
        channelUrl = null, channelAvatar = null, durationSec = 0, views = -1, uploaded = null, isLive = false,
    )
    when (link.kind) {
        com.silva.vidtube.data.UserLink.Kind.YOUTUBE -> {
            val watch = com.silva.vidtube.data.UserLinks.youtubeWatchUrl(link.url)
            if (watch != null) onVideo(item(watch)) else onWeb(link.url, movie.title)
        }
        com.silva.vidtube.data.UserLink.Kind.VIDEO_FILE -> onVideo(item(link.url))
        else -> onWeb(link.url, movie.title)
    }
}
