package dev.mee42.opt

import dev.mee42.parser.*


fun reshuffleExpressions(ast: AST): AST {
//    val inst = Reshuffler(ast)
//    return ast.copy { it.copy(inst.reshuffle(it.content)) }
    TODO()
}

//private class Reshuffler(val ast: AST) {
//    fun reshuffle(block: Block): Block {
//        return Block(block.statements.mapNotNull { it.reshuffle() })
//    }
//
//    private fun Statement.reshuffle(): Statement? = when (this) {
//        is Block -> Block(statements.mapNotNull { it.reshuffle() })
//        is ReturnStatement -> ReturnStatement(expression.reshuffle())
//        NoOpStatement -> null
//        is ExpressionStatement -> {
//            val expr = expression.reshuffle()
//            when {
//                this.needed -> ExpressionStatement(expr, needed = true)
//                expr.isPure() -> null
//                else -> expr.purify()?.let { ExpressionStatement(it) }
//            }
//        }
//        is DeclareVariableStatement -> DeclareVariableStatement(variableName, final, expression.reshuffle())
//        is AssignVariableStatement -> AssignVariableStatement(variableName, expression.reshuffle())
//        is MemoryWriteStatement -> MemoryWriteStatement(location.reshuffle(), value.reshuffle())
//        is IfStatement -> IfStatement(conditional.reshuffle(), reshuffle(block))
//        is WhileStatement -> WhileStatement(conditional.reshuffle(), reshuffle(block))
//    }
//    fun Expression.reshuffle(): Expression { return when (this) {
//        is TypelessBlock -> this // todo optimize?
//        is VariableAccessExpression -> this
//        is BlockExpression -> BlockExpression(this.statements.mapNotNull { it.reshuffle() })
//        is DereferencePointerExpression -> DereferencePointerExpression(this.pointerExpression.reshuffle())
//        is IntegerValueExpression -> this
//        is StringLiteralExpression -> this
//        is ComparisonExpression -> {
//            val x = if(var1 is ComparisonExpression && var1.mathType == MathType.ADD &&
//                    var2 is ComparisonExpression && var2.mathType == MathType.ADD) {
//                val addends = listOf(var1.var1, var1.var2, var2.var1, var2.var2) // all of these need to be added
//                val integerValues = addends.filterIsInstance<IntegerValueExpression>()
//                val other = addends.filter { it !is IntegerValueExpression }
//                // integer values go at the end
//                val integerExpression = integerValues.reduce { a: Expression, b: Expression -> ComparisonExpression(a,b,MathType.ADD) }
//                val otherFolded = when {
//                    other.size == 1 -> other.first()
//                    other.isEmpty() -> null
//                    else -> other.reduce { a, b -> ComparisonExpression(a,b,MathType.ADD) }
//                }
//                if(otherFolded == null) integerExpression
//                else  ComparisonExpression(otherFolded.reshuffle(), integerExpression.reshuffle(), MathType.ADD)
//            } else if(var1.isPure() || var2.isPure()) {
//                if(var1 is ComparisonExpression || var1 is EqualsExpression || var1 is IntegerValueExpression) {
//                    // it goes second
//                    ComparisonExpression(var2.reshuffle(), var1.reshuffle(), mathType)
//                } else null
//            } else null
//            x ?: ComparisonExpression(var1.reshuffle(), var2.reshuffle(), mathType)
//        }
//        is EqualsExpression -> {
//            if(var1.isPure() || var2.isPure()) {
//                // if var1 is an integer value, or contains an integer value somewhere, it should be put second (but only if both are pure)
//                if(var1.isPure()) {
//                    // easy hack to see if a function doesn't call any methods?
//                    // put it second
//                    if(var1 is ComparisonExpression || var1 is EqualsExpression || var1 is IntegerValueExpression){
//                        // it goes second
//                        return EqualsExpression(var2.reshuffle(), var1.reshuffle(), negate)
//                    }
//                }
//            }
//            EqualsExpression(var1.reshuffle(), var2.reshuffle(), negate)
//        }
//        is FunctionCallExpression -> FunctionCallExpression(arguments.map { it.reshuffle() }, argumentNames, functionIdentifier, returnType)
//        is RefExpression -> this
//    } }
//
//    fun Statement.isPure(): Boolean = when (this) {
//        is Block -> statements.all { it.isPure() }
//        is ReturnStatement -> expression.isPure()
//        NoOpStatement -> true
//        is ExpressionStatement -> expression.isPure()
//        is DeclareVariableStatement -> false
//        is AssignVariableStatement -> false
//        is MemoryWriteStatement -> false
//        is IfStatement -> false
//        is WhileStatement -> false
//    }
//
//    fun Expression.isPure(): Boolean = when (this) {
//        is VariableAccessExpression -> false
//        is BlockExpression -> statements.all { it.isPure() }
//        is DereferencePointerExpression -> false
//        is IntegerValueExpression -> true
//        is StringLiteralExpression -> false
//        is ComparisonExpression -> var1.isPure() && var2.isPure()
//        is EqualsExpression -> var1.isPure() && var2.isPure()
//        is FunctionCallExpression -> ast.functions.first { it.identifier == functionIdentifier }.verifiedPure
//                && arguments.all { it.isPure() }
//        is TypelessBlock -> expressions.all { it.isPure() }
//        is RefExpression -> false
//    }
//    /** this should be called when the result value is unimportant, but the things still need to be ran
//     * return value of null means it's not needed and can be safely discarded. All other expressions returned are important
//     */
//    fun Expression.purify(): Expression? {
//        return when (this) {
//            is VariableAccessExpression -> null
//            is BlockExpression -> this // todo optimize
//            is DereferencePointerExpression -> this // todo - this isn't pure, tf
//            is IntegerValueExpression -> null
//            is StringLiteralExpression -> null
//            is ComparisonExpression -> {
//                val v1 = var1.purify()
//                val v2 = var2.purify()
//                when {
//                    v1 == null -> v2
//                    // if v2 == null, great
//                    v2 == null -> v1
//                    else -> TypelessBlock(listOf(v1, v2))
//                }
//            }
//            is EqualsExpression ->  {
//                val v1 = var1.purify()
//                val v2 = var2.purify()
//                when {
//                    v1 == null -> v2
//                    // if v2 == null, great
//                    v2 == null -> v1
//                    else -> TypelessBlock(listOf(v1, v2))
//                }
//            }
//            is FunctionCallExpression -> {
//                val functionIsPure = ast.functions.first { it.identifier == functionIdentifier }.verifiedPure
//                if(functionIsPure) {
//                    // then the function call can be eliminated, ez
//                    val arguments = arguments.mapNotNull { it.purify() }
//                    when {
//                        arguments.isEmpty() -> null
//                        arguments.size == 1 -> arguments[0].reshuffle()
//                        else -> BlockExpression(arguments.map { ExpressionStatement(it) }).reshuffle()
//                    }
//                } else this
//            }
//            is TypelessBlock -> {
//                val exprs = expressions.mapNotNull { it.purify() }
//                if(exprs.isEmpty()) null else TypelessBlock(exprs)
//            }
//            is RefExpression -> TODO()
//        }
//    }
//}
