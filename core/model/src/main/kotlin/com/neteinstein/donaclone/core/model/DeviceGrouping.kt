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
     * [secondary] is a passive extra reading (e.g. an unused light-intensity sensor) shown only
     * on the detail screen, never on the Home tile. [openAction]/[closeAction] are separate pulse
     * relays that actually drive the physical device when [primary] itself has no native
     * open/close (a native [Device.Shutter] already covers that, so action siblings are ignored
     * when [primary] is a Shutter — see [groupDevices]).
     */
    data class Grouped(
        override val primary: Device,
        override val displayName: String,
        val secondary: Device? = null,
        val openAction: Device.Pulse? = null,
        val closeAction: Device.Pulse? = null,
    ) : DeviceDisplayItem
}

private enum class NameRole { STATE, OPEN_ACTION, CLOSE_ACTION }

// Case-insensitive leading-word prefixes, Portuguese. Extend this list if new naming shows up.
// A lone toggle relay (e.g. a light's "On/Off" pulse) has no separate open/close counterpart, so
// it's treated as an OPEN_ACTION: the single-action branch of onGroupedTap() just fires whichever
// action is present, regardless of role, which is exactly the toggle behavior it needs.
private val OPEN_PREFIXES = listOf("Abrir", "Abertura", "On/Off", "Ligar/Desligar")
private val CLOSE_PREFIXES = listOf("Fechar", "Fecho")
private val STATE_PREFIXES = listOf("Sensor", "Estado")

// Subset of OPEN_PREFIXES naming a redundant on/off *toggle* for something that already has its
// own independent control (a light's "On/Off" relay next to its own BinaryOutput/Dimmer), as
// opposed to a directional "Abrir"/"Abertura" action a shutter/gate has no other way to fire.
private val TOGGLE_PREFIXES = listOf("On/Off", "Ligar/Desligar")

private fun isToggleName(name: String): Boolean =
    TOGGLE_PREFIXES.any { name.trim().startsWith("$it ", ignoreCase = true) }

private fun parseName(name: String): Pair<String, NameRole> {
    val trimmed = name.trim()

    fun strip(prefixes: List<String>) =
        prefixes.firstNotNullOfOrNull { p ->
            if (trimmed.startsWith("$p ", ignoreCase = true)) trimmed.substring(p.length).trim() else null
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

/** True when [name] is recognized as an open/close/toggle action by [OPEN_PREFIXES]/[CLOSE_PREFIXES]
 * — e.g. "Abrir Estore Cozinha", "Fechar Portão". Such a device is a command the user fires, never a
 * passive reading, regardless of what raw [Device] subtype the hub happened to report it as. */
private fun isActionName(name: String): Boolean = parseName(name).second != NameRole.STATE

/** Lower number = preferred as the group's primary/state device. */
private fun statePriority(device: Device): Int =
    when (device) {
        is Device.Shutter -> 0
        is Device.BinaryOutput -> 1
        is Device.Dimmer -> 2
        is Device.BinaryInput -> 3
        is Device.AnalogInput -> 4
        is Device.Counter -> 5
        else -> 6
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
        val secondary =
            stateCandidates
                .filter { it !== primaryParsed }
                .map { it.device }
                .firstOrNull { it is Device.AnalogInput || it is Device.Counter }
        val openActionCandidate = group.firstOrNull { it.role == NameRole.OPEN_ACTION }
        val closeActionCandidate = group.firstOrNull { it.role == NameRole.CLOSE_ACTION }
        val openAction = openActionCandidate?.device as? Device.Pulse
        val closeAction = closeActionCandidate?.device as? Device.Pulse

        if (secondary == null && openAction == null && closeAction == null) {
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
            .filter { it !== primaryParsed && it.device.id != secondary?.id }
            .forEach { result += DeviceDisplayItem.Solo(it.device) } // any other duplicate stays untouched
        group.filter { it.role == NameRole.OPEN_ACTION && it.device !== openAction }
            .forEach { result += DeviceDisplayItem.Solo(it.device) } // extra duplicates, don't drop
        group.filter { it.role == NameRole.CLOSE_ACTION && it.device !== closeAction }
            .forEach { result += DeviceDisplayItem.Solo(it.device) }

        result +=
            DeviceDisplayItem.Grouped(
                primary = primaryParsed.device,
                displayName = primaryParsed.baseName,
                secondary = secondary,
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
        val hasOwnAction =
            primary is Device.BinaryOutput || primary is Device.Pulse ||
                primary is Device.Shutter || primary is Device.Dimmer ||
                isActionName(primary.name)
        val hasGroupedAction = this is DeviceDisplayItem.Grouped && (openAction != null || closeAction != null)
        return !hasOwnAction && !hasGroupedAction
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
