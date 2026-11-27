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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.navigation.NavHostController
import androidx.navigation.compose.rememberNavController
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.layout.layout
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.joshlong.mogul.client.api.Mogul
import com.joshlong.mogul.client.api.User
import com.joshlong.mogul.client.events.AppEvent
import com.joshlong.mogul.client.events.Events
import com.joshlong.mogul.client.events.SidebarPanel
import com.joshlong.mogul.client.i18n.t
import com.joshlong.mogul.client.managedfiles.PreviewPanel
import com.joshlong.mogul.client.nav.Pages
import com.joshlong.mogul.client.nav.Route
import com.joshlong.mogul.client.nav.Router
import com.joshlong.mogul.client.notes.NotesPanel
import com.joshlong.mogul.client.notifications.NotificationBox
import com.joshlong.mogul.client.platform.browser
import com.joshlong.mogul.client.platform.signIn
import com.joshlong.mogul.client.search.SearchPalette
import com.joshlong.mogul.client.theme.Gutter
import com.joshlong.mogul.client.theme.MogulTheme
import com.joshlong.mogul.client.theme.TextSize
import com.joshlong.mogul.client.theme.ThemeController
import com.joshlong.mogul.client.theme.fonts
import com.joshlong.mogul.client.theme.palette
import com.joshlong.mogul.client.transcripts.TranscriptsPanel
import com.joshlong.mogul.client.ui.BrandMark
import com.joshlong.mogul.client.ui.ConfirmationDialog
import com.joshlong.mogul.client.ui.FloatingMenu
import com.joshlong.mogul.client.ui.Icon
import com.joshlong.mogul.client.ui.IconButton
import com.joshlong.mogul.client.ui.LayoutWidth
import com.joshlong.mogul.client.ui.LocalLayoutWidth
import com.joshlong.mogul.client.ui.LocalWindowWidth
import com.joshlong.mogul.client.ui.PageLayout
import com.joshlong.mogul.client.ui.MogulIcon
import com.joshlong.mogul.client.ui.TextDragGhost
import com.joshlong.mogul.client.ui.floating
import kotlinx.coroutines.launch

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

// the apps draw to the edges of the screen, so whatever the device keeps for itself (a
// phone's status bar and notch, its home indicator, a tablet's camera) comes out of the
// page: the bars' colors run underneath, their contents stay clear. nothing, on the web
// and the desktop
private val underTheTop
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal)
private val underTheBottom
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal)
private val underTheSides
    @Composable get() = WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal)

@Composable
fun App() {
    val scope = rememberCoroutineScope()
    var me by remember { mutableStateOf<User?>(null) }
    var paletteOpen by remember { mutableStateOf(false) }

    // the apps sign in themselves: until they have, or once the api stops taking their token,
    // they show the sign-in screen. the web build never does; the gateway signs it in
    var needsSignIn by remember { mutableStateOf(false) }

    suspend fun loadSession() {
        // signed in is having a mogul, not having an email: a GitHub login often has none
        val user = runCatching { Mogul.mogul.user() }.getOrNull()
        if (user != null && user.id != 0L) {
            me = user
            needsSignIn = false
            // here rather than in a view: this runs once per session whatever page you land
            // on. anything that resolves "today" server-side needs the zone on record
            runCatching { Mogul.mogul.adoptBrowserTimeZoneIfUnset(user) }
            Mogul.notifications.start()
        } else {
            println("could not get user info from mogul.user()")
            needsSignIn = signIn.available
        }
    }

    LaunchedEffect(Unit) {
        browser.setDocumentLanguage("en")
        ThemeController.start(scope)
        loadSession()
    }

    LaunchedEffect(Unit) {
        Events.events.collect { if (it is AppEvent.Unauthorized && signIn.available) needsSignIn = true }
    }

    val navController = rememberNavController()
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
            if (needsSignIn) {
                SignInScreen(onSignedIn = { scope.launch { loadSession() } })
            } else if (user != null) {
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val width = PageLayout.widthFor(maxWidth, maxHeight)
                    CompositionLocalProvider(LocalLayoutWidth provides width, LocalWindowWidth provides maxWidth) {
                        if (width == LayoutWidth.Mobile) MobileLayout(user, route, navController, onSearch = { paletteOpen = true })
                        else DesktopLayout(user, route, navController, onSearch = { paletteOpen = true })
                    }
                }
                SearchPalette(open = paletteOpen, onDismiss = { paletteOpen = false })
                ConfirmationDialog()
                NotificationBox()
                TextDragGhost()
            }
        }
    }
}


/**
 * the desktop page, as App.vue's grid: the top bar, then the view beside a 400-wide sidebar,
 * a gutter in from the window's edges, so the legends and panels end in open space at any width.
 * the view starts a gutter and a half under the bar (the room the section names drop into)
 * plus a gutter; the sidebar's panels sit a gutter higher than that.
 */
@Composable
private fun DesktopLayout(user: User, route: Route, navController: NavHostController, onSearch: () -> Unit) {
    Column(Modifier.fillMaxSize()) {
        // over the page, which the section names' tabs drop onto
        TopBar(user, route, onSearch, Modifier.zIndex(1f))
        // the gutters live inside the scrolling columns, not around them: a scroll clips what it
        // draws to its own bounds (and barely past them), which would square off the panels' shadows
        Row(Modifier.align(Alignment.CenterHorizontally).widthIn(max = PageLayout.maxWidth).fillMaxSize().windowInsetsPadding(underTheBottom)) {
            Column(
                Modifier.weight(1f).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(start = Gutter.full * 2, top = 40.dp, bottom = 48.dp),
            ) {
                Pages(navController)
            }
            Column(
                Modifier.width(PageLayout.sidebarWidth + Gutter.full * 3).fillMaxSize().verticalScroll(rememberScrollState())
                    .padding(horizontal = Gutter.full).padding(top = 24.dp, bottom = 48.dp),
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
 * one of the sidebar's panels (PanelComponent.vue): a chrome title bar in spaced capitals
 * with a toggle, collapsed on arrival (three expanded panels push the page long before
 * you've asked for any of them), and opened when something asks for it, e.g. a preview
 * link. it ends a gutter short of the window's right edge, so all its corners round.
 */
@Composable
private fun SidebarPanelFrame(panel: SidebarPanel, title: String, icon: MogulIcon, content: @Composable () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(panel) {
        Events.events.collect { if (it is AppEvent.SidebarPanelOpened && it.panel == panel) visible = true }
    }
    val shape = RoundedCornerShape(Gutter.radius)
    Column(
        Modifier
            .fillMaxWidth()
            .shadow(24.dp, shape, ambientColor = Color.Black.copy(alpha = 0.5f), spotColor = Color.Black.copy(alpha = 0.5f))
            .clip(shape)
            .background(palette.panelBg),
    ) {
        // the whole title bar toggles the panel, not just the button at its end
        Row(
            Modifier.fillMaxWidth()
                .pointerHoverIcon(PointerIcon.Hand)
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { visible = !visible }
                .background(palette.chrome)
                .padding(Gutter.half),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Gutter.third),
        ) {
            Icon(icon, null, size = 21.dp, tint = palette.chromeInk)
            Text(
                title,
                Modifier.weight(1f),
                color = palette.chromeInk,
                fontFamily = fonts.mono,
                fontWeight = FontWeight.Medium,
                fontSize = TextSize.sm,
            )
            IconButton(
                if (visible) MogulIcon.Collapse else MogulIcon.Maximize,
                t(if (visible) "panel.alt.minimize" else "panel.alt.open", "panel" to title),
                onClick = { visible = !visible },
                size = 12.dp,
                tint = palette.chromeInk.copy(alpha = 0.85f),
                hoverTint = palette.chromeInk,
            )
        }
        if (visible) {
            Box(Modifier.fillMaxWidth().padding(Gutter.full)) { content() }
        }
    }
}

@Composable
private fun MobileLayout(user: User, route: Route, navController: NavHostController, onSearch: () -> Unit) {
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
                Modifier.fillMaxWidth().background(palette.topbar).windowInsetsPadding(underTheTop)
                    .padding(horizontal = Gutter.full, vertical = Gutter.half),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(Gutter.half),
            ) {
                BrandMark(24)
            }
            Row(Modifier.fillMaxWidth().background(palette.chrome).windowInsetsPadding(underTheSides)) {
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
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()).windowInsetsPadding(underTheBottom)
                    .padding(Gutter.full).padding(bottom = 96.dp),
            ) {
                // the page stays composed (hidden, and clipped so nothing of it shows) while
                // another tab is up, so an editor keeps what you typed; the panels keep their
                // own state in their stores, so they can come and go
                Box(if (active == 0) Modifier else Modifier.size(0.dp).clipToBounds()) { Pages(navController) }
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
                .windowInsetsPadding(underTheBottom)
                .padding(Gutter.full)
                .fillMaxWidth()
                .floating(RoundedCornerShape(Gutter.radius), palette.chrome)
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

/**
 * wordmark, the main sections, then search and the account, on a full-width black band whose
 * contents stay in the page's width; equal sides keep the icons centered on the page
 */
@Composable
private fun TopBar(user: User, route: Route, onSearch: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().background(palette.topbar).windowInsetsPadding(underTheTop)) {
        Row(
            Modifier.align(Alignment.Center).widthIn(max = PageLayout.maxWidth).fillMaxWidth().padding(horizontal = 32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f)) { BrandMark(32) }
            Row(Modifier.padding(vertical = Gutter.full), horizontalArrangement = Arrangement.spacedBy(Gutter.full)) {
                Section.entries.filter { it.main }.forEach { section -> NavItem(section, active = mainSectionFor(route) == section) }
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
}

/**
 * a main section's icon. the section you're in has a short brand-colored rule under it, and
 * hovering shows the section's name in small capitals on a tab that drops out of the bottom
 * of the bar; the tab takes no room, so the icons stay centered
 */
@Composable
private fun NavItem(section: Section, active: Boolean) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    Box(Modifier.hoverable(interaction)) {
        IconButton(section.icon, t(section.label), onClick = { section.go() }, size = 48.dp, tint = palette.chromeInk, hoverTint = palette.brand)
        if (active) {
            Box(
                Modifier.align(Alignment.BottomCenter).offset(y = 9.6.dp)
                    .size(20.dp, 3.dp).clip(RoundedCornerShape(2.dp)).background(palette.brand),
            )
        }
        if (hovered) {
            // a zero-size anchor at the icon's foot, with the tab hung from it, centered
            Box(
                Modifier.align(Alignment.BottomCenter).layout { measurable, _ ->
                    val tab = measurable.measure(Constraints())
                    layout(0, 0) { tab.place(-tab.width / 2, 0) }
                },
            ) {
                Text(
                    t(section.label).uppercase(),
                    modifier = Modifier
                        .clip(RoundedCornerShape(bottomStart = Gutter.radius, bottomEnd = Gutter.radius))
                        .background(palette.topbar)
                        .padding(start = Gutter.full, end = Gutter.full, top = 20.dp, bottom = Gutter.half),
                    color = palette.brand,
                    fontSize = TextSize.xs,
                    fontWeight = FontWeight.SemiBold,
                    letterSpacing = (0.08 * 12).sp,
                    lineHeight = 12.sp,
                    maxLines = 1,
                    softWrap = false,
                )
            }
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
        FloatingMenu(expanded = open, onDismissRequest = { open = false }) {
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
            // the apps hold their own tokens, so they can let them go; the web signs out at the gateway
            if (signIn.available) {
                val scope = rememberCoroutineScope()
                DropdownMenuItem(
                    text = { Text(t("app.sign-out"), color = palette.ink) },
                    onClick = {
                        open = false
                        scope.launch {
                            signIn.signOut()
                            Events.emit(AppEvent.Unauthorized)
                        }
                    },
                )
            }
        }
    }
}
