package dev.mee42.parser

import dev.mee42.asm.RegisterSize
import dev.mee42.type


sealed class Expression(open val type: Type)
class VariableAccessExpression(val variableName: String, type: Type): Expression(type) {
    override fun toString(): String {
        return "VARIABLE($variableName)"
    }
}
class BlockExpression(val statements: List<Statement>,val  last: Expression): Expression(last.type)
class DereferencePointerExpression(val pointerExpression: Expression): Expression((pointerExpression.type as PointerType).type)
class IntegerValueExpression(val value: Int, override val type: BaseType) :Expression(type) {
    override fun toString(): String {
        return "INT_VALUE($value:$type)"
    }
}
data class StringLiteralExpression(val value: String) : Expression(type("char*"))


enum class MathType(val symbol: String) {
    ADD("+"), SUB("-"), MULT("*"), DIV("/");
}
data class MathExpression(val var1: Expression, val var2: Expression, val mathType: MathType): Expression(var1.type) {
    init {
        if(var1.type is PointerType && var2.type is PointerType && mathType != MathType.SUB) {
            error("can't add two pointers")
        }
        if(var1.type is BaseType && var2.type is BaseType && (var1.type as BaseType).type.registerSize != (var2.type as BaseType).type.registerSize) {
            error("mis-matched sizes")
        }
    }
}

data class EqualsExpression(val var1: Expression, val var2: Expression, val negate: Boolean): Expression(BaseType(TypeEnum.BOOLEAN)) {
    init {
        if(var1.type is PointerType && var2.type !is PointerType || var2.type is PointerType && var1.type !is PointerType) {
            error("can't compare pointer to integer")
        }
        if(var1.type is BaseType) {
            val t1 = var1.type as BaseType
            val t2 = var2.type as BaseType
            if(t1 != t2) error("can't compare different types $t1 and $t2")
        }
    }
}

data class FunctionCallExpression(val arguments: List<Expression>,
                                  val argumentNames: List<String>,
                                  val functionIdentifier: String,
                                  val returnType: Type): Expression(returnType)
