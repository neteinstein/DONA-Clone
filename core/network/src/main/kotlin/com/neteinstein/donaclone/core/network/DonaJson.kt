package com.neteinstein.donaclone.core.network

import kotlinx.serialization.json.Json

/**
 * The one `Json` every domotalk request and response goes through. Lives here rather than inline
 * in `networkModule` so its encoding rules — which the hub's full-object `update` verbs depend on —
 * can be pinned down by a test.
 */
fun donaJson(): Json =
    Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        // The hub's `update` verbs replace the whole object, so a request has to carry every
        // field — and kotlinx's default (`false`) silently drops any property that happens to
        // equal its declared default. That made `update user` omit `remoteAccessible`/`enabled`
        // whenever they were being set to `true` (the DTO's default), so enabling a disabled user,
        // or granting remote access, went out as a no-op and looked like Save doing nothing.
        // `explicitNulls = false` still omits nulls, which is what the create-without-an-`id`
        // convention for triggers/conditions/actions relies on.
        encodeDefaults = true
    }
