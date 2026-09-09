package com.neteinstein.donaclone.core.data.ambience

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.mapper.donaResultCatching
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.domain.repository.ShutterInversionRepository
import com.neteinstein.donaclone.core.model.ActionDraft
import com.neteinstein.donaclone.core.model.Ambience
import com.neteinstein.donaclone.core.model.AmbienceConditionType
import com.neteinstein.donaclone.core.model.AutomationActionType
import com.neteinstein.donaclone.core.model.AutomationDetail
import com.neteinstein.donaclone.core.model.ConditionDraft
import com.neteinstein.donaclone.core.model.TriggerDraft
import com.neteinstein.donaclone.core.model.invertShutterPercentage
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.dto.ActionDto
import com.neteinstein.donaclone.core.network.dto.AmbienceDto
import com.neteinstein.donaclone.core.network.dto.ConditionDto
import com.neteinstein.donaclone.core.network.dto.TriggerDto
import com.neteinstein.donaclone.core.network.socket.DomotalkException
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.util.concurrent.ConcurrentHashMap

class AmbienceRepositoryImpl(
    private val api: DomotalkApi,
    private val shutterInversion: ShutterInversionRepository,
) : AmbienceRepository {
    private val rawAmbienceCache = ConcurrentHashMap<Int, JsonObject>()

    /** The parsed side of [rawAmbienceCache] — [getAutomationDetail] needs the decoded
     * `startTriggers`/`stopTriggers`/`conditions`/`firstAction` ids, which the raw object carries
     * but only as untyped JSON. */
    private val ambienceCache = ConcurrentHashMap<Int, AmbienceDto>()

    /** Actions must be sent back to `update action` in full (mirrors `update ambience`'s
     * full-object pattern) — cache each created action's DTO so [setActionNext] can rewrite just
     * `nextAction` without clobbering the rest of the object with defaults. */
    private val actionCache = ConcurrentHashMap<Int, ActionDto>()

    override suspend fun getAmbiences(): DonaResult<List<Ambience>> =
        donaResultCatching {
            api.readAmbiences().map { snapshot ->
                rawAmbienceCache[snapshot.ambience.id] = snapshot.raw
                ambienceCache[snapshot.ambience.id] = snapshot.ambience
                snapshot.ambience.toDomain()
            }
        }

    override suspend fun getAutomationDetail(ambienceId: Int): DonaResult<AutomationDetail> =
        donaResultCatching {
            val ambience =
                ambienceCache[ambienceId]
                    ?: api.readAmbiences()
                        .onEach {
                            rawAmbienceCache[it.ambience.id] = it.raw
                            ambienceCache[it.ambience.id] = it.ambience
                        }
                        .firstOrNull { it.ambience.id == ambienceId }
                        ?.ambience
                    ?: throw DomotalkException.MalformedResponse("Ambience $ambienceId not found on the hub")
            val inverted = shutterInversion.observeInvertedShutterIds().first()
            AutomationDetail(
                startTriggers = ambience.startTriggers.mapNotNull { api.readTrigger(it)?.toDraft() },
                stopTriggers = ambience.stopTriggers.mapNotNull { api.readTrigger(it)?.toDraft() },
                conditions = ambience.conditions.mapNotNull { api.readCondition(it)?.toDraft() },
                actions = readActionChain(ambience.firstAction).map { it.toDraft().correctedForInversion(inverted) },
            )
        }

    /** Walks `ambience.firstAction -> action.nextAction -> ...` (§11.6). [seen] guards against a
     * hub whose chain loops back on itself, which would otherwise hang the editor forever. */
    private suspend fun readActionChain(firstAction: Int?): List<ActionDto> {
        val chain = mutableListOf<ActionDto>()
        val seen = mutableSetOf<Int>()
        var next = firstAction
        while (next != null && seen.add(next)) {
            val action = api.readAction(next) ?: break
            actionCache[next] = action
            chain += action
            next = action.nextAction
        }
        return chain
    }

    override suspend fun triggerAmbience(
        id: Int,
        run: Boolean,
    ): DonaResult<Unit> {
        val raw = cachedRaw(id) ?: return unreadAmbienceError(id)
        return donaResultCatching { api.sendAmbienceAction(raw, run) }
    }

    override suspend fun createAmbience(
        name: String,
        enabled: Boolean,
    ): DonaResult<Ambience> =
        donaResultCatching {
            val snapshot = api.createAmbience(name, enabled)
            rawAmbienceCache[snapshot.ambience.id] = snapshot.raw
            ambienceCache[snapshot.ambience.id] = snapshot.ambience
            snapshot.ambience.toDomain()
        }

    override suspend fun updateAmbienceFields(
        id: Int,
        name: String,
        enabled: Boolean,
    ): DonaResult<Unit> {
        val raw = cachedRaw(id) ?: return unreadAmbienceError(id)
        val updated =
            buildJsonObject {
                raw.forEach { (key, value) -> put(key, value) }
                put("name", JsonPrimitive(name))
                put("enabled", JsonPrimitive(enabled))
            }
        return donaResultCatching {
            api.updateAmbience(updated)
            rawAmbienceCache[id] = updated
        }
    }

    override suspend fun setAmbienceFirstAction(
        id: Int,
        actionId: Int,
    ): DonaResult<Unit> {
        val raw = cachedRaw(id) ?: return unreadAmbienceError(id)
        val updated =
            buildJsonObject {
                raw.forEach { (key, value) -> put(key, value) }
                put("firstAction", JsonPrimitive(actionId))
            }
        return donaResultCatching {
            api.updateAmbience(updated)
            rawAmbienceCache[id] = updated
        }
    }

    override suspend fun deleteAmbience(id: Int): DonaResult<Unit> =
        donaResultCatching {
            api.deleteAmbience(id)
            rawAmbienceCache.remove(id)
            ambienceCache.remove(id)
            Unit
        }

    override suspend fun createTrigger(trigger: TriggerDraft): DonaResult<Int> =
        donaResultCatching {
            val created =
                api.createTrigger(
                    TriggerDto(
                        name = trigger.name,
                        type = trigger.type,
                        time = trigger.time,
                        triggerer = trigger.triggerer,
                        triggererType = trigger.triggererType,
                        triggererSubtype = trigger.triggererSubtype,
                        event = trigger.event,
                        sensor = trigger.sensor,
                        sensorType = trigger.sensorType,
                        sensorSubtype = trigger.sensorSubtype,
                        lowerBound = trigger.lowerBound,
                        upperBound = trigger.upperBound,
                        deviceRoom = trigger.deviceRoom,
                    ),
                )
            created.id ?: throw DomotalkException.MalformedResponse("create trigger response had no id")
        }

    override suspend fun deleteTrigger(id: Int): DonaResult<Unit> = donaResultCatching { api.deleteTrigger(id) }

    override suspend fun linkStartTrigger(
        ambienceId: Int,
        triggerId: Int,
    ): DonaResult<Unit> = donaResultCatching { api.linkAmbienceStartTrigger(ambienceId, triggerId) }

    override suspend fun linkStopTrigger(
        ambienceId: Int,
        triggerId: Int,
    ): DonaResult<Unit> = donaResultCatching { api.linkAmbienceStopTrigger(ambienceId, triggerId) }

    override suspend fun createCondition(condition: ConditionDraft): DonaResult<Int> =
        donaResultCatching {
            val created =
                api.createCondition(
                    ConditionDto(
                        name = condition.name,
                        type = condition.type,
                        after = condition.after,
                        before = condition.before,
                        daysOfTheWeek = condition.daysOfTheWeek,
                        conditioner = condition.conditioner,
                        deviceRoom = condition.deviceRoom,
                        status = condition.status,
                        greaterThanValue = condition.greaterThanValue,
                        lesserThanValue = condition.lesserThanValue,
                    ),
                )
            created.id ?: throw DomotalkException.MalformedResponse("create condition response had no id")
        }

    override suspend fun deleteCondition(id: Int): DonaResult<Unit> = donaResultCatching { api.deleteCondition(id) }

    override suspend fun linkCondition(
        ambienceId: Int,
        conditionId: Int,
    ): DonaResult<Unit> = donaResultCatching { api.linkAmbienceCondition(ambienceId, conditionId) }

    override suspend fun createAction(action: ActionDraft): DonaResult<Int> =
        donaResultCatching {
            val corrected = action.correctedForInversion(shutterInversion.observeInvertedShutterIds().first())
            val created =
                api.createAction(
                    ActionDto(
                        type = corrected.type,
                        device = corrected.device,
                        deviceName = corrected.deviceName,
                        deviceType = corrected.deviceType,
                        deviceSubtype = corrected.deviceSubtype,
                        deviceRoom = corrected.deviceRoom,
                        action = corrected.action,
                        percentage = corrected.percentage,
                        duration = corrected.duration,
                        withLast = corrected.withLast,
                        delayFromLast = corrected.delayFromLast,
                    ),
                )
            val id = created.id ?: throw DomotalkException.MalformedResponse("create action response had no id")
            actionCache[id] = created
            id
        }

    override suspend fun setActionNext(
        actionId: Int,
        nextActionId: Int,
    ): DonaResult<Unit> {
        val current =
            actionCache[actionId]
                ?: return DonaResult.Error(DonaFailure.Unknown("Action $actionId wasn't created by this session"))
        val updated = current.copy(nextAction = nextActionId)
        return donaResultCatching {
            api.updateAction(updated)
            actionCache[actionId] = updated
        }
    }

    override suspend fun deleteAction(id: Int): DonaResult<Unit> =
        donaResultCatching {
            api.deleteAction(id)
            actionCache.remove(id)
            Unit
        }

    /** An automation action targeting a physically inverted shutter has to be mirrored on the way
     * out for the same reason a live command does — the hub's `0 = close / 1 = open` codes and its
     * percentage both mean the opposite thing on that module. The mapping is its own inverse, so
     * [getAutomationDetail] runs the very same correction on the way back in to undo it. */
    private fun ActionDraft.correctedForInversion(inverted: Set<Int>): ActionDraft {
        if (type != AutomationActionType.SHUTTER || device !in inverted) return this
        return when (action) {
            SHUTTER_CLOSE -> copy(action = SHUTTER_OPEN)
            SHUTTER_OPEN -> copy(action = SHUTTER_CLOSE)
            SHUTTER_PERCENTAGE -> copy(percentage = percentage?.let(::invertShutterPercentage))
            else -> this
        }
    }

    private fun cachedRaw(id: Int): JsonObject? = rawAmbienceCache[id]

    private fun unreadAmbienceError(id: Int): DonaResult.Error =
        DonaResult.Error(DonaFailure.Unknown("Ambience $id hasn't been read yet"))

    private fun AmbienceDto.toDomain() = Ambience(id = id, name = name, isPlaying = isPlaying, enabled = enabled)

    private fun TriggerDto.toDraft() =
        TriggerDraft(
            existingId = id,
            type = type,
            name = name,
            time = time,
            triggerer = triggerer,
            triggererType = triggererType,
            triggererSubtype = triggererSubtype,
            event = event,
            sensor = sensor,
            sensorType = sensorType,
            sensorSubtype = sensorSubtype,
            lowerBound = lowerBound,
            upperBound = upperBound,
            deviceRoom = deviceRoom,
        )

    /**
     * A read-back condition's `type` is the *device kind*, not the create-time DEVICE/TIMED
     * selector (see [ConditionDto.type]), so normalize it here: anything carrying a wall-clock
     * window and no `conditioner` is timed, everything else is a device condition. That keeps the
     * rest of the app — and a re-save of an untouched entry — on the create-time enum.
     */
    private fun ConditionDto.toDraft() =
        ConditionDraft(
            existingId = id,
            type = if (conditioner == null && after != null) AmbienceConditionType.TIMED else AmbienceConditionType.DEVICE,
            name = name,
            after = after,
            before = before,
            daysOfTheWeek = daysOfTheWeek,
            conditioner = conditioner,
            deviceRoom = deviceRoom,
            status = status,
            greaterThanValue = greaterThanValue,
            lesserThanValue = lesserThanValue,
        )

    private fun ActionDto.toDraft() =
        ActionDraft(
            existingId = id,
            type = type,
            device = device,
            deviceName = deviceName,
            deviceType = deviceType ?: 0,
            deviceSubtype = deviceSubtype,
            deviceRoom = deviceRoom,
            action = action,
            percentage = percentage,
            duration = duration,
            withLast = withLast,
            delayFromLast = delayFromLast,
        )

    private companion object {
        /** `Shutter.Action` wire codes (protocol notes §11.2). */
        const val SHUTTER_CLOSE = 0
        const val SHUTTER_OPEN = 1
        const val SHUTTER_PERCENTAGE = 2
    }
}
