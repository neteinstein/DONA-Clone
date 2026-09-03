package com.neteinstein.donaclone.core.domain.di

import com.neteinstein.donaclone.core.domain.usecase.DeleteHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.DiscoverHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetActiveHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetAmbiencesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetCurrentSessionUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetDevicesUseCase
import com.neteinstein.donaclone.core.domain.usecase.GetRoomsUseCase
import com.neteinstein.donaclone.core.domain.usecase.LoginUseCase
import com.neteinstein.donaclone.core.domain.usecase.LogoutUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveConnectivityUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDeviceUpdatesUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveDpuUnreachableUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveHousesUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveRoomsExpandedByDefaultUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveSessionStateUseCase
import com.neteinstein.donaclone.core.domain.usecase.ObserveThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.RetryConnectionUseCase
import com.neteinstein.donaclone.core.domain.usecase.SaveHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.SendDeviceCommandUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetActiveHouseUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetBiometricEnabledUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetRoomsExpandedByDefaultUseCase
import com.neteinstein.donaclone.core.domain.usecase.SetThemeModeUseCase
import com.neteinstein.donaclone.core.domain.usecase.TriggerAmbienceUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val useCaseModule =
    module {
        factoryOf(::ObserveHousesUseCase)
        factoryOf(::SaveHouseUseCase)
        factoryOf(::DeleteHouseUseCase)
        factoryOf(::GetActiveHouseUseCase)
        factoryOf(::SetActiveHouseUseCase)
        factoryOf(::DiscoverHousesUseCase)

        factoryOf(::LoginUseCase)
        factoryOf(::LogoutUseCase)
        factoryOf(::ObserveSessionStateUseCase)
        factoryOf(::GetCurrentSessionUseCase)
        factoryOf(::RetryConnectionUseCase)
        factoryOf(::ObserveDpuUnreachableUseCase)

        factoryOf(::GetRoomsUseCase)
        factoryOf(::GetDevicesUseCase)
        factoryOf(::SendDeviceCommandUseCase)
        factoryOf(::ObserveDeviceUpdatesUseCase)

        factoryOf(::GetAmbiencesUseCase)
        factoryOf(::TriggerAmbienceUseCase)

        factoryOf(::ObserveThemeModeUseCase)
        factoryOf(::SetThemeModeUseCase)
        factoryOf(::ObserveBiometricEnabledUseCase)
        factoryOf(::SetBiometricEnabledUseCase)
        factoryOf(::ObserveConnectivityUseCase)
        factoryOf(::ObserveRoomsExpandedByDefaultUseCase)
        factoryOf(::SetRoomsExpandedByDefaultUseCase)
    }
