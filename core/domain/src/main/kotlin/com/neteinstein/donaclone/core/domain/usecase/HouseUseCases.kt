package com.neteinstein.donaclone.core.domain.usecase

import com.neteinstein.donaclone.core.domain.repository.DiscoveryRepository
import com.neteinstein.donaclone.core.domain.repository.HouseRepository
import com.neteinstein.donaclone.core.model.DiscoveredHouse
import com.neteinstein.donaclone.core.model.House
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

class ObserveHousesUseCase(
    private val repository: HouseRepository,
) {
    operator fun invoke(): Flow<List<House>> = repository.observeHouses()
}

class SaveHouseUseCase(
    private val repository: HouseRepository,
) {
    suspend operator fun invoke(house: House) = repository.saveHouse(house)
}

class DeleteHouseUseCase(
    private val repository: HouseRepository,
) {
    suspend operator fun invoke(name: String) = repository.deleteHouse(name)
}

class GetActiveHouseUseCase(
    private val repository: HouseRepository,
) {
    suspend operator fun invoke(): House? = repository.activeHouseName.first()?.let { repository.getHouse(it) }
}

class SetActiveHouseUseCase(
    private val repository: HouseRepository,
) {
    suspend operator fun invoke(name: String?) = repository.setActiveHouseName(name)
}

class DiscoverHousesUseCase(
    private val repository: DiscoveryRepository,
) {
    operator fun invoke(): Flow<DiscoveredHouse> = repository.discoverHouses()
}
