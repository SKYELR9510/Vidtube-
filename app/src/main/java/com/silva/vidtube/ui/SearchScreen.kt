package com.silva.vidtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.NorthWest
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.SportsEsports
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material.icons.outlined.SportsSoccer
import androidx.compose.material.icons.outlined.Newspaper
import androidx.compose.material.icons.outlined.ChildCare
import androidx.compose.material.icons.outlined.Science
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.silva.vidtube.data.Library
import com.silva.vidtube.data.Repository
import com.silva.vidtube.data.VideoItem
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SearchViewModel : ViewModel() {
    var text by mutableStateOf("")
    var submitted by mutableStateOf<String?>(null)
        private set
    var suggestions by mutableStateOf<List<String>>(emptyList())
        private set
    var results: Paged? by mutableStateOf(null)
        private set
    private var suggestJob: Job? = null

    fun onTextChange(value: String) {
        text = value
        submitted = null
        suggestJob?.cancel()
        if (value.isBlank()) {
            suggestions = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(250)
            suggestions = Repository.suggestions(value)
        }
    }

    fun submit(query: String = text) {
        val q = query.trim()
        if (q.isEmpty()) return
        text = q
        submitted = q
        suggestions = emptyList()
        Library.addSearch(q)
        val p = Paged(viewModelScope) { Repository.search(q) }
        results = p
        p.loadIfNeeded()
    }
}

@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
    vm: SearchViewModel = viewModel(),
) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = remember { FocusRequester() }
    // El teclado no se abre solo: así se ven las categorías (tocar la barra para escribir)

    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        Row(
            Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(56.dp)
                .padding(end = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Atrás", tint = VColors.Text)
            }
            Box(
                Modifier
                    .weight(1f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(50))
                    .background(VColors.Chip)
                    .padding(start = 16.dp, end = 4.dp),
                contentAlignment = Alignment.CenterStart,
            ) {
                if (vm.text.isEmpty()) {
                    Text("Buscar en Vidtube", color = VColors.Secondary, fontSize = 16.sp)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BasicTextField(
                        value = vm.text,
                        onValueChange = vm::onTextChange,
                        singleLine = true,
                        textStyle = TextStyle(color = VColors.Text, fontSize = 16.sp),
                        cursorBrush = SolidColor(VColors.Red),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {
                            keyboard?.hide()
                            vm.submit()
                        }),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
                    if (vm.text.isNotEmpty()) {
                        IconButton(onClick = { vm.onTextChange("") }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Filled.Close, "Borrar", tint = VColors.Text, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }

        val results = vm.results
        if (vm.submitted != null && results != null) {
            FeedList(
                paged = results,
                onVideo = onVideo,
                onChannel = onChannel,
                emptyTitle = "Sin resultados",
                emptySubtitle = "Prueba con otras palabras.",
            )
        } else {
            val recent = Library.searches.value
            val rows: List<Pair<String, Boolean>> =
                if (vm.text.isBlank()) recent.map { it to true }
                else vm.suggestions.map { it to false }
            LazyColumn(Modifier.fillMaxSize()) {
                if (vm.text.isBlank()) {
                    item("cat_title") { SectionTitle("Explorar categorías") }
                    items(searchCategories.chunked(3), key = { "cat_" + it.first().label }) { row ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp),
                        ) {
                            row.forEach { cat ->
                                CategoryTile(cat, Modifier.weight(1f)) {
                                    keyboard?.hide()
                                    vm.submit(cat.query)
                                }
                            }
                        }
                    }
                    if (rows.isNotEmpty()) item("recent_title") { SectionTitle("Búsquedas recientes") }
                }
                items(rows, key = { it.first + it.second }) { (s, isRecent) ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable {
                                keyboard?.hide()
                                vm.submit(s)
                            }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            if (isRecent) Icons.Outlined.History else Icons.Outlined.Search,
                            null,
                            tint = VColors.Text,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            s,
                            color = VColors.Text,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { vm.onTextChange("$s ") }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Outlined.NorthWest, "Completar", tint = VColors.Text, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }
}

data class SearchCategory(
    val label: String,
    val query: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val from: Long,
    val to: Long,
)

private val searchCategories = listOf(
    SearchCategory("Películas", "películas completas en español", Icons.Outlined.Movie, 0xFF7A1F2B, 0xFF2A0A10),
    SearchCategory("Videojuegos", "videojuegos gameplay", Icons.Outlined.SportsEsports, 0xFF2346B8, 0xFF0B1640),
    SearchCategory("Música", "música 2026", Icons.Outlined.MusicNote, 0xFF8E2DC6, 0xFF2B0B42),
    SearchCategory("Deportes", "deportes resumen", Icons.Outlined.SportsSoccer, 0xFF1F8A4C, 0xFF0A2E19),
    SearchCategory("Noticias", "noticias hoy", Icons.Outlined.Newspaper, 0xFFB5462A, 0xFF3A130A),
    SearchCategory("Infantil", "canciones infantiles", Icons.Outlined.ChildCare, 0xFFE0912B, 0xFF4A2A06),
    SearchCategory("Ciencia y tecnología", "ciencia y tecnología", Icons.Outlined.Science, 0xFF1B8FA6, 0xFF06303A),
    SearchCategory("Viajes y estilo de vida", "viajes vlog", Icons.Outlined.Flight, 0xFF2B6FD6, 0xFF0A2450),
    SearchCategory("Comida y recetas", "recetas fáciles", Icons.Outlined.Restaurant, 0xFFC7502A, 0xFF43160A),
)

@Composable
private fun CategoryTile(cat: SearchCategory, modifier: Modifier, onClick: () -> Unit) {
    Box(
        modifier
            .height(84.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(
                androidx.compose.ui.graphics.Brush.linearGradient(
                    listOf(androidx.compose.ui.graphics.Color(cat.from), androidx.compose.ui.graphics.Color(cat.to))
                )
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Icon(
            cat.icon, null,
            tint = androidx.compose.ui.graphics.Color.White,
            modifier = Modifier.align(Alignment.TopEnd).size(30.dp),
        )
        Text(
            cat.label,
            color = androidx.compose.ui.graphics.Color.White,
            fontSize = 13.sp,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            lineHeight = 15.sp,
            maxLines = 2,
            modifier = Modifier.align(Alignment.BottomStart),
        )
    }
}
