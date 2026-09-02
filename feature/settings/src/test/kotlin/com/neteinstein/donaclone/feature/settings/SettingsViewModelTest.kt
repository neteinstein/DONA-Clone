package com.neteinstein.donaclone.feature.settings

import app.cash.turbine.test
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.LogoutUseCase
import com.neteinstein.donaclone.core.model.AuthSession
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
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
class SettingsViewModelTest {

    private val dispatcher = StandardTestDispatcher()
    private val getCurrentSession = mockk<GetCurrentSessionUseCase>()
    private val logout = mockk<LogoutUseCase>()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `initial state reflects the current session`() {
        every { getCurrentSession() } returns AuthSession(token = "t", userId = 1, userName = "alice", houseName = "Home")
        val viewModel = SettingsViewModel(getCurrentSession, logout)

        assertEquals("Home", viewModel.uiState.value.houseName)
        assertEquals("alice", viewModel.uiState.value.userName)
    }

    @Test
    fun `logout marks the state as logged out`() = runTest(dispatcher) {
        every { getCurrentSession() } returns null
        coEvery { logout.invoke() } just Runs
        val viewModel = SettingsViewModel(getCurrentSession, logout)

        viewModel.uiState.test {
            expectMostRecentItem()
            viewModel.logout()
            val loggedOut = awaitItem()
            assertEquals(true, loggedOut.loggedOut)
        }
    }
}
