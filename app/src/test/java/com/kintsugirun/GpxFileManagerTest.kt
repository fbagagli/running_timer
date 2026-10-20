package com.kintsugirun

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream

class GpxFileManagerTest {

    @Test
    fun `sanitizeFileName handles normal names`() {
        assertThat(GpxFileManager.sanitizeFileName("MorningRun")).isEqualTo("MorningRun.gpx")
    }

    @Test
    fun `sanitizeFileName handles names with spaces`() {
        assertThat(GpxFileManager.sanitizeFileName("Morning Run 5K")).isEqualTo("Morning Run 5K.gpx")
        assertThat(GpxFileManager.sanitizeFileName("  Interval Training  ")).isEqualTo("Interval Training.gpx")
    }

    @Test
    fun `sanitizeFileName preserves existing gpx extension without duplicating`() {
        assertThat(GpxFileManager.sanitizeFileName("workout.gpx")).isEqualTo("workout.gpx")
        assertThat(GpxFileManager.sanitizeFileName("workout.GPX")).isEqualTo("workout.gpx")
        assertThat(GpxFileManager.sanitizeFileName("  my_run.Gpx  ")).isEqualTo("my_run.gpx")
    }

    @Test
    fun `sanitizeFileName replaces illegal file system characters with underscore`() {
        val dirtyName = "Workout / Test \\ Speed : 5* ? <Fast> | \"Trail\""
        val sanitized = GpxFileManager.sanitizeFileName(dirtyName)
        assertThat(sanitized).isEqualTo("Workout _ Test _ Speed _ 5_ _ _Fast_ _ _Trail_.gpx")
    }

    @Test
    fun `sanitizeFileName handles edge cases and blank names by falling back to workout_gpx`() {
        assertThat(GpxFileManager.sanitizeFileName("")).isEqualTo("workout.gpx")
        assertThat(GpxFileManager.sanitizeFileName("   ")).isEqualTo("workout.gpx")
        assertThat(GpxFileManager.sanitizeFileName("/:*?\"<>|\\")).isEqualTo("workout.gpx")
        assertThat(GpxFileManager.sanitizeFileName("___")).isEqualTo("workout.gpx")
        assertThat(GpxFileManager.sanitizeFileName("...")).isEqualTo("workout.gpx")
        assertThat(GpxFileManager.sanitizeFileName(".gpx")).isEqualTo("workout.gpx")
    }

    @Test
    fun `writeGpxToStream writes valid GPX 1_1 XML to stream`() {
        val points = listOf(
            GpsPoint(
                latitude = 45.4642,
                longitude = 9.1900,
                altitude = 120.5,
                timestamp = 1700000000000L,
                phaseName = "Run"
            ),
            GpsPoint(
                latitude = 45.4645,
                longitude = 9.1905,
                altitude = 0.0,
                timestamp = 1700000010000L,
                phaseName = "Walk"
            )
        )

        val outputStream = ByteArrayOutputStream()
        val result = GpxFileManager.writeGpxToStream(outputStream, "Morning Run", points)

        assertThat(result.isSuccess).isTrue()
        val gpx = outputStream.toString(Charsets.UTF_8.name())
        assertThat(gpx).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        assertThat(gpx).contains("<gpx version=\"1.1\" creator=\"KintsugiRun\"")
        assertThat(gpx).contains("<name>Morning Run</name>")
        assertThat(gpx).contains("<trkseg>")
        assertThat(gpx).contains("<trkpt lat=\"45.4642\" lon=\"9.19\">")
        assertThat(gpx).contains("<ele>120.5</ele>")
        assertThat(gpx).contains("<time>2023-11-14T22:13:20Z</time>")
        assertThat(gpx).contains("<trkpt lat=\"45.4645\" lon=\"9.1905\">")
        // Altitude 0.0 should not produce an ele tag
        assertThat(gpx).doesNotContain("<ele>0.0</ele>")
        assertThat(gpx).contains("</trkseg>")
        assertThat(gpx).contains("</gpx>")
    }

    @Test
    fun `writeGpxToStream escapes XML special characters in workout name`() {
        val outputStream = ByteArrayOutputStream()
        val result = GpxFileManager.writeGpxToStream(
            outputStream,
            "Speed & Sprint <5K> \"Night\" 'Best'",
            emptyList()
        )

        assertThat(result.isSuccess).isTrue()
        val gpx = outputStream.toString(Charsets.UTF_8.name())
        assertThat(gpx).contains("<name>Speed &amp; Sprint &lt;5K&gt; &quot;Night&quot; &apos;Best&apos;</name>")
        assertThat(gpx).contains("<trkseg>\n    </trkseg>")
    }

    @Test
    fun `writeGpxToStream handles large dataset (5000 points) efficiently without issues`() {
        val baseTimestamp = 1700000000000L
        val points = (0 until 5000).map { i ->
            GpsPoint(
                latitude = 45.0 + (i * 0.0001),
                longitude = 9.0 + (i * 0.0001),
                altitude = 100.0 + (i % 50),
                timestamp = baseTimestamp + (i * 1000L),
                phaseName = if (i % 2 == 0) "Run" else "Walk"
            )
        }

        val outputStream = ByteArrayOutputStream()
        val startTime = System.currentTimeMillis()
        val result = GpxFileManager.writeGpxToStream(outputStream, "Marathon Session", points)
        val elapsed = System.currentTimeMillis() - startTime

        assertThat(result.isSuccess).isTrue()
        assertThat(elapsed).isLessThan(5000L) // Should stream very quickly

        val gpx = outputStream.toString(Charsets.UTF_8.name())
        assertThat(gpx).contains("<name>Marathon Session</name>")
        // Verify first and last points are written properly
        assertThat(gpx).contains("<trkpt lat=\"45.0\" lon=\"9.0\">")
        assertThat(gpx).contains("<trkpt lat=\"45.4999\" lon=\"9.4999\">")
        assertThat(gpx).endsWith("</gpx>\n")
    }

    @Test
    fun `writeGpxToStream returns failure when outputStream throws IOException`() {
        val throwingStream = object : OutputStream() {
            override fun write(b: Int) {
                throw IOException("Simulated disk error")
            }

            override fun write(b: ByteArray, off: Int, len: Int) {
                throw IOException("Simulated disk error")
            }
        }

        val result = GpxFileManager.writeGpxToStream(throwingStream, "Test", emptyList())
        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
        assertThat(result.exceptionOrNull()?.message).contains("Simulated disk error")
    }

    @Test
    fun `saveGpxToUri writes to contentResolver output stream and returns success`() {
        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()
        val baos = ByteArrayOutputStream()

        every { context.contentResolver } returns contentResolver
        every { contentResolver.openOutputStream(uri) } returns baos

        val points = listOf(
            GpsPoint(latitude = 45.1, longitude = 9.1, timestamp = 1700000000000L)
        )

        val result = GpxFileManager.saveGpxToUri(context, uri, "Sample", points)

        assertThat(result.isSuccess).isTrue()
        val content = baos.toString(Charsets.UTF_8.name())
        assertThat(content).contains("<name>Sample</name>")
        assertThat(content).contains("<trkpt lat=\"45.1\" lon=\"9.1\">")
    }

    @Test
    fun `saveGpxToUri returns failure when openOutputStream returns null`() {
        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()

        every { context.contentResolver } returns contentResolver
        every { contentResolver.openOutputStream(uri) } returns null

        val result = GpxFileManager.saveGpxToUri(context, uri, "Sample", emptyList())

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(IOException::class.java)
    }

    @Test
    fun `saveGpxToUri returns failure when openOutputStream throws exception`() {
        val context = mockk<Context>()
        val contentResolver = mockk<ContentResolver>()
        val uri = mockk<Uri>()

        every { context.contentResolver } returns contentResolver
        every { contentResolver.openOutputStream(uri) } throws SecurityException("Access denied")

        val result = GpxFileManager.saveGpxToUri(context, uri, "Sample", emptyList())

        assertThat(result.isFailure).isTrue()
        assertThat(result.exceptionOrNull()).isInstanceOf(SecurityException::class.java)
    }
}
