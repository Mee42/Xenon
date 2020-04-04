package dev.mee42



object Config {
    var optimize: Boolean = true

    private val flags = listOf<Flag>(
//            Flag.PRINT_ASM_ID
//            Flag.PRINT_AST,
            Flag.DECOMPILE_AST
//            Flag.PRINT_LEXED
    ).flatMap { it.flatten() }.distinct()
    enum class Flag(private val implies: List<Flag> = emptyList()) {
        PRINT_END_TIMING,
        PRINT_COMPILE_TIMINGS,
        PRINT_OPTIMIZATIONS,
        PRINT_ASM_ID,
        PRINT_AST,
        DECOMPILE_AST,
        PRINT_LEXED,
        PRINT_ALL_TIMINGS(listOf(PRINT_END_TIMING, PRINT_COMPILE_TIMINGS, PRINT_OPTIMIZATIONS, PRINT_ASM_ID)),
        ;
        fun flatten(): List<Flag> {
            return (listOf(this) + this.implies.flatMap { it.flatten() }).distinct()
        }
    }
    fun isPicked(flag: Flag): Boolean {
        return flags.contains(flag)
    }
}

