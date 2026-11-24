package com.joshlong.mogul.client.updates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionsTest {

    private fun version(text: String) = Version.parse(text) ?: error("$text isn't a version")

    @Test
    fun parsesReleaseVersionsAndTags() {
        assertEquals(listOf(1, 0, 37), version("1.0.37").parts)
        assertEquals(listOf(1, 0, 37), version("v1.0.37").parts)
        assertEquals(listOf(2, 1), version(" 2.1 ").parts)
    }

    @Test
    fun rejectsWhatIsNotAVersion() {
        assertNull(Version.parse(""))
        assertNull(Version.parse("v"))
        assertNull(Version.parse("1.0.x"))
        assertNull(Version.parse("1..2"))
        assertNull(Version.parse("1.-2.0"))
        assertNull(Version.parse("latest"))
    }

    @Test
    fun comparesNumbersNotText() {
        assertTrue(version("1.0.10") > version("1.0.9"))
        assertTrue(version("1.1.0") > version("1.0.99"))
        assertTrue(version("2.0.0") > version("1.255.65535"))
        assertTrue(version("1.0.5") < version("v1.0.6"))
    }

    @Test
    fun missingPartsAreZero() {
        assertEquals(0, version("1.0").compareTo(version("1.0.0")))
        assertTrue(version("1.0.1") > version("1.0"))
    }

    @Test
    fun printsWithoutTheTagsV() {
        assertEquals("1.0.37", version("v1.0.37").toString())
    }

    @Test
    fun findsTheInstallerForThisSystemAndCpu() {
        val files = listOf(
            "mogul-studio-1.0.5-linux-x64.deb",
            "mogul-studio-1.0.5-macos-arm64.dmg",
            "mogul-studio-1.0.5-macos-x64.dmg",
            "mogul-studio-1.0.5-windows-x64.msi",
        )
        assertEquals("mogul-studio-1.0.5-macos-arm64.dmg", installerFor(files, "macos", "arm64"))
        assertEquals("mogul-studio-1.0.5-macos-x64.dmg", installerFor(files, "macos", "x64"))
        assertEquals("mogul-studio-1.0.5-windows-x64.msi", installerFor(files, "windows", "x64"))
        assertEquals("mogul-studio-1.0.5-linux-x64.deb", installerFor(files, "linux", "x64"))
        assertNull(installerFor(files, "linux", "arm64"))
    }
}
