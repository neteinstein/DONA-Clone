package com.neteinstein.donaclone.feature.devices.di

import com.neteinstein.donaclone.core.model.RoomsDisplayTab
import com.neteinstein.donaclone.feature.devices.DeviceDetailViewModel
import com.neteinstein.donaclone.feature.devices.DevicesViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.dsl.module

val devicesModule =
    module {
        viewModel { (tab: RoomsDisplayTab) ->
            DevicesViewModel(tab, get(), get(), get(), get(), get(), get(), get(), get(), get(), get())
        }
        viewModel { (deviceId: Int) -> DeviceDetailViewModel(deviceId, get(), get(), get(), get()) }
    }
