package com.neteinstein.donaclone.core.data.device

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.mapper.donaResultCatching
import com.neteinstein.donaclone.core.domain.repository.DeviceRepository
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceUpdate
import com.neteinstein.donaclone.core.model.Division
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.content
import kotlinx.serialization.json.jsonPrimitive
import java.util.concurrent.ConcurrentHashMap

/**
 * Keeps the last-read raw JSON for every device by id, because sending an action back to the
 * hub requires the *full* device object with the changed field updated (§4) — the same
 * arrangement the original app uses (in-memory `ArrayList`s per screen, never persisted).
 */
class DeviceRepositoryImpl(
    private val api: DomotalkApi,
) : DeviceRepository {

    private val rawDeviceCache = ConcurrentHashMap<Int, JsonObject>()

    override suspend fun getRooms(): DonaResult<List<Division>> = donaResultCatching {
        api.readRooms().map { Division(id = it.id, name = it.name, floor = it.floor) }
    }

    override suspend fun getOutputDevices(): DonaResult<List<Device>> = donaResultCatching {
        api.readDeviceOut().map { snapshot ->
            rawDeviceCache[snapshot.device.id] = snapshot.raw
            snapshot.device
        }
    }

    override suspend fun getInputDevices(): DonaResult<List<Device>> = donaResultCatching {
        api.readDeviceIn().map { snapshot ->
            rawDeviceCache[snapshot.device.id] = snapshot.raw
            snapshot.device
        }
    }

    override suspend fun sendCommand(command: DeviceCommand): DonaResult<Unit> {
        val deviceId = command.deviceId()
        val raw = rawDeviceCache[deviceId]
            ?: return DonaResult.Error(
                DonaFailure.Unknown("Device $deviceId hasn't been read yet, cannot build a command for it"),
            )

        return donaResultCatching {
            when (command) {
                is DeviceCommand.SetBinaryOutput -> api.sendBinaryOutputAction(raw, command.turnOn)
                is DeviceCommand.FirePulse -> api.sendPulseAction(raw)
                is DeviceCommand.SetShutterOpen -> api.sendShutterOpenClose(raw, open = true)
                is DeviceCommand.SetShutterClosed -> api.sendShutterOpenClose(raw, open = false)
                is DeviceCommand.SetShutterPercentage -> api.sendShutterPercentage(raw, command.percentage)
                is DeviceCommand.SetDimmerPercentage -> api.sendDimmerPercentage(raw, command.percentage)
            }
        }
    }

    override fun observeDeviceUpdates(): Flow<DeviceUpdate> = api.observeUpdates().mapNotNull { parseUpdate(it) }

    /** Best-effort parse of the unconfirmed push envelope described in protocol notes §8. */
    private fun parseUpdate(message: JsonObject): DeviceUpdate? {
        val objectJson = message["request"]
            ?.let { it as? JsonObject }
            ?.get("options")
            ?.let { it as? JsonObject }
            ?.get("object")
            ?.let { it as? JsonObject }
            ?: return null

        val id = objectJson["id"]?.jsonPrimitive?.content?.toIntOrNull() ?: return null
        rawDeviceCache[id] = objectJson

        return when {
            objectJson.containsKey("percentage") ->
                DeviceUpdate.Percentage(id, objectJson["percentage"]?.jsonPrimitive?.content?.toDoubleOrNull()?.toInt() ?: 0)
            objectJson.containsKey("value") ->
                DeviceUpdate.NumericValue(id, objectJson["value"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0)
            objectJson.containsKey("status") ->
                DeviceUpdate.BinaryStatus(id, (objectJson["status"]?.jsonPrimitive?.content?.toDoubleOrNull() ?: 0.0) > 0)
            else -> null
        }
    }

    private fun DeviceCommand.deviceId(): Int = when (this) {
        is DeviceCommand.SetBinaryOutput -> deviceId
        is DeviceCommand.FirePulse -> deviceId
        is DeviceCommand.SetShutterOpen -> deviceId
        is DeviceCommand.SetShutterClosed -> deviceId
        is DeviceCommand.SetShutterPercentage -> deviceId
        is DeviceCommand.SetDimmerPercentage -> deviceId
    }
}
