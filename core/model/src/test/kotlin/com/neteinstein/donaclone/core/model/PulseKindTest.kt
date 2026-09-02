package com.neteinstein.donaclone.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class PulseKindTest {

    @Test
    fun `known wire values map to the expected kind`() {
        assertEquals(PulseKind.SIREN, PulseKind.fromWireValue(10))
        assertEquals(PulseKind.CHIME, PulseKind.fromWireValue(11))
        assertEquals(PulseKind.LOCK, PulseKind.fromWireValue(20))
        assertEquals(PulseKind.ARM_OUTPUT, PulseKind.fromWireValue(30))
        assertEquals(PulseKind.DISARM_OUTPUT, PulseKind.fromWireValue(31))
        assertEquals(PulseKind.ARM_DISARM_COUPLED, PulseKind.fromWireValue(32))
    }

    @Test
    fun `unknown wire value falls back to UNKNOWN`() {
        assertEquals(PulseKind.UNKNOWN, PulseKind.fromWireValue(999))
    }
}
