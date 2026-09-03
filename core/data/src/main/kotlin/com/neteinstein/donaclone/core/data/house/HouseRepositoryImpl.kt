package com.neteinstein.donaclone.core.data.house

import com.neteinstein.donaclone.core.data.mapper.HouseMapper
import com.neteinstein.donaclone.core.database.house.HouseDao
import com.neteinstein.donaclone.core.database.prefs.SessionPreferences
import com.neteinstein.donaclone.core.domain.repository.HouseRepository
import com.neteinstein.donaclone.core.model.House
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class HouseRepositoryImpl(
    private val houseDao: HouseDao,
    private val sessionPreferences: SessionPreferences,
    private val mapper: HouseMapper,
) : HouseRepository {
    override fun observeHouses(): Flow<List<House>> = houseDao.observeAll().map { list -> list.map(mapper::toDomain) }

    override suspend fun getHouse(name: String): House? = houseDao.findByName(name)?.let(mapper::toDomain)

    override suspend fun saveHouse(house: House) = houseDao.upsert(mapper.toEntity(house))

    override suspend fun deleteHouse(name: String) = houseDao.deleteByName(name)

    override val activeHouseName: Flow<String?> = sessionPreferences.activeHouseName

    override suspend fun setActiveHouseName(name: String?) = sessionPreferences.setActiveHouseName(name)
}
