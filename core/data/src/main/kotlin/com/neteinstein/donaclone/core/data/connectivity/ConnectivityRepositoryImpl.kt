package com.neteinstein.donaclone.core.data.connectivity

import com.neteinstein.donaclone.core.domain.repository.ConnectivityRepository
import com.neteinstein.donaclone.core.network.connectivity.ConnectivityObserver
import kotlinx.coroutines.flow.StateFlow

class ConnectivityRepositoryImpl(
    private val observer: ConnectivityObserver,
) : ConnectivityRepository {
    override val isOnline: StateFlow<Boolean> = observer.isOnline
}
