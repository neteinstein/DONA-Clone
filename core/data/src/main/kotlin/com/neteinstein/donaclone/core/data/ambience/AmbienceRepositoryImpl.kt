package com.neteinstein.donaclone.core.data.ambience

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.data.mapper.donaResultCatching
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.model.Ambience
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import kotlinx.serialization.json.JsonObject
import java.util.concurrent.ConcurrentHashMap

class AmbienceRepositoryImpl(
    private val api: DomotalkApi,
) : AmbienceRepository {
    private val rawAmbienceCache = ConcurrentHashMap<Int, JsonObject>()

    override suspend fun getAmbiences(): DonaResult<List<Ambience>> =
        donaResultCatching {
            api.readAmbiences().map { snapshot ->
                rawAmbienceCache[snapshot.ambience.id] = snapshot.raw
                Ambience(
                    id = snapshot.ambience.id,
                    name = snapshot.ambience.name,
                    isPlaying = snapshot.ambience.isPlaying,
                    enabled = snapshot.ambience.enabled,
                )
            }
        }

    override suspend fun triggerAmbience(
        id: Int,
        run: Boolean,
    ): DonaResult<Unit> {
        val raw =
            rawAmbienceCache[id]
                ?: return DonaResult.Error(DonaFailure.Unknown("Ambience $id hasn't been read yet"))

        return donaResultCatching { api.sendAmbienceAction(raw, run) }
    }
}
