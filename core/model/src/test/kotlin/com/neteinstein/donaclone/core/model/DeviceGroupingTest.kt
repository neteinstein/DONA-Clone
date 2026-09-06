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
        assertTrue(grouped.secondaries.isEmpty())
    }

    @Test
    fun `garage gate merges despite accent and connector-word differences in naming`() {
        val sensor = Device.BinaryInput(id = 1, name = "portao da garagem", roomId = ROOM_A, isActive = false)
        val opener = Device.Pulse(id = 2, name = "Abertura portão garagem", roomId = ROOM_A, kind = PulseKind.UNKNOWN)

        val result = groupDevices(listOf(sensor, opener))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals("portao da garagem", grouped.displayName)
        assertEquals(sensor, grouped.primary)
        assertEquals(opener, grouped.openAction)
        assertNull(grouped.closeAction)
        assertTrue(grouped.secondaries.isEmpty())
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
        assertEquals(listOf(intensity), grouped.secondaries)
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
    fun `kitchen blinds shutter merges with separate English Open and Close actions`() {
        val shutter = Device.Shutter(id = 1, name = "Kitchen Blinds", roomId = ROOM_A, percentage = 0)
        val opener = Device.Pulse(id = 2, name = "Open Kitchen Blinds", roomId = ROOM_A, kind = PulseKind.UNKNOWN)
        val closer = Device.Pulse(id = 3, name = "Close Kitchen Blinds", roomId = ROOM_A, kind = PulseKind.UNKNOWN)

        val result = groupDevices(listOf(shutter, opener, closer))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals("Kitchen Blinds", grouped.displayName)
        assertEquals(shutter, grouped.primary)
        assertEquals(opener, grouped.openAction)
        assertEquals(closer, grouped.closeAction)
    }

    @Test
    fun `a shutter merges with Open-Close AnalogInput readbacks that can't fire but share its name`() {
        // The hub sometimes wires a shutter's "Open X"/"Close X" pair as passive Analog points
        // instead of Device.Pulse relays — they can't fire anything, but they share the shutter's
        // own base name and room, so they're real telemetry about it, not orphaned sensors.
        val shutter = Device.Shutter(id = 1, name = "Living Room Blinds", roomId = ROOM_A, percentage = 100)
        val openReading = Device.AnalogInput(id = 2, name = "Open Living Room Blinds", roomId = ROOM_A, value = 0.0)
        val closeReading = Device.AnalogInput(id = 3, name = "Close Living Room Blinds", roomId = ROOM_A, value = 0.0)

        val result = groupDevices(listOf(shutter, openReading, closeReading))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals(shutter, grouped.primary)
        assertNull(grouped.openAction)
        assertNull(grouped.closeAction)
        assertEquals(setOf(openReading, closeReading), grouped.secondaries.toSet())
        assertTrue(!grouped.isActionlessSensor)
    }

    @Test
    fun `a light merges with its On-Off AnalogInput sibling that can't fire but shares its name`() {
        val light = Device.BinaryOutput(id = 1, name = "Entryway Ceiling Light", roomId = ROOM_A, isOn = false)
        val toggleReading = Device.AnalogInput(id = 2, name = "On/Off Entryway Ceiling Light", roomId = ROOM_A, value = 0.0)

        val result = groupDevices(listOf(light, toggleReading))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals(light, grouped.primary)
        assertEquals(listOf(toggleReading), grouped.secondaries)
        assertTrue(!grouped.isActionlessSensor)
    }

    @Test
    fun `entryway door pulse merges with its colon-prefixed sensor reading`() {
        val door = Device.Pulse(id = 1, name = "Entryway Door", roomId = ROOM_A, kind = PulseKind.UNKNOWN)
        val sensor = Device.AnalogInput(id = 2, name = "Sensor: Entryway Door", roomId = ROOM_A, value = 0.0)

        val result = groupDevices(listOf(door, sensor))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals("Entryway Door", grouped.displayName)
        assertEquals(door, grouped.primary)
        assertEquals(listOf(sensor), grouped.secondaries)
        assertNull(grouped.openAction)
        assertNull(grouped.closeAction)
    }

    @Test
    fun `balcony light merges with its On-Off toggle relay`() {
        val light = Device.BinaryOutput(id = 1, name = "Luz Varanda", roomId = ROOM_A, isOn = false)
        val toggle = Device.Pulse(id = 2, name = "On/Off Luz Varanda", roomId = ROOM_A, kind = PulseKind.UNKNOWN)

        val result = groupDevices(listOf(light, toggle))

        assertEquals(1, result.size)
        val grouped = result.single() as DeviceDisplayItem.Grouped
        assertEquals("Luz Varanda", grouped.displayName)
        assertEquals(light, grouped.primary)
        assertEquals(toggle, grouped.openAction)
        assertNull(grouped.closeAction)
        assertTrue(grouped.secondaries.isEmpty())
    }

    @Test
    fun `a light's redundant On-Off toggle of the wrong device type is dropped, not shown as its own tile`() {
        val light = Device.BinaryOutput(id = 1, name = "Luz Cozinha", roomId = ROOM_A, isOn = true)
        val unusedToggle = Device.BinaryOutput(id = 2, name = "On/Off Luz Cozinha", roomId = ROOM_A, isOn = false)

        val result = groupDevices(listOf(light, unusedToggle))

        assertEquals(1, result.size)
        val solo = result.single() as DeviceDisplayItem.Solo
        assertEquals(light, solo.primary)
    }

    @Test
    fun `a mistyped directional open action is kept, not dropped, since it has no other way to fire`() {
        val sensor = Device.BinaryInput(id = 1, name = "Estore Cozinha", roomId = ROOM_A, isActive = false)
        val opener = Device.BinaryOutput(id = 2, name = "Abrir Estore Cozinha", roomId = ROOM_A, isOn = false)

        val result = groupDevices(listOf(sensor, opener))

        assertEquals(2, result.size)
        assertTrue(result.all { it is DeviceDisplayItem.Solo })
        assertTrue(result.any { it.primary === opener })
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
    fun `an orphan deviceIn sensor with no matching deviceOut device in its room is an actionless sensor`() {
        val waterSensor = Device.AnalogInput(id = 1, name = "Water Sensor: Kitchen", roomId = ROOM_A, value = 0.0)

        val result = groupDevices(listOf(waterSensor))

        assertEquals(1, result.size)
        val solo = result.single() as DeviceDisplayItem.Solo
        assertEquals(waterSensor, solo.primary)
        assertTrue(solo.isActionlessSensor)
    }

    @Test
    fun `a solo read-only sensor is an actionless sensor`() {
        val sensor = Device.BinaryInput(id = 1, name = "Sensor Fumo", roomId = ROOM_A, isActive = false)

        assertTrue(DeviceDisplayItem.Solo(sensor).isActionlessSensor)
    }

    @Test
    fun `a solo unmatched deviceIn device named as an action is still an actionless sensor`() {
        // These never found a deviceOut device to pair with in the same room (see groupDevices),
        // so despite looking like an "Abrir"/"Open" action by name, they're plain deviceIn
        // readings with no way to actually fire anything — Home shows deviceOut devices only,
        // so these belong on the Sensors tab, not Home, regardless of their name.
        val opener = Device.BinaryInput(id = 1, name = "Abrir Portão", roomId = ROOM_A, isActive = false)
        val closer = Device.AnalogInput(id = 2, name = "Close Bedroom Blinds", roomId = ROOM_A, value = 0.0)

        assertTrue(DeviceDisplayItem.Solo(opener).isActionlessSensor)
        assertTrue(DeviceDisplayItem.Solo(closer).isActionlessSensor)
    }

    @Test
    fun `an unmatched action-named AnalogInput or Counter solo is an orphaned action sensor`() {
        val closer = Device.AnalogInput(id = 1, name = "Close Bedroom Blinds", roomId = ROOM_A, value = 0.0)
        val counter = Device.Counter(id = 2, name = "Open Garage Door", roomId = ROOM_A, value = 0.0)

        assertTrue(isOrphanedActionSensor(DeviceDisplayItem.Solo(closer)))
        assertTrue(isOrphanedActionSensor(DeviceDisplayItem.Solo(counter)))
    }

    @Test
    fun `a genuine reading with no action-like name is not an orphaned action sensor`() {
        val humidity = Device.AnalogInput(id = 1, name = "Humidity", roomId = ROOM_A, value = 42.0)

        assertTrue(!isOrphanedActionSensor(DeviceDisplayItem.Solo(humidity)))
    }

    @Test
    fun `a solo BinaryInput named as an action is not an orphaned action sensor`() {
        // isOrphanedActionSensor only fires for AnalogInput/Counter — a BinaryInput has its own
        // real Active/Idle state regardless of a misleading action-like name, so it isn't the same
        // "should have had a matching action device" data defect.
        val opener = Device.BinaryInput(id = 1, name = "Abrir Portão", roomId = ROOM_A, isActive = false)

        assertTrue(!isOrphanedActionSensor(DeviceDisplayItem.Solo(opener)))
    }

    @Test
    fun `a grouped item is never an orphaned action sensor`() {
        val shutter = Device.Shutter(id = 1, name = "Living Room Blinds", roomId = ROOM_A, percentage = 100)
        val openReading = Device.AnalogInput(id = 2, name = "Open Living Room Blinds", roomId = ROOM_A, value = 0.0)

        val grouped = groupDevices(listOf(shutter, openReading)).single()

        assertTrue(!isOrphanedActionSensor(grouped))
    }

    @Test
    fun `a solo actionable device is not an actionless sensor`() {
        val light = Device.BinaryOutput(id = 1, name = "Luz", roomId = ROOM_A, isOn = true)

        assertTrue(!DeviceDisplayItem.Solo(light).isActionlessSensor)
    }

    @Test
    fun `a grouped sensor with an attached pulse relay is not an actionless sensor`() {
        val sensor = Device.BinaryInput(id = 1, name = "Sensor Porta Pedonal", roomId = ROOM_A, isActive = false)
        val opener = Device.Pulse(id = 2, name = "Abertura Porta Pedonal", roomId = ROOM_A, kind = PulseKind.UNKNOWN)

        val grouped = groupDevices(listOf(sensor, opener)).single() as DeviceDisplayItem.Grouped

        assertTrue(!grouped.isActionlessSensor)
    }

    @Test
    fun `a grouped on-off light with only a hidden numeric reading is not an actionless sensor`() {
        val onOff = Device.BinaryOutput(id = 1, name = "Focos exteriores", roomId = ROOM_A, isOn = true)
        val intensity = Device.AnalogInput(id = 2, name = "Focos exteriores", roomId = ROOM_A, value = 0.0)

        val grouped = groupDevices(listOf(onOff, intensity)).single() as DeviceDisplayItem.Grouped

        assertTrue(!grouped.isActionlessSensor)
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
