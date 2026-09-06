package com.neteinstein.donaclone.core.data.user

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.mapper.donaResultCatching
import com.neteinstein.donaclone.core.domain.repository.UserRepository
import com.neteinstein.donaclone.core.model.User
import com.neteinstein.donaclone.core.network.api.DomotalkApi

class UserRepositoryImpl(
    private val api: DomotalkApi,
) : UserRepository {
    override suspend fun getUsers(): DonaResult<List<User>> =
        donaResultCatching {
            api.readUsers().map { User(id = it.id, name = it.name) }
        }
}
