package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertTrue
import org.junit.Test

class DeleteAutomationUseCaseTest {
    private val repository = mockk<AmbienceRepository>()
    private val useCase = DeleteAutomationUseCase(repository)

    @Test
    fun `deleting an automation delegates to the repository`() =
        runTest {
            coEvery { repository.deleteAmbience(7) } returns DonaResult.Success(Unit)

            val result = useCase(7)

            assertTrue(result is DonaResult.Success)
            coVerify { repository.deleteAmbience(7) }
        }
}
