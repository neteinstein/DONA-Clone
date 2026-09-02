package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.model.Ambience

interface AmbienceRepository {
    suspend fun getAmbiences(): DonaResult<List<Ambience>>
    suspend fun triggerAmbience(id: Int, run: Boolean): DonaResult<Unit>
}
