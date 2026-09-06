package com.neteinstein.donaclone.core.model

/** `Trigger.Type` (protocol notes §11.6). */
object TriggerType {
    const val INTERRUPT = 0
    const val TIMED = 1
    const val SENSOR = 2
    const val CONDITION = 3
}

/** The UI-level condition-kind selector (protocol notes §11.6) — NOT the same as
 * `Condition.*_TYPE`, which the real client never assigns and this app must not send either. */
object AmbienceConditionType {
    const val DEVICE = 1
    const val TIMED = 6
}

/** `Action.getTypeForDevice` (protocol notes §11.2/§11.6) — which device-type editor an ambience
 * action entry needs, distinct from the device's own [DpuDeviceCode]. */
object AutomationActionType {
    const val BINARY_OUT = 0
    const val PULSE = 1
    const val SHUTTER = 2
    const val DIMMER = 3
}

/**
 * A `trigger` (or `condition`/`action`) sub-object bound for the hub, in the exact shape
 * protocol notes §11.6 documents. Domain-layer counterpart of
 * [com.neteinstein.donaclone.core.network.dto.TriggerDto] — kept separate since `core:domain`
 * cannot depend on `core:network`'s serialization DTOs.
 */
data class TriggerDraft(
    val type: Int,
    val name: String? = null,
    val time: String? = null,
    val triggerer: Int? = null,
    val triggererType: Int? = null,
    val triggererSubtype: Int? = null,
    val event: Int? = null,
    val sensor: Int? = null,
    val sensorType: Int? = null,
    val sensorSubtype: Int? = null,
    val lowerBound: Double? = null,
    val upperBound: Double? = null,
    val deviceRoom: Int? = null,
)

/** Domain-layer counterpart of [com.neteinstein.donaclone.core.network.dto.ConditionDto]. */
data class ConditionDraft(
    val type: Int,
    val name: String? = null,
    val after: String? = null,
    val before: String? = null,
    val daysOfTheWeek: List<Boolean>? = null,
    val conditioner: Int? = null,
    val deviceRoom: Int? = null,
    val status: Int? = null,
    val greaterThanValue: Double? = null,
    val lesserThanValue: Double? = null,
)

/** Domain-layer counterpart of [com.neteinstein.donaclone.core.network.dto.ActionDto] — one link
 * of the action chain. Chain order (and therefore `nextAction`/`withLast`/`delayFromLast` wiring)
 * is determined by list position in [AutomationDraft.actions], not by a field here. */
data class ActionDraft(
    val type: Int,
    val device: Int,
    val deviceName: String? = null,
    val deviceType: Int,
    val deviceSubtype: Int? = null,
    val deviceRoom: Int? = null,
    val action: Int,
    val percentage: Int? = null,
    val duration: Long? = null,
    val withLast: Boolean = false,
    val delayFromLast: Long = 0,
)

/** Everything [com.neteinstein.donaclone.core.domain.usecase.SaveAutomationUseCase] needs to
 * create (or add to) one ambience/scenario on the hub. */
data class AutomationDraft(
    val name: String,
    val enabled: Boolean,
    val startTriggers: List<TriggerDraft> = emptyList(),
    val stopTriggers: List<TriggerDraft> = emptyList(),
    val conditions: List<ConditionDraft> = emptyList(),
    val actions: List<ActionDraft> = emptyList(),
)
