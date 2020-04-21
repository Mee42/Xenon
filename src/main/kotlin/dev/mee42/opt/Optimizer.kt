package dev.mee42.opt

import dev.mee42.arg.Optimization
import dev.mee42.arg.globalConfig
import dev.mee42.parser.*
import java.nio.file.OpenOption


fun emitWarning(str: String) {
    System.err.println("WARNING: $str")
}


fun optimize(ast: AST, iterations: Int): AST {
    return ast // TODO
//    val optimizations: Map<Optimization, (AST) -> AST> = mapOf(
//            Optimization.DEAD_CODE to { it: AST -> eliminateDeadCode(it) },
//            Optimization.FLATTEN_TYPELESS_BLOCK to { it: AST -> it.copy(::flattenTypelessBlocks) },
//            Optimization.INLINE_MACROS to { it: AST -> inlineMacros(it) },
//            Optimization.RESHUFFLE to { it: AST -> reshuffleExpressions(it) },
//            Optimization.VALUE_PROPAGATOR to { it: AST -> it.copy(::staticValuePropagator)}
//    )
//    val map = optimizations.filterKeys { globalConfig.optimizationsEnabled.contains(it) }.values
//    val everything = map.fold({ it: AST -> it }) { acc, value -> acc.ap(value) }
//    return applyN(iterations, everything, ast)
}

private infix fun <A,B,C> ((A) -> B).ap(m: ((B) -> C)): (A) -> C {
    return { a: A -> m(this(a)) }
}