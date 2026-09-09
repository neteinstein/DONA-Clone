package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.model.Ambience
import com.neteinstein.donaclone.core.model.AutomationDetail
import com.neteinstein.donaclone.core.model.AutomationDraft

class GetAmbiencesUseCase(
    private val repository: AmbienceRepository,
) {
    suspend operator fun invoke(): DonaResult<List<Ambience>> = repository.getAmbiences()
}

/** Reads an existing scenario's triggers/conditions/actions back off the hub so the editor can
 * show what it's actually made of, rather than an empty shell. */
class GetAutomationDetailUseCase(
    private val repository: AmbienceRepository,
) {
    suspend operator fun invoke(ambienceId: Int): DonaResult<AutomationDetail> = repository.getAutomationDetail(ambienceId)
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
 * Entries the editor read back off the hub carry a [TriggerDraft.existingId] and are left exactly
 * as they are — only entries without one are created and linked, so re-saving an untouched
 * scenario doesn't duplicate it. Entries the user removed come through as
 * [AutomationDraft.removedTriggerIds]/[AutomationDraft.removedConditionIds]/
 * [AutomationDraft.removedActionIds] and are deleted first, mirroring the web client's
 * delete-then-create approach (§11.6 documents no `update` verb for triggers or conditions).
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

        draft.removedTriggerIds.forEach { id ->
            val deleted = repository.deleteTrigger(id)
            if (deleted is DonaResult.Error) return deleted
        }
        draft.removedConditionIds.forEach { id ->
            val deleted = repository.deleteCondition(id)
            if (deleted is DonaResult.Error) return deleted
        }
        draft.removedActionIds.forEach { id ->
            val deleted = repository.deleteAction(id)
            if (deleted is DonaResult.Error) return deleted
        }

        draft.startTriggers.forEach { trigger ->
            if (trigger.existingId != null) return@forEach
            val triggerId =
                when (val created = repository.createTrigger(trigger)) {
                    is DonaResult.Success -> created.data
                    is DonaResult.Error -> return created
                }
            val linked = repository.linkStartTrigger(targetId, triggerId)
            if (linked is DonaResult.Error) return linked
        }

        draft.stopTriggers.forEach { trigger ->
            if (trigger.existingId != null) return@forEach
            val triggerId =
                when (val created = repository.createTrigger(trigger)) {
                    is DonaResult.Success -> created.data
                    is DonaResult.Error -> return created
                }
            val linked = repository.linkStopTrigger(targetId, triggerId)
            if (linked is DonaResult.Error) return linked
        }

        draft.conditions.forEach { condition ->
            if (condition.existingId != null) return@forEach
            val conditionId =
                when (val created = repository.createCondition(condition)) {
                    is DonaResult.Success -> created.data
                    is DonaResult.Error -> return created
                }
            val linked = repository.linkCondition(targetId, conditionId)
            if (linked is DonaResult.Error) return linked
        }

        // Walks the chain in list order so a newly added action is spliced onto whatever already
        // precedes it — an existing action just becomes the new "previous" link without being
        // touched, so appending to an existing scenario rewrites exactly one `nextAction`.
        var previousActionId: Int? = null
        draft.actions.forEach { action ->
            if (action.existingId != null) {
                previousActionId = action.existingId
                return@forEach
            }
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
