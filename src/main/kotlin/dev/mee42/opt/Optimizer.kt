package dev.mee42.opt

import dev.mee42.parser.*


fun emitWarning(str: String) {
    System.err.println("WARNING: $str")
}

fun optimize(ast: AST): AST {
    val ast2 = AST(ast.functions.map {
        if(it is XenonFunction) optimizeFunction(it) else it
    })
    return inlineMacros(ast2)
//    return AST(ast3.functions.map {
//        if (it is XenonFunction) optimizeFunction(it) else it
//    })
}

private fun optimizeFunction(func: XenonFunction): XenonFunction {
    val apply = listOf<(XenonFunction) -> XenonFunction>(
            ::staticValuePropagator,
            ::eliminateDeadCode
    )
    return apply.fold(func) { a, applicator -> applicator(a) }
}