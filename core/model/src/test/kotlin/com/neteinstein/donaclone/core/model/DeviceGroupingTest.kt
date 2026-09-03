package com.neteinstein.donaclone.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val ROOM_A = 1
private const val ROOM_B = 2

class DeviceGroupingTest {
    @Test
    fun `pedestrian door sensor merges with its single open action`() {
        val sensor = Device.BinaryInput(id = 1, name = "Sensor Porta Pedonal", roomId = ROOM_A, isActive = false)
        val opener = Device.Pulse(id = 2, name = "Abertura Porta Pedonal", roomId = ROOM_A, kind = PulseKind.UNKNOWN)

        val result = groupDevices(listOf(sensor, opener))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals("Porta Pedonal", grouped.displayName)
        assertEquals(sensor, grouped.primary)
        assertEquals(opener, grouped.openAction)
        assertNull(grouped.closeAction)
        assertNull(grouped.secondary)
    }

    @Test
    fun `exterior lights merges on off with its unused intensity reading`() {
        val onOff = Device.BinaryOutput(id = 1, name = "Focos exteriores", roomId = ROOM_A, isOn = true)
        val intensity = Device.AnalogInput(id = 2, name = "Focos exteriores", roomId = ROOM_A, value = 0.0)

        val result = groupDevices(listOf(onOff, intensity))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals("Focos exteriores", grouped.displayName)
        assertEquals(onOff, grouped.primary)
        assertEquals(intensity, grouped.secondary)
        assertNull(grouped.openAction)
        assertNull(grouped.closeAction)
    }

    @Test
    fun `kitchen shutter merges with its redundant single open action`() {
        val shutter = Device.Shutter(id = 1, name = "Estore da cozinha", roomId = ROOM_A, percentage = 40)
        val opener = Device.Pulse(id = 2, name = "Abrir Estore da cozinha", roomId = ROOM_A, kind = PulseKind.UNKNOWN)

        val result = groupDevices(listOf(shutter, opener))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals("Estore da cozinha", grouped.displayName)
        assertEquals(shutter, grouped.primary)
        assertEquals(opener, grouped.openAction)
    }

    @Test
    fun `living room shutter merges with separate open and close actions`() {
        val state = Device.BinaryInput(id = 1, name = "Estore salão", roomId = ROOM_A, isActive = true)
        val opener = Device.Pulse(id = 2, name = "Abrir Estore Salão", roomId = ROOM_A, kind = PulseKind.UNKNOWN)
        val closer = Device.Pulse(id = 3, name = "Fechar estore salão", roomId = ROOM_A, kind = PulseKind.UNKNOWN)

        val result = groupDevices(listOf(state, opener, closer))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals("Estore salão", grouped.displayName)
        assertEquals(state, grouped.primary)
        assertEquals(opener, grouped.openAction)
        assertEquals(closer, grouped.closeAction)
    }

    @Test
    fun `open action with no state sibling stays solo`() {
        val opener = Device.Pulse(id = 1, name = "Abrir Portão", roomId = ROOM_A, kind = PulseKind.UNKNOWN)

        val result = groupDevices(listOf(opener))

        assertEquals(1, result.size)
        assertTrue(result.single() is DeviceDisplayItem.Solo)
    }

    @Test
    fun `two independent switches sharing an exact name stay solo, not merged`() {
        val first = Device.BinaryOutput(id = 1, name = "Luz", roomId = ROOM_A, isOn = true)
        val second = Device.BinaryOutput(id = 2, name = "Luz", roomId = ROOM_A, isOn = false)

        val result = groupDevices(listOf(first, second))

        assertEquals(2, result.size)
        assertTrue(result.all { it is DeviceDisplayItem.Solo })
    }

    @Test
    fun `devices in different rooms never merge even with matching names`() {
        val sensor = Device.BinaryInput(id = 1, name = "Sensor Porta", roomId = ROOM_A, isActive = false)
        val opener = Device.Pulse(id = 2, name = "Abertura Porta", roomId = ROOM_B, kind = PulseKind.UNKNOWN)

        val result = groupDevices(listOf(sensor, opener))

        assertEquals(2, result.size)
        assertTrue(result.all { it is DeviceDisplayItem.Solo })
    }

    @Test
    fun `unrelated device with no naming match passes through untouched`() {
        val thermostat = Device.AnalogInput(id = 1, name = "Temperatura sala", roomId = ROOM_A, value = 21.5)

        val result = groupDevices(listOf(thermostat))

        assertEquals(1, result.size)
        val solo = result.single() as DeviceDisplayItem.Solo
        assertEquals(thermostat, solo.primary)
        assertEquals("Temperatura sala", solo.displayName)
    }

    @Test
    fun `isDeviceOpenOrOn reflects each device subtype's own notion of open-or-on`() {
        assertTrue(isDeviceOpenOrOn(Device.BinaryOutput(id = 1, name = "n", isOn = true)))
        assertTrue(!isDeviceOpenOrOn(Device.BinaryOutput(id = 1, name = "n", isOn = false)))
        assertTrue(isDeviceOpenOrOn(Device.BinaryInput(id = 1, name = "n", isActive = true)))
        assertTrue(!isDeviceOpenOrOn(Device.BinaryInput(id = 1, name = "n", isActive = false)))
        assertTrue(isDeviceOpenOrOn(Device.AnalogInput(id = 1, name = "n", value = 1.0)))
        assertTrue(!isDeviceOpenOrOn(Device.AnalogInput(id = 1, name = "n", value = 0.0)))
        assertTrue(isDeviceOpenOrOn(Device.Counter(id = 1, name = "n", value = 1.0)))
        assertTrue(!isDeviceOpenOrOn(Device.Counter(id = 1, name = "n", value = 0.0)))
        assertTrue(isDeviceOpenOrOn(Device.Shutter(id = 1, name = "n", percentage = 10)))
        assertTrue(!isDeviceOpenOrOn(Device.Shutter(id = 1, name = "n", percentage = 0)))
        assertTrue(isDeviceOpenOrOn(Device.Dimmer(id = 1, name = "n", percentage = 10)))
        assertTrue(!isDeviceOpenOrOn(Device.Dimmer(id = 1, name = "n", percentage = 0)))
        assertTrue(!isDeviceOpenOrOn(Device.Pulse(id = 1, name = "n", kind = PulseKind.UNKNOWN)))
    }
}
