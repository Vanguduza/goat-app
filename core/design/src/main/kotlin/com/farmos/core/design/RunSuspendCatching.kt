package com.farmos.core.design

import kotlin.coroutines.cancellation.CancellationException

/**
 * Suspend-capable equivalent of stdlib `runCatching`.
 *
 * Stdlib `runCatching { }` takes a non-suspend lambda, so calling a suspend function
 * (DAO query, repository call, `withContext`, …) inside it is a compile error.
 * This helper has the same `Result<T>` semantics and supports the same chains
 * (`.onSuccess`, `.onFailure`, `.getOrNull`, `.getOrElse`, `.fold`, …).
 *
 * Unlike stdlib `runCatching`, [CancellationException] is rethrown, never swallowed.
 */
suspend fun <T> runSuspendCatching(block: suspend () -> T): Result<T> {
    return try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
}
