package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.Role

interface RoleRepository {
    /** `read role` (§11.4) — the hub's named role catalogue. */
    suspend fun getRoles(): DonaResult<List<Role>>
}
