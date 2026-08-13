package com.lumocraft.app.data.network

import kotlinx.coroutines.CancellationException

/**
 * Cancellation must propagate; it must never be swallowed by [runCatching]
 * or converted into a failure [Result].
 */
internal inline fun <T> Result<T>.rethrowCancellation(): Result<T> {
    exceptionOrNull()?.let { error ->
        if (error is CancellationException) throw error
    }
    return this
}
