package com.neteinstein.donaclone.core.data.user

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.Role
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.dto.RoleDto
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

class RoleRepositoryImplTest {
    private val api = mockk<DomotalkApi>()
    private val repository = RoleRepositoryImpl(api)

    @Test
    fun `maps read roles to the domain model`() =
        runTest {
            coEvery { api.readRoles() } returns
                listOf(RoleDto(id = 1, name = "User"), RoleDto(id = 2, name = "Administrator"))

            val result = repository.getRoles()

            assertEquals(
                DonaResult.Success(listOf(Role(id = 1, name = "User"), Role(id = 2, name = "Administrator"))),
                result,
            )
        }
}
