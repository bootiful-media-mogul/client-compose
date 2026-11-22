package com.joshlong.mogul.client.publications

import com.joshlong.mogul.client.api.AyrsharePublicationComposition

// how long a post each platform takes. the labels live in the i18n messages
val AYRSHARE_PLATFORM_LIMITS: Map<String, Int> = mapOf(
    "gmb" to 1500,
    "facebook" to 63206,
    "linkedin" to 3000,
    "instagram" to 2200,
    "telegram" to 4096,
    "snapchat" to 80,
    "pinterest" to 500,
    "threads" to 25000,
    "youtube" to 5000,
    "reddit" to 40000,
    "bluesky" to 300,
    "tiktok" to 2200,
    "twitter" to 280,
)

/**
 * what the user has written for an Ayrshare post: one shared text, the platforms it goes
 * to, and the platforms that get their own text instead. a platform has its own text
 * exactly when it has a key in `overrides`, even if that text happens to match the shared.
 */
data class AyrshareDraft(
    val shared: String,
    val selected: List<String>,
    val overrides: Map<String, String>,
)

fun isCustomized(draft: AyrshareDraft, platform: String): Boolean = draft.overrides.containsKey(platform)

fun effectiveText(draft: AyrshareDraft, platform: String): String =
    if (isCustomized(draft, platform)) draft.overrides.getValue(platform) else draft.shared

fun charactersOver(draft: AyrshareDraft, platform: String): Int {
    val max = AYRSHARE_PLATFORM_LIMITS[platform] ?: return 0
    return maxOf(0, effectiveText(draft, platform).length - max)
}

fun isBlank(draft: AyrshareDraft, platform: String): Boolean = effectiveText(draft, platform).isBlank()

// the api turns down an empty post, so every selected platform needs some text
fun isPublishable(draft: AyrshareDraft): Boolean =
    draft.selected.isNotEmpty() && draft.selected.all { !isBlank(draft, it) }

/**
 * the context the Ayrshare publisher plugins read: each selected platform's text under its
 * code, and the id of its draft composition (whose attachments go out as the post's media)
 * under `{code}CompositionId`
 */
fun buildClientContext(draft: AyrshareDraft, compositions: List<AyrsharePublicationComposition>): Map<String, Any?> {
    val context = linkedMapOf<String, Any?>()
    compositions.forEach { c ->
        if (c.platform in draft.selected) {
            context[c.platform] = effectiveText(draft, c.platform)
            context[c.platform + "CompositionId"] = c.composition.id
        }
    }
    return context
}

/**
 * the badge that stands for a platform: its initials on its own color. letters rather than
 * logos, so there's no artwork to license or keep current. every pairing clears 4.5:1.
 */
data class AyrsharePlatformBadge(val initials: String, val color: Long, val ink: Long)

private const val WHITE = 0xffffffL

val AYRSHARE_PLATFORM_BADGES: Map<String, AyrsharePlatformBadge> = mapOf(
    "gmb" to AyrsharePlatformBadge("G", 0x1e7e34, WHITE),
    "facebook" to AyrsharePlatformBadge("f", 0x1565d8, WHITE),
    "linkedin" to AyrsharePlatformBadge("in", 0x0a66c2, WHITE),
    "instagram" to AyrsharePlatformBadge("Ig", 0xc13584, WHITE),
    "telegram" to AyrsharePlatformBadge("Tg", 0x1873a8, WHITE),
    "snapchat" to AyrsharePlatformBadge("Sc", 0xfffc00, 0x0a0f1a),
    "pinterest" to AyrsharePlatformBadge("P", 0xc8001e, WHITE),
    "threads" to AyrsharePlatformBadge("Th", 0x262626, WHITE),
    "youtube" to AyrsharePlatformBadge("YT", 0xcc0000, WHITE),
    "reddit" to AyrsharePlatformBadge("R", 0xd93a00, WHITE),
    "bluesky" to AyrsharePlatformBadge("Bs", 0x0a6fd8, WHITE),
    "tiktok" to AyrsharePlatformBadge("Tk", 0x111111, WHITE),
    "twitter" to AyrsharePlatformBadge("X", 0x000000, WHITE),
)

fun badgeFor(platform: String): AyrsharePlatformBadge =
    AYRSHARE_PLATFORM_BADGES[platform] ?: AyrsharePlatformBadge("?", 0x405069, WHITE)

/** an integer with thousands separators, as Intl.NumberFormat('en') wrote it */
fun formatNumber(n: Int): String {
    val digits = kotlin.math.abs(n).toString()
    val grouped = digits.reversed().chunked(3).joinToString(",").reversed()
    return if (n < 0) "-$grouped" else grouped
}
