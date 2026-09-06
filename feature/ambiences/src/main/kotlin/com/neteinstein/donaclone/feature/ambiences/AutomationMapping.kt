package com.neteinstein.donaclone.feature.ambiences

import com.neteinstein.donaclone.core.model.ActionDraft
import com.neteinstein.donaclone.core.model.AmbienceConditionType
import com.neteinstein.donaclone.core.model.AutomationActionType
import com.neteinstein.donaclone.core.model.AutomationDraft
import com.neteinstein.donaclone.core.model.ConditionDraft
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DpuDeviceCode
import com.neteinstein.donaclone.core.model.PulseKind
import com.neteinstein.donaclone.core.model.TriggerDraft
import com.neteinstein.donaclone.core.model.TriggerType

/**
 * Maps this screen's locally-collected draft state onto the hub's confirmed trigger/condition/
 * action wire schema (protocol notes §11.6). See [AutomationEntryDraft]'s class doc for exactly
 * which fields each section reads off an entry — `event`/`actionCode`/`actionPercentage`/
 * `withLast`/`delayFromLastSeconds` come straight from the entry when set; only a device whose
 * type leaves them unconfigured in the UI (e.g. a [Device.Pulse] action, which only has one
 * `action` code) falls back to a fixed per-device-type default here.
 */
fun AutomationEditorUiState.toAutomationDraft(): AutomationDraft =
    AutomationDraft(
        name = name,
        enabled = enabled,
        startTriggers = entriesBySection[AutomationSection.TRIGGERS].orEmpty().mapNotNull { it.toTriggerDraft() },
        stopTriggers = entriesBySection[AutomationSection.FINALIZERS].orEmpty().mapNotNull { it.toTriggerDraft() },
        conditions = entriesBySection[AutomationSection.CONDITIONS].orEmpty().mapNotNull { it.toConditionDraft() },
        actions = entriesBySection[AutomationSection.ACTIONS].orEmpty().mapNotNull { it.toActionDraft() },
    )

private fun AutomationEntryDraft.toTriggerDraft(): TriggerDraft? =
    when (type) {
        AutomationEntryType.TIMED -> TriggerDraft(type = TriggerType.TIMED, time = wireTime(hour, minute))
        AutomationEntryType.BY_DEVICE -> {
            val d = device ?: return null
            when (d) {
                is Device.BinaryInput ->
                    TriggerDraft(
                        type = TriggerType.INTERRUPT,
                        triggerer = d.id,
                        triggererType = d.dpuTypeCode(),
                        triggererSubtype = d.dpuSubtype(),
                        event = event,
                        deviceRoom = d.roomId,
                    )

                is Device.AnalogInput, is Device.Counter ->
                    TriggerDraft(
                        type = TriggerType.SENSOR,
                        sensor = d.id,
                        sensorType = d.dpuTypeCode(),
                        sensorSubtype = d.dpuSubtype(),
                        lowerBound = lowerBound,
                        upperBound = upperBound,
                        deviceRoom = d.roomId,
                    )

                // Not a valid trigger/finalizer device (deviceOut, or an unmodeled device) —
                // §11.6 only documents interrupt/sensor input devices here. Skip rather than
                // guess a shape; the device picker should filter these out (see follow-up notes).
                else -> null
            }
        }
    }

private fun AutomationEntryDraft.toConditionDraft(): ConditionDraft? =
    when (type) {
        AutomationEntryType.TIMED ->
            ConditionDraft(
                type = AmbienceConditionType.TIMED,
                after = wireTime(hour, minute),
                before = wireTime(endHour ?: hour, endMinute ?: minute),
                daysOfTheWeek = (0..6).map { daysOfWeek.isEmpty() || it in daysOfWeek },
            )

        AutomationEntryType.BY_DEVICE -> {
            val d = device ?: return null
            when (d) {
                is Device.BinaryOutput, is Device.BinaryInput, is Device.Pulse ->
                    ConditionDraft(
                        type = AmbienceConditionType.DEVICE,
                        conditioner = d.id,
                        deviceRoom = d.roomId,
                        status = if (statusOn) 1 else 0,
                    )

                is Device.AnalogInput, is Device.Counter, is Device.Shutter, is Device.Dimmer ->
                    ConditionDraft(
                        type = AmbienceConditionType.DEVICE,
                        conditioner = d.id,
                        deviceRoom = d.roomId,
                        greaterThanValue = lowerBound,
                        lesserThanValue = upperBound,
                    )

                is Device.UnknownDevice -> null
            }
        }
    }

private fun AutomationEntryDraft.toActionDraft(): ActionDraft? {
    val d = device ?: return null
    val (actionType, dpuType, dpuSubtype, defaultActionCode, defaultPercentage) =
        when (d) {
            is Device.BinaryOutput -> ActionShape(AutomationActionType.BINARY_OUT, d.dpuTypeCode(), null, BINARY_OUT_TURN_ON, null)
            is Device.Pulse -> ActionShape(AutomationActionType.PULSE, d.dpuTypeCode(), d.dpuSubtype(), PULSE_PLAY, null)
            is Device.Shutter -> ActionShape(AutomationActionType.SHUTTER, d.dpuTypeCode(), null, SHUTTER_OPEN, null)
            is Device.Dimmer -> ActionShape(AutomationActionType.DIMMER, d.dpuTypeCode(), null, DIMMER_PERCENTAGE, 100)
            // Not a controllable output device — §11.6's action chain only targets deviceOut.
            else -> return null
        }
    return ActionDraft(
        type = actionType,
        device = d.id,
        deviceName = d.name,
        deviceType = dpuType,
        deviceSubtype = dpuSubtype,
        deviceRoom = d.roomId,
        action = actionCode ?: defaultActionCode,
        percentage = actionPercentage ?: defaultPercentage,
        withLast = withLast,
        delayFromLast = delayFromLastSeconds * MILLIS_PER_SECOND,
    )
}

private data class ActionShape(
    val type: Int,
    val deviceType: Int,
    val deviceSubtype: Int?,
    val action: Int,
    val percentage: Int?,
)

private const val BINARY_OUT_TURN_ON = 1
private const val PULSE_PLAY = 0
private const val SHUTTER_OPEN = 1
private const val DIMMER_PERCENTAGE = 2
private const val MILLIS_PER_SECOND = 1000L

private fun wireTime(
    hour: Int,
    minute: Int,
): String = "%02d:%02d".format(hour, minute)

/**
 * The device's own numeric wire type code (`DpuDeviceCode`, protocol notes §3.2/§11.6). Every
 * [Device] subtype maps 1:1 to one code *except* [Device.BinaryInput], which the domain model
 * doesn't distinguish from a One-/Three-Way interruptor (§3.2 already flags the numeric
 * discriminator's own JSON key as unconfirmed) — this defaults to plain `BINARY_IN`. See the
 * follow-up notes in the PR description for what a real fix needs.
 */
internal fun Device.dpuTypeCode(): Int =
    when (this) {
        is Device.BinaryOutput -> DpuDeviceCode.BINARY_OUT.wireValue
        is Device.Pulse -> DpuDeviceCode.PULSE.wireValue
        is Device.Shutter -> DpuDeviceCode.SHUTTER.wireValue
        is Device.Dimmer -> DpuDeviceCode.DIMMER.wireValue
        is Device.BinaryInput -> DpuDeviceCode.BINARY_IN.wireValue
        is Device.AnalogInput -> DpuDeviceCode.ANALOG.wireValue
        is Device.Counter -> DpuDeviceCode.COUNTER.wireValue
        is Device.UnknownDevice -> rawTypeCode ?: 0
    }

/** Only [Device.Pulse] carries a modeled subtype ([PulseKind]); nothing else in the domain model
 * tracks one yet (icon/subtype metadata for other device kinds isn't captured, see follow-up
 * notes). */
internal fun Device.dpuSubtype(): Int? =
    when (this) {
        is Device.Pulse -> kind.wireValue.takeIf { kind != PulseKind.UNKNOWN }
        else -> null
    }
