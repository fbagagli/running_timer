package com.kintsugirun

import android.content.Context
import android.content.res.AssetManager
import com.google.common.truth.Truth.assertThat
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.After
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File

class OpenStreetMapRouteViewTest {

    @After
    fun tearDown() {
        LeafletAssetLoader.resetCache()
    }

    @Test
    fun `getPhaseColor returns appropriate colors for phases`() {
        assertThat(getPhaseColor("Run")).isEqualTo("#ef4444")
        assertThat(getPhaseColor("Interval Run")).isEqualTo("#ef4444")
        assertThat(getPhaseColor("Corsa")).isEqualTo("#ef4444")

        assertThat(getPhaseColor("Walk")).isEqualTo("#10b981")
        assertThat(getPhaseColor("Recovery Walk")).isEqualTo("#10b981")
        assertThat(getPhaseColor("Camminata")).isEqualTo("#10b981")

        assertThat(getPhaseColor("Warmup")).isEqualTo("#3b82f6")
        assertThat(getPhaseColor("Riscaldamento")).isEqualTo("#3b82f6")

        assertThat(getPhaseColor("Cooldown")).isEqualTo("#8b5cf6")
        assertThat(getPhaseColor("Defaticamento")).isEqualTo("#8b5cf6")

        assertThat(getPhaseColor("Other")).isEqualTo("#f59e0b")
    }

    @Test
    fun `buildRouteSegments returns empty list when given empty points`() {
        val segments = buildRouteSegments(emptyList())
        assertThat(segments).isEmpty()
    }

    @Test
    fun `buildRouteSegments groups consecutive points with same phase and splits on phase change`() {
        val points = listOf(
            GpsPoint(45.0, 9.0, phaseName = "Run"),
            GpsPoint(45.001, 9.001, phaseName = "Run"),
            GpsPoint(45.002, 9.002, phaseName = "Walk"),
            GpsPoint(45.003, 9.003, phaseName = "Walk")
        )

        val segments = buildRouteSegments(points)
        assertThat(segments).hasSize(2)

        // First segment is Run (#ef4444) and connects to point 2
        assertThat(segments[0].color).isEqualTo("#ef4444")
        assertThat(segments[0].coords).containsExactly(
            listOf(45.0, 9.0),
            listOf(45.001, 9.001),
            listOf(45.002, 9.002)
        ).inOrder()

        // Second segment is Walk (#10b981) starting from point 2 to point 3
        assertThat(segments[1].color).isEqualTo("#10b981")
        assertThat(segments[1].coords).containsExactly(
            listOf(45.002, 9.002),
            listOf(45.003, 9.003)
        ).inOrder()
    }

    @Test
    fun `generateLeafletHtml inlines CSS and JS without unpkg external links`() {
        val testCss = "/* test leaflet css */"
        val testJs = "/* test leaflet js */"
        val html = generateLeafletHtml("[]", leafletCss = testCss, leafletJs = testJs)

        assertThat(html).contains(testCss)
        assertThat(html).contains(testJs)
        assertThat(html).doesNotContain("unpkg.com")
        assertThat(html).contains("<div id=\"map\"></div>")
        assertThat(html).contains("map.invalidateSize();")
        assertThat(html).contains("https://tile.openstreetmap.org/")
    }

    @Test
    fun `generateLeafletHtml embeds segments json correctly`() {
        val segmentsJson = """[{"color":"#ef4444","coords":[[45.0,9.0],[45.1,9.1]]}]"""
        val html = generateLeafletHtml(segmentsJson)

        assertThat(html).contains("var segments = $segmentsJson;")
    }

    @Test
    fun `generateLeafletHtml respects showZoomControls parameter`() {
        val htmlDefault = generateLeafletHtml("[]")
        assertThat(htmlDefault).contains("zoomControl: false")

        val htmlZoomFalse = generateLeafletHtml("[]", showZoomControls = false)
        assertThat(htmlZoomFalse).contains("zoomControl: false")

        val htmlZoomTrue = generateLeafletHtml("[]", showZoomControls = true)
        assertThat(htmlZoomTrue).contains("zoomControl: true")
    }

    @Test
    fun `getPresentLegendItems detects all present workout phases`() {
        val points = listOf(
            GpsPoint(45.0, 9.0, 0.0, 1000L, "Warmup"),
            GpsPoint(45.1, 9.1, 0.0, 2000L, "Run 1"),
            GpsPoint(45.2, 9.2, 0.0, 3000L, "Walk 1"),
            GpsPoint(45.3, 9.3, 0.0, 4000L, "Cooldown")
        )

        val items = getPresentLegendItems(points)
        assertThat(items.map { it.label }).containsExactly(
            "Riscaldamento",
            "Corsa",
            "Camminata",
            "Defaticamento"
        ).inOrder()

        assertThat(items[0].color).isEqualTo(androidx.compose.ui.graphics.Color(0xFF3B82F6))
        assertThat(items[1].color).isEqualTo(androidx.compose.ui.graphics.Color(0xFFEF4444))
        assertThat(items[2].color).isEqualTo(androidx.compose.ui.graphics.Color(0xFF10B981))
        assertThat(items[3].color).isEqualTo(androidx.compose.ui.graphics.Color(0xFF8B5CF6))
    }

    @Test
    fun `getPresentLegendItems filters only phases that exist in points`() {
        val points = listOf(
            GpsPoint(45.0, 9.0, 0.0, 1000L, "Corsa veloce"),
            GpsPoint(45.1, 9.1, 0.0, 2000L, "Defaticamento blando")
        )

        val items = getPresentLegendItems(points)
        assertThat(items.map { it.label }).containsExactly("Corsa", "Defaticamento").inOrder()
    }

    @Test
    fun `getPresentLegendItems returns fallback items when empty`() {
        val items = getPresentLegendItems(emptyList())
        assertThat(items.map { it.label }).containsExactly("Corsa", "Camminata").inOrder()
    }

    @Test
    fun `LeafletAssetLoader loads and caches CSS and JS from assets`() {
        val mockContext = mockk<Context>()
        val mockAssets = mockk<AssetManager>()

        every { mockContext.assets } returns mockAssets
        every { mockAssets.open("leaflet/leaflet.css") } answers {
            ByteArrayInputStream("body { margin: 0; }".toByteArray())
        }
        every { mockAssets.open("leaflet/leaflet.js") } answers {
            ByteArrayInputStream("window.L = {};".toByteArray())
        }

        // First call loads from assets
        val css1 = LeafletAssetLoader.getLeafletCss(mockContext)
        val js1 = LeafletAssetLoader.getLeafletJs(mockContext)
        assertThat(css1).isEqualTo("body { margin: 0; }")
        assertThat(js1).isEqualTo("window.L = {};")

        // Second call uses cached values without re-opening assets
        val css2 = LeafletAssetLoader.getLeafletCss(mockContext)
        val js2 = LeafletAssetLoader.getLeafletJs(mockContext)
        assertThat(css2).isEqualTo("body { margin: 0; }")
        assertThat(js2).isEqualTo("window.L = {};")

        verify(exactly = 1) { mockAssets.open("leaflet/leaflet.css") }
        verify(exactly = 1) { mockAssets.open("leaflet/leaflet.js") }
    }

    @Test
    fun `bundled Leaflet assets exist in main assets folder and are non-empty`() {
        val candidates = listOf(
            File("src/main/assets/leaflet"),
            File("app/src/main/assets/leaflet")
        )
        val dir = candidates.firstOrNull { it.exists() }
        assertThat(dir).isNotNull()

        val cssFile = File(dir, "leaflet.css")
        val jsFile = File(dir, "leaflet.js")

        assertThat(cssFile.exists()).isTrue()
        assertThat(cssFile.length()).isGreaterThan(1000L)

        assertThat(jsFile.exists()).isTrue()
        assertThat(jsFile.length()).isGreaterThan(10000L)
    }

    @Test
    fun `MAP_USER_AGENT is configured for OpenStreetMap compliance`() {
        assertThat(MAP_USER_AGENT).contains("KintsugiRun")
        assertThat(MAP_USER_AGENT).contains("Android")
    }

    @Test
    fun `formatPace formats seconds per km correctly`() {
        assertThat(formatPace(0)).isEqualTo("--'--\" /km")
        assertThat(formatPace(-10)).isEqualTo("--'--\" /km")
        assertThat(formatPace(300)).isEqualTo("5'00\" /km")
        assertThat(formatPace(325)).isEqualTo("5'25\" /km")
        assertThat(formatPace(605)).isEqualTo("10'05\" /km")
    }
}
