package com.neteinstein.donaclone.core.data.user

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.auth.PasswordHasher
import com.neteinstein.donaclone.core.model.User
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.dto.UserDto
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UserRepositoryImplTest {
    private val api = mockk<DomotalkApi>()
    private val repository = UserRepositoryImpl(api)

    @Test
    fun `maps read users to the domain model`() =
        runTest {
            val dto = UserDto(id = 1, role = 2, hidden = false, name = "Alice", remoteAccessible = true, enabled = true)
            coEvery { api.readUsers() } returns listOf(dto)

            val result = repository.getUsers()

            assertEquals(
                DonaResult.Success(listOf(User(id = 1, name = "Alice", role = 2, enabled = true, remoteAccessible = true, hidden = false))),
                result,
            )
        }

    @Test
    fun `creating a user hashes the password and maps the response back to the domain model`() =
        runTest {
            val created = UserDto(id = 5, role = 1, name = "Bob", remoteAccessible = false, enabled = true)
            coEvery { api.createUser(any(), any(), any(), any(), any()) } returns created

            val result = repository.createUser(name = "Bob", password = "hunter2", role = 1, enabled = true, remoteAccessible = false)

            assertEquals(
                DonaResult.Success(User(id = 5, name = "Bob", role = 1, enabled = true, remoteAccessible = false)),
                result,
            )
            coVerify {
                api.createUser(
                    name = "Bob",
                    md5Password = PasswordHasher.md5Hex("hunter2"),
                    role = 1,
                    enabled = true,
                    remoteAccessible = false,
                )
            }
        }

    @Test
    fun `updating a user before it was ever read fails without calling the api`() =
        runTest {
            val result = repository.updateUser(id = 1, name = "Alice", role = 1, enabled = true, remoteAccessible = true)

            assertTrue(result is DonaResult.Error)
            coVerify(exactly = 0) { api.updateUser(any(), any()) }
        }

    @Test
    fun `updating a user preserves fields not exposed by the edit form and hashes a new password`() =
        runTest {
            val dto = UserDto(id = 1, role = 1, hidden = true, photoUri = "photo.png", name = "Alice", house = 9, enabled = true)
            coEvery { api.readUsers() } returns listOf(dto)
            repository.getUsers()

            val sentDto = slot<UserDto>()
            coEvery { api.updateUser(capture(sentDto), any()) } returns Unit

            val result =
                repository.updateUser(id = 1, name = "Alicia", role = 2, enabled = false, remoteAccessible = false, newPassword = "new-pw")

            assertTrue(result is DonaResult.Success)
            val updated = sentDto.captured
            assertEquals("Alicia", updated.name)
            assertEquals(2, updated.role)
            assertEquals(false, updated.enabled)
            assertEquals(false, updated.remoteAccessible)
            assertEquals(PasswordHasher.md5Hex("new-pw"), updated.password)
            // Untouched by the edit form - must round-trip unchanged.
            assertEquals(true, updated.hidden)
            assertEquals("photo.png", updated.photoUri)
            assertEquals(9, updated.house)
        }

    @Test
    fun `updating a user without a new password leaves the password field null`() =
        runTest {
            coEvery { api.readUsers() } returns listOf(UserDto(id = 1, name = "Alice"))
            repository.getUsers()
            val sentDto = slot<UserDto>()
            coEvery { api.updateUser(capture(sentDto), any()) } returns Unit

            repository.updateUser(id = 1, name = "Alice", role = 1, enabled = true, remoteAccessible = true)

            assertNull(sentDto.captured.password)
        }

    @Test
    fun `deleting a user calls the api and evicts the cache`() =
        runTest {
            coEvery { api.readUsers() } returns listOf(UserDto(id = 1, name = "Alice"))
            repository.getUsers()
            coEvery { api.deleteUser(1) } returns Unit

            val result = repository.deleteUser(1)

            assertTrue(result is DonaResult.Success)
            coVerify { api.deleteUser(1) }
            // The cache no longer has this user, so a further update must fail like an unread one.
            assertTrue(repository.updateUser(id = 1, name = "Alice", role = 1, enabled = true, remoteAccessible = true) is DonaResult.Error)
        }
}
