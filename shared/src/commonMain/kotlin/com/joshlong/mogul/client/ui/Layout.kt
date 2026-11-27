package com.joshlong.mogul.client.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * the page layout's measurements, as base.css's container queries: everything that lays out
 * the page or decides how its edges look consults these, so the breakpoints can't drift apart
 */
object PageLayout {

    /** narrower than this the layout is mobile's: one column of tabs and a floating toolbar */
    val mobileBelow: Dp = 900.dp

    /**
     * shorter than this the layout is mobile's too, however wide: a phone on its side is as
     * wide as a small laptop, but no laptop or tablet window is this short
     */
    val mobileShorterThan: Dp = 500.dp

    /** the page stops stretching at this width; any wider and it's centered with open space on either side */
    val maxWidth: Dp = 1600.dp

    /** the desktop sidebar's panels, beside the page */
    val sidebarWidth: Dp = 400.dp

    /** how a window this size lays out: mobile's when it's narrow or short, the desktop's otherwise, however wide */
    fun widthFor(windowWidth: Dp, windowHeight: Dp): LayoutWidth =
        if (windowWidth < this.mobileBelow || windowHeight < this.mobileShorterThan) LayoutWidth.Mobile else LayoutWidth.Desktop
}

/** how wide the window is, as [PageLayout.widthFor] splits it: the legends and panels round accordingly */
enum class LayoutWidth { Mobile, Desktop }

val LocalLayoutWidth = compositionLocalOf { LayoutWidth.Desktop }

/** the window's width, for the few things the Vue client sized in vw */
val LocalWindowWidth = compositionLocalOf { 1200.dp }

/**
 * the scroll of the column the page sits in, for whatever on the page needs to know how near
 * its bottom is, e.g. a [PagedList]. null while the page isn't the thing on show.
 */
val LocalPageScroll = compositionLocalOf<ScrollState?> { null }
