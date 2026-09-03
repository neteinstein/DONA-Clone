package com.neteinstein.donaclone.feature.devices

import com.neteinstein.donaclone.core.model.Device
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceIconsTest {
    private fun binaryInput(label: String) =
        Device.BinaryInput(id = 1, name = "Sensor", freeTypeLabel = label, isActive = false)

    @Test
    fun `a valve label is categorized as VALVE`() {
        assertEquals(DeviceCategory.VALVE, deviceCategoryOf(binaryInput("Water valve")))
    }

    @Test
    fun `a flood label is categorized as FLOOD_SENSOR`() {
        assertEquals(DeviceCategory.FLOOD_SENSOR, deviceCategoryOf(binaryInput("Flood sensor")))
    }

    @Test
    fun `a crepuscular label is categorized as CREPUSCULAR_SENSOR`() {
        assertEquals(DeviceCategory.CREPUSCULAR_SENSOR, deviceCategoryOf(binaryInput("Crepuscular sensor")))
    }

    @Test
    fun `a dusk label is also categorized as CREPUSCULAR_SENSOR`() {
        assertEquals(DeviceCategory.CREPUSCULAR_SENSOR, deviceCategoryOf(binaryInput("Dusk sensor")))
    }

    @Test
    fun `a gate label is categorized as GATE_SENSOR, not DOOR_SENSOR`() {
        assertEquals(DeviceCategory.GATE_SENSOR, deviceCategoryOf(binaryInput("Gate sensor")))
    }

    @Test
    fun `a door label is categorized as DOOR_SENSOR`() {
        assertEquals(DeviceCategory.DOOR_SENSOR, deviceCategoryOf(binaryInput("Front door sensor")))
    }

    @Test
    fun `an unlabeled BinaryInput falls back to GENERIC_SENSOR`() {
        assertEquals(DeviceCategory.GENERIC_SENSOR, deviceCategoryOf(binaryInput("")))
    }
}
