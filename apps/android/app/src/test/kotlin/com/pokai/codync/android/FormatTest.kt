package com.pokai.codync.android

import com.pokai.codync.android.ui.relativeShort
import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

    @Test
    fun testRelativeShort() {
        val now = 1_000_000_000L
        assertEquals("now", relativeShort(now - 10_000, now))
        assertEquals("5m", relativeShort(now - 300_000, now))
        assertEquals("2h", relativeShort(now - 7_200_000, now))
        assertEquals("3d", relativeShort(now - 3 * 86_400_000, now))
    }
}
