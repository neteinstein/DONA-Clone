package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.UserRepository
import com.neteinstein.donaclone.core.model.User

class GetUsersUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(): DonaResult<List<User>> = repository.getUsers()
}

class CreateUserUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(
        name: String,
        password: String,
        role: Int,
        enabled: Boolean,
        remoteAccessible: Boolean,
    ): DonaResult<User> = repository.createUser(name, password, role, enabled, remoteAccessible)
}

class UpdateUserUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(
        id: Int,
        name: String,
        role: Int,
        enabled: Boolean,
        remoteAccessible: Boolean,
        newPassword: String? = null,
    ): DonaResult<Unit> = repository.updateUser(id, name, role, enabled, remoteAccessible, newPassword)
}

class DeleteUserUseCase(
    private val repository: UserRepository,
) {
    suspend operator fun invoke(id: Int): DonaResult<Unit> = repository.deleteUser(id)
}
