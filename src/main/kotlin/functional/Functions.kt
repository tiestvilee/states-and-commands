package functional

fun <T> ((T) -> Boolean).negated(): (T) -> Boolean = { v -> !this(v) }

infix fun <A, B, C> ((A) -> B).then(f: (B) -> C): (A) -> C = fun(x: A) = f(this(x))

inline fun <T> T.letIf(b: Boolean, fn: (T) -> T) = if (b) fn(this) else this

inline fun <T> T.letIf(p: (T) -> Boolean, fn: (T) -> T) = if (p(this)) fn(this) else this

inline fun <T : Any> ifTrue(b: Boolean, f: () -> T): T? = if (b) f() else null
