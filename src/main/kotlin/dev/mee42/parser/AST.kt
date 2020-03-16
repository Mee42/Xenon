package dev.mee42.parser

import dev.mee42.asm.RegisterSize
import dev.mee42.asm.RegisterSize.*
import dev.mee42.lexer.Token


enum class TypeEnum(val registerSize: RegisterSize, val names: List<List<String>>) {
    INT8(BIT8, "int8","byte"),
    INT16(BIT16, "int16","short"),
    INT32(BIT32, "int32","int"),
    INT64(BIT64, "int64","long"),
    UINT8(BIT8,"uint8","unsigned byte"),
    UINT16(BIT16, "uint16","unsigned short"),
    UINT32(BIT32, "uint32","unsigned int"),
    UINT64(BIT64, "uint64","unsigned long"),

    VOID(BIT8, "void");
    constructor(size: RegisterSize, vararg names: String): this(size, names.map { it.split(" ") })
}

data class Type(val type: TypeEnum, val attributes: List<String>)



sealed class Statement
data class Block(val statements: List<Statement>): Statement()
data class ReturnStatement(val expression: Expression): Statement()
object NoOpStatement: Statement() {
    override fun toString() = "NOP"
}


sealed class Expression
data class VariableAccessExpression(val variableName: String, val type: Type): Expression()
data class DereferencePointerExpression(val pointerExpression: Expression): Expression()
data class AddExpression(val var1: Expression, val var2: Expression): Expression()



data class Argument(val name: String, val type: Type)


data class Function(val name: String, val arguments: List<Argument>, val returnType: Type, val content: Block)

data class AST(val functions: List<Function>)



data class InitialFunction(val name: String, val arguments: List<Argument>, val attributes: List<String>, val returnType: Type, val content: List<Token>)
data class InitialAST(val functions: List<InitialFunction>)