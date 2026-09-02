package com.neteinstein.donaclone.core.network.mapper

import com.neteinstein.donaclone.core.model.Device
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceJsonMapperTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun obj(text: String): JsonObject = json.parseToJsonElement(text).jsonObject

    @Test
    fun `binaryOut with status only maps to BinaryOutput`() {
        val raw = obj("""{"id":1,"name":"Kitchen light","status":1,"room":3}""")
        val device = DeviceJsonMapper.parseDeviceOut(raw)
        assertTrue(device is Device.BinaryOutput)
        device as Device.BinaryOutput
        assertEquals(1, device.id)
        assertEquals("Kitchen light", device.name)
        assertEquals(3, device.roomId)
        assertTrue(device.isOn)
    }

    @Test
    fun `binaryOut with status zero is off`() {
        val raw = obj("""{"id":2,"name":"Outlet","status":0}""")
        val device = DeviceJsonMapper.parseDeviceOut(raw) as Device.BinaryOutput
        assertTrue(!device.isOn)
    }

    @Test
    fun `shutter has percentage and processDuration`() {
        val raw = obj("""{"id":3,"name":"Living room blinds","percentage":55,"processDuration":20}""")
        val device = DeviceJsonMapper.parseDeviceOut(raw)
        assertTrue(device is Device.Shutter)
        assertEquals(55, (device as Device.Shutter).percentage)
    }

    @Test
    fun `dimmer has percentage without processDuration or status`() {
        val raw = obj("""{"id":4,"name":"Hall dimmer","percentage":80}""")
        val device = DeviceJsonMapper.parseDeviceOut(raw)
        assertTrue(device is Device.Dimmer)
        assertEquals(80, (device as Device.Dimmer).percentage)
    }

    @Test
    fun `pulse has status and duration`() {
        val raw = obj("""{"id":5,"name":"Siren","status":0,"duration":5,"subtype":10}""")
        val device = DeviceJsonMapper.parseDeviceOut(raw)
        assertTrue(device is Device.Pulse)
        assertEquals(com.neteinstein.donaclone.core.model.PulseKind.SIREN, (device as Device.Pulse).kind)
    }

    @Test
    fun `binaryIn with status maps to BinaryInput`() {
        val raw = obj("""{"id":6,"name":"Front door","status":1}""")
        val device = DeviceJsonMapper.parseDeviceIn(raw)
        assertTrue(device is Device.BinaryInput)
        assertTrue((device as Device.BinaryInput).isActive)
    }

    @Test
    fun `analog input with value maps to AnalogInput`() {
        val raw = obj("""{"id":7,"name":"Outdoor temp","value":21.5}""")
        val device = DeviceJsonMapper.parseDeviceIn(raw)
        assertTrue(device is Device.AnalogInput)
        assertEquals(21.5, (device as Device.AnalogInput).value, 0.0001)
    }

    @Test
    fun `buildActionOptions merges field updates into a copy of the raw object`() {
        val raw = obj("""{"id":1,"name":"Kitchen light","status":0}""")
        val options = DeviceJsonMapper.buildActionOptions(
            rawDevice = raw,
            action = 1,
        )
        val objectField = options["object"]!!.jsonObject
        assertEquals(raw["id"], objectField["id"])
        assertEquals(raw["name"], objectField["name"])
        assertEquals(1, options["action"]!!.jsonPrimitive.int)
    }
}
