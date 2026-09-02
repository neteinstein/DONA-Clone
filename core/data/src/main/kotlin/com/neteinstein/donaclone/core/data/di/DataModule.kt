package com.neteinstein.donaclone.core.data.di

import com.neteinstein.donaclone.core.data.ambience.AmbienceRepositoryImpl
import com.neteinstein.donaclone.core.data.auth.AuthRepositoryImpl
import com.neteinstein.donaclone.core.data.device.DeviceRepositoryImpl
import com.neteinstein.donaclone.core.data.house.DiscoveryRepositoryImpl
import com.neteinstein.donaclone.core.data.house.HouseRepositoryImpl
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.domain.repository.AuthRepository
import com.neteinstein.donaclone.core.domain.repository.DeviceRepository
import com.neteinstein.donaclone.core.domain.repository.DiscoveryRepository
import com.neteinstein.donaclone.core.domain.repository.HouseRepository
import org.koin.dsl.module

val dataModule = module {
    single<HouseRepository> { HouseRepositoryImpl(houseDao = get(), sessionPreferences = get()) }
    single<DiscoveryRepository> { DiscoveryRepositoryImpl(discoveryClient = get()) }
    single<AuthRepository> { AuthRepositoryImpl(socket = get(), api = get()) }
    single<DeviceRepository> { DeviceRepositoryImpl(api = get()) }
    single<AmbienceRepository> { AmbienceRepositoryImpl(api = get()) }
}
