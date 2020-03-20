package dev.mee42

import dev.mee42.parser.ParseException
import java.util.*


class ConsumableQueue<T>(creator: Collection<T>) {
    private val queue = ArrayDeque(creator)
    private fun isEmpty() = queue.isEmpty()
    fun peek(): T? = if(isEmpty()) null else queue.first
    fun isNotEmpty() = queue.isNotEmpty()
    fun remove(): T = if(isEmpty()) throw ParseException("reached end of file while parsing") else queue.remove()
    fun removeWhile(condition: (T) -> Boolean): List<T> {
        val list = mutableListOf<T>()
        while(true){
            if(queue.isEmpty()) throw ParseException("reached end of file while parsing")
            if(condition(queue.peek())){
                list.add(queue.remove())
            } else {
                return list
            }
        }
    }
    fun shove(elem: T) {
        queue.addFirst(elem)
    }
}

/**
 * When `includeSplitter` is false, the returned lists has no elements where `elem:splitAt(elem) == true`
 * When it's true, the returned list has those elements as the *first* element in each list, except the first list
 */
fun <T> List<T>.splitBy(includeSplitter: Boolean, splitAt: (T) -> Boolean): List<List<T>> {
    val list = mutableListOf<List<T>>()
    var i = 0
    var lastSplitAt = 0
    while(i < size) {
        if(splitAt(get(i))) {
            // if we need to split
            if(includeSplitter && lastSplitAt != 0){
                lastSplitAt-- // include the last split element in the thing
            }
            list.add(subList(lastSplitAt, i))
            lastSplitAt = i
        }
        i++
    }
    if(includeSplitter && lastSplitAt != 0){
        lastSplitAt-- // include the last split element in the thing
    }
    list.add(subList(lastSplitAt, size))
    return list
}

sealed class Either<A,B> {
    class Left<A>(val a: A):  Either<A, Nothing>()
    class Right<B>(val b: B): Either<Nothing, B>()
}