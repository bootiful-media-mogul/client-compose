package com.joshlong.mogul.client.analysis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.joshlong.mogul.client.api.Analysis
import com.joshlong.mogul.client.api.Highlight
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.parseStringContext
import com.joshlong.mogul.client.events.AppEvent
import com.joshlong.mogul.client.events.Events
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.ui.ButtonRow
import com.joshlong.mogul.client.ui.LayoutWidth
import com.joshlong.mogul.client.ui.Legend
import com.joshlong.mogul.client.ui.LocalLayoutWidth
import com.joshlong.mogul.client.ui.MogulButton
import com.joshlong.mogul.client.ui.MogulReadOnlyField
import com.joshlong.mogul.client.ui.bleed
import com.joshlong.mogul.client.ui.dateTimeToString
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.minutes

// the kinds of highlight the api finds, each with a label
internal val highlightKinds = setOf("entertaining", "useful", "quotable")

/** what the panel says about an analysis that didn't come, as a message key, or null if it came */
internal fun analysisProblemFor(outcome: String?): String? = when (outcome) {
    "analyzed" -> null
    "empty" -> "analysis.errors.empty"
    else -> "analysis.errors.failed"
}

/** the label over a highlight's quote: its kind, how compelling it is, and the segment it's in, if it's in one */
internal fun highlightLabel(highlight: Highlight): String {
    val kind = if (highlight.kind in highlightKinds) t("analysis.kinds.${highlight.kind}") else highlight.kind
    val score = highlight.score.toString()
    val section = highlight.sectionName?.takeIf { it.isNotBlank() }
    return if (section == null) t("analysis.highlight.label", "kind" to kind, "score" to score)
    else t("analysis.highlight.label-in-section", "kind" to kind, "score" to score, "section" to section)
}

// an hour of talk takes the model a minute or two; this is that, with room to spare
private val ANALYSIS_TIMEOUT = 5.minutes

/**
 * what the analysis panel shows. it lives outside the panel: the panel's content is only drawn
 * while it's expanded, but a page says what it's about when it loads, collapsed or not.
 */
internal object AnalysisStore {

    private val scope = MainScope()

    private var started = false

    var type by mutableStateOf<String?>(null)
        private set
    var analyzableId by mutableStateOf(-1L)
        private set
    var entityName by mutableStateOf("")
        private set
    var analysis by mutableStateOf<Analysis?>(null)
        private set

    /**
     * the `type:id` of what's being analyzed, if anything is. it outlives a trip to another page
     * and back, so the panel still says it's analyzing when you return
     */
    private var analyzing by mutableStateOf<String?>(null)

    /** why the last analysis didn't come, as a message key */
    var problem by mutableStateOf<String?>(null)
        private set

    private val key: String? get() = this.type?.let { "$it:${this.analyzableId}" }

    val busy: Boolean get() = this.analyzing != null && this.analyzing == this.key

    val loaded: Boolean get() = this.type != null && this.analyzableId > 0

    fun start() {
        if (this.started) return
        this.started = true
        // the store lives as long as the app, so this is never unregistered
        Mogul.notifications.listenForCategory("analysis-completed-event") { notification ->
            if (notification.key != this.analyzing) return@listenForCategory
            this.analyzing = null
            if (notification.key != this.key) return@listenForCategory
            this.problem = analysisProblemFor(parseStringContext(notification.context)["outcome"])
            this.scope.launch { reload() }
        }
        // undispatched, so it's listening before anything a page does on load can tell it
        this.scope.launch(start = CoroutineStart.UNDISPATCHED) {
            Events.events.collect { event ->
                when (event) {
                    is AppEvent.ResetAnalysisForAnalyzable -> {
                        this@AnalysisStore.type = null
                        this@AnalysisStore.analyzableId = -1
                        this@AnalysisStore.entityName = ""
                        this@AnalysisStore.analysis = null
                        this@AnalysisStore.problem = null
                    }

                    is AppEvent.AnalysisForAnalyzable -> {
                        val changed = event.type != this@AnalysisStore.type || event.analyzableId != this@AnalysisStore.analyzableId
                        this@AnalysisStore.type = event.type
                        this@AnalysisStore.analyzableId = event.analyzableId
                        this@AnalysisStore.entityName = event.entityName
                        if (changed) {
                            this@AnalysisStore.analysis = null
                            this@AnalysisStore.problem = null
                            reload()
                        }
                    }

                    else -> {}
                }
            }
        }
    }

    suspend fun reload() {
        val type = this.type ?: return
        val id = this.analyzableId
        if (id <= 0) return
        val loaded = runCatching { Mogul.analysis.analysis(type, id) }.getOrNull()
        // the page may have changed while it loaded
        if (type == this.type && id == this.analyzableId) this.analysis = loaded
    }

    /** asks for the page's episode or post to be analyzed, in place of any analysis it has */
    suspend fun analyze() {
        val type = this.type ?: return
        val key = this.key ?: return
        if (this.busy) return
        this.problem = null
        val asked = runCatching { Mogul.analysis.analyze(type, this.analyzableId) }
        if (asked.getOrNull() != true) {
            this.problem = if (asked.isSuccess) "analysis.errors.empty" else "analysis.errors.failed"
            return
        }
        this.analyzing = key
        // if no answer comes, give the button back
        this.scope.launch {
            delay(ANALYSIS_TIMEOUT)
            if (this@AnalysisStore.analyzing == key) {
                this@AnalysisStore.analyzing = null
                if (this@AnalysisStore.key == key) this@AnalysisStore.problem = "analysis.errors.timeout"
            }
        }
    }
}

/**
 * the analysis sidebar panel: the most compelling parts of the page's episode or post, as the
 * model found them, and a button to find them (again)
 */
@Composable
fun AnalysisPanel(modifier: Modifier = Modifier) {
    val store = AnalysisStore
    val scope = rememberCoroutineScope()
    val mobile = LocalLayoutWidth.current == LayoutWidth.Mobile
    LaunchedEffect(Unit) { store.start() }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Gutter.half)) {
        if (!store.loaded) {
            Text(t("analysis.none"), color = palette.muted, fontSize = TextSize.sm)
            return@Column
        }
        // the same title bar as the notes panel's sections
        Legend(
            t("analysis.entity.title", "entityName" to store.entityName),
            Modifier.padding(top = Gutter.half).bleed(start = if (mobile) Gutter.full else 0.dp, end = Gutter.full),
            shape = if (mobile) RoundedCornerShape(0.dp) else RoundedCornerShape(topStart = Gutter.radius, bottomStart = Gutter.radius),
        )
        Column(
            Modifier.fillMaxWidth().padding(horizontal = if (mobile) 0.dp else Gutter.full),
            verticalArrangement = Arrangement.spacedBy(Gutter.half),
        ) {
            val analysis = store.analysis
            ButtonRow(horizontalArrangement = Arrangement.spacedBy(Gutter.half)) {
                MogulButton(
                    t(
                        when {
                            store.busy -> "analysis.buttons.analyzing"
                            analysis != null -> "analysis.buttons.reanalyze"
                            else -> "analysis.buttons.analyze"
                        }
                    ),
                    onClick = { scope.launch { store.analyze() } },
                    enabled = !store.busy,
                )
            }
            store.problem?.let { Text(t(it), color = palette.danger, fontSize = TextSize.sm) }
            if (analysis != null) {
                Text(
                    t("analysis.analyzed", "when" to (dateTimeToString(analysis.created) ?: "")),
                    color = palette.muted,
                    fontSize = TextSize.sm,
                )
                if (analysis.highlights.isEmpty())
                    Text(t("analysis.no-highlights"), fontSize = TextSize.sm)
                analysis.highlights.forEach { highlight ->
                    Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
                    HighlightItem(highlight)
                }
            }
        }
    }
}

/** one highlight: the quote, under its kind and score, and why it's compelling */
@Composable
private fun HighlightItem(highlight: Highlight) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = Gutter.half),
        verticalArrangement = Arrangement.spacedBy(Gutter.third),
    ) {
        
        MogulReadOnlyField(highlight.quote, label = highlightLabel(highlight))
        if (highlight.reason.isNotBlank())
            Text(highlight.reason, color = palette.muted, fontSize = TextSize.sm)
    }
}
