package com.neteinstein.donaclone.core.network.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.neteinstein.donaclone.core.network.api.DomotalkApi
import com.neteinstein.donaclone.core.network.api.DomotalkApiImpl
import com.neteinstein.donaclone.core.network.api.GitHubApi
import com.neteinstein.donaclone.core.network.connectivity.ConnectivityObserver
import com.neteinstein.donaclone.core.network.discovery.DiscoveryClient
import com.neteinstein.donaclone.core.network.discovery.UdpDiscoveryClient
import com.neteinstein.donaclone.core.network.socket.DomotalkSocket
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import org.koin.core.qualifier.named
import org.koin.dsl.module
import retrofit2.Retrofit

private const val GITHUB_API_BASE_URL = "https://api.github.com/"
private const val GITHUB_QUALIFIER = "github"

val networkModule =
    module {
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
        single { ConnectivityObserver(get()) }

        // Plain, standard-TLS client for talking to the public internet (GitHub) - deliberately
        // separate from the trust-all client above, which exists only to reach the local hub's
        // commonly self-signed certificate and must never be reused for a real internet endpoint.
        single<OkHttpClient>(named(GITHUB_QUALIFIER)) {
            OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request =
                        chain
                            .request()
                            .newBuilder()
                            .header("Accept", "application/vnd.github+json")
                            .header("User-Agent", "DonaClone-Android")
                            .build()
                    chain.proceed(request)
                }.build()
        }
        single<GitHubApi> {
            Retrofit
                .Builder()
                .baseUrl(GITHUB_API_BASE_URL)
                .client(get(named(GITHUB_QUALIFIER)))
                .addConverterFactory(get<Json>().asConverterFactory("application/json".toMediaType()))
                .build()
                .create(GitHubApi::class.java)
        }
    }
