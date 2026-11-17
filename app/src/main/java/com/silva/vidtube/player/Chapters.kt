package com.silva.vidtube.player

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.schabi.newpipe.extractor.stream.StreamInfo

/** Un capítulo del video (marcado por el autor en la descripción de YouTube). */
data class Chapter(val startMs: Long, val title: String, val preview: String?)

/** Capítulos del video que se está reproduciendo. */
object Chapters {
    private val _current = MutableStateFlow<List<Chapter>>(emptyList())
    val current: StateFlow<List<Chapter>> = _current

    fun set(list: List<Chapter>) { _current.value = list }

    fun from(info: StreamInfo?): List<Chapter> {
        if (info == null) return emptyList()
        val segs = runCatching { info.streamSegments }.getOrNull().orEmpty()
        return segs
            .map { Chapter(it.startTimeSeconds * 1000L, it.title.orEmpty().trim(), it.previewUrl) }
            .filter { it.title.isNotEmpty() }
            .distinctBy { it.startMs }
            .sortedBy { it.startMs }
            // Un solo "capítulo" no aporta nada
            .takeIf { it.size >= 2 }
            .orEmpty()
    }

    /** Capítulo que contiene la posición dada, o null. */
    fun at(list: List<Chapter>, positionMs: Long): Chapter? = list.lastOrNull { it.startMs <= positionMs }
}
