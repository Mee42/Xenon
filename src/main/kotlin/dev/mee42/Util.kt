package dev.mee42

import dev.mee42.parser.ParseException
import java.util.*



fun printTable(col1: List<String>? = null, title1: String? = null,
               col2: List<String>? = null, title2: String? = null,
               col3: List<String>? = null, title3: String? = null,
               col4: List<String>? = null, title4: String? = null) {
    val columns = // just discard the title
            listOf(col1 to title1, col2 to title2, col3 to title3, col4 to title4).mapNotNull { (col, title) ->
                if (title == null && col == null) null
                else if (col == null) null // just discard the title
                else if (title == null) col to ""
                else col to title
            }
    if(columns.isEmpty()) error("can't print an empty table")
    val columnsResized = columns.map { (list, title) ->
        val maxLength = (list + title).maxBy { it.length }?.length ?: error("empty column")
        list.map { it.padEnd(maxLength) } to title.padEnd(maxLength)
    }

    val titleBar = columnsResized.joinToString(" ┃ ", "┃ ", " ┃") { it.second }
    val topBar   = columnsResized.joinToString("━┳━", "┏━", "━┓") { it.second.replace(Regex("""."""), "━") }
    val belowBar = columnsResized.joinToString("━╋━", "┣━", "━┫") { it.second.replace(Regex("""."""), "━") }
    val bottomBar= columnsResized.joinToString("━┻━", "┗━", "━┛") { it.second.replace(Regex("""."""), "━") }
    println(topBar)
    println(titleBar)
    println(belowBar)
    for(row in 0 until (columnsResized.maxBy { it.first.size }?.first?.size ?: error("no"))) {
        print("┃ ")
        for(column in columnsResized) {
            print(column.first[row] + " ┃ ")
        }
        println()
    }
    println(bottomBar + "\n")
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
