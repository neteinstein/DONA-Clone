package com.neteinstein.donaclone

import android.app.Application
import com.neteinstein.donaclone.core.data.di.dataModule
import com.neteinstein.donaclone.core.database.di.databaseModule
import com.neteinstein.donaclone.core.domain.di.useCaseModule
import com.neteinstein.donaclone.core.network.di.networkModule
import com.neteinstein.donaclone.feature.ambiences.di.ambiencesModule
import com.neteinstein.donaclone.feature.dashboard.di.dashboardModule
import com.neteinstein.donaclone.feature.devices.di.devicesModule
import com.neteinstein.donaclone.feature.houses.di.housesModule
import com.neteinstein.donaclone.feature.login.di.loginModule
import com.neteinstein.donaclone.feature.settings.di.settingsModule
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import timber.log.Timber

class DonaCloneApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
        }

        startKoin {
            if (BuildConfig.DEBUG) androidLogger(Level.INFO)
            androidContext(this@DonaCloneApplication)
            modules(
                networkModule,
                databaseModule,
                dataModule,
                useCaseModule,
                loginModule,
                housesModule,
                dashboardModule,
                devicesModule,
                ambiencesModule,
                settingsModule,
            )
        }
    }
}
