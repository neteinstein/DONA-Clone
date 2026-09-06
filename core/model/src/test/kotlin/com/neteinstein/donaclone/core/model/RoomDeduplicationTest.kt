package com.neteinstein.donaclone.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class RoomDeduplicationTest {
    @Test
    fun `rooms sharing a name are merged onto the lowest id`() {
        val rooms = listOf(Division(id = 5, name = "Entryway", floor = 0), Division(id = 2, name = "Entryway", floor = 0))
        val devices = listOf(Device.BinaryOutput(id = 1, name = "Light", roomId = 5, isOn = false))

        val (dedupedRooms, remappedDevices) = dedupeRoomsByName(rooms, devices)

        assertEquals(listOf(Division(id = 2, name = "Entryway", floor = 0)), dedupedRooms)
        assertEquals(2, remappedDevices.single().roomId)
    }

    @Test
    fun `name match is case and whitespace insensitive`() {
        val rooms = listOf(Division(id = 1, name = " entryway ", floor = null), Division(id = 2, name = "Entryway", floor = null))
        val devices = emptyList<Device>()

        val (dedupedRooms, _) = dedupeRoomsByName(rooms, devices)

        assertEquals(listOf(Division(id = 1, name = " entryway ", floor = null)), dedupedRooms)
    }

    @Test
    fun `distinct room names pass through unchanged`() {
        val rooms = listOf(Division(id = 1, name = "Entryway", floor = 0), Division(id = 2, name = "Kitchen", floor = 0))
        val devices = listOf(Device.BinaryOutput(id = 1, name = "Light", roomId = 2, isOn = false))

        val (dedupedRooms, remappedDevices) = dedupeRoomsByName(rooms, devices)

        assertEquals(rooms, dedupedRooms)
        assertEquals(devices, remappedDevices)
    }

    @Test
    fun `devices with no room or an unaffected room are left alone`() {
        val rooms = listOf(Division(id = 5, name = "Entryway", floor = 0), Division(id = 2, name = "Entryway", floor = 0))
        val unassigned = Device.BinaryOutput(id = 1, name = "Light", roomId = null, isOn = false)

        val (_, remappedDevices) = dedupeRoomsByName(rooms, listOf(unassigned))

        assertEquals(listOf(unassigned), remappedDevices)
    }
}
