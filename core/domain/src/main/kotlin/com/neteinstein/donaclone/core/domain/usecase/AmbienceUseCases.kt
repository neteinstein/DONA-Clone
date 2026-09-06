package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.model.Ambience
import com.neteinstein.donaclone.core.model.AutomationDraft

class GetAmbiencesUseCase(
    private val repository: AmbienceRepository,
) {
    suspend operator fun invoke(): DonaResult<List<Ambience>> = repository.getAmbiences()
}

class TriggerAmbienceUseCase(
    private val repository: AmbienceRepository,
) {
    suspend operator fun invoke(ambience: Ambience): DonaResult<Unit> =
        repository.triggerAmbience(ambience.id, run = !ambience.isPlaying)
}

/**
 * Persists an [AutomationDraft] to the hub, following protocol notes §11.6's confirmed
 * CRUD/linking sequence:
 * 1. Create the ambience (or update an existing one's name/enabled).
 * 2. Create each start/stop trigger and condition, then link it onto the ambience via the
 *    `ambienceStartTrigger`/`ambienceStopTrigger`/`ambienceCondition` join subjects.
 * 3. Build the action chain in list order: create each action, `update` the *previous* action's
 *    `nextAction` to point at it (or, for the first action, `update ambience.firstAction`).
 *
 * Note this only ever *adds* triggers/conditions/actions — it never deletes an existing
 * automation's pre-existing sub-objects. There's no confirmed hub `read` for the
 * `ambienceStartTrigger`/`ambienceStopTrigger`/`ambienceCondition` join tables (§11.4 marks them
 * "create only"), so a from-scratch client has no way to discover which trigger/condition/action
 * ids already belong to an ambience being edited in order to diff or remove them.
 */
class SaveAutomationUseCase(
    private val repository: AmbienceRepository,
) {
    suspend operator fun invoke(
        ambienceId: Int?,
        draft: AutomationDraft,
    ): DonaResult<Int> {
        val targetId =
            when (ambienceId) {
                null ->
                    when (val created = repository.createAmbience(draft.name, draft.enabled)) {
                        is DonaResult.Success -> created.data.id
                        is DonaResult.Error -> return created
                    }
                else -> {
                    val updated = repository.updateAmbienceFields(ambienceId, draft.name, draft.enabled)
                    if (updated is DonaResult.Error) return updated
                    ambienceId
                }
            }

        draft.startTriggers.forEach { trigger ->
            val triggerId =
                when (val created = repository.createTrigger(trigger)) {
                    is DonaResult.Success -> created.data
                    is DonaResult.Error -> return created
                }
            val linked = repository.linkStartTrigger(targetId, triggerId)
            if (linked is DonaResult.Error) return linked
        }

        draft.stopTriggers.forEach { trigger ->
            val triggerId =
                when (val created = repository.createTrigger(trigger)) {
                    is DonaResult.Success -> created.data
                    is DonaResult.Error -> return created
                }
            val linked = repository.linkStopTrigger(targetId, triggerId)
            if (linked is DonaResult.Error) return linked
        }

        draft.conditions.forEach { condition ->
            val conditionId =
                when (val created = repository.createCondition(condition)) {
                    is DonaResult.Success -> created.data
                    is DonaResult.Error -> return created
                }
            val linked = repository.linkCondition(targetId, conditionId)
            if (linked is DonaResult.Error) return linked
        }

        var previousActionId: Int? = null
        draft.actions.forEach { action ->
            val actionId =
                when (val created = repository.createAction(action)) {
                    is DonaResult.Success -> created.data
                    is DonaResult.Error -> return created
                }
            val chained =
                previousActionId?.let { repository.setActionNext(it, actionId) }
                    ?: repository.setAmbienceFirstAction(targetId, actionId)
            if (chained is DonaResult.Error) return chained
            previousActionId = actionId
        }

        return DonaResult.Success(targetId)
    }
}

class DeleteAutomationUseCase(
    private val repository: AmbienceRepository,
) {
    suspend operator fun invoke(ambienceId: Int): DonaResult<Unit> = repository.deleteAmbience(ambienceId)
}
