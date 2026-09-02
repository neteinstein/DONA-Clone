package com.neteinstein.donaclone.feature.dashboard.di

import com.neteinstein.donaclone.feature.dashboard.DashboardViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val dashboardModule =
    module {
        viewModelOf(::DashboardViewModel)
    }
