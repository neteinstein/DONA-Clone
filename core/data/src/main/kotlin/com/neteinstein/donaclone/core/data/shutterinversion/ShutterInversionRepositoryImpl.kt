package com.neteinstein.donaclone.core.data.shutterinversion

import com.neteinstein.donaclone.core.database.prefs.ShutterInversionPreferences
import com.neteinstein.donaclone.core.domain.repository.HouseRepository
import com.neteinstein.donaclone.core.domain.repository.ShutterInversionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf

class ShutterInversionRepositoryImpl(
    private val preferences: ShutterInversionPreferences,
    private val houseRepository: HouseRepository,
) : ShutterInversionRepository {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun observeInvertedShutterIds(): Flow<Set<Int>> =
        houseRepository.activeHouseName.flatMapLatest { houseName ->
            if (houseName == null) flowOf(emptySet()) else preferences.invertedShutterIds(houseName)
        }

    override suspend fun setShutterInverted(
        deviceId: Int,
        inverted: Boolean,
    ) {
        val houseName = houseRepository.activeHouseName.first() ?: return
        preferences.setInverted(houseName, deviceId, inverted)
    }
}
