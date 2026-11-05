package com.silva.vidtube.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.MusicNote
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.silva.vidtube.download.DownloadStatus
import com.silva.vidtube.download.DownloadTask
import com.silva.vidtube.download.Downloads

@Composable
fun DownloadsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val tasks by Downloads.tasks.collectAsState()
    var toDelete by remember { mutableStateOf<DownloadTask?>(null) }

    fun open(task: DownloadTask) {
        val uri = task.uri ?: return
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(Uri.parse(uri), if (task.audioOnly) "audio/*" else "video/*")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(context, "No hay una app para abrir este archivo", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            Toast.makeText(context, "El archivo ya no existe", Toast.LENGTH_SHORT).show()
        }
    }

    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        VTopBar(title = "Descargas", onBack = onBack)
        if (tasks.isEmpty()) {
            EmptyView(
                "No tienes descargas",
                "Abre un video y toca \"Descargar\". Los archivos se guardan en la carpeta Descargas/Vidtube del celular.",
            )
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize()) {
            items(tasks, key = { it.id }) { t ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(enabled = t.status == DownloadStatus.DONE) { open(t) }
                        .padding(start = 12.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .width(120.dp)
                            .aspectRatio(16f / 9f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(VColors.Chip),
                        contentAlignment = Alignment.Center,
                    ) {
                        AsyncImage(
                            model = t.thumbnail,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                        if (t.audioOnly) {
                            Icon(
                                Icons.Outlined.MusicNote,
                                null,
                                tint = androidx.compose.ui.graphics.Color.White,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(androidx.compose.ui.graphics.Color(0x99000000))
                                    .padding(4.dp),
                            )
                        }
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.title,
                            color = VColors.Text,
                            fontSize = 14.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Spacer(Modifier.height(2.dp))
                        val status = when (t.status) {
                            DownloadStatus.QUEUED -> "En espera…"
                            DownloadStatus.RUNNING -> "Descargando ${(t.progress * 100).toInt()} %"
                            DownloadStatus.MERGING -> "Uniendo audio y video…"
                            DownloadStatus.DONE -> joinDot(t.label, sizeMb(t.sizeBytes))
                            DownloadStatus.FAILED -> "Error: " + (t.error ?: "desconocido")
                        }
                        Text(
                            status,
                            color = if (t.status == DownloadStatus.FAILED) VColors.Red else VColors.Secondary,
                            fontSize = 12.sp,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (t.status == DownloadStatus.RUNNING || t.status == DownloadStatus.MERGING) {
                            Spacer(Modifier.height(6.dp))
                            if (t.status == DownloadStatus.RUNNING) {
                                LinearProgressIndicator(
                                    progress = { t.progress },
                                    color = VColors.Red,
                                    trackColor = VColors.Chip,
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                )
                            } else {
                                LinearProgressIndicator(
                                    color = VColors.Red,
                                    trackColor = VColors.Chip,
                                    modifier = Modifier.fillMaxWidth().height(3.dp),
                                )
                            }
                        }
                    }
                    if (t.status == DownloadStatus.DONE || t.status == DownloadStatus.FAILED) {
                        IconButton(onClick = { toDelete = t }) {
                            Icon(Icons.Outlined.DeleteOutline, "Borrar", tint = VColors.Text)
                        }
                    } else {
                        Spacer(Modifier.width(12.dp))
                    }
                }
            }
        }
    }

    toDelete?.let { t ->
        AlertDialog(
            onDismissRequest = { toDelete = null },
            title = { Text("¿Borrar descarga?") },
            text = { Text(if (t.status == DownloadStatus.DONE) "Se borrará el archivo del celular." else "Se quitará de la lista.") },
            confirmButton = {
                TextButton(onClick = {
                    Downloads.remove(context, t)
                    toDelete = null
                }) { Text("Borrar", color = VColors.Red) }
            },
            dismissButton = { TextButton(onClick = { toDelete = null }) { Text("Cancelar") } },
            containerColor = VColors.Surface,
        )
    }
}

private fun sizeMb(bytes: Long): String =
    if (bytes <= 0) "" else String.format(java.util.Locale.US, "%.1f MB", bytes / 1_048_576.0).replace('.', ',')
