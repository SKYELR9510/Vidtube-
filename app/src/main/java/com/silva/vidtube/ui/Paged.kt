package com.silva.vidtube.ui

import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.silva.vidtube.data.FeedEntry
import com.silva.vidtube.data.PagedResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import org.schabi.newpipe.extractor.exceptions.ReCaptchaException
import java.io.IOException

/** Lista con carga infinita (scroll) y recarga. */
class Paged(
    private val scope: CoroutineScope,
    private val first: suspend () -> PagedResult,
) {
    var items by mutableStateOf<List<FeedEntry>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var refreshing by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    var loaded by mutableStateOf(false)
        private set

    private var next: (suspend () -> PagedResult)? = null
    private var job: Job? = null

    val canLoadMore: Boolean get() = next != null

    fun loadIfNeeded() {
        if (!loaded && !loading) load()
    }

    fun refresh() {
        refreshing = true
        load()
    }

    fun setInitial(result: PagedResult) {
        items = result.items.distinctBy { it.key }
        next = result.loadMore
        loaded = true
        error = null
    }

    private fun load() {
        job?.cancel()
        loading = true
        error = null
        job = scope.launch {
            try {
                val r = first()
                items = r.items.distinctBy { it.key }
                next = r.loadMore
                loaded = true
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("Vidtube", "Error cargando lista", e)
                error = friendly(e)
            } finally {
                loading = false
                refreshing = false
            }
        }
    }

    fun loadMore() {
        val more = next ?: return
        if (loading) return
        loading = true
        job = scope.launch {
            try {
                val r = more()
                val seen = items.map { it.key }.toHashSet()
                items = items + r.items.filter { seen.add(it.key) }
                next = r.loadMore
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                Log.e("Vidtube", "Error cargando más", e)
                next = null
            } finally {
                loading = false
            }
        }
    }

    companion object {
        fun friendly(e: Throwable): String = when (e) {
            is ReCaptchaException -> "YouTube pidió verificación. Espera unos minutos e intenta de nuevo."
            is IOException -> "Sin conexión. Revisa tu internet."
            else -> "No se pudo cargar el contenido."
        }
    }
}
