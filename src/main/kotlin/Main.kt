package dev.mee42

import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.io.File



fun main() {
    val code = File("res/test3.xn").readText()
//    val code = "\"Hello, world\""  
    val tokens = lex(code)
    println("lexing...")
    println(tokens)
    println("\nparsing...")
    val ast = paintAllBreaks(parse(tokens))
    println("ast: $ast")
//    println(ast.str())
    println("\ntyping...")
    val typedAST = type(ast)
    println(typedAST.structs.joinToString("\n") { it.str() })
    println(typedAST.functions.joinToString("\n") { it.str() })


    val tvmBackend = dev.mee42.tvm.TVMBackend()
    println("\n ==== ${tvmBackend.name}...")
    val endResult = tvmBackend.process(typedAST)

    var s = ""
    for(instr in endResult.instrs)  s += "" + instr + "\n"
    println(s)
    Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(s), null)
    Thread.sleep(10000)
}







object PrintConfig {
    val printAutogenLabels = true
}

fun UntypedStruct.str(): String {
    val generics = this.generics.takeUnless { it.isEmpty() }
        ?.joinToString(", ", "[", "]") ?: ""
    return "struct" + generics + " " + this.name + " {\n" + this.fields.joinToString("") { "    " + it.first.str() + " " + it.second + ";\n" } + "}"
}

fun GenericStruct.str(): String {
    val generics =  this.genericTypes.takeUnless { it.isEmpty() }
        ?.joinToString(", ", "[", "]") ?: ""
    return "struct" + generics + " " + this.name + " {\n" + this.fields.joinToString("") { "    " + it.first.str() + " " + it.second + ";\n" } + "}"
}


fun UntypedAST.str(): String {
    return (this.functions.joinToString("\n\n", transform = UntypedFunction::str) +
            "\n\n" + this.structs.joinToString("\n\n", transform = UntypedStruct::str)).trim()
}

fun UntypedFunction.str(): String {
    val generics = this.generics.takeUnless { it.isEmpty() }
        ?.joinToString(", ", "[", "]") ?: ""
    return "func" + generics + " " + this.name + "(" + this.arguments.joinToString(", ") { it.type.str() + " " + it.name } + ") " + this.retType.str() + " {\n    " + this.body.sub.joinToString("\n    ") { it.str("    ") + ";" } + "\n}"
}

fun Function.str(): String {
    val generics = this.header.genericsInfo.toList().takeUnless { it.isEmpty() }
        ?.joinToString(", ", "[", "]", ){ (name, type) -> "$name=" + type.str() } ?: ""
    return "func" + generics + " " + this.header.name + "(" + this.header.arguments.joinToString(", ") { it.type.str() + " " + it.name } + ") " + this.header.returnType.str() + " {\n    " + this.body.contents.joinToString("\n    ") { it.str("    ") + ";" } + "\n}"
}

fun clean(label: LabelIdentifier?): String {
   return if(label == null || label.endsWith("_") && !PrintConfig.printAutogenLabels) "" else "@$label"
}

fun UntypedExpr.str(indent: String, needsToIndent: Boolean = false, needParens: Boolean = false): String {
    val i = if(needsToIndent) indent else ""
    fun paren(x: String) = if(needParens || true) "($x)" else x
    return when(this) {
        is UntypedExpr.Assignment -> i + paren("${left.str(indent)} = ${right.str(indent)}")
        is UntypedExpr.BinaryOp -> i + paren(left.str(indent, needParens = true) + " $op " + right.str(indent, needParens = true))
        is UntypedExpr.Block ->
            i + clean(label) + "{\n" + this.sub.joinToString("\n") { it.str("$indent    ", true) + ";" } + "\n$indent}"
        is UntypedExpr.FunctionCall -> i + functionName + "(" + arguments.joinToString(", ") { it.str(indent) } + ")"
        is UntypedExpr.NumericalLiteral -> i + number
        is UntypedExpr.PrefixOp -> i + paren(op + right.str(indent, needParens = true) )
        is UntypedExpr.Return -> i + "return " + expr?.str(indent)
        is UntypedExpr.StringLiteral -> i + '"' + content + '"'
        is UntypedExpr.VariableAccess -> i + variableName
        is UntypedExpr.VariableDefinition -> i + paren((if(isConst) "val " else "var ") + (type?.str()?.let { "$it " } ?: "") + variableName + (value?.str(indent)?.let { " = $it" } ?: ""))
        is UntypedExpr.If -> i + "if(" + cond.str(indent) + ") " + ifBlock.str(indent) + (elseBlock?.str(indent)?.let { " else $it" } ?: "")
        is UntypedExpr.CharLiteral -> "$i'$char'"
        is UntypedExpr.Loop -> i + "loop" + block.str(indent)
        is UntypedExpr.Continue -> i + "continue" + clean(label)
        is UntypedExpr.Break -> i + "break" + clean(label) + " " +  (value?.str(indent) ?: "")
        is UntypedExpr.MemberAccess -> i + paren(expr.str(indent,needParens = true) + (if(isArrow) "->" else ".") + memberName)
        is UntypedExpr.StructDefinition -> i + "struct " + (type?.str()?.plus(" ") ?: "") + "{\n" + this.members.joinToString(",\n") {  (name, expr) ->
            "$indent    .$name = " + expr.str("$indent    ")
        } + "\n$indent}"
    }
}

fun Expr.str(indent: String, needsToIndent: Boolean = false, needParens: Boolean = false): String {
    val i = if(needsToIndent) indent else ""
    fun paren(x: String) = if(needParens) "($x)" else x
    return when(this) {
        is Expr.BinaryOp -> i + paren(left.str(indent, needParens = true) + " ${op.op} " + right.str(indent, needParens = true))
        is Expr.Block ->
            i + clean(label) + "{\n" + this.contents.joinToString("\n") { it.str("$indent    ", true) + ";" } + "\n$indent}"
        is Expr.FunctionCall -> i + this.header.name +
                (if(this.header.genericNames.isEmpty()) "" else "::" + this.header.genericNames.map { header.genericsInfo[it]!!.str() }.joinToString(", ", "[", "]")) +
                "(" + arguments.joinToString(", ") { it.str(indent) } + ")"
        is Expr.NumericalLiteral -> i + this.i
        is Expr.StringLiteral -> i + '"' + content + '"'
        is Expr.CharLiteral -> "$i'$char'"
        is Expr.VariableAccess -> variableName
        is Expr.VariableDefinition -> (if(isConst) "val " else "var ") + type.str() + " " + this.variableName + " = " + value.str(indent)
        is Expr.StructDefinition -> i + "struct " + type.str() + " {\n" + this.members.joinToString(",\n") {  (name, expr) ->
            "$indent    .$name = " + expr.str("$indent    ")
        } + "\n$indent}"
        is Expr.If -> i + "if(" + cond.str(indent) + ") " + ifBlock.str(indent) + (elseBlock?.str(indent)?.let {" else $it"} ?: "")
        is Expr.Deref -> i + "*" + expr.str(indent)
        is Expr.MemberAccess -> i + this.expr.str(indent) + (if(isArrow) "->" else ".") + this.memberName
        is Expr.Ref -> i + "&" + expr.str(indent)
        is Expr.Assignment -> i + left.str(indent) + " = " + right.str(indent)
        is Expr.Break -> i + "break" + clean(label) + " "+ value.str(indent)
        is Expr.Continue -> i + "continue" + clean(label)
        is Expr.Loop -> i + "loop " + block.str(indent)
        is Expr.Unit -> i + "Unit"
    }
}



fun UnrealizedType.str(): String = when(this) {
    UnrealizedType.Nothing -> "Nothing"
    is UnrealizedType.Pointer -> this.subType.str() + "*"
    UnrealizedType.Unit -> "Unit"
    is UnrealizedType.NamedType -> this.name + if(this.genericTypes.isNotEmpty()) this.genericTypes.joinToString(", ", "[", "]") { it.str() } else ""
}

fun Type.str(): String = when(this) {
    Type.Nothing -> "Nothing"
    is Type.Pointer -> this.inner.str() + "*"
    Type.Unit -> "Unit"
    is Type.Struct -> this.name + if(this.genericTypes.isNotEmpty()) this.genericTypes.joinToString(", ", "[", "]") { it.str() } else ""
    is Type.Builtin -> this.name
    is Type.Generic -> this.identifier
}
