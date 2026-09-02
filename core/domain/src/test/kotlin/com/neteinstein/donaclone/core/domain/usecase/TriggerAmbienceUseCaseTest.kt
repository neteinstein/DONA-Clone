package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.model.Ambience
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class TriggerAmbienceUseCaseTest {
    private val repository = mockk<AmbienceRepository>()
    private val useCase = TriggerAmbienceUseCase(repository)

    @Test
    fun `a stopped ambience is triggered to run`() =
        runTest {
            val ambience = Ambience(id = 1, name = "Movie night", isPlaying = false, enabled = true)
            coEvery { repository.triggerAmbience(1, run = true) } returns DonaResult.Success(Unit)

            val result = useCase(ambience)

            assertTrue(result is DonaResult.Success)
            coVerify { repository.triggerAmbience(1, run = true) }
        }

    @Test
    fun `a running ambience is stopped`() =
        runTest {
            val ambience = Ambience(id = 2, name = "Good morning", isPlaying = true, enabled = true)
            coEvery { repository.triggerAmbience(2, run = false) } returns DonaResult.Success(Unit)

            useCase(ambience)

            coVerify { repository.triggerAmbience(2, run = false) }
        }
}
