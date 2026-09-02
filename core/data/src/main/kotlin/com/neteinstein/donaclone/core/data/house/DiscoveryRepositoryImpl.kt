package com.neteinstein.donaclone.core.data.house

import com.neteinstein.donaclone.core.domain.repository.DiscoveryRepository
import com.neteinstein.donaclone.core.model.DiscoveredHouse
import com.neteinstein.donaclone.core.network.discovery.DiscoveryClient
import kotlinx.coroutines.flow.Flow

class DiscoveryRepositoryImpl(
    private val discoveryClient: DiscoveryClient,
) : DiscoveryRepository {
    override fun discoverHouses(): Flow<DiscoveredHouse> = discoveryClient.discoverHouses()
}
