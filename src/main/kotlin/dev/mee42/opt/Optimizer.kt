package dev.mee42.opt

import dev.mee42.parser.*


fun emitWarning(str: String) {
    System.err.println("WARNING: $str")
}

private fun AST.forAllXenonFunctions(mapper: (XenonFunction) -> XenonFunction): AST {
    return AST(functions.map {
        if(it is XenonFunction) mapper(it) else it
    })
}

fun optimize(ast: AST): AST {
    val map = ::eliminateDeadCode
            .ap(::inlineMacros)
            .ap(::reshuffleExpressions)
            .ap { it.forAllXenonFunctions(::staticValuePropagator.ap(::flattenTypelessBlocks)) }
    return map(map(map(map(ast))))
}

private infix fun <A,B,C> ((A) -> B).ap(m: ((B) -> C)): (A) -> C {
    return { a: A -> m(this(a)) }
}