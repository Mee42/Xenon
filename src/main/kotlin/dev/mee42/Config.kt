package dev.mee42



object Config {
    enum class OptimizeLevel(val iterations: Int){
        O0(0), O1(1), O2(3), O3(10)
    }
    var optimizationLevel: OptimizeLevel = OptimizeLevel.O2

    private val flags = listOf<Flag>(
//            Flag.PRINT_ASM_ID
//            Flag.PRINT_AST,
//            Flag.DECOMPILE_AST
//            Flag.PRINT_LEXED,
//            Flag.PRINT_PURITY
//            Flag.VERBOSE
    ).flatMap { it.flatten() }.distinct()
    enum class Flag(private val implies: List<Flag> = emptyList()) {
        PRINT_END_TIMING,
        PRINT_COMPILE_TIMINGS,
        PRINT_OPTIMIZATIONS,
        PRINT_ASM_ID,
        PRINT_AST,
        DECOMPILE_AST,
        PRINT_LEXED,
        PRINT_PURITY,
        PRINT_ALL_TIMINGS(listOf(PRINT_END_TIMING, PRINT_COMPILE_TIMINGS, PRINT_OPTIMIZATIONS)),
        VERBOSE(listOf(PRINT_END_TIMING, PRINT_COMPILE_TIMINGS, PRINT_OPTIMIZATIONS, PRINT_ASM_ID, PRINT_AST, DECOMPILE_AST, PRINT_LEXED, PRINT_PURITY))
        ;
        fun flatten(): List<Flag> {
            return (listOf(this) + this.implies.flatMap { it.flatten() }).distinct()
        }
    }
    fun isPicked(flag: Flag): Boolean {
        return flags.contains(flag)
    }
}

