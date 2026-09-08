package com.neteinstein.donaclone.core.data.device

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.ShutterInversionRepository
import com.neteinstein.donaclone.core.model.Device
import com.neteinstein.donaclone.core.model.DeviceCommand
import com.neteinstein.donaclone.core.model.DeviceUpdate
import com.neteinstein.donaclone.core.network.api.DeviceSnapshot
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.mapper.DeviceJsonMapper
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DeviceRepositoryImplTest {
    private val api = mockk<DomotalkApi>()

    /** Which shutters the user has flagged as wired backwards, mutable per test. */
    private val invertedIds = MutableStateFlow(emptySet<Int>())
    private val shutterInversion =
        mockk<ShutterInversionRepository>().also {
            every { it.observeInvertedShutterIds() } returns invertedIds
        }
    private val repository = DeviceRepositoryImpl(api, shutterInversion)

    private fun rawBinaryOut(id: Int) = Json.parseToJsonElement("""{"id":$id,"name":"Light","status":0}""").jsonObject

    private fun rawShutter(
        id: Int,
        percentage: Int,
    ) = Json
        .parseToJsonElement(
            """{"id":$id,"name":"Blind","type":70,"percentage":$percentage,"processDuration":20}""",
        ).jsonObject

    private suspend fun readShutter(
        id: Int,
        percentage: Int,
    ): Device.Shutter {
        val raw = rawShutter(id, percentage)
        coEvery { api.readDeviceOut() } returns listOf(DeviceSnapshot(DeviceJsonMapper.parseDeviceOut(raw), raw))
        val devices = repository.getOutputDevices() as DonaResult.Success
        return devices.data.filterIsInstance<Device.Shutter>().single()
    }

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

    @Test
    fun `an inverted shutter's reported position is mirrored on read`() =
        runTest {
            invertedIds.value = setOf(7)

            assertEquals(70, readShutter(id = 7, percentage = 30).percentage)
        }

    @Test
    fun `a shutter that isn't flagged is read unchanged`() =
        runTest {
            assertEquals(30, readShutter(id = 7, percentage = 30).percentage)
        }

    @Test
    fun `opening an inverted shutter sends the close action, and closing sends open`() =
        runTest {
            invertedIds.value = setOf(7)
            val raw = rawShutter(id = 7, percentage = 0)
            coEvery { api.readDeviceOut() } returns listOf(DeviceSnapshot(DeviceJsonMapper.parseDeviceOut(raw), raw))
            coEvery { api.sendShutterOpenClose(raw, any()) } just Runs
            repository.getOutputDevices()

            repository.sendCommand(DeviceCommand.SetShutterOpen(deviceId = 7))
            repository.sendCommand(DeviceCommand.SetShutterClosed(deviceId = 7))

            coVerify { api.sendShutterOpenClose(raw, open = false) }
            coVerify { api.sendShutterOpenClose(raw, open = true) }
        }

    @Test
    fun `setting a percentage on an inverted shutter sends the mirrored value`() =
        runTest {
            invertedIds.value = setOf(7)
            val raw = rawShutter(id = 7, percentage = 0)
            coEvery { api.readDeviceOut() } returns listOf(DeviceSnapshot(DeviceJsonMapper.parseDeviceOut(raw), raw))
            coEvery { api.sendShutterPercentage(raw, any()) } just Runs
            repository.getOutputDevices()

            repository.sendCommand(DeviceCommand.SetShutterPercentage(deviceId = 7, percentage = 80))

            coVerify { api.sendShutterPercentage(raw, 20) }
        }

    @Test
    fun `a push update for an inverted shutter is mirrored, and one for another device is not`() =
        runTest {
            invertedIds.value = setOf(7)
            val push =
                Json
                    .parseToJsonElement(
                        """{"request":{"options":{"object":{"id":7,"percentage":25}}}}""",
                    ).jsonObject
            val otherPush =
                Json
                    .parseToJsonElement(
                        """{"request":{"options":{"object":{"id":8,"percentage":25}}}}""",
                    ).jsonObject
            every { api.observeUpdates() } returns flowOf(push, otherPush)

            val updates = repository.observeDeviceUpdates().take(2).toList()

            assertEquals(DeviceUpdate.Percentage(7, 75), updates[0])
            assertEquals(DeviceUpdate.Percentage(8, 25), updates[1])
        }
}
