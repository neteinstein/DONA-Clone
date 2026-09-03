package com.neteinstein.donaclone.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class ShutterStateTest {
    @Test
    fun `0 percent reads as Closed`() {
        assertEquals("Closed", shutterStateLabel(0))
    }

    @Test
    fun `partial percentages read as Open (N%)`() {
        assertEquals("Open (1%)", shutterStateLabel(1))
        assertEquals("Open (10%)", shutterStateLabel(10))
        assertEquals("Open (20%)", shutterStateLabel(20))
    }

    @Test
    fun `fully open still shows the percentage`() {
        assertEquals("Open (100%)", shutterStateLabel(100))
    }
}
