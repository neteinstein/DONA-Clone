package com.neteinstein.donaclone.core.data.user

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.auth.PasswordHasher
import com.neteinstein.donaclone.core.data.mapper.donaResultCatching
import com.neteinstein.donaclone.core.domain.repository.UserRepository
import com.neteinstein.donaclone.core.model.User
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.dto.UserDto
import java.util.concurrent.ConcurrentHashMap

class UserRepositoryImpl(
    private val api: DomotalkApi,
) : UserRepository {
    /** Caches each user's full dto (including fields the UI doesn't edit, e.g. `photoUri`/`house`)
     * so [updateUser] can round-trip them untouched — mirrors `AmbienceRepositoryImpl`'s raw-object
     * cache pattern. */
    private val cache = ConcurrentHashMap<Int, UserDto>()

    override suspend fun getUsers(): DonaResult<List<User>> =
        donaResultCatching {
            api.readUsers().map { dto ->
                cache[dto.id] = dto
                dto.toDomain()
            }
        }

    override suspend fun createUser(
        name: String,
        password: String,
        role: Int,
        enabled: Boolean,
        remoteAccessible: Boolean,
    ): DonaResult<User> =
        donaResultCatching {
            val created =
                api.createUser(
                    name = name,
                    md5Password = PasswordHasher.md5Hex(password),
                    role = role,
                    enabled = enabled,
                    remoteAccessible = remoteAccessible,
                )
            cache[created.id] = created
            created.toDomain()
        }

    override suspend fun updateUser(
        id: Int,
        name: String,
        role: Int,
        enabled: Boolean,
        remoteAccessible: Boolean,
        newPassword: String?,
    ): DonaResult<Unit> {
        val current = cache[id] ?: return unreadUserError(id)
        val updated =
            current.copy(
                name = name,
                role = role,
                enabled = enabled,
                remoteAccessible = remoteAccessible,
                password = newPassword?.let { PasswordHasher.md5Hex(it) },
            )
        return donaResultCatching {
            api.updateUser(updated)
            cache[id] = updated.copy(password = null)
        }
    }

    override suspend fun deleteUser(id: Int): DonaResult<Unit> =
        donaResultCatching {
            api.deleteUser(id)
            cache.remove(id)
            Unit
        }

    private fun unreadUserError(id: Int): DonaResult.Error = DonaResult.Error(DonaFailure.Unknown("User $id hasn't been read yet"))

    private fun UserDto.toDomain() =
        User(id = id, name = name, role = role, enabled = enabled, remoteAccessible = remoteAccessible, hidden = hidden)
}
