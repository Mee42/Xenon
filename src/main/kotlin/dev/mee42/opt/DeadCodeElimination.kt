package dev.mee42.opt

import dev.mee42.parser.*


fun eliminateDeadCode(ast: AST): AST {
    val inst = DeadCodeElimination(ast)
    return AST(ast.functions.map {
        if(it is XenonFunction) {
            XenonFunction(
                    name = it.name,
                    id = it.id,
                    arguments = it.arguments,
                    returnType = it.returnType,
                    content = inst.optimizeBlock(it.content),
                    attributes = it.attributes
            )
        } else it
    })
}
fun reshuffleExpressions(ast: AST): AST {
    val inst = ReshuffleExpressions(ast)
    return AST(ast.functions.map {
        if(it is XenonFunction) {
            XenonFunction(
                    name = it.name,
                    id = it.id,
                    arguments = it.arguments,
                    returnType = it.returnType,
                    content = inst.reshuffle(it.content),
                    attributes = it.attributes
            )
        } else it
    })
}

class ReshuffleExpressions(val ast: AST) {
    fun Expression.reshuffle(): Expression = when(this) {

    fun Statement.reshuffle(): List<Statement> = when(this) {
        is Block -> TODO()
        is ReturnStatement -> TODO()
        NoOpStatement -> TODO()
        is ExpressionStatement -> TODO()
        is DeclareVariableStatement -> TODO()
        is AssignVariableStatement -> TODO()
        is MemoryWriteStatement -> TODO()
        is IfStatement -> TODO()
        is WhileStatement -> TODO()
    }
}

class PurityChecker(private val ast: AST) {
    fun Statement.isPure(): Boolean = when(this){
        is Block -> statements.all { it.isPure() }
        is ReturnStatement -> expression.isPure()
        NoOpStatement -> true
        is ExpressionStatement -> expression.isPure()
        is DeclareVariableStatement -> false
        is AssignVariableStatement -> false
        is MemoryWriteStatement -> false
        is IfStatement -> false
        is WhileStatement -> false
    }
    fun Expression.isPure(): Boolean = when(this) {
        is VariableAccessExpression -> false
        is BlockExpression -> statements.all { it.isPure() }
        is DereferencePointerExpression -> false
        is IntegerValueExpression -> true
        is StringLiteralExpression -> false
        is MathExpression -> var1.isPure() && var2.isPure()
        is EqualsExpression -> var1.isPure() && var2.isPure()
        is FunctionCallExpression -> ast.functions.first { it.identifier == functionIdentifier }.attributes.contains("pure")
                && arguments.all { it.isPure() }
    }
}

class DeadCodeElimination(private val ast: AST) {
    private fun variablesUsed(expression: Expression): List<String> = when(expression) {
        is VariableAccessExpression -> listOf(expression.variableName)
        is DereferencePointerExpression -> variablesUsed(expression.pointerExpression)
        is IntegerValueExpression -> emptyList()
        is StringLiteralExpression -> emptyList()
        is MathExpression -> (variablesUsed(expression.var1) + variablesUsed(expression.var2))
        is EqualsExpression -> (variablesUsed(expression.var1) + variablesUsed(expression.var2))
        is FunctionCallExpression -> expression.arguments.flatMap(::variablesUsed)
        is BlockExpression -> expression.statements.flatMap { variablesUsed(it) }
    }

    private fun variablesUsed(statement: Statement): List<String> = when(statement) {
        is NoOpStatement -> emptyList()
        is Block -> statement.statements.flatMap(::variablesUsed)
        is ReturnStatement -> variablesUsed(statement.expression)
        is ExpressionStatement -> variablesUsed(statement.expression)
        is DeclareVariableStatement -> variablesUsed(statement.expression)
        is AssignVariableStatement -> variablesUsed(statement.expression) + statement.variableName
        is MemoryWriteStatement -> (variablesUsed(statement.location) + variablesUsed(statement.value))
        is IfStatement -> (variablesUsed(statement.conditional) + variablesUsed(statement.block))
        is WhileStatement -> (variablesUsed(statement.conditional) + variablesUsed(statement.block))
    }

    private fun Expression.optimize(): Expression {
        return when(this) {
            is VariableAccessExpression -> {
                VariableAccessExpression(variableName, type)
            }
            is BlockExpression -> {
                val optimized = optimizeBlock(statements)
                if(optimized.size == 1) optimized.first()
                BlockExpression(optimized)
            }
            is DereferencePointerExpression -> DereferencePointerExpression(pointerExpression.optimize())
            is IntegerValueExpression -> this
            is StringLiteralExpression -> this
            is MathExpression -> MathExpression(var1.optimize(), var2.optimize(), mathType)
            is EqualsExpression -> EqualsExpression(var1.optimize(), var2.optimize(), negate)
            is FunctionCallExpression -> FunctionCallExpression(arguments.map { it.optimize() }, argumentNames, functionIdentifier, returnType)
        }
    }
    fun optimizeBlock(b: Block): Block = b.optimize() as Block
    private fun Statement.optimize(): Statement {
        return when(this) {
            is Block -> Block(optimizeBlock(statements))
            is ReturnStatement -> ReturnStatement(expression.optimize())
            NoOpStatement -> NoOpStatement
            is ExpressionStatement -> ExpressionStatement(expression.optimize())
            is DeclareVariableStatement -> DeclareVariableStatement(variableName, final, expression.optimize())
            is AssignVariableStatement -> AssignVariableStatement(variableName,expression.optimize())
            is MemoryWriteStatement -> MemoryWriteStatement(location.optimize(), value.optimize())
            is IfStatement -> {
                val optimizedIf = conditional.optimize()
                val optimizedBlock = block.optimize() as Block
                if(optimizedBlock.statements.isEmpty() || optimizedBlock.statements.all {it == NoOpStatement }) {
                    ExpressionStatement(optimizedIf)
                } else IfStatement(optimizedIf, optimizedBlock)
            }
            is WhileStatement -> WhileStatement(conditional.optimize(), block.optimize() as Block)
        }
    }
    private fun Expression.inlineImpure(variable: Expression, name: String): Expression = when(this){
        // this expression (should) contain the variable named $name
        // let's pattern match and
        is VariableAccessExpression -> if(this.variableName == name) variable else this
        is BlockExpression -> this // TODO improve?
        is DereferencePointerExpression -> DereferencePointerExpression(pointerExpression.inlineImpure(variable, name))
        is IntegerValueExpression -> this
        is StringLiteralExpression -> this
        is MathExpression -> {
            MathExpression(var1.inlineImpure(variable, name), var2.inlineImpure(variable, name), mathType)
        }
        is EqualsExpression -> {
            EqualsExpression(var1.inlineImpure(variable, name), var2.inlineImpure(variable, name), negate)
        }
        is FunctionCallExpression -> {
            FunctionCallExpression(
                    arguments.map { it.inlineImpure(variable, name) },
                    argumentNames,functionIdentifier, returnType
            )
        }
    }


    private fun optimizeBlock(statements: List<Statement>): List<Statement> {
        if (statements.isEmpty()) return emptyList()
        val first = statements.first()
        val rest = statements.drop(1)
        val real = if (first is DeclareVariableStatement) {
            if (rest.isEmpty() || !rest.flatMap { variablesUsed(it) }.distinct().contains(first.variableName)) {
                ExpressionStatement(first.expression)
            } else {
                first
            }
        } else first
        val realList = listOf(real)
        val new = realList + optimizeBlock(rest)
        val newer = new.map { it.optimize() }
        return variableMerging(newer.filter { it != NoOpStatement })
        //return optimizeBlock(newer)
    }

    private fun variableMerging(statements: List<Statement>): List<Statement> {
        if(statements.size < 2) return statements
        val (first, second) = statements
        if(first is DeclareVariableStatement){
            if(!statements.drop(2).flatMap { variablesUsed(it) }.contains(first.variableName)){
                // so it's only used in `second`, no where else
                // how is it used in `second`?
                if(variablesUsed(second).count { it == first.variableName} == 1) {
                    // inline it, it's only used
                    val newSecond = forAllExpressions(second) { it.inlineImpure(
                            first.expression, first.variableName
                    ) }
                    return listOf(newSecond) + statements.drop(2)
                }
            }
        }

        return listOf(first) + variableMerging(statements.drop(1))
    }

}


fun Statement.forAllExpressions(mapper: (Expression) -> Expression): Statement = when(this) {
    is Block -> Block(statements.map { it.forAllExpressions(mapper) })
    is ReturnStatement -> ReturnStatement(mapper(expression))
    NoOpStatement -> NoOpStatement
    is ExpressionStatement -> ExpressionStatement(mapper(expression))
    is DeclareVariableStatement -> DeclareVariableStatement(variableName, final, mapper(expression))
    is AssignVariableStatement -> AssignVariableStatement(variableName, mapper(expression))
    is MemoryWriteStatement -> MemoryWriteStatement(mapper(location), mapper(value))
    is IfStatement -> IfStatement(mapper(conditional), block.forAllExpressions(mapper) as Block)
    is WhileStatement -> WhileStatement(mapper(conditional), block.forAllExpressions(mapper) as Block)
}