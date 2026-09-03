package com.neteinstein.donaclone.core.domain.repository

import kotlinx.coroutines.flow.StateFlow

interface ConnectivityRepository {
    val isOnline: StateFlow<Boolean>
}
