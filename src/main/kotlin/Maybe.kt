package dev.mee42

sealed class Maybe<T> {
    class Just<T>(val t: T): Maybe<T>()
    class Nothing<T>: Maybe<T>()

    fun <R> map(f: (T) -> R): Maybe<R> = when(this) {
        is Just -> Just(f(t))
        is Nothing -> Nothing()
    }
}