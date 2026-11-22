package com.joshlong.mogul.client.i18n

/**
 * the vue-i18n messages, looked up the way vue-i18n did: `t("app.menu.home")`,
 * `t("confirm.deletion", "title" to podcast.title)`, and `tc("...attachments", n)` for a
 * `one | many` message. an unknown key comes back as the key, as it did there.
 */
fun t(key: String, vararg args: Pair<String, Any?>): String = interpolate(message(key) ?: key, args.toMap())

fun t(key: String, args: Map<String, Any?>): String = interpolate(message(key) ?: key, args)

/**
 * strings the Compose client has that the Vue client didn't (the Vue client hardcoded them,
 * or had no such place). Messages.kt is generated from the Vue client's messages, so new
 * ones go here rather than there.
 */
private val composeMessages: Map<String, String> = mapOf(
    "app.redirecting" to "Redirecting…",
)

private fun message(key: String): String? = englishMessages[key] ?: composeMessages[key]

/** a plural message: `one | many`, or `none | one | many`; `{n}` and `{count}` are the count */
fun tc(key: String, count: Int, vararg args: Pair<String, Any?>): String {
    val message = message(key) ?: return key
    val choices = message.split(" | ", "|").map { it.trim() }
    val choice = when {
        choices.size >= 3 -> choices[if (count == 0) 0 else if (count == 1) 1 else 2]
        choices.size == 2 -> choices[if (count == 1) 0 else 1]
        else -> choices[0]
    }
    return interpolate(choice, mapOf("n" to count, "count" to count) + args.toMap())
}

/** whether a message exists for the key */
fun te(key: String): Boolean = message(key) != null

private val placeholder = Regex("""\{\s*([\w.-]+)\s*\}""")

private fun interpolate(message: String, args: Map<String, Any?>): String =
    placeholder.replace(message) { match ->
        val name = match.groupValues[1]
        if (args.containsKey(name)) args[name]?.toString() ?: "" else match.value
    }
