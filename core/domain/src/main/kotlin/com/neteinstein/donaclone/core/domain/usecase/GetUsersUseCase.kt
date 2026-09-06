package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.UserRepository
import com.neteinstein.donaclone.core.model.User

class GetUsersUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(): DonaResult<List<User>> = repository.getUsers()
}
