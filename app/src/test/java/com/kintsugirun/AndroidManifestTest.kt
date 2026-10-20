package com.kintsugirun

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class AndroidManifestTest {

    private fun getManifestFile(): File {
        val candidates = listOf(
            File("src/main/AndroidManifest.xml"),
            File("app/src/main/AndroidManifest.xml")
        )
        val file = candidates.firstOrNull { it.exists() }
        assertThat(file).isNotNull()
        return file!!
    }

    @Test
    fun `manifest contains all required permissions including INTERNET and location`() {
        val file = getManifestFile()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val nodes = doc.getElementsByTagName("uses-permission")
        val permissions = (0 until nodes.length).map { i ->
            (nodes.item(i) as Element).getAttribute("android:name")
        }.toSet()

        assertThat(permissions).contains("android.permission.INTERNET")
        assertThat(permissions).contains("android.permission.FOREGROUND_SERVICE")
        assertThat(permissions).contains("android.permission.FOREGROUND_SERVICE_SPECIAL_USE")
        assertThat(permissions).contains("android.permission.FOREGROUND_SERVICE_LOCATION")
        assertThat(permissions).contains("android.permission.ACCESS_FINE_LOCATION")
        assertThat(permissions).contains("android.permission.ACCESS_COARSE_LOCATION")
        assertThat(permissions).contains("android.permission.WAKE_LOCK")
        assertThat(permissions).contains("android.permission.POST_NOTIFICATIONS")
    }

    @Test
    fun `manifest declares TimerService with location foreground service type`() {
        val file = getManifestFile()
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file)
        val services = doc.getElementsByTagName("service")
        var timerServiceFound = false
        for (i in 0 until services.length) {
            val service = services.item(i) as Element
            if (service.getAttribute("android:name") == ".TimerService") {
                timerServiceFound = true
                val fgType = service.getAttribute("android:foregroundServiceType")
                assertThat(fgType).contains("location")
                assertThat(fgType).contains("specialUse")
            }
        }
        assertThat(timerServiceFound).isTrue()
    }
}
