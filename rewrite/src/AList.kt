package dev.mee42

typealias AList<A, B> = List<Pair<A, B>>

fun <A,B> AList<A, B>.searchA(a: A): B? {
    return this.firstOrNull { it.first == a }?.second
}
fun <A,B> AList<A,B>.searchB(b: B): A? {
    return this.firstOrNull { it.second == b }?.first
}
