package com.silva.vidtube.ui

import android.annotation.SuppressLint
import android.graphics.Color as AColor
import android.view.View
import android.view.ViewGroup
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.OpenInBrowser
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView

/**
 * Reproductor para enlaces de páginas con su propio reproductor (no son archivos de video).
 * La página se abre dentro de Vidtube con estas protecciones:
 *  - Sin ventanas emergentes (la publicidad de esas páginas suele abrir pestañas nuevas).
 *  - Solo http/https: nada de intent:, market:, file: ni descargas de apps.
 *  - Sin acceso a archivos del celular.
 * El botón de pantalla completa del reproductor de la página funciona y gira la pantalla.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebPlayerScreen(url: String, title: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val activity = remember(context) { context.findActivity() }
    var progress by remember { mutableIntStateOf(0) }
    var blocked by remember { mutableIntStateOf(0) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var fullView by remember { mutableStateOf<View?>(null) }
    var fullCallback by remember { mutableStateOf<WebChromeClient.CustomViewCallback?>(null) }

    fun exitFull() {
        val v = fullView ?: return
        (activity?.window?.decorView as? FrameLayout)?.removeView(v)
        fullCallback?.onCustomViewHidden()
        fullView = null
        fullCallback = null
        activity?.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.window?.let { w ->
            androidx.core.view.WindowCompat.getInsetsController(w, w.decorView)
                .show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        }
    }

    BackHandler {
        when {
            fullView != null -> exitFull()
            webView?.canGoBack() == true -> webView?.goBack()
            else -> onBack()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exitFull()
            webView?.apply { stopLoading(); onPause(); destroy() }
            webView = null
        }
    }

    Column(Modifier.fillMaxSize().background(VColors.Background)) {
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = VColors.Text) }
            Column(Modifier.weight(1f)) {
                Text(title, color = VColors.Text, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    com.silva.vidtube.data.UserLinks.hostOf(url) + if (blocked > 0) " · $blocked ventanas emergentes bloqueadas" else "",
                    color = VColors.Secondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(webView?.url ?: url))
                            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
            }) { Icon(Icons.Outlined.OpenInBrowser, "Abrir en el navegador", tint = VColors.Text) }
        }
        if (progress in 1..99) {
            LinearProgressIndicator(
                progress = { progress / 100f }, color = VColors.Coral, trackColor = VColors.Divider,
                modifier = Modifier.fillMaxWidth().height(2.dp),
            )
        }
        Box(Modifier.fillMaxSize().background(androidx.compose.ui.graphics.Color.Black)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                        setBackgroundColor(AColor.BLACK)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            mediaPlaybackRequiresUserGesture = false
                            allowFileAccess = false
                            allowContentAccess = false
                            setSupportMultipleWindows(true) // para poder rechazarlas en onCreateWindow
                            javaScriptCanOpenWindowsAutomatically = false
                            mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                            loadWithOverviewMode = true
                            useWideViewPort = true
                        }
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val scheme = request.url.scheme?.lowercase()
                                if (scheme != "http" && scheme != "https") { blocked++; return true }
                                return false
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, newProgress: Int) { progress = newProgress }

                            // Ventanas emergentes: se rechazan siempre
                            override fun onCreateWindow(view: WebView, isDialog: Boolean, isUserGesture: Boolean, resultMsg: android.os.Message?): Boolean {
                                blocked++
                                return false
                            }

                            // Pantalla completa del reproductor de la página
                            override fun onShowCustomView(view: View, callback: CustomViewCallback) {
                                if (fullView != null) { callback.onCustomViewHidden(); return }
                                val act = activity ?: run { callback.onCustomViewHidden(); return }
                                val decor = act.window.decorView as? FrameLayout ?: run { callback.onCustomViewHidden(); return }
                                view.setBackgroundColor(AColor.BLACK)
                                decor.addView(view, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
                                fullView = view
                                fullCallback = callback
                                act.requestedOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                androidx.core.view.WindowCompat.getInsetsController(act.window, act.window.decorView).apply {
                                    hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
                                    systemBarsBehavior = androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
                                }
                            }

                            override fun onHideCustomView() { exitFull() }
                        }
                        // Descargas que la página intente iniciar: no se hacen solas
                        setDownloadListener { _, _, _, _, _ -> blocked++ }
                        loadUrl(url)
                        webView = this
                    }
                },
            )
        }
    }
}
