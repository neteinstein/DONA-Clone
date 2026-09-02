package com.neteinstein.donaclone.core.network.api

import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.network.dto.AmbienceDto
import com.neteinstein.donaclone.core.network.dto.DivisionDto
import com.neteinstein.donaclone.core.network.dto.SessionDto
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

    /** Raw unsolicited push messages; a higher layer interprets them (envelope unconfirmed, §8). */
    fun observeUpdates(): Flow<JsonObject>
}

class DomotalkApiImpl(
    private val socket: DomotalkSocket,
    private val json: Json,
) : DomotalkApi {
    override suspend fun readUsers(): List<UserDto> = decodeList(socket.request("read", "user"), UserDto.serializer())

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
}
