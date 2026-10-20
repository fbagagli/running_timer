package com.kintsugirun

import android.content.Context
import android.net.Uri
import java.io.IOException
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

object GpxFileManager {

    private val iso8601Format = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
        timeZone = TimeZone.getTimeZone("UTC")
    }

    /**
     * Sanitizes a workout name to ensure a safe, valid file name ending with `.gpx`.
     * Replaces illegal file system characters (`/`, `\`, `:`, `*`, `?`, `"`, `<`, `>`, `|` and control characters) with `_`.
     * Fallbacks to "workout.gpx" if the resulting base name is blank or invalid.
     */
    fun sanitizeFileName(workoutName: String): String {
        val trimmed = workoutName.trim()
        val baseName = if (trimmed.endsWith(".gpx", ignoreCase = true)) {
            trimmed.dropLast(4).trim()
        } else {
            trimmed
        }
        val sanitized = baseName.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1F]"), "_").trim()
        val finalBase = if (sanitized.isBlank() || sanitized.all { it == '_' || it == '.' }) {
            "workout"
        } else {
            sanitized
        }
        return "$finalBase.gpx"
    }

    /**
     * Writes GPX 1.1 XML formatted data for [points] directly to [outputStream].
     * Uses a buffered writer and flushes the content upon completion.
     */
    fun writeGpxToStream(
        outputStream: OutputStream,
        workoutName: String,
        points: List<GpsPoint>
    ): Result<Unit> = runCatching {
        val writer = outputStream.bufferedWriter(Charsets.UTF_8)
        writer.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        writer.write("<gpx version=\"1.1\" creator=\"KintsugiRun\" xmlns=\"http://www.topografix.com/GPX/1/1\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://www.topografix.com/GPX/1/1 http://www.topografix.com/GPX/1/1/gpx.xsd\">\n")
        writer.write("  <trk>\n")
        writer.write("    <name>")
        writer.write(escapeXml(workoutName))
        writer.write("</name>\n")
        writer.write("    <trkseg>\n")
        for (point in points) {
            val formattedTime = synchronized(iso8601Format) {
                iso8601Format.format(Date(point.timestamp))
            }
            writer.write("      <trkpt lat=\"")
            writer.write(point.latitude.toString())
            writer.write("\" lon=\"")
            writer.write(point.longitude.toString())
            writer.write("\">\n")
            if (point.altitude != 0.0) {
                writer.write("        <ele>")
                writer.write(point.altitude.toString())
                writer.write("</ele>\n")
            }
            writer.write("        <time>")
            writer.write(formattedTime)
            writer.write("</time>\n")
            writer.write("      </trkpt>\n")
        }
        writer.write("    </trkseg>\n")
        writer.write("  </trk>\n")
        writer.write("</gpx>\n")
        writer.flush()
    }

    /**
     * Safely opens [uri] via [Context.getContentResolver] and streams GPX content into it.
     * Ensures all streams are closed properly and returns a [Result] wrapping success or any [Exception].
     */
    fun saveGpxToUri(
        context: Context,
        uri: Uri,
        workoutName: String,
        points: List<GpsPoint>
    ): Result<Unit> = runCatching {
        val stream = context.contentResolver.openOutputStream(uri)
            ?: throw IOException("Unable to open output stream for URI: $uri")
        stream.use { outputStream ->
            writeGpxToStream(outputStream, workoutName, points).getOrThrow()
        }
    }

    internal fun escapeXml(input: String): String {
        return input
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
