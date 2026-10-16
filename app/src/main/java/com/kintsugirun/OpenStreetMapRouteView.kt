package com.kintsugirun

import android.annotation.SuppressLint
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.Locale

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

internal fun generateLeafletHtml(segmentsJson: String): String {
    return """
<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8" />
  <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
  <link rel="stylesheet" href="https://unpkg.com/leaflet@1.9.4/dist/leaflet.css" />
  <script src="https://unpkg.com/leaflet@1.9.4/dist/leaflet.js"></script>
  <style>
    html, body, #map {
      height: 100%;
      width: 100%;
      margin: 0;
      padding: 0;
      background-color: #1e293b;
    }
    .leaflet-control-attribution {
      font-size: 8px !important;
      background: rgba(255, 255, 255, 0.6) !important;
      padding: 0 4px !important;
    }
  </style>
</head>
<body>
  <div id="map"></div>
  <script>
    document.addEventListener("DOMContentLoaded", function() {
      try {
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
      } catch (err) {
        console.error("Leaflet error:", err);
      }
    });
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

    val html = remember(gpsPoints) {
        val segments = buildRouteSegments(gpsPoints)
        val json = Json.encodeToString(segments)
        generateLeafletHtml(json)
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                tag = html
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    loadWithOverviewMode = true
                    useWideViewPort = true
                }
                webViewClient = object : WebViewClient() {
                    override fun onReceivedError(
                        view: WebView?,
                        request: WebResourceRequest?,
                        error: WebResourceError?
                    ) {
                        super.onReceivedError(view, request, error)
                    }
                }
                loadDataWithBaseURL("https://tile.openstreetmap.org/", html, "text/html", "UTF-8", null)
            }
        },
        update = { webView ->
            if (webView.tag != html) {
                webView.tag = html
                webView.loadDataWithBaseURL("https://tile.openstreetmap.org/", html, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier.clip(RoundedCornerShape(12.dp))
    )
}
