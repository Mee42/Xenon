package dev.mee42

import dev.mee42.parser.*

private typealias Indent = String
fun Indent.more(): Indent = "$this    "

fun AST.decompile(name: String = "ast"): String {
    return "---- $name ---- \n" + this.structs.joinToString("\n", "", "", transform = ::decomp) +
            "\n\n" +
            functions.joinToString("", "", "") {
                if (it is XenonFunction) decompileFunction(it) + "\n" else ""
            } + "\n---- end $name ----"
}

fun decomp(it: Struct): String {
    return "struct ${it.name} " +
            it.fields.joinToString("\n","{\n","\n}") { "    " + decompile(it.type) + " " + it.name + ";" } +
            " (" + it.size.bytes + " bytes)"
}

private fun decompileFunction(function: XenonFunction): String {
    var s = ""
    s += function.attributes.joinToString(" ", "", "")
    s += " " + decompile(function.returnType)
    s += " " + function.identifier
    s += function.arguments.joinToString(", ", "(", ")") { decompile(it.type) + " " + it.name }
    s += decompileBlock(function.content, "".more(), "")
    return s
}

fun decompileBlock(block: Block, indent: Indent, lastIndent: Indent): String {
    var s = ""
    s += "{\n"
    for(statement in block.statements) {
        s += indent + when(statement) {
            is Block -> decompileBlock(statement, indent.more(), indent) + ";\n"
            is ReturnStatement -> "return " + decompile(statement.expression, indent) + ";\n"
            NoOpStatement -> "//nop\n"
            is ExpressionStatement -> decompile(statement.expression, indent) + ";\n"
            is DeclareVariableStatement -> {
                (if (statement.final) "" else "mut ") +
                        decompile(statement.expression.type) + " " + statement.variableName + " = " + decompile(statement.expression, indent) + ";\n"
            }
            is IfStatement -> {
                "if " + decompile(statement.conditional, indent) + " " + decompileBlock(statement.block, indent.more(), indent) + ";\n"
            }
            is WhileStatement -> {
                "while " + decompile(statement.conditional, indent) + " " + decompileBlock(statement.block, indent.more(), indent) + "\n"
            }
        }
    }
    return "$s$lastIndent}"
}


fun decompile(type: Type): String {
    return when (type) {
        is PointerType -> decompile(type.type) + "*"
        is BaseType -> type.type.names.first()
        is StructType -> type.struct.name
    }
}

fun decompile(expression: Expression, indent: Indent): String {
    return when(expression) {
        is VariableAccessExpression -> expression.variableName
        is BlockExpression -> decompileBlock(Block(expression.statements), indent.more(), indent)
        is DereferencePointerExpression -> "*(" + decompile(expression.pointerExpression, indent) + ")"
        is IntegerValueExpression -> expression.value.toString()
        is StringLiteralExpression -> '"' + expression.value.replace("\n","\\n") + '"'
        is ComparisonExpression -> "(" + decompile(expression.var1, indent) + ") " + expression.mathType.symbol +
                " (" + decompile(expression.var2, indent) + ")"
        is FunctionCallExpression -> expression.functionIdentifier +
                expression.arguments.joinToString(", ", "(",")") { decompile(it, indent) }
        is TypelessBlock -> "t{" + expression.expressions.joinToString(", ", "","") { decompile(it, indent.more()) } + "}"
        is RefExpression -> "&${decompile(expression.lvalue, indent.more())}"
        is AssigmentExpression -> decompile(expression.setLocation, indent.more()) + " = " + decompile(expression.value, indent.more())
    }
}
