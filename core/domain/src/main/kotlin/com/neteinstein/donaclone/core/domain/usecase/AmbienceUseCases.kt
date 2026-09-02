package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.model.Ambience

class GetAmbiencesUseCase(
    private val repository: AmbienceRepository,
) {
    suspend operator fun invoke(): DonaResult<List<Ambience>> = repository.getAmbiences()
}

class TriggerAmbienceUseCase(
    private val repository: AmbienceRepository,
) {
    suspend operator fun invoke(ambience: Ambience): DonaResult<Unit> =
        repository.triggerAmbience(ambience.id, run = !ambience.isPlaying)
}
