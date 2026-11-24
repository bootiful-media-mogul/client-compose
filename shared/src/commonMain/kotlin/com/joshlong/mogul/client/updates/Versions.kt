package com.joshlong.mogul.client.updates

/** a release's version as the desktop releases number them, 1.0.37, or as its tag does, v1.0.37 */
internal data class Version(val parts: List<Int>) : Comparable<Version> {

    // 1.0 and 1.0.0 are the same version
    override fun compareTo(other: Version): Int {
        for (i in 0 until maxOf(this.parts.size, other.parts.size)) {
            val compared = this.parts.getOrElse(i) { 0 }.compareTo(other.parts.getOrElse(i) { 0 })
            if (compared != 0) return compared
        }
        return 0
    }

    override fun toString(): String = this.parts.joinToString(".")

    companion object {
        /** the version, or null if the text isn't one */
        fun parse(text: String): Version? {
            val parts = text.trim().removePrefix("v").split('.').map { it.toIntOrNull()?.takeIf { part -> part >= 0 } ?: return null }
            return Version(parts)
        }
    }
}

/**
 * the installer for this system and cpu among a release's files, which bin/desktop.sh names
 * for both: mogul-studio-1.0.37-macos-arm64.dmg
 */
internal fun installerFor(files: List<String>, system: String, cpu: String): String? =
    files.firstOrNull { it.contains("-$system-$cpu.") }
