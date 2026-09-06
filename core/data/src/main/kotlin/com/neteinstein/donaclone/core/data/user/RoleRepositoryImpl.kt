package com.neteinstein.donaclone.core.data.user

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.mapper.donaResultCatching
import com.neteinstein.donaclone.core.domain.repository.RoleRepository
import com.neteinstein.donaclone.core.model.Role
import com.neteinstein.donaclone.core.network.api.DomotalkApi

class RoleRepositoryImpl(
    private val api: DomotalkApi,
) : RoleRepository {
    override suspend fun getRoles(): DonaResult<List<Role>> =
        donaResultCatching {
            api.readRoles().map { Role(id = it.id, name = it.name) }
        }
}
