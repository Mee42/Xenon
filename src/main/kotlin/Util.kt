package dev.mee42


fun <T> id(t: T): T = t

fun <T> List<T>.splitBy(f: (T) -> Boolean): List<List<T>> {
    val list = mutableListOf<List<T>>()
    var current = mutableListOf<T>()
    for(x in this) {
        if(f(x)) {
            list.add(current)
            current = mutableListOf()
        } else {
            current.add(x)
        }
    }
    if(this.isNotEmpty() && !f(this.last())) list.add(current)

    return list
}
