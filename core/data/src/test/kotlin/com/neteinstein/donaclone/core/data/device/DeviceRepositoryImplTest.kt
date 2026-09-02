package com.neteinstein.donaclone.core.data.device

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.network.api.DeviceSnapshot
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.mapper.DeviceJsonMapper
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRepositoryImplTest {
    private val api = mockk<DomotalkApi>()
    private val repository = DeviceRepositoryImpl(api)

    private fun rawBinaryOut(id: Int) = Json.parseToJsonElement("""{"id":$id,"name":"Light","status":0}""").jsonObject

    @Test
    fun `sending a command for a device that was never read fails without calling the api`() =
        runTest {
            val result = repository.sendCommand(DeviceCommand.SetBinaryOutput(deviceId = 99, turnOn = true))

            assertTrue(result is DonaResult.Error)
            coVerify(exactly = 0) { api.sendBinaryOutputAction(any(), any()) }
        }

    @Test
    fun `sending a command after reading the device forwards the cached raw json`() =
        runTest {
            val raw = rawBinaryOut(1)
            coEvery { api.readDeviceOut() } returns listOf(DeviceSnapshot(DeviceJsonMapper.parseDeviceOut(raw), raw))
            coEvery { api.sendBinaryOutputAction(raw, true) } just Runs

            repository.getOutputDevices()
            val result = repository.sendCommand(DeviceCommand.SetBinaryOutput(deviceId = 1, turnOn = true))

            assertTrue(result is DonaResult.Success)
            coVerify { api.sendBinaryOutputAction(raw, true) }
        }
}
