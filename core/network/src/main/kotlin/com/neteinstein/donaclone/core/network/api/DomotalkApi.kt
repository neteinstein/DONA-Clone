package com.neteinstein.donaclone.core.network.api

import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.network.dto.ActionDto
import com.neteinstein.donaclone.core.network.dto.AmbienceDto
import com.neteinstein.donaclone.core.network.dto.ConditionDto
import com.neteinstein.donaclone.core.network.dto.DivisionDto
import com.neteinstein.donaclone.core.network.dto.MasterLogEntryDto
import com.neteinstein.donaclone.core.network.dto.SessionDto
import com.neteinstein.donaclone.core.network.dto.TriggerDto
import com.neteinstein.donaclone.core.network.dto.UserDto
import com.neteinstein.donaclone.core.network.mapper.DeviceJsonMapper
import com.neteinstein.donaclone.core.network.socket.DomotalkException
import com.neteinstein.donaclone.core.network.socket.DomotalkSocket
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * A device paired with the exact raw JSON it was read as — needed to send actions later, since
 * the hub expects the full device object back with the changed field(s) updated (§4).
 */
data class DeviceSnapshot(
    val device: Device,
    val raw: JsonObject,
)

/** Same idea as [DeviceSnapshot]: an ambience carries opaque trigger/condition fields (§3.2)
 * that this client doesn't model, so we must round-trip the raw object rather than
 * reconstructing one from [AmbienceDto] alone when sending an action/update. */
data class AmbienceSnapshot(
    val ambience: AmbienceDto,
    val raw: JsonObject,
)

interface DomotalkApi {
    suspend fun readUsers(): List<UserDto>

    /** `create user`, `options.object = {name, password, role, enabled, remoteAccessible}` (§11.4)
     * — no `id` in the request; the hub assigns and returns one. [md5Password] is assumed hashed
     * the same way as login (§2.3) — unconfirmed for this specific call. */
    suspend fun createUser(
        name: String,
        md5Password: String,
        role: Int,
        enabled: Boolean,
        remoteAccessible: Boolean,
    ): UserDto

    /** `update user`, `options.object = <full dto>`, optionally `options.oldPassword` (§11.4) when
     * changing the account's password — unconfirmed whether the hub expects it MD5-hashed like
     * login, but we mirror that convention for consistency. */
    suspend fun updateUser(
        user: UserDto,
        oldMd5Password: String? = null,
    )

    /** `delete user`, filtered by `id` (§11.4). */
    suspend fun deleteUser(id: Int)

    suspend fun createSession(
        userId: Int,
        md5Password: String,
    ): String

    suspend fun resumeSession(token: String)

    suspend fun logout()

    suspend fun readRooms(): List<DivisionDto>

    suspend fun readDeviceOut(): List<DeviceSnapshot>

    suspend fun readDeviceIn(): List<DeviceSnapshot>

    suspend fun readAmbiences(): List<AmbienceSnapshot>

    /** Event/audit log (§2.4), narrowed by an `objectId`/`date`-range [filters] array. */
    suspend fun readMasterLog(filters: JsonArray? = null): List<MasterLogEntryDto>

    suspend fun sendBinaryOutputAction(
        raw: JsonObject,
        turnOn: Boolean,
    )

    suspend fun sendPulseAction(raw: JsonObject)

    suspend fun sendShutterOpenClose(
        raw: JsonObject,
        open: Boolean,
    )

    suspend fun sendShutterPercentage(
        raw: JsonObject,
        percentage: Int,
    )

    suspend fun sendDimmerPercentage(
        raw: JsonObject,
        percentage: Int,
    )

    suspend fun sendAmbienceAction(
        raw: JsonObject,
        run: Boolean,
    )

    suspend fun updateAmbience(raw: JsonObject)

    /** `create ambience`, `options.object = {name, enabled}` (no `id` — the hub assigns one).
     * §11.6's CRUD/linking pattern. */
    suspend fun createAmbience(
        name: String,
        enabled: Boolean,
    ): AmbienceSnapshot

    /** `delete ambience`, filtered by `id` — same "filtered by id" pattern §11.6 confirms for
     * `trigger`/`condition`/`action`. */
    suspend fun deleteAmbience(id: Int)

    /** `create trigger`, `options.object = <trigger, no id>` — the hub assigns and returns the id. */
    suspend fun createTrigger(trigger: TriggerDto): TriggerDto

    /** `delete trigger`, filtered by `id`. There's no `update trigger` — §11.6 confirms the real
     * client always deletes and recreates instead. */
    suspend fun deleteTrigger(id: Int)

    /** `create condition`, `options.object = <condition, no id>`. */
    suspend fun createCondition(condition: ConditionDto): ConditionDto

    /** `delete condition`, filtered by `id`. Same no-update caveat as [deleteTrigger]. */
    suspend fun deleteCondition(id: Int)

    /** `create action`, `options.object = <action, no id, no nextAction>`. */
    suspend fun createAction(action: ActionDto): ActionDto

    /** `update action` — the one sub-object that genuinely has an update verb, used both to edit
     * an action's own fields and to splice a new action onto the chain by rewriting the
     * *previous* action's `nextAction`. */
    suspend fun updateAction(action: ActionDto)

    /** `delete action`, filtered by `id`. Deleting a non-last action in the chain has no observed
     * "reconnect the list" support — callers must truncate everything after it themselves. */
    suspend fun deleteAction(id: Int)

    /** `create ambienceStartTrigger`, `options.object = {ambience, startTrigger}` — links a
     * newly-created [Trigger][TriggerDto] onto `ambience.startTriggers`. */
    suspend fun linkAmbienceStartTrigger(
        ambienceId: Int,
        triggerId: Int,
    )

    /** Same as [linkAmbienceStartTrigger] for `ambience.stopTriggers`. */
    suspend fun linkAmbienceStopTrigger(
        ambienceId: Int,
        triggerId: Int,
    )

    /** `create ambienceCondition`, `options.object = {ambience, condition}`. */
    suspend fun linkAmbienceCondition(
        ambienceId: Int,
        conditionId: Int,
    )

    /** Raw unsolicited push messages; a higher layer interprets them (envelope unconfirmed, §8). */
    fun observeUpdates(): Flow<JsonObject>
}

class DomotalkApiImpl(
    private val socket: DomotalkSocket,
    private val json: Json,
) : DomotalkApi {
    override suspend fun readUsers(): List<UserDto> = decodeList(socket.request("read", "user"), UserDto.serializer())

    override suspend fun createUser(
        name: String,
        md5Password: String,
        role: Int,
        enabled: Boolean,
        remoteAccessible: Boolean,
    ): UserDto {
        val options =
            buildJsonObject {
                put(
                    "object",
                    buildJsonObject {
                        put("name", JsonPrimitive(name))
                        put("password", JsonPrimitive(md5Password))
                        put("role", JsonPrimitive(role))
                        put("enabled", JsonPrimitive(enabled))
                        put("remoteAccessible", JsonPrimitive(remoteAccessible))
                    },
                )
            }
        val raw = asJsonObject(socket.request("create", "user", options))
        return json.decodeFromJsonElement(UserDto.serializer(), raw)
    }

    override suspend fun updateUser(
        user: UserDto,
        oldMd5Password: String?,
    ) {
        val options =
            buildJsonObject {
                put("object", json.encodeToJsonElement(UserDto.serializer(), user))
                oldMd5Password?.let { put("oldPassword", JsonPrimitive(it)) }
            }
        socket.request("update", "user", options)
    }

    override suspend fun deleteUser(id: Int) {
        socket.request("delete", "user", filters = idFilter(id))
    }

    override suspend fun createSession(
        userId: Int,
        md5Password: String,
    ): String {
        val options =
            buildJsonObject {
                put("userId", JsonPrimitive(userId))
                put("password", JsonPrimitive(md5Password))
                put("forever", JsonPrimitive(true))
            }
        val element = socket.request("create", "session", options)
        val session = json.decodeFromJsonElement(SessionDto.serializer(), element)
        socket.token = session.token
        return session.token
    }

    override suspend fun resumeSession(token: String) {
        socket.token = token
        socket.request("action", "session", buildJsonObject { put("token", JsonPrimitive(token)) })
    }

    override suspend fun logout() {
        runCatching { socket.request("delete", "session") }
        socket.token = null
    }

    override suspend fun readRooms(): List<DivisionDto> =
        decodeList(socket.request("read", "room"), DivisionDto.serializer())

    override suspend fun readDeviceOut(): List<DeviceSnapshot> =
        asJsonObjects(socket.request("read", "deviceOut")).map {
            DeviceSnapshot(DeviceJsonMapper.parseDeviceOut(it), it)
        }

    override suspend fun readDeviceIn(): List<DeviceSnapshot> =
        asJsonObjects(socket.request("read", "deviceIn")).map {
            DeviceSnapshot(DeviceJsonMapper.parseDeviceIn(it), it)
        }

    override suspend fun readAmbiences(): List<AmbienceSnapshot> =
        asJsonObjects(socket.request("read", "ambience")).map { raw ->
            AmbienceSnapshot(json.decodeFromJsonElement(AmbienceDto.serializer(), raw), raw)
        }

    override suspend fun readMasterLog(filters: JsonArray?): List<MasterLogEntryDto> =
        decodeList(socket.request("read", "masterLog", filters = filters), MasterLogEntryDto.serializer())

    override suspend fun sendBinaryOutputAction(
        raw: JsonObject,
        turnOn: Boolean,
    ) {
        val action = if (turnOn) 1 else 0
        socket.request("action", "binaryOut", DeviceJsonMapper.buildActionOptions(raw, action))
    }

    override suspend fun sendPulseAction(raw: JsonObject) {
        socket.request("action", "pulse", DeviceJsonMapper.buildActionOptions(raw, action = 0))
    }

    override suspend fun sendShutterOpenClose(
        raw: JsonObject,
        open: Boolean,
    ) {
        val action = if (open) 1 else 0
        socket.request("action", "shutter", DeviceJsonMapper.buildActionOptions(raw, action))
    }

    override suspend fun sendShutterPercentage(
        raw: JsonObject,
        percentage: Int,
    ) {
        socket.request(
            "action",
            "shutter",
            DeviceJsonMapper.buildActionOptions(raw, action = 2, percentage = percentage),
        )
    }

    override suspend fun sendDimmerPercentage(
        raw: JsonObject,
        percentage: Int,
    ) {
        socket.request(
            "action",
            "dimmer",
            DeviceJsonMapper.buildActionOptions(raw, action = 2, percentage = percentage),
        )
    }

    override suspend fun sendAmbienceAction(
        raw: JsonObject,
        run: Boolean,
    ) {
        val action = if (run) 1 else 0
        socket.request("action", "ambience", DeviceJsonMapper.buildActionOptions(raw, action))
    }

    override suspend fun updateAmbience(raw: JsonObject) {
        socket.request("update", "ambience", buildJsonObject { put("object", raw) })
    }

    override suspend fun createAmbience(
        name: String,
        enabled: Boolean,
    ): AmbienceSnapshot {
        val options =
            buildJsonObject {
                put(
                    "object",
                    buildJsonObject {
                        put("name", JsonPrimitive(name))
                        put("enabled", JsonPrimitive(enabled))
                    },
                )
            }
        val raw = asJsonObject(socket.request("create", "ambience", options))
        return AmbienceSnapshot(json.decodeFromJsonElement(AmbienceDto.serializer(), raw), raw)
    }

    override suspend fun deleteAmbience(id: Int) {
        socket.request("delete", "ambience", filters = idFilter(id))
    }

    override suspend fun createTrigger(trigger: TriggerDto): TriggerDto =
        create("trigger", TriggerDto.serializer(), trigger)

    override suspend fun deleteTrigger(id: Int) {
        socket.request("delete", "trigger", filters = idFilter(id))
    }

    override suspend fun createCondition(condition: ConditionDto): ConditionDto =
        create("condition", ConditionDto.serializer(), condition)

    override suspend fun deleteCondition(id: Int) {
        socket.request("delete", "condition", filters = idFilter(id))
    }

    override suspend fun createAction(action: ActionDto): ActionDto = create("action", ActionDto.serializer(), action)

    override suspend fun updateAction(action: ActionDto) {
        socket.request("update", "action", buildJsonObject { put("object", json.encodeToJsonElement(ActionDto.serializer(), action)) })
    }

    override suspend fun deleteAction(id: Int) {
        socket.request("delete", "action", filters = idFilter(id))
    }

    override suspend fun linkAmbienceStartTrigger(
        ambienceId: Int,
        triggerId: Int,
    ) {
        createLink("ambienceStartTrigger", "ambience" to ambienceId, "startTrigger" to triggerId)
    }

    override suspend fun linkAmbienceStopTrigger(
        ambienceId: Int,
        triggerId: Int,
    ) {
        createLink("ambienceStopTrigger", "ambience" to ambienceId, "stopTrigger" to triggerId)
    }

    override suspend fun linkAmbienceCondition(
        ambienceId: Int,
        conditionId: Int,
    ) {
        createLink("ambienceCondition", "ambience" to ambienceId, "condition" to conditionId)
    }

    override fun observeUpdates(): Flow<JsonObject> = socket.updates

    private fun <T> decodeList(
        element: JsonElement,
        serializer: KSerializer<T>,
    ): List<T> {
        val array = element as? JsonArray ?: throw DomotalkException.MalformedResponse("Expected a JSON array")
        return json.decodeFromJsonElement(ListSerializer(serializer), array)
    }

    private fun asJsonObjects(element: JsonElement): List<JsonObject> {
        val array = element as? JsonArray ?: throw DomotalkException.MalformedResponse("Expected a JSON array")
        return array.map { it as? JsonObject ?: throw DomotalkException.MalformedResponse("Expected JSON objects") }
    }

    private fun asJsonObject(element: JsonElement): JsonObject =
        element as? JsonObject ?: throw DomotalkException.MalformedResponse("Expected a JSON object")

    /** `create <subject>`, `options.object = <dto, no id>` (explicitNulls=false omits it) —
     * decodes the response back into [T], which the hub populates with the assigned id. */
    private suspend fun <T> create(
        subject: String,
        serializer: KSerializer<T>,
        dto: T,
    ): T {
        val options = buildJsonObject { put("object", json.encodeToJsonElement(serializer, dto)) }
        val element = socket.request("create", subject, options)
        return json.decodeFromJsonElement(serializer, element)
    }

    /** `create <subject>`, `options.object = {<field>: <id>, ...}` — the ambience-sub-object
     * join-record pattern (§11.6): `ambienceStartTrigger`/`ambienceStopTrigger`/`ambienceCondition`. */
    private suspend fun createLink(
        subject: String,
        vararg fields: Pair<String, Int>,
    ) {
        val options =
            buildJsonObject {
                put(
                    "object",
                    buildJsonObject {
                        fields.forEach { (key, value) -> put(key, JsonPrimitive(value)) }
                    },
                )
            }
        socket.request("create", subject, options)
    }

    /** `filters: [{"field":"id","operation":"equal","value":<id>}]` — the "delete filtered by id"
     * pattern used throughout §11.4/§11.6. */
    private fun idFilter(id: Int): JsonArray =
        buildJsonArray {
            add(
                buildJsonObject {
                    put("field", JsonPrimitive("id"))
                    put("operation", JsonPrimitive("equal"))
                    put("value", JsonPrimitive(id))
                },
            )
        }
}
