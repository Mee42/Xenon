package dev.mee42

import dev.mee42.parser.*

private typealias Indent = String
fun Indent.more(): Indent = "$this    "

fun decompileFunction(function: XenonFunction): String {
    var s = ""
    s += function.attributes.joinToString(" ", "", "")
    s += " " + decompile(function.returnType)
    s += " " + function.identifier
    s += function.arguments.joinToString(", ", "(", ")") { decompile(it.type) + " " + it.name }
    s += decompileBlock(function.content, "".more())
    return s
}

fun decompileBlock(block: Block, indent: Indent): String {
    var s = ""
    s += "{\n"
    for(statement in block.statements) {
        s += indent + when(statement) {
            is Block -> decompileBlock(statement, indent.more()) + ";\n"
            is ReturnStatement -> "return " + decompile(statement.expression, indent) + ";\n"
            NoOpStatement -> "//nop\n"
            is ExpressionStatement -> decompile(statement.expression, indent) + ";\n"
            is DeclareVariableStatement -> {
                (if (statement.final) "" else "mut ") +
                        decompile(statement.expression.type) + " " + statement.variableName + " = " + decompile(statement.expression, indent) + ";\n"
            }
            is AssignVariableStatement -> {
                statement.variableName + " = " + decompile(statement.expression, indent) + ";\n"
            }
            is MemoryWriteStatement -> {
                "*(" + decompile(statement.location, indent) + ") = " + decompile(statement.value, indent) + ";\n"
            }
            is IfStatement -> {
                "if " + decompile(statement.conditional, indent) + decompileBlock(statement.block, indent.more()) + ";\n"
            }
            is WhileStatement -> {
                "while " + decompile(statement.conditional, indent) + decompileBlock(statement.block, indent.more()) + "&\n"
            }
        }
    }
    return "$s}\n"
}


fun decompile(type: Type): String {
    if(type is PointerType) return decompile(type.type) + "*"
    if(type is BaseType) return type.type.names.first()
    error("idk")
}

fun decompile(expression: Expression, indent: Indent): String {
    return when(expression) {
        is VariableAccessExpression -> expression.variableName
        is BlockExpression -> decompileBlock(Block(expression.statements), indent.more())
        is DereferencePointerExpression -> "*(" + decompile(expression.pointerExpression, indent) + ")"
        is IntegerValueExpression -> expression.value.toString()
        is StringLiteralExpression -> '"' + expression.value.replace("\n","\\n") + '"'
        is MathExpression -> "(" + decompile(expression.var1, indent) + ") " + expression.mathType.symbol +
                " (" + decompile(expression.var2, indent) + ")"
        is EqualsExpression -> "(" + decompile(expression.var1, indent) + ") " + (if(expression.negate) "!=" else "==") +
                " (" + decompile(expression.var2, indent) + ")"
        is FunctionCallExpression -> expression.functionIdentifier +
                expression.arguments.joinToString(", ", "(",")") { decompile(it, indent) }
        is TypelessBlock -> "t{" + expression.expressions.joinToString(", ", "","") { decompile(it, indent.more()) } + "}"
    }
}
