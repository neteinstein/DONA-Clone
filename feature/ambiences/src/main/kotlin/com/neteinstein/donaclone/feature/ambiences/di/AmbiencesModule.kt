package com.neteinstein.donaclone.feature.ambiences.di

import com.neteinstein.donaclone.feature.ambiences.AmbiencesViewModel
import com.neteinstein.donaclone.feature.ambiences.AutomationEditorViewModel
import org.koin.androidx.viewmodel.dsl.viewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.dsl.module

val ambiencesModule =
    module {
        viewModelOf(::AmbiencesViewModel)
        viewModel { (ambienceId: Int?) -> AutomationEditorViewModel(ambienceId, get(), get(), get()) }
    }
