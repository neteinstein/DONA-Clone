package com.neteinstein.donaclone.di

import com.neteinstein.donaclone.BuildConfig
import com.neteinstein.donaclone.MainActivityViewModel
import org.koin.androidx.viewmodel.dsl.viewModelOf
import org.koin.core.qualifier.named
import org.koin.dsl.module

val appModule =
    module {
        viewModelOf(::MainActivityViewModel)
        single(named("githubRepoSlug")) { BuildConfig.GITHUB_REPO_SLUG }
    }
