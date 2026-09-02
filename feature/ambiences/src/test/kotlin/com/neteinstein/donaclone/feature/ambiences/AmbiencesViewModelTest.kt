package com.neteinstein.donaclone.feature.ambiences

import app.cash.turbine.test
import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.GetAmbiencesUseCase
import com.neteinstein.donaclone.core.domain.usecase.TriggerAmbienceUseCase
import com.neteinstein.donaclone.core.model.Ambience
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AmbiencesViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val getAmbiences = mockk<GetAmbiencesUseCase>()
    private val triggerAmbience = mockk<TriggerAmbienceUseCase>()

    private val movieNight = Ambience(id = 1, name = "Movie night", isPlaying = false, enabled = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getAmbiences() } returns DonaResult.Success(listOf(movieNight))
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel() = AmbiencesViewModel(getAmbiences, triggerAmbience)

    @Test
    fun `toggling optimistically flips isPlaying immediately`() =
        runTest(dispatcher) {
            coEvery { triggerAmbience(movieNight) } returns DonaResult.Success(Unit)
            val viewModel = createViewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                awaitItem() // ambiences loaded by the init { refresh() } call
                viewModel.toggle(movieNight)
                val optimistic = awaitItem()
                assertEquals(true, optimistic.ambiences.first().isPlaying)
            }
        }

    @Test
    fun `a failed trigger rolls back the optimistic update`() =
        runTest(dispatcher) {
            coEvery { triggerAmbience(movieNight) } returns DonaResult.Error(DonaFailure.Unreachable("offline"))
            val viewModel = createViewModel()

            viewModel.uiState.test {
                expectMostRecentItem()
                awaitItem() // ambiences loaded by the init { refresh() } call
                viewModel.toggle(movieNight)
                awaitItem() // optimistic flip
                val rolledBack = awaitItem()
                assertEquals(false, rolledBack.ambiences.first().isPlaying)
                assertEquals("offline", rolledBack.errorMessage)
            }
        }
}
