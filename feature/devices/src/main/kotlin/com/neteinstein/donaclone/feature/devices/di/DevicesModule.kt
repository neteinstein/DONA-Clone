package com.neteinstein.donaclone.feature.devices.di

import com.neteinstein.donaclone.feature.devices.DeviceDetailViewModel
import com.neteinstein.donaclone.feature.devices.DevicesViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val devicesModule =
    module {
        viewModelOf(::DevicesViewModel)
        viewModel { (deviceId: Int) -> DeviceDetailViewModel(deviceId, get(), get(), get(), get()) }
    }
