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

/** One configured entry inside a section, still being edited locally — see
 * [AutomationEditorUiState]. */
data class AutomationEntryDraft(
    val id: Long,
    val type: AutomationEntryType,
    val device: Device? = null,
    val hour: Int = 0,
    val minute: Int = 0,
) {
    val summary: String
        get() =
            when (type) {
                AutomationEntryType.BY_DEVICE -> device?.name ?: "No device selected"
                AutomationEntryType.TIMED -> "%02d:%02d".format(hour, minute)
            }
}
