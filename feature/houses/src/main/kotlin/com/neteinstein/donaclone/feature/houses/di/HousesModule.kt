package com.neteinstein.donaclone.feature.houses.di

import com.neteinstein.donaclone.feature.houses.HousesViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val housesModule =
    module {
        viewModelOf(::HousesViewModel)
    }
