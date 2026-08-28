package com.kintsugirun

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

object WorkoutQrHelper {

    private const val SCHEME = "kintsugirun"
    private const val HOST = "workout"

    fun encodeWorkoutToUri(workout: Workout): String {
        val encodedName = URLEncoder.encode(workout.workoutName, StandardCharsets.UTF_8.name())
        return "$SCHEME://$HOST?name=$encodedName&w=${workout.warmupSeconds}&r=${workout.runSeconds}&k=${workout.walkSeconds}&n=${workout.repeats}&c=${workout.cooldownSeconds}"
    }

    fun parseWorkoutFromUri(rawUri: String): Workout? {
        return try {
            val uri = URI(rawUri)
            if (!uri.scheme.equals(SCHEME, ignoreCase = true) || !uri.host.equals(HOST, ignoreCase = true)) {
                return null
            }

            val queryParams = uri.rawQuery?.split("&")?.associate { param ->
                val parts = param.split("=", limit = 2)
                val key = parts.getOrNull(0) ?: ""
                val value = if (parts.size > 1) URLDecoder.decode(parts[1], StandardCharsets.UTF_8.name()) else ""
                key to value
            } ?: emptyMap()

            val name = queryParams["name"]?.takeIf { it.isNotBlank() } ?: "Imported Workout"
            val warmup = queryParams["w"]?.toIntOrNull() ?: 0
            val run = queryParams["r"]?.toIntOrNull() ?: 60
            val walk = queryParams["k"]?.toIntOrNull() ?: 60
            val repeats = queryParams["n"]?.toIntOrNull() ?: 1
            val cooldown = queryParams["c"]?.toIntOrNull() ?: 0

            Workout(
                id = 0,
                workoutName = name,
                warmupSeconds = warmup,
                runSeconds = run,
                walkSeconds = walk,
                repeats = repeats.coerceAtLeast(1),
                cooldownSeconds = cooldown
            )
        } catch (e: Exception) {
            null
        }
    }

    fun generateQrBitmap(content: String, sizePx: Int = 512): Bitmap {
        val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) Color.BLACK else Color.WHITE)
            }
        }
        return bitmap
    }
}
