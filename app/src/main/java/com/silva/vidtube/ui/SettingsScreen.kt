package com.silva.vidtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.HighQuality
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LightMode
import androidx.compose.material.icons.outlined.PictureInPicture
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material3.AlertDialog
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silva.vidtube.data.Library
import com.silva.vidtube.update.Updater
import kotlinx.coroutines.launch

/** Configuración: apariencia, reproducción, descargas, privacidad y acerca de. */
@Composable
fun SettingsScreen(onBack: () -> Unit, onDownloads: () -> Unit) {
    val settings by Library.settings.collectAsState()
    val update by Updater.state.collectAsState()
    val scope = rememberCoroutineScope()
    var qualityDialog by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var tmdbDialog by remember { mutableStateOf(false) }
    var panelDialog by remember { mutableStateOf(false) }
    val panelStatus by com.silva.vidtube.data.PanelLinks.status.collectAsState()
    val panelSettings by com.silva.vidtube.data.PanelLinks.settings.collectAsState()
    val tmdbUser by com.silva.vidtube.data.Tmdb.userToken.collectAsState()

    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        VTopBar(title = "Configuración", onBack = onBack)
        Column(Modifier.readableWidth().fillMaxSize().verticalScroll(rememberScrollState())) {
            Group("Apariencia") {
                SwitchRow(
                    if (settings.lightMode) Icons.Outlined.LightMode else Icons.Outlined.DarkMode,
                    "Modo claro",
                    if (settings.lightMode) "Activado" else "Desactivado · la app usa el modo oscuro",
                    settings.lightMode,
                ) { v -> Library.updateSettings { it.copy(lightMode = v) } }
            }
            Group("Reproducción y calidad") {
                MenuRow(Icons.Outlined.HighQuality, "Calidad predeterminada", "${settings.quality}p") { qualityDialog = true }
                SwitchRow(
                    Icons.Outlined.SkipNext, "Reproducción automática",
                    "Al terminar un video, sigue con uno relacionado", settings.autoplay,
                ) { v -> Library.updateSettings { it.copy(autoplay = v) } }
                SwitchRow(
                    Icons.Outlined.Headphones, "Reproducir en segundo plano",
                    "Sigue sonando con la pantalla apagada o en otra app", settings.backgroundPlay,
                ) { v -> Library.updateSettings { it.copy(backgroundPlay = v) } }
                SwitchRow(
                    Icons.Outlined.PictureInPicture, "Imagen en imagen",
                    "Ventana flotante al salir de la app viendo un video", settings.pictureInPicture,
                ) { v -> Library.updateSettings { it.copy(pictureInPicture = v) } }
            }
            Group("Películas") {
                // Si la app ya trae la clave de TMDB y el panel, no se muestra nada que configurar:
                // quien recibe la app solo la usa.
                if (!com.silva.vidtube.data.Tmdb.hasBuiltInToken()) {
                    MenuRow(
                        Icons.Outlined.Movie, "Clave de TMDB",
                        if (tmdbUser.isNotBlank()) "Usando tu clave · pósters, estrenos y fichas de películas"
                        else "Sin configurar · toca para pegar tu clave de themoviedb.org",
                    ) { tmdbDialog = true }
                }
                if (com.silva.vidtube.data.PanelLinks.hasBuiltIn()) {
                    MenuRow(
                        Icons.Outlined.Movie, "Películas, series y canales",
                        panelStatus ?: "Se actualizan solos al abrir la app", null,
                    )
                } else {
                    MenuRow(
                        Icons.Outlined.Movie, "Panel de enlaces",
                        panelStatus ?: if (com.silva.vidtube.data.PanelLinks.isConfigured()) "Configurado" else
                            "Sin configurar · conecta tu panel web para tener tus enlaces siempre",
                    ) { panelDialog = true }
                }
            }
            Group("Descargas") {
                MenuRow(Icons.Outlined.Download, "Mis descargas", "Se guardan en Descargas/Vidtube", onDownloads)
            }
            Group("Privacidad") {
                SwitchRow(
                    Icons.Outlined.History, "Guardar historial",
                    "Videos vistos y búsquedas recientes", settings.saveHistory,
                ) { v -> Library.updateSettings { it.copy(saveHistory = v) } }
                MenuRow(Icons.Outlined.DeleteOutline, "Borrar historial", "Videos vistos, búsquedas y posiciones") { confirmClear = true }
            }
            Group("Acerca de") {
                if (Updater.enabled) {
                    MenuRow(
                        Icons.Outlined.SystemUpdate, "Buscar actualizaciones",
                        when (val u = update) {
                            is Updater.State.Checking -> "Revisando…"
                            is Updater.State.UpToDate -> "Tienes la última versión"
                            is Updater.State.Available -> "Disponible: ${u.release.versionName}. Por qué: " +
                                (u.release.reasons.firstOrNull() ?: "mejoras internas") + " (detalles en Inicio)"
                            is Updater.State.Downloading -> "Descargando ${(u.progress * 100).toInt()} %"
                            is Updater.State.Failed -> u.message
                            else -> "Desde GitHub"
                        },
                    ) { scope.launch { Updater.check(silent = false) } }
                }
                MenuRow(
                    Icons.Outlined.Info, "Acerca de Vidtube",
                    "Versión ${com.silva.vidtube.BuildConfig.VERSION_NAME} · Funciona con NewPipe Extractor (GPLv3)", null,
                )
            }
            Spacer(Modifier.height(28.dp))
        }
    }

    if (panelDialog) {
        var url by remember { mutableStateOf(panelSettings.first) }
        var key by remember { mutableStateOf(panelSettings.second) }
        var testing by remember { mutableStateOf(false) }
        AlertDialog(
            onDismissRequest = { panelDialog = false },
            title = { Text("Panel de enlaces") },
            text = {
                Column {
                    Text(
                        "Dirección de tu panel (https://…/vidtube-panel/) y la clave «api_key» de su config.php." +
                            if (com.silva.vidtube.data.PanelLinks.hasBuiltIn()) " Ya viene configurado en la app; solo llénalo para cambiarlo." else "",
                        color = VColors.Secondary, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = url, onValueChange = { url = it.trim() }, singleLine = true,
                        label = { Text("Dirección del panel") }, placeholder = { Text("https://…") }, modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = key, onValueChange = { key = it.trim() }, singleLine = true,
                        label = { Text("Clave") }, modifier = Modifier.fillMaxWidth(),
                    )
                    panelStatus?.let { Text(it, color = VColors.Secondary, fontSize = 12.sp, modifier = Modifier.padding(top = 8.dp)) }
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(enabled = !testing, onClick = {
                    com.silva.vidtube.data.PanelLinks.save(url, key)
                    testing = true
                    scope.launch { com.silva.vidtube.data.PanelLinks.refresh(force = true); testing = false }
                }) { Text(if (testing) "Probando…" else "Guardar y probar", color = VColors.Coral) }
            },
            dismissButton = { androidx.compose.material3.TextButton(onClick = { panelDialog = false }) { Text("Cerrar") } },
        )
    }

    if (tmdbDialog) {
        var text by remember { mutableStateOf(tmdbUser) }
        AlertDialog(
            onDismissRequest = { tmdbDialog = false },
            title = { Text("Clave de TMDB") },
            text = {
                Column {
                    Text(
                        "Pega el «API Read Access Token» de tu cuenta de themoviedb.org (Configuración → API). " +
                            "Se guarda solo en este celular.",
                        color = VColors.Secondary, fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(10.dp))
                    androidx.compose.material3.OutlinedTextField(
                        value = text, onValueChange = { text = it.trim() },
                        singleLine = true, placeholder = { Text("eyJ…") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                androidx.compose.material3.TextButton(onClick = {
                    com.silva.vidtube.data.Tmdb.setUserToken(text); tmdbDialog = false
                }) { Text("Guardar", color = VColors.Coral) }
            },
            dismissButton = {
                androidx.compose.material3.TextButton(onClick = { tmdbDialog = false }) { Text("Cancelar") }
            },
        )
    }

    if (qualityDialog) {
        AlertDialog(
            onDismissRequest = { qualityDialog = false },
            title = { Text("Calidad predeterminada") },
            text = {
                Column {
                    qualityOptions.forEach { q ->
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                Library.updateSettings { it.copy(quality = q) }
                                qualityDialog = false
                            },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(
                                selected = settings.quality == q, onClick = null,
                                colors = RadioButtonDefaults.colors(selectedColor = VColors.Red),
                            )
                            Text("${q}p", modifier = Modifier.padding(start = 8.dp, top = 12.dp, bottom = 12.dp))
                        }
                    }
                    Text("Menos calidad = menos datos móviles.", color = VColors.Secondary, fontSize = 12.sp)
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
                TextButton(onClick = { Library.clearHistory(); confirmClear = false }) { Text("Borrar", color = VColors.Red) }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancelar") } },
            containerColor = VColors.Surface,
        )
    }
}

/** Grupo de opciones en una tarjeta redondeada. */
@Composable
private fun Group(title: String, content: @Composable () -> Unit) {
    Text(
        title,
        color = VColors.Secondary,
        fontSize = 13.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(start = 20.dp, top = 18.dp, bottom = 6.dp),
    )
    Column(
        Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(VColors.Card)
    ) { content() }
}
