package com.neteinstein.donaclone.core.common

/**
 * Generic outcome wrapper used across the domain/data layers so use cases never leak
 * transport-specific exceptions (OkHttp, Room, etc.) into the presentation layer.
 */
sealed interface DonaResult<out T> {
    data class Success<T>(
        val data: T,
    ) : DonaResult<T>

    data class Error(
        val failure: DonaFailure,
    ) : DonaResult<Nothing>
}

sealed interface DonaFailure {
    val message: String?
    val cause: Throwable?

    /** The DPU could not be reached at all (host unreachable, timeout, DNS failure). */
    data class Unreachable(
        override val message: String?,
        override val cause: Throwable? = null,
    ) : DonaFailure

    /** The DPU responded but rejected the credentials. */
    data class InvalidCredentials(
        override val message: String? = null,
        override val cause: Throwable? = null,
    ) : DonaFailure

    /** The DPU responded with an unexpected payload/status that we could not parse. */
    data class UnexpectedResponse(
        override val message: String?,
        override val cause: Throwable? = null,
    ) : DonaFailure

    /** No house/session configured yet. */
    data class NotAuthenticated(
        override val message: String? = "Not logged in",
    ) : DonaFailure {
        override val cause: Throwable? = null
    }

    data class Unknown(
        override val message: String?,
        override val cause: Throwable? = null,
    ) : DonaFailure
}

inline fun <T, R> DonaResult<T>.map(transform: (T) -> R): DonaResult<R> =
    when (this) {
        is DonaResult.Success -> DonaResult.Success(transform(data))
        is DonaResult.Error -> this
    }

inline fun <T> DonaResult<T>.onSuccess(action: (T) -> Unit): DonaResult<T> {
    if (this is DonaResult.Success) action(data)
    return this
}

inline fun <T> DonaResult<T>.onError(action: (DonaFailure) -> Unit): DonaResult<T> {
    if (this is DonaResult.Error) action(failure)
    return this
}

fun <T> T.asSuccess(): DonaResult<T> = DonaResult.Success(this)

fun DonaFailure.asError(): DonaResult<Nothing> = DonaResult.Error(this)
