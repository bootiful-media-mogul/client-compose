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
    // the apps sign in themselves; the web build never shows these
    "buttons.ok" to "OK",
    "app.sign-in" to "Sign in",
    "app.sign-in.prompt" to "Sign in to Mogul to reach your podcasts, blogs, and notes.",
    "app.sign-in.failed" to "Signing in didn't work: {message}",
    "app.sign-out" to "Sign out",
    // the desktop app, when there's a newer release than it
    "updates.available" to "Mogul Studio {latest} is out. This is {current}.",
    "updates.download" to "Download",
    "updates.later" to "Later",
    // a note's url, read and summarized into its text
    "notes.buttons.summarize" to "Summarize",
    "notes.buttons.summarizing" to "Summarizing…",
    "notes.summary.errors.address" to "Mogul only reads pages on the public internet, so it can't summarize this one.",
    "notes.summary.errors.blocked" to "This site doesn't let automated readers in, so Mogul can't summarize it.",
    "notes.summary.errors.not-found" to "There's no page at that address.",
    "notes.summary.errors.server-error" to "The site had an error. Try again later.",
    "notes.summary.errors.status" to "The site wouldn't send the page, so there's no summary.",
    "notes.summary.errors.redirects" to "The page redirects too many times to summarize.",
    "notes.summary.errors.content-type" to "This isn't a web page (it may be a PDF or an image), so there's no summary.",
    "notes.summary.errors.encoding" to "The site sent the page in a form Mogul doesn't read.",
    "notes.summary.errors.not-html" to "The site says this is a web page, but it isn't, so there's no summary.",
    "notes.summary.errors.empty" to "There's no text on the page to summarize.",
    "notes.summary.errors.unreachable" to "Mogul couldn't reach the page. Try again later.",
    "notes.summary.errors.model" to "The page was read, but summarizing it didn't work. Try again.",
    "notes.summary.errors.timeout" to "Summarizing is taking too long. Try again later.",
    "notes.summary.errors.failed" to "This page couldn't be summarized. Try again.",
    // a list that loads a page at a time as you scroll
    "paging.loading" to "Loading…",
    "paging.failed" to "The rest couldn't be loaded: {message}",
    "paging.retry" to "Try again",
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
