package com.neteinstein.donaclone.core.data.mapper

import com.neteinstein.donaclone.core.common.DonaFailure
import com.neteinstein.donaclone.core.common.DonaResult
import com.neteinstein.donaclone.core.network.socket.DomotalkException
import kotlinx.coroutines.CancellationException

fun Throwable.toDonaFailure(): DonaFailure =
    when (this) {
        is DomotalkException.NotConnected -> DonaFailure.NotAuthenticated()
        is DomotalkException.RequestTimeout, is DomotalkException.ConnectionLost ->
            DonaFailure.Unreachable(message, this)
        is DomotalkException.MalformedResponse -> DonaFailure.UnexpectedResponse(message, this)
        else -> DonaFailure.Unknown(message, this)
    }

/**
 * Runs [block], wrapping its outcome as a [DonaResult]. Unlike [kotlin.runCatching], this lets
 * [CancellationException] propagate instead of turning a cancelled coroutine into an error
 * result — swallowing cancellation there would break structured concurrency (e.g. a ViewModel
 * scope being cleared mid-request would surface as a spurious "Unknown" failure instead of
 * simply cancelling).
 */
suspend fun <T> donaResultCatching(block: suspend () -> T): DonaResult<T> =
    try {
        DonaResult.Success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        DonaResult.Error(e.toDonaFailure())
    }
