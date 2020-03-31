package dev.mee42.opt

import dev.mee42.parser.*
import kotlin.math.exp

fun inlineMacros(ast: AST): AST {
    return AST(ast.functions.map { if(it is XenonFunction) inlineMacros(it,ast) else it })
}

private fun inlineMacros(function: XenonFunction, ast: AST): XenonFunction {
    return XenonFunction(
            name = function.name,
            content = inlineMacros(function.content, ast),
            returnType = function.returnType,
            arguments = function.arguments,
            id = function.id
    )
}

private fun inlineMacros(block: Block, ast: AST): Block {
    return Block(block.statements.map { inlineMacros(it, ast) })
}

private fun inlineMacros(statement: Statement, ast: AST): Statement {
    return when(statement) {
        is Block -> inlineMacros(statement, ast)
        is ReturnStatement -> ReturnStatement(inlineMacros(statement.expression, ast))
        NoOpStatement -> NoOpStatement
        is ExpressionStatement -> ExpressionStatement(inlineMacros(statement.expression, ast))
        is DeclareVariableStatement -> DeclareVariableStatement(
                variableName = statement.variableName,
                expression = inlineMacros(statement.expression, ast),
                final = statement.final
        )
        is AssignVariableStatement -> AssignVariableStatement(
                variableName = statement.variableName,
                expression = inlineMacros(statement.expression, ast)
        )
        is MemoryWriteStatement -> MemoryWriteStatement(
                location = inlineMacros(statement.location, ast),
                value = inlineMacros(statement.value, ast)
        )
        is IfStatement -> IfStatement(
                conditional = inlineMacros(statement.conditional, ast),
                block = inlineMacros(statement.block, ast)
        )
        is WhileStatement -> WhileStatement(
                conditional = inlineMacros(statement.conditional, ast),
                block = inlineMacros(statement.block, ast)
        )
    }
}

fun inlineMacros(expression: Expression, ast: AST): Expression {
    val x = when(expression) {
        is VariableAccessExpression, is IntegerValueExpression, is StringLiteralExpression -> expression
        is DereferencePointerExpression -> DereferencePointerExpression(inlineMacros(expression.pointerExpression, ast))
        is MathExpression -> MathExpression(
                var1 = inlineMacros(expression.var1, ast),
                var2 = inlineMacros(expression.var2, ast),
                mathType = expression.mathType
        )
        is EqualsExpression -> EqualsExpression(
                var1 = inlineMacros(expression.var1, ast),
                var2 = inlineMacros(expression.var2, ast),
                negate = expression.negate
        )
        is FunctionCallExpression -> {
            val function = ast.functions.first { it.identifier == expression.functionIdentifier }
            if(function is XenonFunction) {
                val arguments = expression.arguments.map { inlineMacros(it, ast) }
                if(function.attributes.contains("@inline")) {
                    inlineInto(function, arguments)
                } else {
                    FunctionCallExpression(
                            arguments = arguments,
                            returnType = expression.returnType,
                            functionIdentifier = expression.functionIdentifier,
                            argumentNames = expression.argumentNames)
                }
            } else expression
        }
    }
}

private fun markVariableName(name: String, functionIdentifier: String): String {
    return "_${name}_$functionIdentifier"
}

private fun inlineInto(func: XenonFunction, arguments: List<Expression>): BlockExpression {
    // generate variable bindings...
    val variableBindings = arguments.mapIndexed { i, expression ->
        func.arguments[i].name to expression
    }.toMap()
    val functionIdentifier = func.identifier

    val statements = func.content.statements.dropLast(1)
    val last = func.content.statements.last()
    if(last !is ReturnStatement)
        error("inline function ${func.name} is not inlineable because it does not end with a return")
    if(statements.flatMap { it.flatten() }.any { it is ReturnStatement})
        error("inline function ${func.name} can not be inlined because it has a return somewhere other then the last line")
    // there's zero type checking, so screw... everything
    // two step process - first, rename everything
    val renamed = statements.map {

    }
}
private fun rename(mappings: Map<String,String>, statement: Statement): Statement {
    return when(statement){
        is Block -> Block(statement.statements.map { rename(mappings, it) })
        is ReturnStatement -> ReturnStatement(rename(mappings, statement.expression))
        NoOpStatement -> NoOpStatement
        is ExpressionStatement -> ExpressionStatement(rename(mappings, statement.expression))
        is DeclareVariableStatement -> DeclareVariableStatement(statement.variableName, statement.final , rename(mappings, statement.expression))
        is AssignVariableStatement -> AssignVariableStatement(statement.variableName, rename(mappings, statement.expression))
        is MemoryWriteStatement -> MemoryWriteStatement(
                // TODO stopped herec
        )
        is IfStatement -> TODO()
        is WhileStatement -> TODO()
    }
}
private fun rename(mappings: Map<String, String>, expression: Expression): Expression {

}


private fun Statement.flatten(): List<Statement> {
    return when(this){
        is Block -> this.statements.flatMap { it.flatten() }
        is ReturnStatement,
        NoOpStatement,
        is ExpressionStatement,
        is DeclareVariableStatement,
        is AssignVariableStatement,
        is MemoryWriteStatement -> listOf(this)
        is IfStatement -> this.block.flatten()
        is WhileStatement -> this.block.flatten()
    }
}
