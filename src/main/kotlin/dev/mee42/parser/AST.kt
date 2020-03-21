package dev.mee42.parser

import dev.mee42.lexer.Token


sealed class Statement{
    abstract val localVariableMaxBytes: Int
}
data class Block(val statements: List<Statement>): Statement() {
    override val localVariableMaxBytes: Int
        get() = statements.sumBy { it.localVariableMaxBytes }
}
data class ReturnStatement(val expression: Expression): Statement(){
    override val localVariableMaxBytes: Int
        get() = 0
}
object NoOpStatement: Statement() {
    override val localVariableMaxBytes: Int
        get() = 0

    override fun toString() = "NOP"
}
data class ExpressionStatement(val expression: Expression): Statement() {
    override val localVariableMaxBytes: Int
        get() = 0
}

data class DeclareVariableStatement(val variableName: String, val final: Boolean, val expression: Expression) : Statement() {
    override val localVariableMaxBytes: Int
        get() = expression.type.size.bytes
}

sealed class Expression(open val type: Type)
class VariableAccessExpression(val variableName: String, type: Type): Expression(type) {
    override fun toString(): String {
        return "VARIABLE($variableName)"
    }
}
class DereferencePointerExpression(val pointerExpression: Expression): Expression((pointerExpression.type as PointerType).type)
class IntegerValueExpression(val value: Int, override val type: BaseType) :Expression(type) {
    override fun toString(): String {
        return "INT_VALUE($value:$type)"
    }
}



enum class MathType(val symbol: String) {
    ADD("+"), SUB("-"), MULT("*"), DIV("/");
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
class XenonFunction(name: String, arguments: List<Argument>, returnType: Type, val content: Block): Function(name, arguments, returnType) {
    override fun toString(): String {
        return "Function-$name($arguments)$returnType{$content}"
    }
}
abstract class Function(val name: String, val arguments: List<Argument>, val returnType: Type)

data class AST(val functions: List<Function>)


data class InitialFunction(val name: String, val arguments: List<Argument>, val attributes: List<String>, val returnType: Type, val content: List<Token>?)
data class InitialAST(val functions: List<InitialFunction>) {
    fun withLibrary(functions: List<InitialFunction>): InitialAST = InitialAST(this.functions + functions)
}