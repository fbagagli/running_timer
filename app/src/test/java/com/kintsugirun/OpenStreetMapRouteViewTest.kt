package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OpenStreetMapRouteViewTest {

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
    fun `formatPace formats seconds per km correctly`() {
        assertThat(formatPace(0)).isEqualTo("--'--\" /km")
        assertThat(formatPace(-10)).isEqualTo("--'--\" /km")
        assertThat(formatPace(300)).isEqualTo("5'00\" /km")
        assertThat(formatPace(325)).isEqualTo("5'25\" /km")
        assertThat(formatPace(605)).isEqualTo("10'05\" /km")
    }
}
