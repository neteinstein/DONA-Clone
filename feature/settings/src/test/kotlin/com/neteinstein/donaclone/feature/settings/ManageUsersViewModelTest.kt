package com.neteinstein.donaclone.feature.settings

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.usecase.CreateUserUseCase
import com.neteinstein.donaclone.core.domain.usecase.DeleteUserUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRolesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetUsersUseCase
import com.neteinstein.donaclone.core.domain.usecase.UpdateUserUseCase
import com.neteinstein.donaclone.core.model.Role
import com.neteinstein.donaclone.core.model.User
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ManageUsersViewModelTest {
    private val dispatcher = StandardTestDispatcher()
    private val getUsers = mockk<GetUsersUseCase>()
    private val getRoles = mockk<GetRolesUseCase>()
    private val createUser = mockk<CreateUserUseCase>()
    private val updateUser = mockk<UpdateUserUseCase>()
    private val deleteUser = mockk<DeleteUserUseCase>()

    private val alice = User(id = 1, name = "Alice", role = 1, enabled = true, remoteAccessible = true)
    private val roles = listOf(Role(id = 1, name = "User"), Role(id = 2, name = "Administrator"))

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
        coEvery { getUsers() } returns DonaResult.Success(listOf(alice))
        coEvery { getRoles() } returns DonaResult.Success(roles)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel() = ManageUsersViewModel(getUsers, getRoles, createUser, updateUser, deleteUser)

    @Test
    fun `loads users and roles on init`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(alice), viewModel.uiState.value.users)
            assertEquals(roles, viewModel.uiState.value.roles)
            assertEquals(false, viewModel.uiState.value.isLoading)
        }

    @Test
    fun `a user load failure surfaces an error message`() =
        runTest(dispatcher) {
            coEvery { getUsers() } returns DonaResult.Error(DonaFailure.Unknown("boom"))

            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals("boom", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `a role load failure doesn't block loading the user list`() =
        runTest(dispatcher) {
            coEvery { getRoles() } returns DonaResult.Error(DonaFailure.Unknown("roles down"))

            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            assertEquals(listOf(alice), viewModel.uiState.value.users)
            assertEquals(emptyList<Role>(), viewModel.uiState.value.roles)
            assertEquals(null, viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `starting to add a user defaults the role to the first one from the hub`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.startAddingUser()

            val mode = viewModel.uiState.value.mode as ManageUsersMode.Editing
            assertEquals(null, mode.original)
            assertEquals(UserDraft(roleId = 1), mode.draft)
        }

    @Test
    fun `starting to add a user with no roles loaded leaves the role unset`() =
        runTest(dispatcher) {
            coEvery { getRoles() } returns DonaResult.Success(emptyList())
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.startAddingUser()

            val mode = viewModel.uiState.value.mode as ManageUsersMode.Editing
            assertEquals(UserDraft(), mode.draft)
        }

    @Test
    fun `starting to edit a user seeds the draft from its current fields`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.startEditingUser(alice)

            val mode = viewModel.uiState.value.mode as ManageUsersMode.Editing
            assertEquals(alice, mode.original)
            assertEquals(UserDraft(name = "Alice", roleId = 1, enabled = true, remoteAccessible = true), mode.draft)
        }

    @Test
    fun `saving a new user requires a name and a password`() =
        runTest(dispatcher) {
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.startAddingUser()

            viewModel.saveDraft()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { createUser(any(), any(), any(), any(), any()) }
            assertTrue(viewModel.uiState.value.mode is ManageUsersMode.Editing)
        }

    @Test
    fun `saving a new user without a role loaded does nothing`() =
        runTest(dispatcher) {
            coEvery { getRoles() } returns DonaResult.Success(emptyList())
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.startAddingUser()
            viewModel.updateDraft { it.copy(name = "Bob", password = "pw") }

            viewModel.saveDraft()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { createUser(any(), any(), any(), any(), any()) }
        }

    @Test
    fun `saving a new user creates it with the hub's default remote-access setting and returns to the list`() =
        runTest(dispatcher) {
            coEvery { createUser("Bob", "pw", 1, true, false) } returns DonaResult.Success(User(id = 2, name = "Bob"))
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.startAddingUser()
            viewModel.updateDraft { it.copy(name = "Bob", password = "pw") }

            viewModel.saveDraft()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { createUser("Bob", "pw", 1, true, false) }
            assertEquals(ManageUsersMode.List, viewModel.uiState.value.mode)
        }

    @Test
    fun `saving an edited user updates it with a null password when none was entered`() =
        runTest(dispatcher) {
            coEvery { updateUser(1, "Alicia", 1, true, true, null) } returns DonaResult.Success(Unit)
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.startEditingUser(alice)
            viewModel.updateDraft { it.copy(name = "Alicia") }

            viewModel.saveDraft()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { updateUser(1, "Alicia", 1, true, true, null) }
            assertEquals(ManageUsersMode.List, viewModel.uiState.value.mode)
        }

    @Test
    fun `saving an edited user sends the newly selected role`() =
        runTest(dispatcher) {
            coEvery { updateUser(1, "Alice", 2, true, true, null) } returns DonaResult.Success(Unit)
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.startEditingUser(alice)
            viewModel.updateDraft { it.copy(roleId = 2) }

            viewModel.saveDraft()
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { updateUser(1, "Alice", 2, true, true, null) }
        }

    @Test
    fun `a save failure keeps the editor open and surfaces an error`() =
        runTest(dispatcher) {
            coEvery { updateUser(any(), any(), any(), any(), any(), any()) } returns
                DonaResult.Error(DonaFailure.Unknown("nope"))
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()
            viewModel.startEditingUser(alice)

            viewModel.saveDraft()
            dispatcher.scheduler.advanceUntilIdle()

            assertTrue(viewModel.uiState.value.mode is ManageUsersMode.Editing)
            assertEquals("nope", viewModel.uiState.value.errorMessage)
        }

    @Test
    fun `toggling enabled sends the user's other fields unchanged`() =
        runTest(dispatcher) {
            coEvery { updateUser(1, "Alice", 1, false, true, null) } returns DonaResult.Success(Unit)
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.setEnabled(alice, false)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { updateUser(1, "Alice", 1, false, true, null) }
        }

    @Test
    fun `deleting a user removes it and refreshes the list`() =
        runTest(dispatcher) {
            coEvery { deleteUser(1) } returns DonaResult.Success(Unit)
            val viewModel = viewModel()
            dispatcher.scheduler.advanceUntilIdle()

            viewModel.delete(alice)
            dispatcher.scheduler.advanceUntilIdle()

            coVerify { deleteUser(1) }
            coVerify(atLeast = 2) { getUsers() }
        }
}
