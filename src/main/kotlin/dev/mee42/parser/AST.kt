package dev.mee42.parser

import dev.mee42.asm.RegisterSize
import dev.mee42.asm.RegisterSize.*
import dev.mee42.lexer.Token
import java.awt.Point


enum class TypeEnum(val registerSize: RegisterSize, val names: List<String>) {
    INT8(BIT8, "int8","byte"),
    INT16(BIT16, "int16","short"),
    INT32(BIT32, "int32","int"),
    INT64(BIT64, "int64","long"),
    UINT8(BIT8,"uint8","byte"),
    UINT16(BIT16, "uint16","short"),
    UINT32(BIT32, "uint32","int"),
    UINT64(BIT64, "uint64","long"),
    BOOLEAN(BIT8, "bool"),
    VOID(BIT8, "void")
    ;

    constructor(size: RegisterSize, vararg names: String): this(size, names.toList())
}
sealed class Type(val size: RegisterSize)

data class BaseType(val type: TypeEnum): Type(type.registerSize)
data class PointerType(val type: Type): Type(BIT64)

object DynamicType: Type(BIT64)

sealed class Statement
data class Block(val statements: List<Statement>): Statement()
data class ReturnStatement(val expression: Expression): Statement()
object NoOpStatement: Statement() {
    override fun toString() = "NOP"
}
data class ExpressionStatement(val expression: Expression): Statement()

sealed class Expression(val type: Type)
class VariableAccessExpression(val variableName: String, type: Type): Expression(type) {
    override fun toString(): String {
        return "VARIABLE($variableName)"
    }
}
class DereferencePointerExpression(val pointerExpression: Expression): Expression((pointerExpression.type as PointerType).type)

enum class MathType(val symbol: String) {
    ADD("+"), SUB("-"), MULT("*"), DIV("/")
    ;

}
data class MathExpression(val var1: Expression, val var2: Expression, val mathType: MathType): Expression(var1.type) {
    init {
        if(var1.type is PointerType || var2.type is PointerType) {
            error("adding to pointer types not supported as of right now")
        }
        if((var1.type as BaseType).type.registerSize != (var2.type as BaseType).type.registerSize) {
            error("mis-matched sizes")
        }
    }
}

data class FunctionCallExpression(val arguments: List<Expression>,val argumentNames: List<String>,  val function: String, val returnType: Type): Expression(returnType)

data class Argument(val name: String, val type: Type)

class AssemblyFunction(name: String, arguments: List<Argument>, returnType: Type): Function(name, arguments, returnType)
class XenonFunction(name: String, arguments: List<Argument>, returnType: Type, val content: Block): Function(name, arguments, returnType)
abstract class Function(val name: String, val arguments: List<Argument>, val returnType: Type)

data class AST(val functions: List<Function>)


data class InitialFunction(val name: String, val arguments: List<Argument>, val attributes: List<String>, val returnType: Type, val content: List<Token>?)
data class InitialAST(val functions: List<InitialFunction>) {
    fun withLibrary(functions: List<InitialFunction>): InitialAST = InitialAST(this.functions + functions)
}