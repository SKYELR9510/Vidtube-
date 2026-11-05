package com.silva.vidtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silva.vidtube.data.Library
import com.silva.vidtube.data.VideoItem

val qualityOptions = listOf(1080, 720, 480, 360, 240, 144)

@Composable
fun LibraryScreen(
    onVideo: (VideoItem) -> Unit,
    onOpenList: (String) -> Unit,
    onSearch: () -> Unit,
) {
    val history by Library.history.collectAsState()
    val later by Library.watchLater.collectAsState()
    val subs by Library.subscriptions.collectAsState()
    val settings by Library.settings.collectAsState()
    val downloads by com.silva.vidtube.download.Downloads.tasks.collectAsState()
    var qualityDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        VTopBar(leading = { VidtubeLogo() }) {
            IconButton(onClick = onSearch) { Icon(Icons.Outlined.Search, "Buscar", tint = VColors.Text) }
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Avatar(null, "Tú", 64.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text("Tú", color = VColors.Text, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    Text(
                        "${subs.size} suscripciones · sin anuncios · todo guardado en tu celular",
                        color = VColors.Secondary,
                        fontSize = 12.sp,
                    )
                }
            }

            SectionHeader("Historial", if (history.isNotEmpty()) ({ onOpenList("history") }) else null)
            if (history.isEmpty()) {
                Text(
                    "Los videos que veas aparecerán aquí.",
                    color = VColors.Secondary,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                )
            } else {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(history.take(20), key = { it.url }) { v ->
                        Column(Modifier.width(150.dp).clickable { onVideo(v) }) {
                            Thumbnail(v, Modifier.fillMaxWidth().aspectRatio(16f / 9f), rounded = 8.dp, smallBadge = true)
                            Text(
                                v.title,
                                color = VColors.Text,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(top = 6.dp),
                            )
                            Text(v.channelName, color = VColors.Secondary, fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            MenuRow(Icons.Outlined.History, "Historial", "${history.size} videos") { onOpenList("history") }
            MenuRow(Icons.Outlined.Schedule, "Ver más tarde", "${later.size} videos") { onOpenList("later") }
            MenuRow(Icons.Outlined.Download, "Descargas", "${downloads.size} archivos · se guardan en Descargas/Vidtube") {
                onOpenList("downloads")
            }

            HorizontalDivider(color = VColors.Divider, modifier = Modifier.padding(vertical = 8.dp))
            Text(
                "Ajustes",
                color = VColors.Text,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
            MenuRow(Icons.Outlined.HighQuality, "Calidad predeterminada", "${settings.quality}p") {
                qualityDialog = true
            }
            SwitchRow(
                Icons.Outlined.SkipNext,
                "Reproducción automática",
                "Al terminar un video, sigue con uno relacionado",
                settings.autoplay,
            ) { v -> Library.updateSettings { it.copy(autoplay = v) } }
            SwitchRow(
                Icons.Outlined.Headphones,
                "Reproducir en segundo plano",
                "Sigue sonando con la pantalla apagada o en otra app",
                settings.backgroundPlay,
            ) { v -> Library.updateSettings { it.copy(backgroundPlay = v) } }
            SwitchRow(
                Icons.Outlined.PictureInPicture,
                "Imagen en imagen",
                "Ventana flotante al salir de la app viendo un video",
                settings.pictureInPicture,
            ) { v -> Library.updateSettings { it.copy(pictureInPicture = v) } }
            SwitchRow(
                Icons.Outlined.History,
                "Guardar historial",
                "Videos vistos y búsquedas recientes",
                settings.saveHistory,
            ) { v -> Library.updateSettings { it.copy(saveHistory = v) } }
            MenuRow(Icons.Outlined.DeleteOutline, "Borrar historial", null) { confirmClear = true }
            val update by com.silva.vidtube.update.Updater.state.collectAsState()
            val scope = rememberCoroutineScope()
            if (com.silva.vidtube.update.Updater.enabled) {
                MenuRow(
                    Icons.Outlined.SystemUpdate,
                    "Buscar actualizaciones",
                    when (val u = update) {
                        is com.silva.vidtube.update.Updater.State.Checking -> "Revisando…"
                        is com.silva.vidtube.update.Updater.State.UpToDate -> "Tienes la última versión"
                        is com.silva.vidtube.update.Updater.State.Available -> "Disponible: ${u.release.versionName} (míralo en Inicio)"
                        is com.silva.vidtube.update.Updater.State.Downloading -> "Descargando ${(u.progress * 100).toInt()} %"
                        is com.silva.vidtube.update.Updater.State.Failed -> u.message
                        else -> "Desde GitHub"
                    },
                ) { scope.launch { com.silva.vidtube.update.Updater.check(silent = false) } }
            }
            MenuRow(
                Icons.Outlined.Info,
                "Acerca de Vidtube",
                "Versión ${com.silva.vidtube.BuildConfig.VERSION_NAME} · Funciona con NewPipe Extractor (GPLv3)",
                null,
            )
            Spacer(Modifier.height(24.dp))
        }
    }

    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text("Calidad predeterminada") },
            text = {
                Column {
                    qualityOptions.forEach { q ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    Library.updateSettings { it.copy(quality = q) }
                                    qualityDialog = false
                                },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.quality == q,
                                onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = VColors.Red),
                            )
                            Text("${q}p", modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                    Text(
                        "Menos calidad = menos datos móviles.",
                        color = VColors.Secondary,
                        fontSize = 12.sp,
                    )
                }
            },
            confirmButton = { TextButton(onClick = { qualityDialog = false }) { Text("Cerrar") } },
            containerColor = VColors.Surface,
        )
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("¿Borrar historial?") },
            text = { Text("Se borrarán los videos vistos, las búsquedas recientes y las posiciones guardadas.") },
            confirmButton = {
                TextButton(onClick = {
                    Library.clearHistory()
                    confirmClear = false
                }) { Text("Borrar", color = VColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
            containerColor = VColors.Surface,
        )
    }
}

@Composable
private fun SectionHeader(title: String, onSeeAll: (() -> Unit)?) {
    Row(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = VColors.Text, fontSize = 18.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
        if (onSeeAll != null) {
            TextButton(onClick = onSeeAll) { Text("Ver todo", color = VColors.Text) }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String?, onClick: (() -> Unit)?) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) { onClick?.invoke() }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = VColors.Text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = VColors.Text, fontSize = 15.sp)
            if (subtitle != null) Text(subtitle, color = VColors.Secondary, fontSize = 12.sp)
        }
        if (onClick != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = VColors.Secondary)
        }
    }
}

@Composable
private fun SwitchRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onChange(!checked) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, tint = VColors.Text, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(20.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = VColors.Text, fontSize = 15.sp)
            Text(subtitle, color = VColors.Secondary, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = VColors.Red,
            ),
        )
    }
}

/** Historial completo o Ver más tarde. */
@Composable
fun VideoListScreen(
    kind: String,
    onBack: () -> Unit,
    onVideo: (VideoItem) -> Unit,
    onChannel: (String) -> Unit,
) {
    val isHistory = kind == "history"
    val list by (if (isHistory) Library.history else Library.watchLater).collectAsState()
    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        VTopBar(title = if (isHistory) "Historial" else "Ver más tarde", onBack = onBack)
        if (list.isEmpty()) {
            EmptyView(
                if (isHistory) "Tu historial está vacío" else "No hay videos guardados",
                if (isHistory) "Los videos que veas aparecerán aquí."
                else "Usa el menú ⋮ de un video y elige \"Guardar en Ver más tarde\".",
            )
        } else {
            LazyColumn(Modifier.fillMaxSize()) {
                items(list, key = { it.url }) { v ->
                    VideoRow(
                        v,
                        onClick = { onVideo(v) },
                        onChannel = onChannel,
                        onRemove = {
                            if (isHistory) Library.removeFromHistory(v.url) else Library.toggleWatchLater(v)
                        },
                    )
                }
            }
        }
    }
}
