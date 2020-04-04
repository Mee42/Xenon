package dev.mee42.opt

import dev.mee42.parser.*

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
                if(function.attributes.contains("@macro") || function.attributes.contains("@inline")) {
                    inlineInto(function, arguments, function.attributes.contains("@inline"))
                } else {
                    FunctionCallExpression(
                            arguments = arguments,
                            returnType = expression.returnType,
                            functionIdentifier = expression.functionIdentifier,
                            argumentNames = expression.argumentNames)
                }
            } else expression
        }
        is BlockExpression -> BlockExpression(
                statements = expression.statements.map { inlineMacros(it, ast) }
        )
    }
}

private fun markVariableName(name: String, functionIdentifier: String): String {
//    if(name.startsWith("_")) error("sanity check")
    return "_${name}_$functionIdentifier"
}
private object FunctionLambdaMarkingDelegator {
    private var i = 0
    fun getLabel(): String = (++i).toString()
}

private fun inlineInto(func: XenonFunction, arguments: List<Expression>, properInline: Boolean): Expression {
    // generate variable bindings...
    val functionIdentifier = func.identifier + "_" + FunctionLambdaMarkingDelegator.getLabel()

    val variableBindings = arguments.mapIndexed { i, expression ->
        markVariableName(func.arguments[i].name,functionIdentifier) to expression
    }.toMap()
    val variablesToSave = if(properInline) {
        // then save all the variables
        arguments.mapIndexed { i, it ->
            DeclareVariableStatement(
                    variableName = markVariableName(func.arguments[i].name, functionIdentifier), // these need not renaming
                    expression = it,
                    final = true
            )
        }
    } else emptyList()
    val statements = func.content.statements.dropLast(1)
    val last = func.content.statements.last()
    if(last !is ReturnStatement)
        error("inline function ${func.name} is not inlineable because it does not end with a return")
    if(statements.flatMap { it.flatten() }.any { it is ReturnStatement})
        error("inline function ${func.name} can not be inlined because it has a return somewhere other then the last line")
    val allStatements = statements + last
    // there's zero type checking, so screw... everything
    // two step process - first, rename everything
    val renamed1 = iterateThroughNodes(allStatements, VariableAccessExpression::class.java) {
        VariableAccessExpression(markVariableName(it.variableName, functionIdentifier), it.type)
    }
    val renamed2 = iterateThroughNodes(renamed1, AssignVariableStatement::class.java) {
        AssignVariableStatement(markVariableName(it.variableName, functionIdentifier), it.expression)
    }
    val renamed3 = variablesToSave + iterateThroughNodes(renamed2, DeclareVariableStatement::class.java) {
        DeclareVariableStatement(markVariableName(it.variableName, functionIdentifier), it.final, it.expression)
    }
    val end = if(properInline) {
        renamed3
    } else {
        iterateThroughNodes(renamed3, VariableAccessExpression::class.java) {
            variableBindings[it.variableName] ?: it
        }
    }
    // take the last expression, turn it into a
    val front = end.dropLast(1).filter { it != NoOpStatement }
    val back = (end.last() as ReturnStatement).expression
   // return if(front.isEmpty()) {
     //   back
    //} else {
      return  BlockExpression(front + ExpressionStatement(back))
    //}
}

fun <M> iterateThroughNodes(statements: List<Statement>, c: Class<M>, mapper: (M) -> Any): List<Statement> {
    return statements.map { iterateThroughNodes(it, c, mapper) }
}

fun <M> iterateThroughNodes(statement: Statement, c: Class<M>, mapper: (M) -> Any): Statement {
    return when {
        statement.javaClass == c -> mapper(statement as M) as Statement
        statement is Block -> Block(statement.statements.map { iterateThroughNodes(it,c, mapper) })
        statement is ReturnStatement -> ReturnStatement(iterateThroughNodes(statement.expression,c, mapper))
        statement == NoOpStatement -> NoOpStatement
        statement is ExpressionStatement -> ExpressionStatement(iterateThroughNodes(statement.expression,c, mapper))
        statement is DeclareVariableStatement -> DeclareVariableStatement(statement.variableName, statement.final , iterateThroughNodes(statement.expression,c, mapper))
        statement is AssignVariableStatement -> AssignVariableStatement(statement.variableName, iterateThroughNodes(statement.expression,c, mapper))
        statement is MemoryWriteStatement -> MemoryWriteStatement(
                location = iterateThroughNodes(statement.location,c, mapper),
                value = iterateThroughNodes(statement.location,c, mapper)
        )
        statement is IfStatement -> IfStatement(
                conditional = iterateThroughNodes(statement.conditional,c, mapper),
                block = iterateThroughNodes(statement.block, c,mapper) as Block
        )
        statement is WhileStatement -> WhileStatement(
                conditional = iterateThroughNodes(statement.conditional,c, mapper),
                block = iterateThroughNodes(statement.block,c, mapper) as Block
        )
        else -> error("ohno")
    }
}
fun <M> iterateThroughNodes(expression: Expression, c: Class<M>, mapper: (M) -> Any): Expression {
    return when {
        expression.javaClass == c -> mapper(expression as M) as Expression
        expression is BlockExpression -> BlockExpression(
                statements = expression.statements.map { iterateThroughNodes(it,c,mapper) }
        )
        expression is DereferencePointerExpression -> DereferencePointerExpression(
                pointerExpression = iterateThroughNodes(expression.pointerExpression, c,mapper)
        )
        expression is IntegerValueExpression -> expression
        expression is StringLiteralExpression -> expression
        expression is MathExpression -> MathExpression(
                var1 = iterateThroughNodes(expression.var1,c, mapper),
                var2 = iterateThroughNodes(expression.var2,c, mapper),
                mathType = expression.mathType
        )
        expression is EqualsExpression -> EqualsExpression(
                var1 = iterateThroughNodes(expression.var1, c, mapper),
                var2 = iterateThroughNodes(expression.var2, c, mapper),
                negate = expression.negate
        )
        expression is FunctionCallExpression -> FunctionCallExpression(
                arguments = expression.arguments.map { iterateThroughNodes(it, c, mapper) },
                argumentNames = expression.argumentNames,
                functionIdentifier = expression.functionIdentifier,
                returnType = expression.returnType
        )
        expression is VariableAccessExpression -> VariableAccessExpression(
                variableName = expression.variableName,
                type = expression.type
        )
        else -> error("ohno")
    }
}


private fun Statement.flatten(): List<Statement> {
    return when(this){
        is Block -> this.statements.flatMap { it.flatten() }
        is ReturnStatement,
        is ExpressionStatement,
        is DeclareVariableStatement,
        is AssignVariableStatement,
        is MemoryWriteStatement -> listOf(this)
        NoOpStatement -> emptyList()
        is IfStatement -> this.block.flatten()
        is WhileStatement -> this.block.flatten()
    }
}
