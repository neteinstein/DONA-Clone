package com.neteinstein.donaclone.core.data.ambience

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.mapper.donaResultCatching
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.domain.repository.ShutterInversionRepository
import com.neteinstein.donaclone.core.model.ActionDraft
import com.neteinstein.donaclone.core.model.Ambience
import com.neteinstein.donaclone.core.model.AutomationActionType
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

    /** Actions must be sent back to `update action` in full (mirrors `update ambience`'s
     * full-object pattern) — cache each created action's DTO so [setActionNext] can rewrite just
     * `nextAction` without clobbering the rest of the object with defaults. */
    private val actionCache = ConcurrentHashMap<Int, ActionDto>()

    override suspend fun getAmbiences(): DonaResult<List<Ambience>> =
        donaResultCatching {
            api.readAmbiences().map { snapshot ->
                rawAmbienceCache[snapshot.ambience.id] = snapshot.raw
                snapshot.ambience.toDomain()
            }
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

    /** An automation action targeting a physically inverted shutter has to be mirrored on the way
     * out for the same reason a live command does — the hub's `0 = close / 1 = open` codes and its
     * percentage both mean the opposite thing on that module. Actions are write-only here (the
     * editor can add entries but never reads existing ones back), so this is the only direction
     * that needs correcting. */
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

    private companion object {
        /** `Shutter.Action` wire codes (protocol notes §11.2). */
        const val SHUTTER_CLOSE = 0
        const val SHUTTER_OPEN = 1
        const val SHUTTER_PERCENTAGE = 2
    }
}
