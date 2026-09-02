package com.neteinstein.donaclone.core.network.mapper

import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.PulseKind
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.intOrNull

/**
 * Maps the raw, heterogeneous `deviceOut`/`deviceIn` JSON objects the hub returns into typed
 * [Device] subclasses.
 *
 * The spec for a `deviceOut` read says each element is "tagged with numeric type" so the
 * client can pick the right subclass (`q4.e`/`DpuDeviceCode`), but the exact JSON key that
 * numeric discriminator lives under was never observed as a literal in the decompiled app —
 * see protocol notes §3.2. Rather than guess a key name, this mapper infers the concrete kind
 * structurally from which fields are actually present, which is robust to that ambiguity:
 * a `shutter` has `percentage` + `processDuration`, a `dimmer` has `percentage` alone, a
 * `pulse` has `status` + `duration`, and a plain `binaryOut` has `status` alone. If the hub
 * *does* send an explicit numeric type code (matching [com.neteinstein.donaclone.core.model.DpuDeviceCode]'s
 * wire values) under a `type` key, that is honoured first.
 */
object DeviceJsonMapper {
    fun parseDeviceOut(raw: JsonObject): Device {
        val common = commonFieldsOf(raw)
        val hasPercentage = raw.containsKey("percentage")
        val hasProcessDuration = raw.containsKey("processDuration")
        val hasStatus = raw.containsKey("status")
        val hasDuration = raw.containsKey("duration")
        val explicitCode = raw["type"]?.let { (it as? JsonPrimitive)?.intOrNull }

        return when {
            explicitCode == 70 || (hasPercentage && hasProcessDuration) ->
                Device.Shutter(
                    id = common.id,
                    name = common.name,
                    description = common.description,
                    enabled = common.enabled,
                    online = common.online,
                    roomId = common.roomId,
                    freeTypeLabel = common.freeTypeLabel,
                    percentage = raw["percentage"]?.let { (it as JsonPrimitive).int } ?: 0,
                )

            explicitCode == 71 || (hasPercentage && !hasStatus) ->
                Device.Dimmer(
                    id = common.id,
                    name = common.name,
                    description = common.description,
                    enabled = common.enabled,
                    online = common.online,
                    roomId = common.roomId,
                    freeTypeLabel = common.freeTypeLabel,
                    percentage = raw["percentage"]?.let { (it as JsonPrimitive).int } ?: 0,
                )

            explicitCode == 61 || (hasStatus && hasDuration) ->
                Device.Pulse(
                    id = common.id,
                    name = common.name,
                    description = common.description,
                    enabled = common.enabled,
                    online = common.online,
                    roomId = common.roomId,
                    freeTypeLabel = common.freeTypeLabel,
                    kind =
                        raw["subtype"]
                            ?.let { (it as? JsonPrimitive)?.intOrNull }
                            ?.let(PulseKind::fromWireValue) ?: PulseKind.UNKNOWN,
                    durationSeconds = raw["duration"]?.let { (it as? JsonPrimitive)?.intOrNull },
                )

            explicitCode == 60 || hasStatus ->
                Device.BinaryOutput(
                    id = common.id,
                    name = common.name,
                    description = common.description,
                    enabled = common.enabled,
                    online = common.online,
                    roomId = common.roomId,
                    freeTypeLabel = common.freeTypeLabel,
                    isOn = statusIsOn(raw),
                )

            else ->
                Device.UnknownDevice(
                    id = common.id,
                    name = common.name,
                    description = common.description,
                    enabled = common.enabled,
                    online = common.online,
                    roomId = common.roomId,
                    freeTypeLabel = common.freeTypeLabel,
                    rawTypeCode = explicitCode,
                )
        }
    }

    fun parseDeviceIn(raw: JsonObject): Device {
        val common = commonFieldsOf(raw)
        return when {
            raw.containsKey("status") ->
                Device.BinaryInput(
                    id = common.id,
                    name = common.name,
                    description = common.description,
                    enabled = common.enabled,
                    online = common.online,
                    roomId = common.roomId,
                    freeTypeLabel = common.freeTypeLabel,
                    isActive = statusIsOn(raw),
                )

            raw.containsKey("value") ->
                Device.AnalogInput(
                    id = common.id,
                    name = common.name,
                    description = common.description,
                    enabled = common.enabled,
                    online = common.online,
                    roomId = common.roomId,
                    freeTypeLabel = common.freeTypeLabel,
                    value = (raw.getValue("value") as JsonPrimitive).double,
                )

            else ->
                Device.UnknownDevice(
                    id = common.id,
                    name = common.name,
                    description = common.description,
                    enabled = common.enabled,
                    online = common.online,
                    roomId = common.roomId,
                    freeTypeLabel = common.freeTypeLabel,
                    rawTypeCode = null,
                )
        }
    }

    /**
     * Builds the `options` object for a `verb: action` request: the full device JSON as read,
     * with [fieldUpdates] merged in, plus the action code and optional percentage — per the
     * confirmed shape in protocol notes §4.
     */
    fun buildActionOptions(
        rawDevice: JsonObject,
        action: Int,
        percentage: Int? = null,
        fieldUpdates: Map<String, JsonElement> = emptyMap(),
    ): JsonObject =
        buildJsonObject {
            put(
                "object",
                buildJsonObject {
                    rawDevice.forEach { (key, value) -> put(key, fieldUpdates[key] ?: value) }
                    fieldUpdates.forEach { (key, value) -> if (!rawDevice.containsKey(key)) put(key, value) }
                },
            )
            put("action", JsonPrimitive(action))
            if (percentage != null) put("percentage", JsonPrimitive(percentage))
        }

    private fun statusIsOn(raw: JsonObject): Boolean {
        val status = raw["status"] as? JsonPrimitive ?: return false
        return status.intOrNull?.let { it > 0 } ?: status.booleanOrNull ?: false
    }

    private data class CommonFields(
        val id: Int,
        val name: String,
        val description: String?,
        val enabled: Boolean,
        val online: Boolean,
        val roomId: Int?,
        val freeTypeLabel: String?,
    )

    private fun commonFieldsOf(raw: JsonObject): CommonFields =
        CommonFields(
            id = (raw.getValue("id") as JsonPrimitive).int,
            name = (raw["name"] as? JsonPrimitive)?.content.orEmpty(),
            description = (raw["description"] as? JsonPrimitive)?.content,
            enabled = (raw["enabled"] as? JsonPrimitive)?.boolean ?: true,
            online = (raw["online"] as? JsonPrimitive)?.boolean ?: true,
            roomId = (raw["room"] as? JsonPrimitive)?.intOrNull,
            freeTypeLabel = (raw["type"] as? JsonPrimitive)?.let { if (!it.isString) null else it.content },
        )
}
