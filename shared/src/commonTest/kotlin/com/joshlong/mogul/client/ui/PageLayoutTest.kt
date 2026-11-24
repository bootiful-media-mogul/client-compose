package com.joshlong.mogul.client.ui

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals

class PageLayoutTest {

    private val tall = 1000.dp

    @Test
    fun narrowWindowsAreMobile() {
        assertEquals(LayoutWidth.Mobile, PageLayout.widthFor(PageLayout.mobileBelow - 1.dp, tall))
        assertEquals(LayoutWidth.Desktop, PageLayout.widthFor(PageLayout.mobileBelow, tall))
    }

    @Test
    fun shortWindowsAreMobileHoweverWide() {
        // an iPhone Pro Max or a big Android phone on its side
        assertEquals(LayoutWidth.Mobile, PageLayout.widthFor(956.dp, 440.dp))
        assertEquals(LayoutWidth.Mobile, PageLayout.widthFor(915.dp, 412.dp))
        // a landscape iPad mini and a 13" MacBook Air are not
        assertEquals(LayoutWidth.Desktop, PageLayout.widthFor(1133.dp, 744.dp))
        assertEquals(LayoutWidth.Desktop, PageLayout.widthFor(1470.dp, 956.dp))
    }

    @Test
    fun theLayoutIsWideExactlyWhenThePageIsCentered() {
        // at the page's width it still meets both window edges; any wider and it floats
        assertEquals(LayoutWidth.Desktop, PageLayout.widthFor(PageLayout.maxWidth, tall))
        assertEquals(LayoutWidth.Wide, PageLayout.widthFor(PageLayout.maxWidth + 1.dp, tall))
    }
}
