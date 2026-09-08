package com.neteinstein.donaclone.feature.settings.di

import com.neteinstein.donaclone.feature.settings.AuditLogViewModel
import com.neteinstein.donaclone.feature.settings.ManageUsersViewModel
import com.neteinstein.donaclone.feature.settings.SettingsViewModel
import com.neteinstein.donaclone.feature.settings.ShutterFixerViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val settingsModule =
    module {
        viewModelOf(::SettingsViewModel)
        viewModelOf(::AuditLogViewModel)
        viewModelOf(::ManageUsersViewModel)
        viewModelOf(::ShutterFixerViewModel)
    }
