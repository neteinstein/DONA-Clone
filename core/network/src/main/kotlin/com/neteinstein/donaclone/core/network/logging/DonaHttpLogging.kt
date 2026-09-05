package com.neteinstein.donaclone.core.network.logging

import okhttp3.logging.HttpLoggingInterceptor
import timber.log.Timber

/**
 * An [HttpLoggingInterceptor] that routes its output through Timber instead of `android.util.Log`,
 * so every HTTP request/response this app makes (the GitHub update check/download, and the
 * `domotalk` WebSocket handshake) is subject to the same "Debug Mode" on/off switch as everything
 * else logged with Timber - see `DonaCloneApplication`'s logging tree.
 */
fun donaHttpLoggingInterceptor(): HttpLoggingInterceptor =
    HttpLoggingInterceptor { message -> Timber.tag("OkHttp").d(message) }
        .apply { level = HttpLoggingInterceptor.Level.BODY }
