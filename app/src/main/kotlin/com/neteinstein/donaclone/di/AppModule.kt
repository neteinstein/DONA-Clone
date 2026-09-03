package com.neteinstein.donaclone.di

import com.neteinstein.donaclone.MainActivityViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val appModule =
    module {
        viewModelOf(::MainActivityViewModel)
    }
