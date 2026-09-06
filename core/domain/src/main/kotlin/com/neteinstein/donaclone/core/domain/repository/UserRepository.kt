package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.User

interface UserRepository {
    suspend fun getUsers(): DonaResult<List<User>>
}
