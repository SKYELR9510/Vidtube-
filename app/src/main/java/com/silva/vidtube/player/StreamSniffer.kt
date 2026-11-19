package com.silva.vidtube.player

import android.annotation.SuppressLint
import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/** Señal encontrada: enlace del video y las cabeceras con que la pidió la página oficial. */
data class SniffedStream(val url: String, val headers: Map<String, String>)

/**
 * Abre la página oficial de un canal en segundo plano (invisible, 1×1 px) y detecta el enlace
 * de su señal (.m3u8 / .mpd) para reproducirlo en el reproductor de Vidtube.
 * No se salta ninguna protección: si la señal usa DRM, el reproductor no podrá abrirla.
 */
object StreamSniffer {
    private val ua = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0 Mobile Safari/537.36"

    @SuppressLint("SetJavaScriptEnabled")
    suspend fun find(activity: Activity, pageUrl: String, timeoutMs: Long = 25_000): SniffedStream? =
        withContext(Dispatchers.Main) {
            var web: WebView? = null
            val main = Handler(Looper.getMainLooper())
            val result = withTimeoutOrNull(timeoutMs) {
                suspendCancellableCoroutine<SniffedStream?> { cont ->
                    val w = WebView(activity)
                    web = w
                    w.alpha = 0f
                    w.settings.apply {
                        javaScriptEnabled = true
                        domStorageEnabled = true
                        mediaPlaybackRequiresUserGesture = false
                        allowFileAccess = false
                        allowContentAccess = false
                        javaScriptCanOpenWindowsAutomatically = false
                        setSupportMultipleWindows(false)
                        userAgentString = ua
                    }
                    CookieManager.getInstance().setAcceptThirdPartyCookies(w, true)
                    w.webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                            val s = request.url.scheme?.lowercase()
                            return s != "http" && s != "https"
                        }

                        override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                            val u = request.url.toString()
                            val path = request.url.path.orEmpty().lowercase()
                            val isStream = path.endsWith(".m3u8") || path.endsWith(".mpd") ||
                                Regex("\\.(mp4|webm|m4v)$").containsMatchIn(path)
                            if (isStream && cont.isActive) {
                                val h = request.requestHeaders.filterKeys {
                                    it.equals("Referer", true) || it.equals("Origin", true)
                                }.toMutableMap()
                                CookieManager.getInstance().getCookie(u)?.let { c -> if (c.isNotBlank()) h["Cookie"] = c }
                                if (h.keys.none { it.equals("Referer", true) }) h["Referer"] = pageUrl
                                cont.resume(SniffedStream(u, h))
                            }
                            return null
                        }

                        override fun onPageFinished(view: WebView, url: String) {
                            // Muchos reproductores esperan un toque: intentamos iniciar el video nosotros
                            val js = """
                                (function(){
                                  document.querySelectorAll('video').forEach(function(v){ try{ v.muted=true; v.play(); }catch(e){} });
                                  var b = document.querySelector('.vjs-big-play-button, .jw-icon-display, .play-button, [aria-label*="Play" i], [aria-label*="Reproducir" i], .plyr__control--overlaid');
                                  if (b) try { b.click(); } catch(e) {}
                                })();
                            """.trimIndent()
                            view.evaluateJavascript(js, null)
                            main.postDelayed({ if (cont.isActive) view.evaluateJavascript(js, null) }, 3000)
                        }
                    }
                    // Se agrega invisible a la pantalla (algunos reproductores no cargan si la página no está "a la vista")
                    (activity.window.decorView as? FrameLayout)?.addView(w, FrameLayout.LayoutParams(1, 1))
                    w.loadUrl(pageUrl)
                    cont.invokeOnCancellation { }
                }
            }
            web?.let { w ->
                w.stopLoading()
                (w.parent as? ViewGroup)?.removeView(w)
                w.destroy()
            }
            result
        }

    /** Cabeceras para la siguiente reproducción directa (las usa PlayerHolder.playDirect). */
    val pendingHeaders = java.util.concurrent.ConcurrentHashMap<String, Map<String, String>>()
}
