package com.neteinstein.donaclone.core.network.dto

import kotlinx.serialization.Serializable

@Serializable
data class UserDto(
    val id: Int,
    val role: Int = 0,
    val hidden: Boolean = false,
    val photoUri: String? = null,
    val name: String = "",
    val remoteAccessible: Boolean = true,
    val house: Int? = null,
    val enabled: Boolean = true,
)

@Serializable
data class SessionDto(
    val token: String,
)

@Serializable
data class DivisionDto(
    val id: Int,
    val name: String = "",
    val floor: Int? = null,
)

@Serializable
data class AmbienceDto(
    val id: Int,
    val name: String = "",
    val isPlaying: Boolean = false,
    val enabled: Boolean = true,
    /** Id of the first [ActionDto] in the scenario's action chain (protocol notes §11.6). */
    val firstAction: Int? = null,
)

/**
 * One `trigger` object (protocol notes §11.6, read from the real hub's
 * `editTriggerModalController.js`). `id` is omitted (stays null, `explicitNulls = false`) when
 * building a `create` request; the hub assigns it and returns it in the create response.
 */
@Serializable
data class TriggerDto(
    val id: Int? = null,
    val name: String? = null,
    /** `Trigger.Type`: 0=INTERRUPT, 1=TIMED, 2=SENSOR, 3=CONDITION. */
    val type: Int,
    /** TIMED_TRIGGER only, `"HH:mm"`. */
    val time: String? = null,
    /** INTERRUPT_TRIGGER only. */
    val triggerer: Int? = null,
    val triggererType: Int? = null,
    val triggererSubtype: Int? = null,
    val event: Int? = null,
    /** SENSOR_TRIGGER only. */
    val sensor: Int? = null,
    val sensorType: Int? = null,
    val sensorSubtype: Int? = null,
    val lowerBound: Double? = null,
    val upperBound: Double? = null,
    /** UI-convenience field, persisted alongside INTERRUPT_TRIGGER/SENSOR_TRIGGER. */
    val deviceRoom: Int? = null,
)

/**
 * One `condition` object (protocol notes §11.6, read from `editConditionModalController.js`).
 * Same create-without-`id` convention as [TriggerDto].
 */
@Serializable
data class ConditionDto(
    val id: Int? = null,
    val name: String? = null,
    /** UI-level selector, NOT `Condition.*_TYPE`: 1=DEVICE_CONDITION, 6=TIMED_CONDITION. */
    val type: Int,
    /** TIMED_CONDITION only, `"HH:mm"`. */
    val after: String? = null,
    val before: String? = null,
    /** TIMED_CONDITION only, Monday..Sunday. */
    val daysOfTheWeek: List<Boolean>? = null,
    /** DEVICE_CONDITION only. */
    val conditioner: Int? = null,
    val deviceRoom: Int? = null,
    val status: Int? = null,
    val greaterThanValue: Double? = null,
    val lesserThanValue: Double? = null,
)

/**
 * One `action` object (protocol notes §11.6, read from `editActionModalController.js` /
 * `editActionSuccessionModalController.js`). Forms a singly-linked list via [nextAction] —
 * `ambience.firstAction -> action -> action.nextAction -> ... -> (absent)`. Same
 * create-without-`id` convention as [TriggerDto]; unlike triggers/conditions, actions *do* have
 * a real `update` verb (used to rewrite `nextAction` without breaking the chain).
 */
@Serializable
data class ActionDto(
    val id: Int? = null,
    /** `Action.getTypeForDevice`: 0=BINARY_OUT, 1=PULSE, 2=SHUTTER, 3=DIMMER. */
    val type: Int,
    val device: Int,
    val deviceName: String? = null,
    /** The device's own numeric type code (`DpuDeviceCode`), distinct from [type] above. */
    val deviceType: Int? = null,
    val deviceSubtype: Int? = null,
    val deviceRoom: Int? = null,
    /** The per-type action code — `BinaryOut.Action`/`Shutter.Action`/`Dimmer.Action`/`Pulse.Action`. */
    val action: Int,
    /** Shutter/dimmer only, 0-100. */
    val percentage: Int? = null,
    /** Optional: delay this action's own effect (ms). Distinct from [delayFromLast], which is
     * about the chain's timing, not the device's. */
    val duration: Long? = null,
    /** The chain pointer to the next action, absent on the last one. */
    val nextAction: Int? = null,
    /** True = fire simultaneously with the previous action in the chain. */
    val withLast: Boolean = false,
    /** Delay (ms) before firing, measured from the previous action's start ([withLast]) or
     * completion (otherwise). */
    val delayFromLast: Long = 0,
)

/**
 * One `masterLog` entry (`v4/f.java:76-99`). The hub's exact field list beyond the
 * `objectId`/`date` pair used for filtering (§2.4) was not recoverable from decompiled code,
 * so everything past those two is read defensively as nullable.
 */
@Serializable
data class MasterLogEntryDto(
    val id: Int = 0,
    val objectId: Int? = null,
    val date: Long = 0,
    val type: Int? = null,
    val description: String? = null,
    val userId: Int? = null,
)
