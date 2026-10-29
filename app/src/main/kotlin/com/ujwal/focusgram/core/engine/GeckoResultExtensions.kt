package com.ujwal.focusgram.core.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Adapts GeckoView's callback-based [GeckoResult] to a cancellable coroutine.
 *
 * If the engine fails with a null exception, wraps it in [RuntimeException]
 * because [suspendCancellableCoroutine.resumeWithException] requires a non-null Throwable.
 */
suspend fun <T> GeckoResult<T>.await(): T? = suspendCancellableCoroutine { continuation ->
    accept(
        { value ->
            if (continuation.isActive) {
                continuation.resume(value)
            }
        },
        { exception ->
            if (continuation.isActive) {
                continuation.resumeWithException(
                    exception ?: RuntimeException("GeckoResult terminated with null exception")
                )
            }
        }
    )
}
