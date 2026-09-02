package com.neteinstein.donaclone.core.domain.repository

import com.neteinstein.donaclone.core.model.DiscoveredHouse
import com.neteinstein.donaclone.core.model.House
import kotlinx.coroutines.flow.Flow

interface HouseRepository {
    fun observeHouses(): Flow<List<House>>

    suspend fun getHouse(name: String): House?

    suspend fun saveHouse(house: House)

    suspend fun deleteHouse(name: String)

    val activeHouseName: Flow<String?>

    suspend fun setActiveHouseName(name: String?)
}

interface DiscoveryRepository {
    /** Broadcasts on the LAN and emits every hub found within the listen window. */
    fun discoverHouses(): Flow<DiscoveredHouse>
}
