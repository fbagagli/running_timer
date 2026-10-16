package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class GpxExportHelperTest {

    @Test
    fun `generateGpx generates valid GPX 1_1 XML with waypoints and coordinates`() {
        val points = listOf(
            GpsPoint(
                latitude = 45.4642,
                longitude = 9.1900,
                altitude = 120.5,
                timestamp = 1700000000000L, // 2023-11-14T22:13:20Z
                phaseName = "Run"
            ),
            GpsPoint(
                latitude = 45.4645,
                longitude = 9.1905,
                altitude = 0.0,
                timestamp = 1700000010000L, // 2023-11-14T22:13:30Z
                phaseName = "Walk"
            )
        )

        val gpx = GpxExportHelper.generateGpx("Morning Run", points)

        assertThat(gpx).startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        assertThat(gpx).contains("<gpx version=\"1.1\" creator=\"KintsugiRun\"")
        assertThat(gpx).contains("<name>Morning Run</name>")
        assertThat(gpx).contains("<trkseg>")
        assertThat(gpx).contains("<trkpt lat=\"45.4642\" lon=\"9.19\">")
        assertThat(gpx).contains("<ele>120.5</ele>")
        assertThat(gpx).contains("<time>2023-11-14T22:13:20Z</time>")
        assertThat(gpx).contains("<trkpt lat=\"45.4645\" lon=\"9.1905\">")
        assertThat(gpx).contains("<time>2023-11-14T22:13:30Z</time>")
        assertThat(gpx).contains("</trkseg>")
        assertThat(gpx).contains("</gpx>")
    }

    @Test
    fun `generateGpx escapes XML special characters in workout name`() {
        val gpx = GpxExportHelper.generateGpx("Speed & Sprint <5K> \"Night\"", emptyList())

        assertThat(gpx).contains("<name>Speed &amp; Sprint &lt;5K&gt; &quot;Night&quot;</name>")
        assertThat(gpx).contains("<trkseg>\n    </trkseg>")
    }

    @Test
    fun `generateGpx with empty points produces empty trkseg`() {
        val gpx = GpxExportHelper.generateGpx("Empty Workout", emptyList())

        assertThat(gpx).contains("<trkseg>")
        assertThat(gpx).doesNotContain("<trkpt")
        assertThat(gpx).contains("</trkseg>")
    }
}
