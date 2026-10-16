package com.kintsugirun

import kotlinx.serialization.Serializable
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Serializable
data class GpsPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
    val timestamp: Long = System.currentTimeMillis(),
    val phaseName: String = ""
)

@Serializable
data class GpsMetrics(
    val totalDistanceMeters: Double = 0.0,
    val runDistanceMeters: Double = 0.0,
    val walkDistanceMeters: Double = 0.0,
    val currentIntervalDistanceMeters: Double = 0.0,
    val currentIntervalPaceSecPerKm: Int = 0,
    val points: List<GpsPoint> = emptyList()
)

object GpxExportHelper {
    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    fun generateGpx(workoutName: String, points: List<GpsPoint>): String {
        val safeName = escapeXml(workoutName)
        val sb = StringBuilder()
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        sb.append("<gpx version=\"1.1\" creator=\"KintsugiRun\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        sb.append("  <trk>\n")
        sb.append("    <name>").append(safeName).append("</name>\n")
        sb.append("    <trkseg>\n")
        for (point in points) {
            val formattedTime = synchronized(iso8601Format) {
                iso8601Format.format(Date(point.timestamp))
            }
            sb.append("      <trkpt lat=\"").append(point.latitude).append("\" lon=\"").append(point.longitude).append("\">\n")
            if (point.altitude != 0.0) {
                sb.append("        <ele>").append(point.altitude).append("</ele>\n")
            }
            sb.append("        <time>").append(formattedTime).append("</time>\n")
            sb.append("      </trkpt>\n")
        }
        sb.append("    </trkseg>\n")
        sb.append("  </trk>\n")
        sb.append("</gpx>\n")
        return sb.toString()
    }

    private fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}

