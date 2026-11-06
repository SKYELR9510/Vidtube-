package com.silva.vidtube.player

import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import java.io.File
import java.io.FileNotFoundException

/**
 * Entrega la carátula del video que está sonando a la pantalla de bloqueo y a los
 * controles del sistema (Xiaomi, Samsung…), que la piden como enlace "content://".
 * Solo expone imágenes de la carpeta de carátulas, en modo lectura.
 */
class CoverProvider : ContentProvider() {
    override fun onCreate() = true

    override fun getType(uri: Uri) = "image/png"

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor {
        val ctx = context ?: throw FileNotFoundException()
        val name = uri.lastPathSegment ?: throw FileNotFoundException()
        // Solo nombres simples (sin rutas) de la carpeta de carátulas
        if (!name.matches(Regex("[a-zA-Z0-9_-]+\\.png"))) throw FileNotFoundException()
        val file = File(File(ctx.cacheDir, "covers"), name)
        if (!file.exists()) throw FileNotFoundException()
        return ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun query(uri: Uri, p: Array<out String>?, s: String?, a: Array<out String>?, o: String?): Cursor? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, s: String?, a: Array<out String>?) = 0
    override fun update(uri: Uri, v: ContentValues?, s: String?, a: Array<out String>?) = 0

    companion object {
        /** Guarda la carátula y devuelve su enlace content://. */
        fun save(context: android.content.Context, key: String, bmp: android.graphics.Bitmap): Uri {
            val dir = File(context.cacheDir, "covers").apply { mkdirs() }
            // Dejamos solo las últimas carátulas
            dir.listFiles()?.sortedByDescending { it.lastModified() }?.drop(5)?.forEach { it.delete() }
            val name = "c" + Integer.toHexString(key.hashCode()).replace('-', 'n') + ".png"
            val file = File(dir, name)
            if (!file.exists()) file.outputStream().use { bmp.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
            return Uri.parse("content://${context.packageName}.cover/$name")
        }
    }
}
