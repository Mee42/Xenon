package dev.mee42.parser

import dev.mee42.type

sealed class LValueExpression(type: Type): Expression(type) {
    abstract fun isMutableLValue(): Boolean
}
sealed class Expression(open val type: Type)

class VariableAccessExpression(val variableName: String, type: Type, val isMutable: Boolean): LValueExpression(type){

    override fun isMutableLValue(): Boolean {
        return isMutable
    }

    override fun toString(): String {
        return "VARIABLE($variableName)"
    }

}

class TypelessBlock(val expressions: List<Expression>): Expression(BaseType(TypeEnum.VOID))

class BlockExpression(val statements: List<Statement>): Expression((statements.last() as ExpressionStatement).expression.type)

class DereferencePointerExpression(val pointerExpression: Expression): LValueExpression((pointerExpression.type as PointerType).type) {
    override fun isMutableLValue(): Boolean {
        return (pointerExpression.type as PointerType).writeable
    }
}

data class RefExpression(val lvalue: LValueExpression,
                         val isMutable: Boolean): Expression(PointerType(lvalue.type, isMutable)) {
}

class IntegerValueExpression(val value: Int, override val type: BaseType) :Expression(type) {
    override fun toString(): String {
        return "INT_VALUE($value:$type)"
    }
}
data class StringLiteralExpression(val value: String) : Expression(type("char*"))


enum class MathType(val symbol: String) {
    ADD("+"), SUB("-"), MULT("*"), DIV("/"), EQUALS("=="),NOT_EQUALS("!=");
}
class ComparisonExpression(val var1: Expression, val var2: Expression, val mathType: MathType): Expression(if(mathType in listOf(MathType.EQUALS, MathType.NOT_EQUALS)) BaseType(TypeEnum.BOOLEAN) else var1.type) {
    init {
        if(var1.type is PointerType && var2.type is PointerType && mathType != MathType.SUB) {
            error("can't add two pointers")
        }
        if(var1.type is BaseType && var2.type is BaseType && (var1.type as BaseType).type.registerSize != (var2.type as BaseType).type.registerSize) {
            error("mis-matched sizes")
        }
    }
}

data class AssigmentExpression(val setLocation: LValueExpression, val value: Expression): Expression(value.type)


data class FunctionCallExpression(val arguments: List<Expression>,
                                  val functionIdentifier: String,
                                  val returnType: Type): Expression(returnType)
