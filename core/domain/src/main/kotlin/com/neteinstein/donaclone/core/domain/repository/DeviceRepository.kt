package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceUpdate
import com.neteinstein.donaclone.core.model.Division
import kotlinx.coroutines.flow.Flow

interface DeviceRepository {
    suspend fun getRooms(): DonaResult<List<Division>>
    suspend fun getOutputDevices(): DonaResult<List<Device>>
    suspend fun getInputDevices(): DonaResult<List<Device>>
    suspend fun sendCommand(command: DeviceCommand): DonaResult<Unit>

    /** Live device-state pushes from the hub (best-effort parsed, envelope unconfirmed §8). */
    fun observeDeviceUpdates(): Flow<DeviceUpdate>
}
