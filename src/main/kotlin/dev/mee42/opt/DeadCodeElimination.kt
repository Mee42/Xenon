package dev.mee42.opt

import dev.mee42.parser.*

fun variablesUsed(expression: Expression): List<String> = when(expression) {
    is VariableAccessExpression -> listOf(expression.variableName)
    is DereferencePointerExpression -> variablesUsed(expression.pointerExpression)
    is IntegerValueExpression -> emptyList()
    is StringLiteralExpression -> emptyList()
    is MathExpression -> (variablesUsed(expression.var1) + variablesUsed(expression.var2)).distinct()
    is EqualsExpression -> (variablesUsed(expression.var1) + variablesUsed(expression.var2)).distinct()
    is FunctionCallExpression -> expression.arguments.flatMap(::variablesUsed).distinct()
}

fun variablesUsed(statement: Statement): List<String> = when(statement) {
    is NoOpStatement -> emptyList()
    is Block -> statement.statements.flatMap(::variablesUsed).distinct()
    is ReturnStatement -> variablesUsed(statement.expression)
    is ExpressionStatement -> variablesUsed(statement.expression)
    is DeclareVariableStatement -> variablesUsed(statement.expression)
    is AssignVariableStatement -> variablesUsed(statement.expression) + statement.variableName
    is MemoryWriteStatement -> (variablesUsed(statement.location) + variablesUsed(statement.value)).distinct()
    is IfStatement -> (variablesUsed(statement.conditional) + variablesUsed(statement.block)).distinct()
    is WhileStatement -> (variablesUsed(statement.conditional) + variablesUsed(statement.block)).distinct()
}

fun eliminateDeadCode(function: XenonFunction): XenonFunction {
    // replaces variable definitions with the expression they represent
    // too bad we can't eliminate all function arguments...
    return XenonFunction(
            name = function.name,
            id = function.id,
            arguments = function.arguments,
            returnType = function.returnType,
            content = Block(optimizeBlock(function.content.statements))
    )
}

fun optimizeBlock(statements: List<Statement>): List<Statement> {
    if(statements.isEmpty()) return emptyList()
    val first = statements.first()
    val rest = statements.drop(1)
    val real = if(first is DeclareVariableStatement) {
        if (rest.isEmpty() || !rest.flatMap { variablesUsed(it) }.distinct().contains(first.variableName)) {
            ExpressionStatement(first.expression)
        } else {
            first
        }
    } else if(first is ExpressionStatement && first.expression is IntegerValueExpression) {
        NoOpStatement
    } else first
    val new = listOf(real) + optimizeBlock(rest)
    if(new == statements) return new
    return optimizeBlock(new)
}
