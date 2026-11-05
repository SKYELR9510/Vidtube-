package com.silva.vidtube.data

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import android.webkit.WebViewClient
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Descifra las "firmas" y el parámetro "n" de las URLs de YouTube ejecutando el propio
 * reproductor de YouTube (base.js) con el solucionador de yt-dlp (yt-dlp/ejs, assets/ejs).
 * Se ejecuta en un WebView invisible (el mismo motor de Chrome del celular).
 *
 * Lo usamos para el plan B de videos de niños: el descifrador del extractor ya no
 * entiende el código actual de YouTube.
 */
object JsSolver {
    private const val TAG = "VidtubeJs"
    private val HASH = Regex("""player\\/([a-z0-9]{8})\\/""")
    private val STS = Regex("""signatureTimestamp[=:](\d+)""")

    private var playerHash: String? = null
    private var playerCode: String? = null
    private var preprocessed: String? = null
    private var libJs: String? = null
    private var coreJs: String? = null

    /** Código del reproductor actual de YouTube (se guarda mientras no cambie). */
    @Synchronized
    private fun player(): String {
        val dl = DownloaderImpl.getInstance()
        val iframe = dl.get("https://www.youtube.com/iframe_api").responseBody() ?: ""
        val hash = HASH.find(iframe)?.groupValues?.get(1)
            ?: throw IOException("No se encontró la versión del reproductor de YouTube")
        if (hash != playerHash || playerCode == null) {
            val code = dl.get("https://www.youtube.com/s/player/$hash/player_ias.vflset/en_US/base.js")
                .responseBody() ?: throw IOException("No se pudo descargar el reproductor de YouTube")
            playerHash = hash
            playerCode = code
            preprocessed = null
        }
        return playerCode!!
    }

    fun signatureTimestamp(): Int? =
        runCatching { STS.find(player())?.groupValues?.get(1)?.toInt() }.getOrNull()

    /**
     * Resuelve los desafíos. Devuelve mapas desafío → resultado para "sig" y "n".
     */
    fun solve(context: Context, sigs: Collection<String>, ns: Collection<String>): Pair<Map<String, String>, Map<String, String>> {
        if (sigs.isEmpty() && ns.isEmpty()) return emptyMap<String, String>() to emptyMap()
        val code = player()
        val requests = JSONArray()
        if (sigs.isNotEmpty()) requests.put(JSONObject().put("type", "sig").put("challenges", JSONArray(sigs.distinct())))
        if (ns.isNotEmpty()) requests.put(JSONObject().put("type", "n").put("challenges", JSONArray(ns.distinct())))
        val pre = preprocessed
        val input = if (pre != null) {
            JSONObject().put("type", "preprocessed").put("preprocessed_player", pre).put("requests", requests)
        } else {
            JSONObject().put("type", "player").put("player", code).put("requests", requests)
                .put("output_preprocessed", true)
        }
        val raw = runInWebView(context.applicationContext, input.toString())
        val out = JSONObject(raw)
        if (out.optString("type") != "result") {
            throw IOException("Solucionador: " + out.optString("error").take(200))
        }
        out.optString("preprocessed_player").takeIf { it.isNotBlank() }?.let { preprocessed = it }

        val sigMap = HashMap<String, String>()
        val nMap = HashMap<String, String>()
        val responses = out.optJSONArray("responses") ?: JSONArray()
        for (i in 0 until responses.length()) {
            val r = responses.getJSONObject(i)
            val type = requests.getJSONObject(i).getString("type")
            if (r.optString("type") != "result") {
                Log.w(TAG, "No se resolvió $type: ${r.optString("error").take(300)}")
                continue
            }
            val data = r.getJSONObject("data")
            val target = if (type == "sig") sigMap else nMap
            data.keys().forEach { k -> data.optString(k).takeIf { it.isNotBlank() && it != "null" }?.let { target[k] = it } }
        }
        return sigMap to nMap
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun runInWebView(context: Context, inputJson: String): String {
        val lib = libJs ?: context.assets.open("ejs/yt.solver.lib.min.js").bufferedReader().readText().also { libJs = it }
        val core = coreJs ?: context.assets.open("ejs/yt.solver.core.min.js").bufferedReader().readText().also { coreJs = it }
        val script = lib + "\n;Object.assign(globalThis, lib);\n" + core + "\n;" +
            "(function(){try{return JSON.stringify(jsc(JSON.parse(VidtubeBridge.input())));}" +
            "catch(e){return JSON.stringify({type:'error',error:String(e&&e.stack||e)});}})()"

        val latch = CountDownLatch(1)
        var result: String? = null
        val main = Handler(Looper.getMainLooper())
        main.post {
            try {
                val wv = WebView(context)
                wv.settings.javaScriptEnabled = true
                wv.addJavascriptInterface(object {
                    @JavascriptInterface
                    fun input(): String = inputJson
                }, "VidtubeBridge")
                var started = false
                wv.webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView, url: String?) {
                        if (started) return
                        started = true
                        view.evaluateJavascript(script) { r ->
                            result = r
                            latch.countDown()
                            main.post { runCatching { view.destroy() } }
                        }
                    }
                }
                wv.loadDataWithBaseURL("https://www.youtube.com/", "<html><body></body></html>", "text/html", "utf-8", null)
            } catch (e: Throwable) {
                Log.e(TAG, "WebView no disponible", e)
                result = null
                latch.countDown()
            }
        }
        if (!latch.await(60, TimeUnit.SECONDS)) throw IOException("El solucionador tardó demasiado")
        val r = result ?: throw IOException("No se pudo ejecutar el solucionador")
        // evaluateJavascript entrega el valor como texto JSON (una cadena entre comillas)
        if (r == "null") throw IOException("El solucionador no devolvió resultado")
        return JSONArray("[$r]").getString(0)
    }
}
