package functional

import functional.Result.Companion.success
import functional.Result.Failure
import functional.Result.Success

typealias BasicResult<T> = Result<ErrorCode, T>

typealias UnitResult = BasicResult<Unit>

typealias Outcome<E, T> = Result<E, T>

@Suppress("unused")
sealed class Result<out ERR : ErrorCode, out T> {

    data class Failure<out ERR : ErrorCode>(val error: ERR) : Result<ERR, Nothing>()
    data class Success<out T>(val value: T) : Result<Nothing, T>() {
        companion object {
            operator fun invoke(): Result<Nothing, Unit> = Success(Unit)
        }
    }


    companion object {
        fun <ERR : ErrorCode> failure(err: ERR): Result<ERR, Nothing> = Failure(err)

        fun <T> success(value: T): Result<Nothing, T> = Success(value)

        fun <ERR : ErrorCode, T> ERR?.onSuccess(value: T): Result<ERR, T> =
            this?.let { Failure<ERR>(this) } ?: success(value)

        fun <ERR : ErrorCode, T> fromNullable(opt: T?, orElse: () -> ERR): Result<ERR, T> =
            opt?.let { success(it) } ?: Failure(orElse())

        inline fun <ERR : ErrorCode, A, R> apply(fn: (A) -> R, a: Result<ERR, A>): Result<ERR, R> =
            a.flatMap({ aValue: A -> success(fn(aValue)) })

        inline fun <ERR : ErrorCode, A, B, R> apply(
            fn: (A, B) -> R,
            a: Result<ERR, A>,
            b: Result<ERR, B>
        ): Result<ERR, R> =
            a.flatMap({ aValue: A -> b.flatMap({ bValue: B -> success(fn(aValue, bValue)) }) })

        inline fun <ERR : ErrorCode, A, B, C, R> apply(
            fn: (A, B, C) -> R,
            a: Result<ERR, A>,
            b: Result<ERR, B>,
            c: Result<ERR, C>
        ): Result<ERR, R> =
            a.flatMap { aValue: A ->
                b.flatMap { bValue: B ->
                    c.flatMap { cValue ->
                        success(fn(aValue, bValue, cValue))
                    }
                }
            }

        inline fun <ERR : ErrorCode, A, B, C, D, R> apply(
            fn: (A, B, C, D) -> R,
            a: Result<ERR, A>,
            b: Result<ERR, B>,
            c: Result<ERR, C>,
            d: Result<ERR, D>
        ): Result<ERR, R> =
            a.flatMap { aValue: A ->
                b.flatMap { bValue: B ->
                    c.flatMap { cValue: C ->
                        d.flatMap { dValue: D ->
                            success(fn(aValue, bValue, cValue, dValue))
                        }
                    }
                }
            }

    }
}


inline fun <ERR : ErrorCode, T, U> Result<ERR, T>.flatMap(f: (T) -> Result<ERR, U>): Result<ERR, U> =
    when (this) {
        is Failure -> this
        is Success<T> -> f(this.value)
    }

inline fun <ERR : ErrorCode, T, U> Result<ERR, T>.map(f: (T) -> U): Result<ERR, U> =
    when (this) {
        is Failure -> this
        is Success<T> -> Success(f(value))
    }

inline fun <ERR : ErrorCode, T> Result<ERR, T>.flatMapFailure(f: (ERR) -> Result<ERR, T>): Result<ERR, T> =
    when (this) {
        is Failure -> f(error)
        is Success -> this
    }

inline fun <ERRA : ErrorCode, ERRB : ErrorCode, T> Result<ERRA, T>.mapFailure(f: (ERRA) -> ERRB): Result<ERRB, T> =
    when (this) {
        is Failure -> Failure(f(error))
        is Success -> this
    }


inline fun <ERR : ErrorCode, T> Result<ERR, T>.forEach(f: (T) -> Unit) {
    if (this is Success<T>) f(value)
}

inline fun <ERR : ErrorCode, T> Result<ERR, T>.onEach(f: (T) -> Unit): Result<ERR, T> {
    forEach(f)

    return this
}

inline fun <ERR : ErrorCode, T> Result<ERR, T>.forEachFailure(f: (ERR) -> Unit) {
    if (this is Failure<ERR>) f(error)
}

inline fun <ERR : ErrorCode, T> Result<ERR, T>.onEachFailure(f: (ERR) -> Unit): Result<ERR, T> {
    forEachFailure(f)

    return this
}

inline fun <ERR : ErrorCode, T, X> Result<ERR, T>.fold(failure: (ERR) -> X, success: (T) -> X): X =
    map(success).orElse(failure)

inline fun <ERR : ErrorCode, T> Result<ERR, T>.keepIf(
    noinline predicate: (T) -> Boolean,
    orElse: (T) -> ERR
): Result<ERR, T> =
    failIf(predicate.negated(), orElse)

inline fun <ERR : ErrorCode, T> Result<ERR, T>.failIf(
    noinline predicate: (T) -> Boolean,
    withError: (T) -> ERR
): Result<ERR, T> =
    when (this) {
        is Success -> if (predicate(value)) withError(value).asFailure() else this
        is Failure -> this
    }

inline fun <ERR : ErrorCode, T> Result<ERR, T>.orElse(f: (ERR) -> T): T =
    when (this) {
        is Failure -> f(error)
        is Success<T> -> value
    }

fun <T> T.asSuccess(): Result<Nothing, T> = Success(this)

fun <ERR : ErrorCode> ERR.asFailure(): Result<ERR, Nothing> = Failure(this)


inline fun <ERR : ErrorCode, T> T.asSuccessIf(noinline predicate: (T) -> Boolean, orElse: (T) -> ERR): Result<ERR, T> =
    if (predicate(this)) Success(this) else Failure(orElse(this))

inline fun <ERR : ErrorCode, T> T.asSuccessUnless(
    noinline predicate: (T) -> Boolean,
    orElse: (T) -> ERR
): Result<ERR, T> =
    asSuccessIf(predicate.negated(), orElse)

fun <ERR : ErrorCode, T> T?.asResultOr(errorFn: () -> ERR): Result<ERR, T> =
    Result.fromNullable(this, errorFn)

operator fun <ERR : ErrorCode, T> Result<ERR, List<T>>.plus(that: Result<ERR, List<T>>) =
    this.flatMap { e1 -> that.flatMap { e2 -> success(e1 + e2) } }

fun <ERR : ErrorCode, T> Iterable<Result<ERR, T>>.sequence(): Result<ERR, List<T>> =
    fold((emptyList<T>().asSuccess() as Result<ERR, List<T>>),
        { sequencedResults, nextResult ->
            sequencedResults.flatMap { acc: List<T> ->
                nextResult.flatMap { r: T ->
                    success(acc + r)
                }
            }
        }
    )


fun <ERR : ErrorCode> Result<ERR, Nothing>.failure(): ERR? =
    when (this) {
        is Success -> null
        is Failure -> error
    }

fun <T> Result<Nothing, T>.success(): T? =
    when (this) {
        is Success -> value
        is Failure -> null
    }

inline fun <ERR : ErrorCode, T> Result<ERR, T>.onFailure(handler: (Failure<ERR>) -> Nothing): T =
    when (this) {
        is Failure -> handler(this)
        is Success -> value
    }

fun <ERR : ErrorCode, T> Result<ERR, T>.orThrow(): T = onFailure { throw Exception(it.error.toString()) }

fun <ERR : ErrorCode, T, U> Iterable<T>.failFastMap(f: (T) -> Result<ERR, U>): Result<ERR, List<U>> =
    success(map { e -> f(e).onFailure { return it } })

fun <ERR : ErrorCode, T, U> Iterable<T>.failFastMapIndexed(f: (T, Int) -> Result<ERR, U>): Result<ERR, List<U>> =
    success(mapIndexed { index, element -> f(element, index).onFailure { return it } })

fun <ERR : ErrorCode, T, U> Iterable<T>.failFastMapWithRecovery(
    f: (T) -> Result<ERR, U>,
    recover: (T, ERR) -> U?
): Result<ERR, List<U>> {
    val results = mutableListOf<U>()
    this.forEach { t ->
        f(t)
            .onEach { results.add(it) }
            .onEachFailure { err ->
                recover(t, err)?.let(results::add)
                return success(results)
            }
    }
    return success(results)
}

fun <ERR : ErrorCode, T, U> Result<ERR, T>.flatMapTry(f: (T) -> U, onError: (T, Throwable) -> ERR): Result<ERR, U> =
    flatMap {
        try {
            f(it).asSuccess()
        } catch (e: Throwable) {
            onError(it, e).asFailure()
        }
    }

fun <ERR : ErrorCode, T> tryCatchResult(f: () -> T, onError: (Throwable) -> ERR): Result<ERR, T> =
    try {
        Success(f())
    } catch (t: Throwable) {
        Failure(onError(t))
    }


inline fun <ERR : ErrorCode, T> Result<ERR, T>.filter(errF: (T) -> ERR?): Result<ERR, T> =
    flatMap { value -> errF(value)?.let { err -> Failure(err) } ?: Success(value) }

fun <ERR : ErrorCode, T> List<Result<ERR, T>>.mapWithErrorFilter(keep: (ERR) -> Boolean): List<Result<ERR, T>> =
    filter { it.fold(keep, { true }) }
