package com.neteinstein.donaclone.core.data.di

import com.neteinstein.donaclone.core.data.ambience.AmbienceRepositoryImpl
import com.neteinstein.donaclone.core.data.auth.AuthRepositoryImpl
import com.neteinstein.donaclone.core.data.biometric.BiometricRepositoryImpl
import com.neteinstein.donaclone.core.data.connectivity.ConnectivityRepositoryImpl
import com.neteinstein.donaclone.core.data.device.DeviceRepositoryImpl
import com.neteinstein.donaclone.core.data.house.DiscoveryRepositoryImpl
import com.neteinstein.donaclone.core.data.house.HouseRepositoryImpl
import com.neteinstein.donaclone.core.data.mapper.HouseMapper
import com.neteinstein.donaclone.core.data.roomsdisplay.RoomsDisplayRepositoryImpl
import com.neteinstein.donaclone.core.data.theme.ThemeRepositoryImpl
import com.neteinstein.donaclone.core.data.update.UpdateInstallerImpl
import com.neteinstein.donaclone.core.data.update.UpdateRepositoryImpl
import com.neteinstein.donaclone.core.domain.repository.AmbienceRepository
import com.neteinstein.donaclone.core.domain.repository.AuthRepository
import com.neteinstein.donaclone.core.domain.repository.BiometricRepository
import com.neteinstein.donaclone.core.domain.repository.ConnectivityRepository
import com.neteinstein.donaclone.core.domain.repository.DeviceRepository
import com.neteinstein.donaclone.core.domain.repository.DiscoveryRepository
import com.neteinstein.donaclone.core.domain.repository.HouseRepository
import com.neteinstein.donaclone.core.domain.repository.RoomsDisplayRepository
import com.neteinstein.donaclone.core.domain.repository.ThemeRepository
import com.neteinstein.donaclone.core.domain.repository.UpdateInstaller
import com.neteinstein.donaclone.core.domain.repository.UpdateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module

val dataModule =
    module {
        single { HouseMapper(cipher = get()) }
        single<HouseRepository> { HouseRepositoryImpl(houseDao = get(), sessionPreferences = get(), mapper = get()) }
        single<DiscoveryRepository> { DiscoveryRepositoryImpl(discoveryClient = get()) }
        single(named("applicationScope")) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
        single<AuthRepository> {
            AuthRepositoryImpl(
                socket = get(),
                api = get(),
                houseRepository = get(),
                applicationScope = get(named("applicationScope")),
            )
        }
        single<DeviceRepository> { DeviceRepositoryImpl(api = get()) }
        single<AmbienceRepository> { AmbienceRepositoryImpl(api = get()) }
        single<ThemeRepository> { ThemeRepositoryImpl(themePreferences = get()) }
        single<BiometricRepository> { BiometricRepositoryImpl(biometricPreferences = get()) }
        single<RoomsDisplayRepository> { RoomsDisplayRepositoryImpl(roomsDisplayPreferences = get()) }
        single<ConnectivityRepository> { ConnectivityRepositoryImpl(observer = get()) }
        single<UpdateRepository> {
            UpdateRepositoryImpl(
                api = get(),
                context = get(),
                repoSlug = get(named("githubRepoSlug")),
                downloadClient = get<OkHttpClient>(named("github")),
            )
        }
        single<UpdateInstaller> { UpdateInstallerImpl(context = get()) }
    }
