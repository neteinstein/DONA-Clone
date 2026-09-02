package com.neteinstein.donaclone.core.model

/**
 * Numeric device-type codes as returned by the DPU for `deviceOut`/`deviceIn` reads.
 * Confirmed from the decompiled `q4.e` enum.
 */
enum class DpuDeviceCode(val wireValue: Int) {
    BINARY_IN(10),
    ANALOG(20),
    COUNTER(30),
    ONE_WAY_INTERRUPTOR(40),
    THREE_WAY_INTERRUPTOR(50),
    BINARY_OUT(60),
    PULSE(61),
    SHUTTER(70),
    DIMMER(71),
    VIDEO_CAMERA(80),
    INTERCOM(81),
    ;

    companion object {
        fun fromWireValue(value: Int): DpuDeviceCode? = entries.firstOrNull { it.wireValue == value }
    }
}

/** Momentary-output subtype, confirmed from `t4.d`/`p4.h`'s icon-mapping switch. */
enum class PulseKind(val wireValue: Int) {
    SIREN(10),
    CHIME(11),
    LOCK(20),
    ARM_OUTPUT(30),
    DISARM_OUTPUT(31),
    ARM_DISARM_COUPLED(32),
    UNKNOWN(-1),
    ;

    companion object {
        fun fromWireValue(value: Int): PulseKind = entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

/**
 * Fields shared by every device the DPU exposes. `freeTypeLabel` is the hub's own free-text
 * category (e.g. "lights", "fridges") used purely for grouping/iconography in the UI — it is
 * independent from [DpuDeviceCode], which selects the concrete [Device] subclass/behaviour.
 */
interface DeviceCommon {
    val id: Int
    val name: String
    val description: String?
    val enabled: Boolean
    val online: Boolean
    val roomId: Int?
    val freeTypeLabel: String?
}

sealed class Device : DeviceCommon {

    data class BinaryOutput(
        override val id: Int,
        override val name: String,
        override val description: String? = null,
        override val enabled: Boolean = true,
        override val online: Boolean = true,
        override val roomId: Int? = null,
        override val freeTypeLabel: String? = null,
        val isOn: Boolean,
    ) : Device()

    data class Pulse(
        override val id: Int,
        override val name: String,
        override val description: String? = null,
        override val enabled: Boolean = true,
        override val online: Boolean = true,
        override val roomId: Int? = null,
        override val freeTypeLabel: String? = null,
        val kind: PulseKind,
        val durationSeconds: Int? = null,
    ) : Device()

    data class Shutter(
        override val id: Int,
        override val name: String,
        override val description: String? = null,
        override val enabled: Boolean = true,
        override val online: Boolean = true,
        override val roomId: Int? = null,
        override val freeTypeLabel: String? = null,
        val percentage: Int,
    ) : Device()

    data class Dimmer(
        override val id: Int,
        override val name: String,
        override val description: String? = null,
        override val enabled: Boolean = true,
        override val online: Boolean = true,
        override val roomId: Int? = null,
        override val freeTypeLabel: String? = null,
        val percentage: Int,
    ) : Device()

    data class BinaryInput(
        override val id: Int,
        override val name: String,
        override val description: String? = null,
        override val enabled: Boolean = true,
        override val online: Boolean = true,
        override val roomId: Int? = null,
        override val freeTypeLabel: String? = null,
        val isActive: Boolean,
    ) : Device()

    data class AnalogInput(
        override val id: Int,
        override val name: String,
        override val description: String? = null,
        override val enabled: Boolean = true,
        override val online: Boolean = true,
        override val roomId: Int? = null,
        override val freeTypeLabel: String? = null,
        val value: Double,
    ) : Device()

    data class Counter(
        override val id: Int,
        override val name: String,
        override val description: String? = null,
        override val enabled: Boolean = true,
        override val online: Boolean = true,
        override val roomId: Int? = null,
        override val freeTypeLabel: String? = null,
        val value: Double,
    ) : Device()

    /** A device the hub reported with a type code this client doesn't model yet. */
    data class UnknownDevice(
        override val id: Int,
        override val name: String,
        override val description: String? = null,
        override val enabled: Boolean = true,
        override val online: Boolean = true,
        override val roomId: Int? = null,
        override val freeTypeLabel: String? = null,
        val rawTypeCode: Int?,
    ) : Device()
}

/** Actions that can be sent for a device, per the confirmed `verb: action` semantics. */
sealed interface DeviceCommand {
    data class SetBinaryOutput(val deviceId: Int, val turnOn: Boolean) : DeviceCommand
    data class FirePulse(val deviceId: Int) : DeviceCommand
    data class SetShutterOpen(val deviceId: Int) : DeviceCommand
    data class SetShutterClosed(val deviceId: Int) : DeviceCommand
    data class SetShutterPercentage(val deviceId: Int, val percentage: Int) : DeviceCommand
    data class SetDimmerPercentage(val deviceId: Int, val percentage: Int) : DeviceCommand
}
