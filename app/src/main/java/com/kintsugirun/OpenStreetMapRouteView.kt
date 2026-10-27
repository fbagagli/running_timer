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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    leafletJs: String = "",
    showZoomControls: Boolean = false
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
          zoomControl: $showZoomControls,
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
    modifier: Modifier = Modifier,
    showZoomControls: Boolean = false
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

    val html = remember(gpsPoints, showZoomControls) {
        val segments = buildRouteSegments(gpsPoints)
        val json = Json.encodeToString(segments)
        generateLeafletHtml(json, showZoomControls = showZoomControls)
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

data class RouteLegendItem(
    val label: String,
    val color: Color
)

internal fun getPresentLegendItems(gpsPoints: List<GpsPoint>): List<RouteLegendItem> {
    val items = mutableListOf<RouteLegendItem>()
    val hasWarmup = gpsPoints.any {
        val l = it.phaseName.lowercase(Locale.ROOT)
        l.contains("warm") || l.contains("riscald")
    }
    val hasRun = gpsPoints.any {
        val l = it.phaseName.lowercase(Locale.ROOT)
        l.contains("run") || l.contains("corsa")
    }
    val hasWalk = gpsPoints.any {
        val l = it.phaseName.lowercase(Locale.ROOT)
        l.contains("walk") || l.contains("cammin")
    }
    val hasCooldown = gpsPoints.any {
        val l = it.phaseName.lowercase(Locale.ROOT)
        l.contains("cool") || l.contains("defatic")
    }

    if (hasWarmup) items.add(RouteLegendItem("Riscaldamento", Color(0xFF3B82F6)))
    if (hasRun) items.add(RouteLegendItem("Corsa", Color(0xFFEF4444)))
    if (hasWalk) items.add(RouteLegendItem("Camminata", Color(0xFF10B981)))
    if (hasCooldown) items.add(RouteLegendItem("Defaticamento", Color(0xFF8B5CF6)))

    if (items.isEmpty()) {
        items.add(RouteLegendItem("Corsa", Color(0xFFEF4444)))
        items.add(RouteLegendItem("Camminata", Color(0xFF10B981)))
    }
    return items
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RouteLegendRow(
    gpsPoints: List<GpsPoint>,
    modifier: Modifier = Modifier,
    includeMarkers: Boolean = false
) {
    val legendItems = remember(gpsPoints) { getPresentLegendItems(gpsPoints) }

    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        legendItems.forEach { item ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(color = item.color, shape = CircleShape)
                )
                Text(
                    text = item.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (includeMarkers && gpsPoints.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                        .background(color = Color(0xFF10B981), shape = CircleShape)
                )
                Text(
                    text = "Partenza",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (gpsPoints.size > 1) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .border(1.5.dp, MaterialTheme.colorScheme.surface, CircleShape)
                            .background(color = Color(0xFFEF4444), shape = CircleShape)
                    )
                    Text(
                        text = "Arrivo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FullscreenRouteMapDialog(
    workoutName: String,
    gpsPoints: List<GpsPoint>,
    onDismiss: () -> Unit,
    onExportGpx: (() -> Unit)? = null
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Column {
                                Text(
                                    text = workoutName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Mappa percorso",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onDismiss) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Chiudi"
                                )
                            }
                        },
                        actions = {
                            if (onExportGpx != null) {
                                IconButton(onClick = onExportGpx) {
                                    Icon(
                                        imageVector = Icons.Filled.Share,
                                        contentDescription = "Salva GPX"
                                    )
                                }
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface
                        )
                    )
                }
            ) { paddingValues ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                ) {
                    OpenStreetMapRouteView(
                        gpsPoints = gpsPoints,
                        showZoomControls = true,
                        modifier = Modifier.fillMaxSize()
                    )

                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                            .fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f)
                        ),
                        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "Legenda percorso",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            RouteLegendRow(
                                gpsPoints = gpsPoints,
                                includeMarkers = true
                            )
                        }
                    }
                }
            }
        }
    }
}
