package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.User

interface UserRepository {
    suspend fun getUsers(): DonaResult<List<User>>

    /** `create user` (§11.4) — the hub assigns and returns the id. */
    suspend fun createUser(
        name: String,
        password: String,
        role: Int,
        enabled: Boolean,
        remoteAccessible: Boolean,
    ): DonaResult<User>

    /** `update user` on the cached dto for [id] — requires [getUsers] to have populated the cache
     * first (mirrors `AmbienceRepository.updateAmbienceFields`'s own precondition). Pass
     * [newPassword] to also change the account's password. */
    suspend fun updateUser(
        id: Int,
        name: String,
        role: Int,
        enabled: Boolean,
        remoteAccessible: Boolean,
        newPassword: String? = null,
    ): DonaResult<Unit>

    /** `delete user`, filtered by `id` (§11.4). */
    suspend fun deleteUser(id: Int): DonaResult<Unit>
}
