package com.neteinstein.donaclone.feature.login.di

import com.neteinstein.donaclone.feature.login.LoginViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val loginModule = module {
    viewModelOf(::LoginViewModel)
}
