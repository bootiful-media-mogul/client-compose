package com.joshlong.mogul.client.podcasts.segments

private val wholeNumber = Regex("""^\d+$""")
private val fractionalNumber = Regex("""^\d+(\.\d+)?$""")

/**
 * reads a time someone typed: plain seconds ("90", "90.5"), minutes and seconds ("1:30",
 * "1:30.25"), or hours, minutes and seconds ("1:02:03.5"). a trailing "s" is fine. returns
 * seconds, or null for anything that isn't a time.
 */
fun parseTimecode(text: String): Double? {
    val trimmed = text.trim().let { if (it.endsWith("s", ignoreCase = true)) it.dropLast(1) else it }
    if (trimmed.isEmpty()) return null
    val parts = trimmed.split(':')
    if (parts.size > 3) return null
    // only the seconds may have a fraction; hours and minutes are whole
    val numbers = parts.mapIndexed { index, part ->
        val pattern = if (index == parts.size - 1) fractionalNumber else wholeNumber
        if (pattern.matches(part)) part.toDouble() else return null
    }
    // past the first part, minutes and seconds stay under 60: "1:75" is a typo, not 2:15
    if (numbers.drop(1).any { it >= 60 }) return null
    return numbers.fold(0.0) { total, n -> total * 60 + n }
}
