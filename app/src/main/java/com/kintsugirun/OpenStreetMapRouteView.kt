package com.kintsugirun

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.net.http.SslError
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.webkit.WebViewAssetLoader
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

internal object AppLog {
    fun d(tag: String, msg: String) {
        runCatching { Log.d(tag, msg) }
    }
    fun e(tag: String, msg: String, tr: Throwable? = null) {
        runCatching {
            if (tr != null) Log.e(tag, msg, tr) else Log.e(tag, msg)
        }
    }
}

const val MAP_USER_AGENT = "KintsugiRun/1.0 (Android; Workout Interval Timer)"

object LeafletAssetLoader {
    @Volatile
    private var cachedCss: String? = null
    @Volatile
    private var cachedJs: String? = null

    fun getLeafletCss(context: Context): String {
        return cachedCss ?: synchronized(this) {
            cachedCss ?: runCatching {
                val css = context.assets.open("leaflet/leaflet.css").bufferedReader().use { it.readText() }
                AppLog.d("LeafletMap", "Loaded leaflet.css from assets, size: ${css.length}")
                css
            }.getOrElse { e ->
                AppLog.e("LeafletMap", "FAILED to load leaflet.css from assets", e)
                ""
            }.also { if (it.isNotEmpty()) cachedCss = it }
        }
    }

    fun getLeafletJs(context: Context): String {
        return cachedJs ?: synchronized(this) {
            cachedJs ?: runCatching {
                val js = context.assets.open("leaflet/leaflet.js").bufferedReader().use { it.readText() }
                AppLog.d("LeafletMap", "Loaded leaflet.js from assets, size: ${js.length}")
                js
            }.getOrElse { e ->
                AppLog.e("LeafletMap", "FAILED to load leaflet.js from assets", e)
                ""
            }.also { if (it.isNotEmpty()) cachedJs = it }
        }
    }

    internal fun resetCache() {
        synchronized(this) {
            cachedCss = null
            cachedJs = null
        }
    }
}

@Serializable
internal data class RouteSegment(
    val color: String,
    val coords: List<List<Double>>
)

internal fun getPhaseColor(phaseName: String): String {
    val lower = phaseName.lowercase(Locale.ROOT)
    return when {
        lower.contains("run") || lower.contains("corsa") -> "#ef4444"
        lower.contains("walk") || lower.contains("cammin") -> "#10b981"
        lower.contains("warm") || lower.contains("riscald") -> "#3b82f6"
        lower.contains("cool") || lower.contains("defatic") -> "#8b5cf6"
        else -> "#f59e0b"
    }
}

internal fun buildRouteSegments(points: List<GpsPoint>): List<RouteSegment> {
    if (points.isEmpty()) return emptyList()
    if (points.size == 1) {
        val color = getPhaseColor(points[0].phaseName)
        return listOf(RouteSegment(color, listOf(listOf(points[0].latitude, points[0].longitude))))
    }

    val segments = mutableListOf<RouteSegment>()
    var currentCoords = mutableListOf<List<Double>>()
    var currentColor = getPhaseColor(points[0].phaseName)
    currentCoords.add(listOf(points[0].latitude, points[0].longitude))

    for (i in 1 until points.size) {
        val pt = points[i]
        val color = getPhaseColor(pt.phaseName)
        if (color != currentColor) {
            currentCoords.add(listOf(pt.latitude, pt.longitude))
            segments.add(RouteSegment(currentColor, currentCoords))
            currentCoords = mutableListOf(listOf(pt.latitude, pt.longitude))
            currentColor = color
        } else {
            currentCoords.add(listOf(pt.latitude, pt.longitude))
        }
    }
    if (currentCoords.size > 1 || (segments.isEmpty() && currentCoords.isNotEmpty())) {
        segments.add(RouteSegment(currentColor, currentCoords))
    }
    return segments
}

const val APP_ASSETS_BASE_URL = "https://appassets.androidplatform.net/assets/leaflet/"

internal fun generateLeafletHtml(
    segmentsJson: String,
    leafletCss: String = "",
    leafletJs: String = ""
): String {
    val cssContent = if (leafletCss.isNotBlank()) {
        "<style>\n$leafletCss\n</style>"
    } else {
        "<link rel=\"stylesheet\" href=\"${APP_ASSETS_BASE_URL}leaflet.css\" />"
    }

    val jsContent = if (leafletJs.isNotBlank()) {
        "<script>\n$leafletJs\n</script>"
    } else {
        "<script src=\"${APP_ASSETS_BASE_URL}leaflet.js\"></script>"
    }

    return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <link rel="icon" href="data:,">
  $cssContent
  <style>
    html, body {
      width: 100%;
      height: 100%;
      margin: 0;
      padding: 0;
      overflow: hidden;
      background-color: transparent;
    }
    #map {
      width: 100%;
      height: 100%;
      min-height: 100%;
    }
    .leaflet-control-attribution {
      font-size: 8px !important;
      background: rgba(255, 255, 255, 0.6) !important;
      padding: 0 4px !important;
    }
  </style>
  $jsContent
</head>
<body>
  <div id="map"></div>
  <script>
    function ensureMapSize() {
      var h = window.innerHeight || document.documentElement.clientHeight;
      if (h && h > 0) {
        var hStr = h + 'px';
        document.documentElement.style.height = hStr;
        document.body.style.height = hStr;
        var container = document.getElementById('map');
        if (container) {
          container.style.height = hStr;
        }
      }
    }

    function initMap() {
      try {
        ensureMapSize();
        var container = document.getElementById('map');
        console.log("initMap called, typeof L: " + typeof L + ", container: " + (container ? (container.offsetWidth + "x" + container.offsetHeight) : "null"));

        var map = L.map('map', {
          zoomControl: false,
          attributionControl: true
        });

        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
          maxZoom: 19,
          attribution: '&copy; OpenStreetMap'
        }).addTo(map);

        var segments = $segmentsJson;
        var allCoords = [];

        segments.forEach(function(seg) {
          if (seg.coords && seg.coords.length > 0) {
            L.polyline(seg.coords, {
              color: seg.color,
              weight: 5,
              opacity: 0.9
            }).addTo(map);
            seg.coords.forEach(function(pt) {
              allCoords.push(pt);
            });
          }
        });

        if (allCoords.length === 1) {
          map.setView(allCoords[0], 15);
          L.circleMarker(allCoords[0], {
            radius: 6,
            color: '#ffffff',
            weight: 2,
            fillColor: '#10b981',
            fillOpacity: 1.0
          }).addTo(map);
        } else if (allCoords.length > 1) {
          map.fitBounds(allCoords, { padding: [24, 24] });
          L.circleMarker(allCoords[0], {
            radius: 6,
            color: '#ffffff',
            weight: 2,
            fillColor: '#10b981',
            fillOpacity: 1.0
          }).addTo(map);
          L.circleMarker(allCoords[allCoords.length - 1], {
            radius: 6,
            color: '#ffffff',
            weight: 2,
            fillColor: '#ef4444',
            fillOpacity: 1.0
          }).addTo(map);
        }

        console.log("Map initialized successfully, total coords: " + allCoords.length);

        window.addEventListener('resize', function() {
          ensureMapSize();
          map.invalidateSize();
        });

        setTimeout(function() {
          ensureMapSize();
          map.invalidateSize();
        }, 100);
        setTimeout(function() {
          ensureMapSize();
          map.invalidateSize();
        }, 350);
      } catch (err) {
        console.error("Leaflet error:", err.message, err.stack);
      }
    }

    if (document.readyState === 'loading') {
      document.addEventListener("DOMContentLoaded", initMap);
    } else {
      initMap();
    }
  </script>
</body>
</html>
    """.trimIndent()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun OpenStreetMapRouteView(
    gpsPoints: List<GpsPoint>,
    modifier: Modifier = Modifier
) {
    if (gpsPoints.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Nessun dato GPS disponibile",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }

    val context = LocalContext.current
    val assetLoader = remember(context) {
        WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", WebViewAssetLoader.AssetsPathHandler(context))
            .build()
    }

    val html = remember(gpsPoints) {
        val segments = buildRouteSegments(gpsPoints)
        val json = Json.encodeToString(segments)
        generateLeafletHtml(json)
    }

    AppLog.d("LeafletMap", "OpenStreetMapRouteView composing, points: ${gpsPoints.size}, html size: ${html.length}")

    AndroidView(
        factory = { ctx ->
            runCatching { WebView.setWebContentsDebuggingEnabled(true) }
            WebView(ctx).apply {
                tag = html
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = false
                    useWideViewPort = false
                    userAgentString = MAP_USER_AGENT
                }
                webChromeClient = object : WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: ConsoleMessage): Boolean {
                        AppLog.d(
                            "LeafletMap",
                            "[JS ${consoleMessage.messageLevel()}] ${consoleMessage.message()} (at ${consoleMessage.sourceId()}:${consoleMessage.lineNumber()})"
                        )
                        return true
                    }
                }
                webViewClient = object : WebViewClient() {
                    override fun shouldInterceptRequest(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): WebResourceResponse? {
                        val url = request?.url ?: return null
                        val response = assetLoader.shouldInterceptRequest(url)
                        if (response != null) {
                            AppLog.d("LeafletMap", "Intercepted asset: $url -> ${response.mimeType}")
                            return response
                        }
                        return super.shouldInterceptRequest(view, request)
                    }

                    override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                        super.onPageStarted(view, url, favicon)
                        AppLog.d("LeafletMap", "onPageStarted: $url")
                    }

                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        AppLog.d("LeafletMap", "onPageFinished: $url")
                    }

                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                        AppLog.e(
                            "LeafletMap",
                            "onReceivedError: url=${request?.url}, code=${error?.errorCode}, desc=${error?.description}"
                        )
                    }

                    override fun onReceivedHttpError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        errorResponse: WebResourceResponse?
                    ) {
                        super.onReceivedHttpError(view, request, errorResponse)
                        AppLog.e(
                            "LeafletMap",
                            "onReceivedHttpError: url=${request?.url}, status=${errorResponse?.statusCode}, reason=${errorResponse?.reasonPhrase}"
                        )
                    }

                    override fun onReceivedSslError(
                        view: WebView?,
                        handler: SslErrorHandler?,
                        error: SslError?
                    ) {
                        super.onReceivedSslError(view, handler, error)
                        AppLog.e("LeafletMap", "onReceivedSslError: url=${error?.url}, error=$error")
                    }
                }
                AppLog.d("LeafletMap", "factory: loading HTML via loadDataWithBaseURL (baseUrl=$APP_ASSETS_BASE_URL)")
                loadDataWithBaseURL(APP_ASSETS_BASE_URL, html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                AppLog.d("LeafletMap", "update: reloading HTML via loadDataWithBaseURL (baseUrl=$APP_ASSETS_BASE_URL)")
                webView.loadDataWithBaseURL(APP_ASSETS_BASE_URL, html, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}
