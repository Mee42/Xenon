package dev.mee42.opt

import dev.mee42.parser.*

fun flattenTypelessBlocks(function: XenonFunction): XenonFunction {
    return function.copy(function.content.flatten())
}
private fun Block.flatten(): Block =Block(statements.flatMap {
    when {
        it is Block -> listOf(it.flatten())
        it is NoOpStatement -> emptyList()
        it is ExpressionStatement && it.expression is TypelessBlock -> it.expression.expressions.map { e -> ExpressionStatement(e) }
        it is IfStatement -> listOf(IfStatement(it.conditional, it.block.flatten()))
        it is WhileStatement -> listOf(WhileStatement(it.conditional, it.block.flatten()))
        else -> listOf(it)
    }
})