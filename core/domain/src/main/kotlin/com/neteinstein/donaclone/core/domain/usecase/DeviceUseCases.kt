package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.DeviceRepository
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceUpdate
import com.neteinstein.donaclone.core.model.Division
import kotlinx.coroutines.flow.Flow

class GetRoomsUseCase(
    private val repository: DeviceRepository,
) {
    suspend operator fun invoke(): DonaResult<List<Division>> = repository.getRooms()
}

/** Fetches every controllable/readable device (outputs + inputs) in one call. */
class GetDevicesUseCase(
    private val repository: DeviceRepository,
) {
    suspend operator fun invoke(): DonaResult<List<Device>> {
        val outputs = repository.getOutputDevices()
        if (outputs is DonaResult.Error) return outputs
        val inputs = repository.getInputDevices()
        if (inputs is DonaResult.Error) return inputs
        val all = (outputs as DonaResult.Success).data + (inputs as DonaResult.Success).data
        return DonaResult.Success(all)
    }
}

class SendDeviceCommandUseCase(
    private val repository: DeviceRepository,
) {
    suspend operator fun invoke(command: DeviceCommand): DonaResult<Unit> = repository.sendCommand(command)
}

class ObserveDeviceUpdatesUseCase(
    private val repository: DeviceRepository,
) {
    operator fun invoke(): Flow<DeviceUpdate> = repository.observeDeviceUpdates()
}
