package com.neteinstein.donaclone.feature.ambiences

import com.neteinstein.donaclone.core.model.Device

/** Which of the automation editor's four sections a given [AutomationEntryDraft] belongs to —
 * mirrors the hub's own "Iniciadores de Ação" / "Ações a Executar" / "Condições" / "Finalizadores
 * de Ação" scenario structure. */
enum class AutomationSection(
    val title: String,
) {
    TRIGGERS("Iniciadores de Ação"),
    ACTIONS("Ações a Executar"),
    CONDITIONS("Condições"),
    FINALIZERS("Finalizadores de Ação"),
}

/** How a single trigger/condition/finalizer entry is defined — either watching a device, or a
 * fixed time of day. [AutomationSection.ACTIONS] entries are always [BY_DEVICE] (there's no
 * "run this action at a time" concept — that's what a [TIMED] trigger is for). */
enum class AutomationEntryType {
    BY_DEVICE,
    TIMED,
}

/**
 * One configured entry inside a section, still being edited locally — see
 * [AutomationEditorUiState]. Mirrors the hub's trigger/condition/action field-level schema
 * (protocol notes §11.6) closely enough that [AutomationEditorViewModel.save] can map it
 * directly, but keeps only what [AutomationEditorScreen] actually collects from the user plus a
 * handful of fields with a documented, sensible default (see the class doc on
 * [AutomationEditorViewModel] for exactly which ones, and why):
 *
 * - [lowerBound]/[upperBound]: a [AutomationSection.TRIGGERS]/[FINALIZERS] entry's sensor bounds
 *   (`Trigger.lowerBound`/`upperBound`) when [device] is an Analog/Counter input, *or* a
 *   [AutomationSection.CONDITIONS] entry's ranged thresholds (`Condition.greaterThanValue`/
 *   `lesserThanValue`) when [device] is Analog/Counter/Shutter/Dimmer — same two numbers, just a
 *   different wire field name depending on which kind of sub-object this becomes.
 * - [statusOn]: a [AutomationSection.CONDITIONS] entry's target on/off state
 *   (`Condition.status`) when [device] is a binary-state device (BinaryIn/BinaryOut/Pulse).
 * - [endHour]/[endMinute]/[daysOfWeek]: only meaningful for a [AutomationSection.CONDITIONS]
 *   [AutomationEntryType.TIMED] entry (`Condition.before`/`daysOfTheWeek` — [hour]/[minute]
 *   double as `Condition.after`). A [AutomationSection.TRIGGERS]/[FINALIZERS] [TIMED] entry only
 *   ever needs the single [hour]/[minute] time (`Trigger.time`) and ignores these.
 * - [event]: a [AutomationSection.TRIGGERS]/[FINALIZERS] entry's `Trigger.event` when [device] is
 *   an interrupt-type input (`BinaryIn.EVENT_ACTION1 = 0` / `EVENT_ACTION2 = 1`).
 * - [actionCode]/[actionPercentage]/[withLast]/[delayFromLastSeconds]: an
 *   [AutomationSection.ACTIONS] entry's own `Action.action`/`percentage`/`withLast`/
 *   `delayFromLast`. `actionCode`/`actionPercentage` null means "use a sensible per-device-type
 *   default" (see [toAutomationDraft]).
 */
data class AutomationEntryDraft(
    val id: Long,
    val type: AutomationEntryType,
    val device: Device? = null,
    val hour: Int = 0,
    val minute: Int = 0,
    val endHour: Int? = null,
    val endMinute: Int? = null,
    /** 0=Monday..6=Sunday. Empty is treated as "every day" when building a timed condition. */
    val daysOfWeek: Set<Int> = emptySet(),
    val lowerBound: Double? = null,
    val upperBound: Double? = null,
    val statusOn: Boolean = true,
    val event: Int = 0,
    val actionCode: Int? = null,
    val actionPercentage: Int? = null,
    val withLast: Boolean = false,
    val delayFromLastSeconds: Int = 0,
) {
    val summary: String
        get() =
            when (type) {
                AutomationEntryType.BY_DEVICE -> device?.name ?: "No device selected"
                AutomationEntryType.TIMED ->
                    if (endHour != null && endMinute != null) {
                        "%02d:%02d - %02d:%02d".format(hour, minute, endHour, endMinute)
                    } else {
                        "%02d:%02d".format(hour, minute)
                    }
            }
}
