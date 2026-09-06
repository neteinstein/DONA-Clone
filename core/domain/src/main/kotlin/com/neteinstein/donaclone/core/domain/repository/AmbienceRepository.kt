package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.ActionDraft
import com.neteinstein.donaclone.core.model.Ambience
import com.neteinstein.donaclone.core.model.ConditionDraft
import com.neteinstein.donaclone.core.model.TriggerDraft

interface AmbienceRepository {
    suspend fun getAmbiences(): DonaResult<List<Ambience>>

    suspend fun triggerAmbience(
        id: Int,
        run: Boolean,
    ): DonaResult<Unit>

    /** `create ambience` (protocol notes §11.6). */
    suspend fun createAmbience(
        name: String,
        enabled: Boolean,
    ): DonaResult<Ambience>

    /** `update ambience` on the cached raw object for [id] — requires [getAmbiences] to have
     * populated the cache first (mirrors [triggerAmbience]'s own precondition). */
    suspend fun updateAmbienceFields(
        id: Int,
        name: String,
        enabled: Boolean,
    ): DonaResult<Unit>

    /** `update ambience`, setting `firstAction` — used once the action chain's first link exists. */
    suspend fun setAmbienceFirstAction(
        id: Int,
        actionId: Int,
    ): DonaResult<Unit>

    /** `delete ambience`, filtered by `id`. */
    suspend fun deleteAmbience(id: Int): DonaResult<Unit>

    /** `create trigger`, returning the hub-assigned id. */
    suspend fun createTrigger(trigger: TriggerDraft): DonaResult<Int>

    suspend fun linkStartTrigger(
        ambienceId: Int,
        triggerId: Int,
    ): DonaResult<Unit>

    suspend fun linkStopTrigger(
        ambienceId: Int,
        triggerId: Int,
    ): DonaResult<Unit>

    /** `create condition`, returning the hub-assigned id. */
    suspend fun createCondition(condition: ConditionDraft): DonaResult<Int>

    suspend fun linkCondition(
        ambienceId: Int,
        conditionId: Int,
    ): DonaResult<Unit>

    /** `create action`, returning the hub-assigned id. */
    suspend fun createAction(action: ActionDraft): DonaResult<Int>

    /** `update action`, rewriting [actionId]'s `nextAction` pointer to splice [nextActionId] onto
     * the chain right after it. */
    suspend fun setActionNext(
        actionId: Int,
        nextActionId: Int,
    ): DonaResult<Unit>
}
