package com.neteinstein.donaclone.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ShutterInversionTest {
    @Test
    fun `fully closed becomes fully open and back again`() {
        assertEquals(100, invertShutterPercentage(0))
        assertEquals(0, invertShutterPercentage(100))
    }

    @Test
    fun `a partial position is mirrored around the midpoint`() {
        assertEquals(70, invertShutterPercentage(30))
        assertEquals(50, invertShutterPercentage(50))
    }

    @Test
    fun `an out-of-range reading from the hub is clamped rather than propagated`() {
        assertEquals(100, invertShutterPercentage(-10))
        assertEquals(0, invertShutterPercentage(130))
    }
}
