package com.silva.vidtube.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.silva.vidtube.update.Updater
import kotlinx.coroutines.launch

/** Aviso arriba del Inicio: hay versión nueva, se está descargando, o YouTube cambió algo. */
@Composable
fun UpdateBanner() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val state by Updater.state.collectAsState()
    val broken by Updater.youtubeBroken.collectAsState()

    when (val s = state) {
        is Updater.State.Available -> Banner(
            icon = Icons.Outlined.SystemUpdate,
            title = "Nueva versión de Vidtube",
            text = s.release.versionName,
            action = "Actualizar",
            onAction = { scope.launch { Updater.download(context, s.release) } },
            onClose = { Updater.dismiss() },
        )
        is Updater.State.Downloading -> Banner(
            icon = Icons.Outlined.SystemUpdate,
            title = "Descargando actualización…",
            text = "${(s.progress * 100).toInt()} %",
            progress = s.progress,
        )
        is Updater.State.ReadyToInstall -> Banner(
            icon = Icons.Outlined.SystemUpdate,
            title = "Actualización lista",
            text = "Toca Instalar y confirma en la ventana de Android.",
            action = "Instalar",
            onAction = { Updater.install(context, s.file) },
            onClose = { Updater.dismiss() },
        )
        is Updater.State.Failed -> if (s.release != null) Banner(
            icon = Icons.Outlined.WarningAmber,
            title = s.message,
            text = s.release.versionName,
            action = "Reintentar",
            onAction = { scope.launch { Updater.download(context, s.release) } },
            onClose = { Updater.dismiss() },
        )
        else -> if (broken) Banner(
            icon = Icons.Outlined.WarningAmber,
            title = "Varios videos no cargan",
            text = "Probablemente YouTube cambió algo. Revisa si hay una actualización de Vidtube.",
            action = if (Updater.enabled) "Buscar" else null,
            onAction = { scope.launch { Updater.check(silent = false) } },
            onClose = { Updater.dismissBroken() },
        )
    }
}

@Composable
private fun Banner(
    icon: ImageVector,
    title: String,
    text: String,
    action: String? = null,
    onAction: () -> Unit = {},
    onClose: (() -> Unit)? = null,
    progress: Float? = null,
) {
    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(VColors.Surface)
            .padding(start = 14.dp, top = 10.dp, bottom = 10.dp, end = 4.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = VColors.Red, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = VColors.Text, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                Text(text, color = VColors.Secondary, fontSize = 12.sp)
            }
            if (action != null) {
                TextButton(onClick = onAction) { Text(action, color = VColors.Red, fontWeight = FontWeight.Bold) }
            }
            if (onClose != null) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Filled.Close, "Cerrar", tint = VColors.Secondary, modifier = Modifier.size(18.dp))
                }
            }
        }
        if (progress != null) {
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress },
                color = VColors.Red,
                trackColor = Color(0xFF3A3A3A),
                modifier = Modifier.fillMaxWidth().padding(end = 10.dp).height(3.dp),
            )
        }
    }
}
