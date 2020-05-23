package dev.mee42.asm

object StringInterner {
    private val map = mutableMapOf<String, String>()
    private const val prefix = "str_n"
    private var i: Int = 0
    fun labelString(stringLiteral: String): Pair<String, DataEntry?> {
        return when(val f = map[stringLiteral]) {
            null -> {
                val label = prefix + (++i)
                map[stringLiteral] = label
                label to DataEntry(
                        name = label,
                        data = stringLiteral.map { it.toByte().toInt() } + listOf(0) // strings are zero-terminated
                )
            }
            else -> f to null
        }
    }
}