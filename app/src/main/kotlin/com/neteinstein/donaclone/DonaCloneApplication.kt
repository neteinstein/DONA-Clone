package com.neteinstein.donaclone

import android.app.Application
import com.neteinstein.donaclone.core.data.di.dataModule
import com.neteinstein.donaclone.core.database.di.databaseModule
import com.neteinstein.donaclone.core.domain.di.useCaseModule
import com.neteinstein.donaclone.core.domain.usecase.ObserveDebugModeUseCase
import com.neteinstein.donaclone.core.network.di.networkModule
import com.neteinstein.donaclone.di.appModule
import com.neteinstein.donaclone.feature.ambiences.di.ambiencesModule
import com.neteinstein.donaclone.feature.devices.di.devicesModule
import com.neteinstein.donaclone.feature.houses.di.housesModule
import com.neteinstein.donaclone.feature.login.di.loginModule
import com.neteinstein.donaclone.feature.settings.di.settingsModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import org.koin.core.qualifier.named
import timber.log.Timber

class DonaCloneApplication :
    Application(),
    KoinComponent {
    override fun onCreate() {
        super.onCreate()

        startKoin {
            if (BuildConfig.DEBUG) androidLogger(Level.INFO)
            androidContext(this@DonaCloneApplication)
            modules(
                networkModule,
                databaseModule,
                dataModule,
                useCaseModule,
                appModule,
                loginModule,
                housesModule,
                devicesModule,
                ambiencesModule,
                settingsModule,
            )
        }

        plantLoggingTree()
    }

    /**
     * A debug build always logs. A release build normally logs nothing - unless the user turns on
     * "Debug Mode" in Settings, which flips this on live (no restart needed) even on a release
     * build, to help debug field issues.
     */
    private fun plantLoggingTree() {
        val tree = ReleaseAwareDebugTree(loggingEnabled = BuildConfig.DEBUG)
        Timber.plant(tree)

        val observeDebugMode: ObserveDebugModeUseCase = get()
        val applicationScope: CoroutineScope = get(named("applicationScope"))
        applicationScope.launch {
            observeDebugMode().collect { debugModeEnabled ->
                tree.loggingEnabled = BuildConfig.DEBUG || debugModeEnabled
            }
        }
    }
}

/** A [Timber.DebugTree] that can be turned on/off at runtime via [loggingEnabled], instead of the
 * usual "planted only in debug builds" all-or-nothing choice - see [DonaCloneApplication.plantLoggingTree]. */
private class ReleaseAwareDebugTree(
    @Volatile var loggingEnabled: Boolean,
) : Timber.DebugTree() {
    override fun isLoggable(
        tag: String?,
        priority: Int,
    ): Boolean = loggingEnabled
}
