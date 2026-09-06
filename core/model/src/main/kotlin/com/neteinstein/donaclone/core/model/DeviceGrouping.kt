package com.neteinstein.donaclone.core.model

import java.text.Normalizer

/**
 * What a Home-tab tile (and the detail screen) actually renders: either one raw [Device]
 * untouched, or several raw devices the hub reports separately that are really one physical
 * thing — see [groupDevices].
 */
sealed interface DeviceDisplayItem {
    val primary: Device
    val displayName: String

    data class Solo(
        override val primary: Device,
    ) : DeviceDisplayItem {
        override val displayName get() = primary.name
    }

    /**
     * [secondaries] are passive extra readings shown only on the detail screen, never on the Home
     * tile — an unused light-intensity sensor sharing [primary]'s state-role name, and/or an
     * open/close-role AnalogInput/Counter sibling that reads like an action ("Open X", "Close X")
     * but can't fire one (only a [Device.Pulse] can — see [groupDevices]). [openAction]/[closeAction]
     * are separate pulse relays that actually drive the physical device when [primary] itself has
     * no native open/close (a native [Device.Shutter] already covers that, so action-role Pulse
     * siblings are ignored when [primary] is a Shutter — see [groupDevices]).
     */
    data class Grouped(
        override val primary: Device,
        override val displayName: String,
        val secondaries: List<Device> = emptyList(),
        val openAction: Device.Pulse? = null,
        val closeAction: Device.Pulse? = null,
    ) : DeviceDisplayItem
}

private enum class NameRole { STATE, OPEN_ACTION, CLOSE_ACTION }

// Case-insensitive leading-word prefixes. The hub is observed to name devices in either
// Portuguese or English depending on install — both are recognized. Extend these lists if new
// naming shows up.
// A lone toggle relay (e.g. a light's "On/Off" pulse) has no separate open/close counterpart, so
// it's treated as an OPEN_ACTION: the single-action branch of onGroupedTap() just fires whichever
// action is present, regardless of role, which is exactly the toggle behavior it needs.
private val OPEN_PREFIXES = listOf("Abrir", "Abertura", "Open", "On/Off", "Ligar/Desligar")
private val CLOSE_PREFIXES = listOf("Fechar", "Fecho", "Close")
private val STATE_PREFIXES = listOf("Sensor", "Estado")

// Subset of OPEN_PREFIXES naming a redundant on/off *toggle* for something that already has its
// own independent control (a light's "On/Off" relay next to its own BinaryOutput/Dimmer), as
// opposed to a directional "Abrir"/"Abertura" action a shutter/gate has no other way to fire.
private val TOGGLE_PREFIXES = listOf("On/Off", "Ligar/Desligar")

private fun isToggleName(name: String): Boolean =
    TOGGLE_PREFIXES.any { name.trim().startsWith("$it ", ignoreCase = true) }

private fun parseName(name: String): Pair<String, NameRole> {
    val trimmed = name.trim()

    // A recognized prefix is followed by a word boundary — a space, or a colon (with or without
    // a following space, e.g. "Sensor: Entryway Door") — never bare concatenation.
    fun strip(prefixes: List<String>) =
        prefixes.firstNotNullOfOrNull { p ->
            when {
                trimmed.startsWith("$p ", ignoreCase = true) -> trimmed.substring(p.length).trim()
                trimmed.startsWith("$p:", ignoreCase = true) -> trimmed.substring(p.length + 1).trim()
                else -> null
            }
        }
    strip(OPEN_PREFIXES)?.let { return it to NameRole.OPEN_ACTION }
    strip(CLOSE_PREFIXES)?.let { return it to NameRole.CLOSE_ACTION }
    strip(STATE_PREFIXES)?.let { return it to NameRole.STATE }
    return trimmed to NameRole.STATE // no recognized prefix — treat the whole name as the base, state role
}

// Portuguese connector words (articles/prepositions) that show up inconsistently between how a
// state device and its action siblings got named on the hub — e.g. "Abertura Portão Garagem" vs
// "Portão da Garagem". Stripped only for the purposes of matching a shared base name, never from
// the displayed name itself.
private val CONNECTOR_WORDS = setOf("da", "de", "do", "das", "dos")

/** Case/accent/connector-word-insensitive key for matching a base name across differently-worded
 * hub names for the same physical thing (e.g. "portão garagem" and "portao da garagem" both
 * normalize to "portao garagem"). Used only as a grouping key — [DeviceDisplayItem.displayName]
 * always keeps the original wording. */
private fun normalizeForMatching(name: String): String {
    val withoutAccents =
        Normalizer.normalize(name, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "")
    return withoutAccents
        .lowercase()
        .split(Regex("\\s+"))
        .filter { it.isNotEmpty() && it !in CONNECTOR_WORDS }
        .joinToString(" ")
}

/**
 * Lower number = preferred as the group's primary/state device. All deviceOut subtypes
 * (Shutter/BinaryOutput/Pulse/Dimmer) always outrank any deviceIn reading, so a momentary
 * output like a door striker (a plain-named [Device.Pulse], e.g. "Entryway Door") stays the
 * group's primary even when paired with its own state-role sensor reading.
 */
private fun statePriority(device: Device): Int =
    when (device) {
        is Device.Shutter -> 0
        is Device.BinaryOutput -> 1
        is Device.Pulse -> 2
        is Device.Dimmer -> 3
        is Device.BinaryInput -> 4
        is Device.AnalogInput -> 5
        is Device.Counter -> 6
        else -> 7
    }

/**
 * Groups raw hub devices that are really one physical thing into [DeviceDisplayItem.Grouped],
 * keyed on (room, base-name-after-stripping-known-prefixes). Devices that don't share a base name
 * + room with anything else pass through unchanged as [DeviceDisplayItem.Solo] — merging is
 * opt-in per naming convention, never assumed.
 */
fun groupDevices(devices: List<Device>): List<DeviceDisplayItem> {
    data class Parsed(val device: Device, val baseName: String, val role: NameRole)

    val parsed =
        devices.map {
            val (base, role) = parseName(it.name)
            Parsed(it, base, role)
        }
    val result = mutableListOf<DeviceDisplayItem>()

    parsed.groupBy { it.device.roomId to normalizeForMatching(it.baseName) }.values.forEach { group ->
        if (group.size == 1) {
            result += DeviceDisplayItem.Solo(group.single().device)
            return@forEach
        }

        val stateCandidates = group.filter { it.role == NameRole.STATE }
        val primaryParsed = stateCandidates.minByOrNull { statePriority(it.device) }
        if (primaryParsed == null) {
            // Only open/close-role names shared a base with nothing to attach to — don't guess.
            group.forEach { result += DeviceDisplayItem.Solo(it.device) }
            return@forEach
        }

        // Only merge a second STATE-role device in as a hidden "secondary" when it's a passive
        // numeric reading (AnalogInput/Counter) — never silently hide a second independently
        // controllable switch/sensor that happens to share a name.
        val stateSecondary =
            stateCandidates
                .filter { it !== primaryParsed }
                .map { it.device }
                .firstOrNull { it is Device.AnalogInput || it is Device.Counter }
        val openActionCandidate = group.firstOrNull { it.role == NameRole.OPEN_ACTION }
        val closeActionCandidate = group.firstOrNull { it.role == NameRole.CLOSE_ACTION }
        val openAction = openActionCandidate?.device as? Device.Pulse
        val closeAction = closeActionCandidate?.device as? Device.Pulse

        // An open/close-role sibling that isn't a Device.Pulse can never fire anything, but when
        // it's a passive numeric reading (AnalogInput/Counter) it's still real telemetry about
        // primary's own action (a shutter's "Open X" readback, say) — attach it as an extra
        // secondary instead of stranding it on the Sensors tab, same as stateSecondary above.
        val openReading = openActionCandidate?.device?.takeIf { it is Device.AnalogInput || it is Device.Counter }
        val closeReading = closeActionCandidate?.device?.takeIf { it is Device.AnalogInput || it is Device.Counter }
        val secondaries = listOfNotNull(stateSecondary, openReading, closeReading)

        if (secondaries.isEmpty() && openAction == null && closeAction == null) {
            // A same-named "On/Off"/"Ligar-Desligar" toggle relay that couldn't be wired as an
            // action (it isn't a Device.Pulse) is presumed a redundant, non-functional duplicate
            // of a state device that's already independently controllable on its own (a light's
            // own BinaryOutput/Dimmer) — showing it as a second tile would just be dead clutter,
            // so it's dropped instead of kept. A genuine directional "Abrir"/"Fechar" action of
            // the wrong type is left alone (below) since that one has no other way to be fired.
            val isRedundantToggle =
                (primaryParsed.device is Device.BinaryOutput || primaryParsed.device is Device.Dimmer) &&
                    openActionCandidate?.let { isToggleName(it.device.name) } == true
            if (!isRedundantToggle) {
                group.forEach { result += DeviceDisplayItem.Solo(it.device) }
                return@forEach
            }
            result += DeviceDisplayItem.Solo(primaryParsed.device)
            stateCandidates.filter { it !== primaryParsed }.forEach { result += DeviceDisplayItem.Solo(it.device) }
            return@forEach
        }

        stateCandidates
            .filter { it !== primaryParsed && it.device.id != stateSecondary?.id }
            .forEach { result += DeviceDisplayItem.Solo(it.device) } // any other duplicate stays untouched
        group.filter { it.role == NameRole.OPEN_ACTION && it.device !== openAction && it.device !== openReading }
            .forEach { result += DeviceDisplayItem.Solo(it.device) } // extra duplicates, don't drop
        group.filter { it.role == NameRole.CLOSE_ACTION && it.device !== closeAction && it.device !== closeReading }
            .forEach { result += DeviceDisplayItem.Solo(it.device) }

        result +=
            DeviceDisplayItem.Grouped(
                primary = primaryParsed.device,
                displayName = primaryParsed.baseName,
                secondaries = secondaries,
                openAction = openAction,
                closeAction = closeAction,
            )
    }
    return result
}

/**
 * True for a display item with no tap action of its own — a plain read-only sensor (door contact,
 * humidity reading, pulse counter, ...) that isn't a [DeviceDisplayItem.Grouped] item's state with
 * an attached pulse relay. Drives the Home/Sensors tab split: these items move to the Sensors tab
 * instead of Home. Mirrors the tile-rendering rule in `DeviceRoomGrid`'s `DeviceCell` (the "else ->
 * ReadOnly" branch) and its `onClick` dispatch rule.
 */
val DeviceDisplayItem.isActionlessSensor: Boolean
    get() {
        // "Own action" is judged purely by the primary's concrete deviceOut subtype, never by
        // name — a solo deviceIn device (BinaryInput/AnalogInput/Counter) that never matched a
        // deviceOut device stays an actionless sensor no matter what it's named, so it can only
        // ever surface on the Sensors tab, never Home (Home shows deviceOut devices only).
        val hasOwnAction =
            primary is Device.BinaryOutput || primary is Device.Pulse ||
                primary is Device.Shutter || primary is Device.Dimmer
        val hasGroupedAction = this is DeviceDisplayItem.Grouped && (openAction != null || closeAction != null)
        return !hasOwnAction && !hasGroupedAction
    }

/**
 * True for a [DeviceDisplayItem.Solo] wrapping a passive numeric reading (AnalogInput/Counter)
 * whose name reads as a directional or toggle action ("Open X", "Close X", "On/Off X", "Abrir X",
 * ...) per the same prefixes [groupDevices] itself recognizes, but which [groupDevices] could not
 * attach to any action-capable device sharing its room + base name — see the `secondaries`
 * doc on [DeviceDisplayItem.Grouped]. Every such sensor is expected to have a real action
 * counterpart on the hub; one that doesn't signals a naming/wiring mismatch in the hub's own
 * device setup, not normal input, so callers should log it as an error rather than accept it
 * silently (see `DevicesViewModel.refresh`).
 */
fun isOrphanedActionSensor(item: DeviceDisplayItem): Boolean {
    if (item !is DeviceDisplayItem.Solo) return false
    val device = item.primary
    if (device !is Device.AnalogInput && device !is Device.Counter) return false
    return parseName(device.name).second != NameRole.STATE
}

/**
 * Generic "is this device currently open/on/active" used for the grouped tile's smart-toggle tap
 * rule — mirrors the native shutter's own open/closed notion for devices that have no percentage
 * of their own.
 */
fun isDeviceOpenOrOn(device: Device): Boolean =
    when (device) {
        is Device.BinaryOutput -> device.isOn
        is Device.BinaryInput -> device.isActive
        is Device.AnalogInput -> device.value > 0
        is Device.Counter -> device.value > 0
        is Device.Shutter -> device.percentage > 0
        is Device.Dimmer -> device.percentage > 0
        else -> false
    }
