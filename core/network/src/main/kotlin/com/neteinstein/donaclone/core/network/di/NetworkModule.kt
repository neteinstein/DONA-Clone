package com.neteinstein.donaclone.core.network.di

import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.api.DomotalkApiImpl
import com.neteinstein.donaclone.core.network.discovery.DiscoveryClient
import com.neteinstein.donaclone.core.network.discovery.UdpDiscoveryClient
import com.neteinstein.donaclone.core.network.socket.DomotalkSocket
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import org.koin.dsl.module

val networkModule = module {
    single {
        Json {
            ignoreUnknownKeys = true
            isLenient = true
            coerceInputValues = true
            explicitNulls = false
        }
    }
    single<OkHttpClient> { DomotalkSocket.defaultOkHttpClient() }
    single { DomotalkSocket(okHttpClient = get(), json = get()) }
    single<DomotalkApi> { DomotalkApiImpl(socket = get(), json = get()) }
    single<DiscoveryClient> { UdpDiscoveryClient() }
}
