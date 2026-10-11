package com.ujwal.focusgram.core.engine

import kotlinx.coroutines.suspendCancellableCoroutine
import org.mozilla.geckoview.GeckoResult
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Await extension for GeckoResult.
 * Bridges Mozilla's reactive GeckoResult into Kotlin Coroutines seamlessly.
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
