package dev.mee42.opt

import dev.mee42.parser.*
import kotlin.coroutines.coroutineContext
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
            id = function.id,
            attributes = function.attributes)
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
    return when(expression) {
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
                    println("inlining fgunction")

                    FunctionCallExpression(
                            arguments = arguments,
                            returnType = expression.returnType,
                            functionIdentifier = expression.functionIdentifier,
                            argumentNames = expression.argumentNames)
                }
            } else expression
        }
        is BlockExpression -> BlockExpression(
                statements = expression.statements.map { inlineMacros(it, ast) },
                last = inlineMacros(expression.last, ast)
        )
    }
}

private fun markVariableName(name: String, functionIdentifier: String): String {
    return "_${name}_$functionIdentifier"
}

private fun inlineInto(func: XenonFunction, arguments: List<Expression>): BlockExpression {
    // generate variable bindings...
    val functionIdentifier = func.identifier

    val variableBindings = arguments.mapIndexed { i, expression ->
        markVariableName(func.arguments[i].name,functionIdentifier) to expression
    }.toMap()

    val statements = func.content.statements.dropLast(1)
    val last = func.content.statements.last()
    if(last !is ReturnStatement)
        error("inline function ${func.name} is not inlineable because it does not end with a return")
    if(statements.flatMap { it.flatten() }.any { it is ReturnStatement})
        error("inline function ${func.name} can not be inlined because it has a return somewhere other then the last line")
    // there's zero type checking, so screw... everything
    // two step process - first, rename everything
    val niceBlock = BlockExpression(statements, last.expression)
    val renamed = replaceVariableDefinitions(niceBlock) {
        VariableAccessExpression(markVariableName(it.variableName, functionIdentifier), it.type)
    }
    val inlined = replaceVariableDefinitions(renamed) {
        variableBindings[it.variableName] ?: it
    }
    return inlined as BlockExpression
}

fun <B: Expression> replaceVariableDefinitions(statement: Statement, mapper: (VariableAccessExpression) -> B): Statement {
    return when(statement){
        is Block -> Block(statement.statements.map { replaceVariableDefinitions(it, mapper) })
        is ReturnStatement -> ReturnStatement(replaceVariableDefinitions(statement.expression, mapper))
        NoOpStatement -> NoOpStatement
        is ExpressionStatement -> ExpressionStatement(replaceVariableDefinitions(statement.expression, mapper))
        is DeclareVariableStatement -> DeclareVariableStatement(statement.variableName, statement.final , replaceVariableDefinitions(statement.expression, mapper))
        is AssignVariableStatement -> AssignVariableStatement(statement.variableName, replaceVariableDefinitions(statement.expression, mapper))
        is MemoryWriteStatement -> MemoryWriteStatement(
                location = replaceVariableDefinitions(statement.location, mapper),
                value = replaceVariableDefinitions(statement.location, mapper)
        )
        is IfStatement -> IfStatement(
                conditional = replaceVariableDefinitions(statement.conditional, mapper),
                block = replaceVariableDefinitions(statement.block, mapper) as Block
        )
        is WhileStatement -> WhileStatement(
                conditional = replaceVariableDefinitions(statement.conditional, mapper),
                block = replaceVariableDefinitions(statement.block, mapper) as Block
        )
    }
}
fun <B: Expression> replaceVariableDefinitions(expression: Expression, mapper: (VariableAccessExpression) -> B): Expression {
    return when(expression) {
        is VariableAccessExpression -> mapper(expression)
        is BlockExpression -> BlockExpression(
                statements = expression.statements.map { replaceVariableDefinitions(it,mapper) },
                last = replaceVariableDefinitions(expression.last,mapper)
        )
        is DereferencePointerExpression -> DereferencePointerExpression(
                pointerExpression = replaceVariableDefinitions(expression.pointerExpression,mapper)
        )
        is IntegerValueExpression -> expression
        is StringLiteralExpression -> expression
        is MathExpression -> MathExpression(
                var1 = replaceVariableDefinitions(expression.var1,mapper),
                var2 = replaceVariableDefinitions(expression.var2,mapper),
                mathType = expression.mathType
        )
        is EqualsExpression -> EqualsExpression(
                var1 = replaceVariableDefinitions(expression.var1,mapper),
                var2 = replaceVariableDefinitions(expression.var2,mapper),
                negate = expression.negate
        )
        is FunctionCallExpression -> FunctionCallExpression(
                arguments = expression.arguments.map { replaceVariableDefinitions(it,mapper) },
                argumentNames = expression.argumentNames,
                functionIdentifier = expression.functionIdentifier,
                returnType = expression.returnType
        )
    }
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
