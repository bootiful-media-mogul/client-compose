package com.joshlong.mogul.client.i18n

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** the languages the studio is written in. [label] is the language's name for itself */
enum class Language(val code: String, val label: String, val englishName: String) {
    English("en", "English", "English"),
    Spanish("es", "Español", "Spanish"),
    French("fr", "Français", "French");

    companion object {
        /** the language for a tag like `es`, `es-MX` or `fr_CA`, if the studio has it */
        fun of(tag: String?): Language? {
            val code = tag?.trim()?.lowercase()?.substringBefore('-')?.substringBefore('_')
            return entries.firstOrNull { it.code == code }
        }
    }
}

/**
 * the language every message is looked up in. it is snapshot state, so a composable that
 * calls [t] is drawn again when it changes. LanguageController sets it
 */
var currentLanguage: Language by mutableStateOf(Language.English)

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
    // the language the studio is shown in
    "settings.language.title" to "Language",
    "settings.language.label" to "Show Mogul in",
    "settings.language.hint" to "Saved to your account, so it follows you to every device. Summaries and AI writing use this language too.",
    "settings.language.save-failed" to "The language couldn't be saved to your account: {message}",
    // dates in words. weekdays count from monday
    "dates.months.1" to "January",
    "dates.months.2" to "February",
    "dates.months.3" to "March",
    "dates.months.4" to "April",
    "dates.months.5" to "May",
    "dates.months.6" to "June",
    "dates.months.7" to "July",
    "dates.months.8" to "August",
    "dates.months.9" to "September",
    "dates.months.10" to "October",
    "dates.months.11" to "November",
    "dates.months.12" to "December",
    "dates.weekdays.1" to "Monday",
    "dates.weekdays.2" to "Tuesday",
    "dates.weekdays.3" to "Wednesday",
    "dates.weekdays.4" to "Thursday",
    "dates.weekdays.5" to "Friday",
    "dates.weekdays.6" to "Saturday",
    "dates.weekdays.7" to "Sunday",
    "dates.long-day" to "{weekday}, {month} {day}",
    // the audio editor's track
    "track-editor.label" to "Audio track editor",
    "track-editor.go-to.label" to "Go to time",
    "track-editor.go-to.placeholder" to "go to 0:30.0",
    "track-editor.timeline" to "TIMELINE",
    "track-editor.track" to "Audio 1",
    "track-editor.format" to "Stereo · MP3",
    "track-editor.source" to "SOURCE TRACK",
    "track-editor.selected" to "{duration} selected",
    "track-editor.select-hint" to "Drag across the track to select a range",
    "track-editor.delete-range" to "Delete range",
    // the analysis panel: the most compelling parts of an episode or a post
    "app.panels.analysis" to "Analysis",
    "analysis.none" to "Open an episode or a post to find its most compelling parts.",
    "analysis.entity.title" to "Analysis for {entityName}",
    "analysis.buttons.analyze" to "Analyze",
    "analysis.buttons.reanalyze" to "Analyze again",
    "analysis.buttons.analyzing" to "Analyzing…",
    "analysis.analyzed" to "Analyzed {when}",
    "analysis.no-highlights" to "Nothing stood out.",
    "analysis.highlight.label" to "{kind} ({score}/10)",
    "analysis.highlight.label-in-section" to "{kind} ({score}/10), in {section}",
    "analysis.kinds.entertaining" to "Entertaining",
    "analysis.kinds.useful" to "Useful",
    "analysis.kinds.quotable" to "Quotable",
    "analysis.errors.empty" to "There's nothing to analyze yet. An episode needs a transcript first.",
    "analysis.errors.failed" to "The analysis didn't work. Try again in a little while.",
    "analysis.errors.timeout" to "The analysis is taking too long. Try again in a little while.",
)

private val spanishComposeMessages: Map<String, String> = mapOf(
    "app.redirecting" to "Redirigiendo…",
    "buttons.ok" to "Aceptar",
    "app.sign-in" to "Iniciar sesión",
    "app.sign-in.prompt" to "Inicia sesión en Mogul para acceder a tus podcasts, blogs y notas.",
    "app.sign-in.failed" to "No se pudo iniciar sesión: {message}",
    "app.sign-out" to "Cerrar sesión",
    "updates.available" to "Ya está disponible Mogul Studio {latest}. Esta es la versión {current}.",
    "updates.download" to "Descargar",
    "updates.later" to "Más tarde",
    "notes.buttons.summarize" to "Resumir",
    "notes.buttons.summarizing" to "Resumiendo…",
    "notes.summary.errors.address" to "Mogul solo lee páginas de la internet pública, así que no puede resumir esta.",
    "notes.summary.errors.blocked" to "Este sitio no deja entrar a lectores automáticos, así que Mogul no puede resumirlo.",
    "notes.summary.errors.not-found" to "No hay ninguna página en esa dirección.",
    "notes.summary.errors.server-error" to "El sitio tuvo un error. Inténtalo de nuevo más tarde.",
    "notes.summary.errors.status" to "El sitio no quiso enviar la página, así que no hay resumen.",
    "notes.summary.errors.redirects" to "La página redirige demasiadas veces para poder resumirla.",
    "notes.summary.errors.content-type" to "Esto no es una página web (puede que sea un PDF o una imagen), así que no hay resumen.",
    "notes.summary.errors.encoding" to "El sitio envió la página en un formato que Mogul no lee.",
    "notes.summary.errors.not-html" to "El sitio dice que esto es una página web, pero no lo es, así que no hay resumen.",
    "notes.summary.errors.empty" to "No hay texto en la página que resumir.",
    "notes.summary.errors.unreachable" to "Mogul no pudo acceder a la página. Inténtalo de nuevo más tarde.",
    "notes.summary.errors.model" to "La página se leyó, pero no se pudo resumir. Inténtalo de nuevo.",
    "notes.summary.errors.timeout" to "El resumen está tardando demasiado. Inténtalo de nuevo más tarde.",
    "notes.summary.errors.failed" to "No se pudo resumir esta página. Inténtalo de nuevo.",
    "paging.loading" to "Cargando…",
    "paging.failed" to "No se pudo cargar el resto: {message}",
    "paging.retry" to "Reintentar",
    "settings.language.title" to "Idioma",
    "settings.language.label" to "Mostrar Mogul en",
    "settings.language.hint" to "Se guarda en tu cuenta, así que te acompaña en todos tus dispositivos. Los resúmenes y los textos de la IA también se escriben en este idioma.",
    "settings.language.save-failed" to "No se pudo guardar el idioma en tu cuenta: {message}",
    "dates.months.1" to "enero",
    "dates.months.2" to "febrero",
    "dates.months.3" to "marzo",
    "dates.months.4" to "abril",
    "dates.months.5" to "mayo",
    "dates.months.6" to "junio",
    "dates.months.7" to "julio",
    "dates.months.8" to "agosto",
    "dates.months.9" to "septiembre",
    "dates.months.10" to "octubre",
    "dates.months.11" to "noviembre",
    "dates.months.12" to "diciembre",
    "dates.weekdays.1" to "lunes",
    "dates.weekdays.2" to "martes",
    "dates.weekdays.3" to "miércoles",
    "dates.weekdays.4" to "jueves",
    "dates.weekdays.5" to "viernes",
    "dates.weekdays.6" to "sábado",
    "dates.weekdays.7" to "domingo",
    "dates.long-day" to "{weekday}, {day} de {month}",
    "track-editor.label" to "Editor de la pista de audio",
    "track-editor.go-to.label" to "Ir a un momento",
    "track-editor.go-to.placeholder" to "ir a 0:30.0",
    "track-editor.timeline" to "LÍNEA DE TIEMPO",
    "track-editor.track" to "Audio 1",
    "track-editor.format" to "Estéreo · MP3",
    "track-editor.source" to "PISTA ORIGINAL",
    "track-editor.selected" to "{duration} seleccionados",
    "track-editor.select-hint" to "Arrastra sobre la pista para seleccionar un tramo",
    "track-editor.delete-range" to "Eliminar tramo",
    // the analysis panel: the most compelling parts of an episode or a post
    "app.panels.analysis" to "Análisis",
    "analysis.none" to "Abre un episodio o una entrada para encontrar sus partes más atractivas.",
    "analysis.entity.title" to "Análisis de {entityName}",
    "analysis.buttons.analyze" to "Analizar",
    "analysis.buttons.reanalyze" to "Volver a analizar",
    "analysis.buttons.analyzing" to "Analizando…",
    "analysis.analyzed" to "Analizado {when}",
    "analysis.no-highlights" to "Nada destacó.",
    "analysis.highlight.label" to "{kind} ({score}/10)",
    "analysis.highlight.label-in-section" to "{kind} ({score}/10), en {section}",
    "analysis.kinds.entertaining" to "Entretenido",
    "analysis.kinds.useful" to "Útil",
    "analysis.kinds.quotable" to "Citable",
    "analysis.errors.empty" to "Todavía no hay nada que analizar. Un episodio necesita una transcripción primero.",
    "analysis.errors.failed" to "El análisis no funcionó. Inténtalo de nuevo dentro de un rato.",
    "analysis.errors.timeout" to "El análisis está tardando demasiado. Inténtalo de nuevo dentro de un rato.",
)

private val frenchComposeMessages: Map<String, String> = mapOf(
    "app.redirecting" to "Redirection…",
    "buttons.ok" to "OK",
    "app.sign-in" to "Se connecter",
    "app.sign-in.prompt" to "Connectez-vous à Mogul pour retrouver vos podcasts, vos blogs et vos notes.",
    "app.sign-in.failed" to "La connexion a échoué : {message}",
    "app.sign-out" to "Se déconnecter",
    "updates.available" to "Mogul Studio {latest} est disponible. Vous utilisez la version {current}.",
    "updates.download" to "Télécharger",
    "updates.later" to "Plus tard",
    "notes.buttons.summarize" to "Résumer",
    "notes.buttons.summarizing" to "Résumé en cours…",
    "notes.summary.errors.address" to "Mogul ne lit que les pages de l'internet public ; il ne peut donc pas résumer celle-ci.",
    "notes.summary.errors.blocked" to "Ce site n'accepte pas les lecteurs automatisés ; Mogul ne peut donc pas le résumer.",
    "notes.summary.errors.not-found" to "Il n'y a aucune page à cette adresse.",
    "notes.summary.errors.server-error" to "Le site a rencontré une erreur. Réessayez plus tard.",
    "notes.summary.errors.status" to "Le site n'a pas voulu envoyer la page ; il n'y a donc pas de résumé.",
    "notes.summary.errors.redirects" to "La page redirige trop de fois pour être résumée.",
    "notes.summary.errors.content-type" to "Ce n'est pas une page web (peut-être un PDF ou une image) ; il n'y a donc pas de résumé.",
    "notes.summary.errors.encoding" to "Le site a envoyé la page sous une forme que Mogul ne sait pas lire.",
    "notes.summary.errors.not-html" to "Le site indique qu'il s'agit d'une page web, mais ce n'en est pas une ; il n'y a donc pas de résumé.",
    "notes.summary.errors.empty" to "Il n'y a aucun texte à résumer sur la page.",
    "notes.summary.errors.unreachable" to "Mogul n'a pas pu atteindre la page. Réessayez plus tard.",
    "notes.summary.errors.model" to "La page a été lue, mais le résumé a échoué. Réessayez.",
    "notes.summary.errors.timeout" to "Le résumé prend trop de temps. Réessayez plus tard.",
    "notes.summary.errors.failed" to "Cette page n'a pas pu être résumée. Réessayez.",
    "paging.loading" to "Chargement…",
    "paging.failed" to "Le reste n'a pas pu être chargé : {message}",
    "paging.retry" to "Réessayer",
    "settings.language.title" to "Langue",
    "settings.language.label" to "Afficher Mogul en",
    "settings.language.hint" to "Enregistrée dans votre compte, elle vous suit sur tous vos appareils. Les résumés et les textes de l'IA sont eux aussi rédigés dans cette langue.",
    "settings.language.save-failed" to "La langue n'a pas pu être enregistrée dans votre compte : {message}",
    "dates.months.1" to "janvier",
    "dates.months.2" to "février",
    "dates.months.3" to "mars",
    "dates.months.4" to "avril",
    "dates.months.5" to "mai",
    "dates.months.6" to "juin",
    "dates.months.7" to "juillet",
    "dates.months.8" to "août",
    "dates.months.9" to "septembre",
    "dates.months.10" to "octobre",
    "dates.months.11" to "novembre",
    "dates.months.12" to "décembre",
    "dates.weekdays.1" to "lundi",
    "dates.weekdays.2" to "mardi",
    "dates.weekdays.3" to "mercredi",
    "dates.weekdays.4" to "jeudi",
    "dates.weekdays.5" to "vendredi",
    "dates.weekdays.6" to "samedi",
    "dates.weekdays.7" to "dimanche",
    "dates.long-day" to "{weekday} {day} {month}",
    "track-editor.label" to "Éditeur de la piste audio",
    "track-editor.go-to.label" to "Aller à un instant",
    "track-editor.go-to.placeholder" to "aller à 0:30.0",
    "track-editor.timeline" to "CHRONOLOGIE",
    "track-editor.track" to "Audio 1",
    "track-editor.format" to "Stéréo · MP3",
    "track-editor.source" to "PISTE SOURCE",
    "track-editor.selected" to "{duration} sélectionnées",
    "track-editor.select-hint" to "Faites glisser sur la piste pour sélectionner une plage",
    "track-editor.delete-range" to "Supprimer la plage",
    // the analysis panel: the most compelling parts of an episode or a post
    "app.panels.analysis" to "Analyse",
    "analysis.none" to "Ouvrez un épisode ou un article pour en trouver les passages les plus marquants.",
    "analysis.entity.title" to "Analyse de {entityName}",
    "analysis.buttons.analyze" to "Analyser",
    "analysis.buttons.reanalyze" to "Analyser à nouveau",
    "analysis.buttons.analyzing" to "Analyse en cours…",
    "analysis.analyzed" to "Analysé {when}",
    "analysis.no-highlights" to "Rien ne ressort.",
    "analysis.highlight.label" to "{kind} ({score}/10)",
    "analysis.highlight.label-in-section" to "{kind} ({score}/10), dans {section}",
    "analysis.kinds.entertaining" to "Divertissant",
    "analysis.kinds.useful" to "Utile",
    "analysis.kinds.quotable" to "Citable",
    "analysis.errors.empty" to "Il n'y a encore rien à analyser. Un épisode a d'abord besoin d'une transcription.",
    "analysis.errors.failed" to "L'analyse n'a pas fonctionné. Réessayez dans un moment.",
    "analysis.errors.timeout" to "L'analyse prend trop de temps. Réessayez dans un moment.",
)

private fun translated(language: Language, key: String): String? = when (language) {
    Language.English -> null
    Language.Spanish -> spanishMessages[key] ?: spanishComposeMessages[key]
    Language.French -> frenchMessages[key] ?: frenchComposeMessages[key]
}

/** the message in the current language, or in English where that language doesn't have it */
private fun message(key: String): String? =
    translated(currentLanguage, key) ?: englishMessages[key] ?: composeMessages[key]

/** every key each language has, for checking that a translation is whole */
internal fun messageKeys(language: Language): Set<String> = when (language) {
    Language.English -> englishMessages.keys + composeMessages.keys
    Language.Spanish -> spanishMessages.keys + spanishComposeMessages.keys
    Language.French -> frenchMessages.keys + frenchComposeMessages.keys
}

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
