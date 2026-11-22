package com.joshlong.mogul.client

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.joshlong.mogul.client.about.AboutScreen
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.User
import com.joshlong.mogul.client.blogs.BlogPostEditorScreen
import com.joshlong.mogul.client.blogs.BlogPostsScreen
import com.joshlong.mogul.client.blogs.BlogsScreen
import com.joshlong.mogul.client.events.AppEvent
import com.joshlong.mogul.client.events.Events
import com.joshlong.mogul.client.events.SidebarPanel
import com.joshlong.mogul.client.home.HomeScreen
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.jobs.JobsScreen
import com.joshlong.mogul.client.managedfiles.PreviewPanel
import com.joshlong.mogul.client.nav.Route
import com.joshlong.mogul.client.nav.Router
import com.joshlong.mogul.client.notes.NotesPanel
import com.joshlong.mogul.client.notifications.NotificationBox
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.podcasts.PodcastEpisodeEditorScreen
import com.joshlong.mogul.client.podcasts.PodcastEpisodesScreen
import com.joshlong.mogul.client.podcasts.PodcastsScreen
import com.joshlong.mogul.client.search.SearchPalette
import com.joshlong.mogul.client.search.SearchScreen
import com.joshlong.mogul.client.settings.SettingsScreen
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.MogulTheme
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.ThemeController
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.transcripts.TranscriptsPanel
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.MogulIcon

/** the places the studio's chrome can take you: the main ones are the big icons in the top bar */
private enum class Section(val label: String, val icon: MogulIcon, val main: Boolean) {
    Home("app.menu.home", MogulIcon.Home, true),
    Podcasts("app.menu.podcasts", MogulIcon.Podcast, true),
    Blogs("app.menu.blogs", MogulIcon.Rss, true),
    Settings("app.menu.settings", MogulIcon.Settings, false),
    Jobs("app.menu.jobs", MogulIcon.Jobs, false);

    fun go() = when (this) {
        Home -> Router.push(Route.Home)
        Podcasts -> Router.podcasts()
        Blogs -> Router.push(Route.Blogs)
        Settings -> Router.push(Route.Settings)
        Jobs -> Router.push(Route.Jobs)
    }
}

/** the main section the current page belongs to, marked under its icon */
private fun mainSectionFor(route: Route): Section? = when (route) {
    Route.Home -> Section.Home
    is Route.Podcasts, is Route.PodcastEpisodes, is Route.PodcastEpisode -> Section.Podcasts
    Route.Blogs, is Route.BlogPosts, is Route.BlogPost -> Section.Blogs
    else -> null
}

/** below this the layout is mobile's: one column of tabs and a floating toolbar */
private val desktopBreak = 900.dp

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<User?>(null) }
    var paletteOpen by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        browser.setDocumentLanguage("en")
        ThemeController.start(scope)
        Router.start(scope)
        Router.resumePendingAction()
        // signed in is having a mogul, not having an email: a GitHub login often has none
        val user = runCatching { Mogul.mogul.user() }.getOrNull()
        if (user != null && user.id != 0L) {
            me = user
            // here rather than in a view: this runs once per session whatever page you land
            // on. anything that resolves "today" server-side needs the zone on record
            runCatching { Mogul.mogul.adoptBrowserTimeZoneIfUnset(user) }
            Mogul.notifications.start()
        } else {
            println("could not get user info from mogul.user()")
        }
    }

    val route = Router.route
    LaunchedEffect(route) {
        val app = t("app.title")
        browser.setTitle(route.titleKey?.let { "${t(it)} - $app" } ?: app)
    }

    MogulTheme {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(palette.bg)
                .onPreviewKeyEvent { event ->
                    val shortcut = if (browser.isMac) event.isMetaPressed else event.isCtrlPressed
                    if (event.type == KeyEventType.KeyDown && event.key == Key.K && shortcut) {
                        paletteOpen = !paletteOpen
                        true
                    } else {
                        false
                    }
                },
        ) {
            val user = me
            if (user != null) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val wide = maxWidth >= desktopBreak
                    if (wide) DesktopLayout(user, route, onSearch = { paletteOpen = true })
                    else MobileLayout(user, route, onSearch = { paletteOpen = true })
                }
                SearchPalette(open = paletteOpen, onDismiss = { paletteOpen = false })
                NotificationBox()
            }
        }
    }
}

/** the page for the route; keyed on it, so a new route (even the same page) starts fresh */
@Composable
private fun RouteContent(route: Route) {
    key(route) {
        when (route) {
            Route.Home -> HomeScreen()
            Route.Jobs -> JobsScreen()
            is Route.Podcasts -> PodcastsScreen()
            Route.Blogs -> BlogsScreen()
            is Route.Search -> SearchScreen(route.query)
            Route.Settings -> SettingsScreen()
            Route.About -> AboutScreen()
            is Route.BlogPosts -> BlogPostsScreen(route.blogId)
            is Route.BlogPost -> BlogPostEditorScreen(route.blogId, route.postId)
            is Route.PodcastEpisodes -> PodcastEpisodesScreen(route.podcastId)
            is Route.PodcastEpisode -> PodcastEpisodeEditorScreen(route.podcastId, route.episodeId)
            is Route.Entity -> Text(t("app.redirecting"), color = palette.muted)
            is Route.NotFound -> Text(route.path, color = palette.muted)
        }
    }
}

@Composable
private fun DesktopLayout(user: User, route: Route, onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        TopBar(user, route, onSearch)
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = Gutter.full)
                .widthIn(max = 1600.dp)
                .align(Alignment.CenterHorizontally),
            horizontalArrangement = Arrangement.spacedBy(Gutter.full),
        ) {
            Column(
                Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Gutter.full, bottom = 48.dp),
            ) {
                RouteContent(route)
            }
            Column(
                Modifier.width(400.dp).fillMaxSize().verticalScroll(rememberScrollState()).padding(top = Gutter.full, bottom = 48.dp),
                verticalArrangement = Arrangement.spacedBy(Gutter.full),
            ) {
                SidebarPanelFrame(SidebarPanel.Notes, t("app.panels.notes"), MogulIcon.Notes) { NotesPanel() }
                SidebarPanelFrame(SidebarPanel.Preview, t("app.panels.media-preview"), MogulIcon.Preview) { PreviewPanel() }
                SidebarPanelFrame(SidebarPanel.Transcripts, t("app.panels.transcripts"), MogulIcon.Transcript) { TranscriptsPanel() }
            }
        }
    }
}

/**
 * one of the sidebar's panels: a chrome title bar with a toggle, collapsed on arrival
 * (three expanded panels push the page long before you've asked for any of them), and
 * opened when something asks for it, e.g. a preview link
 */
@Composable
private fun SidebarPanelFrame(panel: SidebarPanel, title: String, icon: MogulIcon, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(panel) {
        Events.events.collect { if (it is AppEvent.SidebarPanelOpened && it.panel == panel) visible = true }
    }
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(Gutter.radius)).background(palette.panelBg)) {
        Row(
            Modifier.fillMaxWidth().background(palette.chrome).padding(horizontal = Gutter.full, vertical = Gutter.half),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gutter.half),
        ) {
            Icon(icon, null, size = 20.dp, tint = palette.chromeInk)
            Text(title, Modifier.weight(1f), color = palette.chromeInk, fontFamily = fonts.mono, fontSize = TextSize.sm)
            IconButton(
                if (visible) MogulIcon.Collapse else MogulIcon.Maximize,
                t(if (visible) "panel.alt.minimize" else "panel.alt.open", "panel" to title),
                onClick = { visible = !visible },
                size = 20.dp,
                tint = palette.chromeInk,
                hoverTint = palette.brand,
            )
        }
        if (visible) {
            Box(Modifier.fillMaxWidth().padding(Gutter.full)) { content() }
        }
    }
}

@Composable
private fun MobileLayout(user: User, route: Route, onSearch: () -> Unit) {
    val tabs = listOf("app.tabs.main", "app.panels.notes", "app.panels.media-preview", "app.panels.transcripts")
    var active by remember { mutableStateOf(0) }
    // a new page shows the main tab; a panel asking to open shows its own
    LaunchedEffect(route) { active = 0 }
    LaunchedEffect(Unit) {
        Events.events.collect {
            if (it is AppEvent.SidebarPanelOpened) {
                active = when (it.panel) {
                    SidebarPanel.Notes -> 1
                    SidebarPanel.Preview -> 2
                    SidebarPanel.Transcripts -> 3
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                Modifier.fillMaxWidth().background(palette.topbar).padding(horizontal = Gutter.full, vertical = Gutter.half),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gutter.half),
            ) {
                BrandMark(24)
            }
            Row(Modifier.fillMaxWidth().background(palette.chrome)) {
                tabs.forEachIndexed { index, label ->
                    Text(
                        t(label),
                        modifier = Modifier
                            .weight(1f)
                            .clickable { active = index }
                            .background(if (index == active) palette.panelBg else palette.chrome)
                            .padding(vertical = Gutter.half),
                        color = if (index == active) palette.ink else palette.chromeInk,
                        fontSize = TextSize.sm,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(Gutter.full).padding(bottom = 96.dp),
            ) {
                // the page stays composed (hidden, and clipped so nothing of it shows) while
                // another tab is up, so an editor keeps what you typed; the panels keep their
                // own state in their stores, so they can come and go
                Box(if (active == 0) Modifier else Modifier.size(0.dp).clipToBounds()) { RouteContent(route) }
                when (active) {
                    1 -> NotesPanel()
                    2 -> PreviewPanel()
                    3 -> TranscriptsPanel()
                }
            }
        }
        // one floating row at the bottom of the window: the main sections, then search and the account
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(Gutter.full)
                .fillMaxWidth()
                .clip(RoundedCornerShape(Gutter.radius))
                .background(palette.chrome)
                .padding(horizontal = Gutter.full, vertical = Gutter.half),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(Modifier.weight(1f), horizontalArrangement = Arrangement.SpaceAround) {
                Section.entries.filter { it.main }.forEach { section ->
                    IconButton(
                        section.icon,
                        t(section.label),
                        onClick = { section.go() },
                        size = 32.dp,
                        tint = if (mainSectionFor(route) == section) palette.chromeInk else palette.chromeMuted,
                        hoverTint = palette.chromeInk,
                    )
                }
            }
            IconButton(MogulIcon.Search, t("search.open"), onClick = onSearch, tint = palette.chromeMuted, hoverTint = palette.chromeInk)
            AccountMenu(user, up = true)
        }
    }
}

/** the wordmark: "mogul" in the display face, "studio" in spaced mono in the brand hue */
@Composable
private fun BrandMark(size: Int) {
    Row(
        Modifier.clickable { Router.push(Route.Home) }.pointerHoverIcon(PointerIcon.Hand),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.spacedBy((size / 4).dp),
    ) {
        Text(t("brand.name"), color = palette.chromeInk, fontFamily = fonts.display, fontWeight = FontWeight.Bold, fontSize = size.sp, letterSpacing = (-0.05).sp * size)
        Text(
            t("brand.product").uppercase(),
            modifier = Modifier.padding(bottom = (size / 10).dp),
            color = palette.brand,
            fontFamily = fonts.mono,
            fontWeight = FontWeight.Medium,
            fontSize = maxOf(10, size * 3 / 8).sp,
            letterSpacing = 0.24.sp * maxOf(10, size * 3 / 8),
        )
    }
}

/** wordmark, the main sections, then search and the account, on a full-width black band */
@Composable
private fun TopBar(user: User, route: Route, onSearch: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(palette.topbar).padding(horizontal = 32.dp, vertical = Gutter.full),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) { BrandMark(32) }
        Row(horizontalArrangement = Arrangement.spacedBy(Gutter.full), verticalAlignment = Alignment.CenterVertically) {
            Section.entries.filter { it.main }.forEach { section ->
                val active = mainSectionFor(route) == section
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(
                        section.icon,
                        t(section.label),
                        onClick = { section.go() },
                        size = 36.dp,
                        tint = palette.chromeInk,
                        hoverTint = palette.brand,
                    )
                    // where you are: a short rule in the brand color under the section's icon
                    Box(
                        Modifier.padding(top = 4.dp).width(20.dp).height(3.dp).clip(RoundedCornerShape(2.dp))
                            .background(if (active) palette.brand else palette.topbar),
                    )
                }
            }
        }
        Row(
            Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(Gutter.half, Alignment.End),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SearchButton(onSearch)
            AccountMenu(user, up = false)
        }
    }
}

/** a small field-shaped button that opens the search palette, with its shortcut */
@Composable
private fun SearchButton(onSearch: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val shortcut = if (browser.isMac) "⌘K" else "Ctrl K"
    Row(
        Modifier
            .width(208.dp)
            .clip(RoundedCornerShape(Gutter.radius))
            .border(1.dp, palette.chromeInk.copy(alpha = if (hovered) 0.28f else 0.14f), RoundedCornerShape(Gutter.radius))
            .background(palette.chromeInk.copy(alpha = 0.04f))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onSearch)
            .pointerHoverIcon(PointerIcon.Hand)
            .padding(horizontal = Gutter.half, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Gutter.half),
    ) {
        Icon(MogulIcon.Search, null, size = 16.dp, tint = if (hovered) palette.chromeInk else palette.chromeMuted)
        Text(t("search.open"), Modifier.weight(1f), color = if (hovered) palette.chromeInk else palette.chromeMuted, fontSize = TextSize.sm)
        Text(
            shortcut,
            Modifier.border(1.dp, palette.chromeInk.copy(alpha = 0.16f), RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 2.dp),
            color = palette.chromeMuted,
            fontFamily = fonts.mono,
            fontSize = TextSize.xs,
        )
    }
}

/** the mogul's initials; what drops down is who you are, then the sections not in the bar */
@Composable
private fun AccountMenu(user: User, up: Boolean) {
    var open by remember { mutableStateOf(false) }
    Box {
        Row(
            Modifier
                .clip(CircleShape)
                .clickable { open = !open }
                .pointerHoverIcon(PointerIcon.Hand)
                .padding(3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Box(
                Modifier.size(34.dp).clip(CircleShape).background(palette.chromeHover),
                contentAlignment = Alignment.Center,
            ) {
                Text(user.initials, color = palette.chromeInk, fontSize = TextSize.xs, fontWeight = FontWeight.SemiBold)
            }
            Text(if (up) "▴" else "▾", color = palette.chromeMuted, fontSize = TextSize.xs)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }, containerColor = palette.raised) {
            Column(Modifier.widthIn(min = 224.dp).padding(horizontal = Gutter.full, vertical = Gutter.half)) {
                Text(user.fullName.ifBlank { user.email ?: "" }, color = palette.ink, fontWeight = FontWeight.SemiBold)
                user.email?.let { Text(it, color = palette.muted, fontSize = TextSize.sm) }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(palette.rule))
            Section.entries.filter { !it.main }.forEach { section ->
                DropdownMenuItem(
                    leadingIcon = { Icon(section.icon, null, size = 18.dp, tint = palette.muted) },
                    text = { Text(t(section.label), color = palette.ink) },
                    onClick = {
                        open = false
                        section.go()
                    },
                )
            }
        }
    }
}
