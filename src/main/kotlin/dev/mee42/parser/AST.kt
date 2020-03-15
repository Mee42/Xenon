package dev.mee42.parser

import dev.mee42.lexer.Token


enum class TypeEnum(val names: List<List<String>>) {
    INT8("int8","byte"),
    INT16("int16","short"),
    INT32("int32","int"),
    INT64("int64","long"),
    UINT8("uint8","unsigned byte"),
    UINT16("uint16","unsigned short"),
    UINT32("uint32","unsigned int"),
    UINT64("uint64","unsigned long"),

    VOID("void");
    constructor(vararg names: String): this(names.map { it.split(" ") })
}

data class Type(val type: TypeEnum, val attributes: List<String>) {
    val isFinal
        get() = attributes.contains("@const")
}



sealed class Statement
data class Block(val statements: List<Statement>): Statement()
object NoOpStatement: Statement() {
    override fun toString() = "NOP"
}

sealed class Expression
data class VariableAccessExpression(val variableName: String, val type: Type): Statement()




data class Argument(val name: String, val type: Type)


data class Function(val name: String, val arguments: List<Argument>, val returnType: Type, val content: Block)

data class AST(val functions: List<Function>)



data class InitialFunction(val name: String, val arguments: List<Argument>, val attributes: List<String>, val returnType: Type, val content: List<Token>)
data class InitialAST(val functions: List<InitialFunction>)