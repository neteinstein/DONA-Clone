package com.neteinstein.donaclone.core.model

/**
 * The hub can report the same physical room under more than one numeric id — e.g. after a
 * reconfiguration that recreated a room without removing its old entry — while both rows keep an
 * identical name. The UI keys rooms strictly by id (see feature/devices' `DevicesUiState`), so
 * left alone this renders one duplicate room/category header per stray id. Rooms sharing a
 * case/whitespace-insensitive name are merged onto the lowest id, and every device pointing at a
 * merged-away id is remapped onto that canonical id so it isn't orphaned into "Unassigned".
 */
fun dedupeRoomsByName(
    rooms: List<Division>,
    devices: List<Device>,
): Pair<List<Division>, List<Device>> {
    val canonicalIdByName = mutableMapOf<String, Int>()
    val idRemap = mutableMapOf<Int, Int>()

    rooms.sortedBy { it.id }.forEach { room ->
        val key = room.name.trim().lowercase()
        val canonicalId = canonicalIdByName.getOrPut(key) { room.id }
        if (canonicalId != room.id) idRemap[room.id] = canonicalId
    }

    if (idRemap.isEmpty()) return rooms to devices

    val dedupedRooms = rooms.filterNot { it.id in idRemap }
    val remappedDevices =
        devices.map { device ->
            val canonicalId = device.roomId?.let { idRemap[it] } ?: return@map device
            device.withRoomId(canonicalId)
        }
    return dedupedRooms to remappedDevices
}

private fun Device.withRoomId(roomId: Int): Device =
    when (this) {
        is Device.BinaryOutput -> copy(roomId = roomId)
        is Device.Pulse -> copy(roomId = roomId)
        is Device.Shutter -> copy(roomId = roomId)
        is Device.Dimmer -> copy(roomId = roomId)
        is Device.BinaryInput -> copy(roomId = roomId)
        is Device.AnalogInput -> copy(roomId = roomId)
        is Device.Counter -> copy(roomId = roomId)
        is Device.UnknownDevice -> copy(roomId = roomId)
    }
