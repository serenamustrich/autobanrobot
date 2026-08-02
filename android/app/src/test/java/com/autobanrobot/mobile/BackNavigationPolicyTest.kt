package com.autobanrobot.mobile

import org.junit.Assert.assertEquals
import org.junit.Test

class BackNavigationPolicyTest {
    @Test
    fun decide_shouldShowHome_whenMenuPageIsVisible() {
        assertEquals(
            BackNavigationAction.SHOW_HOME,
            BackNavigationPolicy.decide(isHomePage = false)
        )
    }

    @Test
    fun decide_shouldClickPageBack_whenHomePageIsVisible() {
        assertEquals(
            BackNavigationAction.CLICK_PAGE_BACK,
            BackNavigationPolicy.decide(isHomePage = true)
        )
    }
}
