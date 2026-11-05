package com.silva.vidtube.ui

import android.content.Context
import android.content.Intent

private fun oneDecimal(v: Double): String {
    val s = if (v < 10) String.format(java.util.Locale.US, "%.1f", v) else v.toLong().toString()
    return s.removeSuffix(".0").replace('.', ',')
}

fun compactNumber(n: Long): String = when {
    n < 1_000 -> n.toString()
    n < 1_000_000 -> oneDecimal(n / 1_000.0) + " k"
    n < 1_000_000_000 -> oneDecimal(n / 1_000_000.0) + " M"
    else -> oneDecimal(n / 1_000_000_000.0) + " mil M"
}

fun viewsText(n: Long): String = when {
    n < 0 -> ""
    n == 1L -> "1 vista"
    else -> compactNumber(n) + " vistas"
}

fun subscribersText(n: Long): String = when {
    n < 0 -> ""
    n == 1L -> "1 suscriptor"
    else -> compactNumber(n) + " suscriptores"
}

fun durationText(sec: Long): String {
    if (sec <= 0) return ""
    val h = sec / 3600
    val m = (sec % 3600) / 60
    val s = sec % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

fun joinDot(vararg parts: String?): String =
    parts.filterNot { it.isNullOrBlank() }.joinToString(" · ")

fun shareUrl(context: Context, title: String, url: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, title)
        putExtra(Intent.EXTRA_TEXT, url)
    }
    context.startActivity(Intent.createChooser(intent, "Compartir"))
}
