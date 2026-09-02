package com.neteinstein.donaclone.feature.ambiences.di

import com.neteinstein.donaclone.feature.ambiences.AmbiencesViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val ambiencesModule =
    module {
        viewModelOf(::AmbiencesViewModel)
    }
