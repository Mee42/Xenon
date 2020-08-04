package dev.mee42

typealias Identifier = String
typealias TypeName = String

class UntypedAST(val functions: List<UntypedFunction>, val structs: List<UntypedStruct>)

class UntypedFunction(
        val identifier: Identifier,
        val arguments: List<Pair<UntypedType, Identifier>>,
        val templated: TemplateMode,
        val block: UntypedStatement.Block
) {
    sealed class TemplateMode {
        class  BindedTemplateMode(val types: List<Pair<TypeName, UntypedType>>): TemplateMode()
        class  UnbindedTemplateMode(val types: List<TypeName>): TemplateMode()
        object NoTemplate: TemplateMode()
    }
}
sealed class UntypedExpression {
    class VariableAccess(val identifier: Identifier): UntypedExpression()
    class Block(val statements: List<UntypedStatement>, val last: UntypedExpression): UntypedExpression()
    class DerefPointer(val expr: UntypedExpression): UntypedExpression()
    class RefValue(val expr: UntypedExpression): UntypedExpression()
    class IntegerLiteral(val value: Int): UntypedExpression() // TODO add other integer types
    class StringLiteral(val value: String): UntypedExpression()
    class BinaryOp(val op: BinaryOperator, val left: UntypedExpression, val right: UntypedExpression): UntypedExpression()
    class ValueAssign(val left: UntypedExpression, val right: UntypedExpression): UntypedExpression()
    class FunctionCall(val arguments: List<UntypedExpression>,
                       val calledOnReciever: Boolean,
                       val functionIdentifier: Identifier,
                       val templates: List<UntypedType>?): UntypedExpression()
    class MemberAccess(val struct: UntypedExpression, val member: Identifier): UntypedExpression()
}
enum class BinaryOperator(val op: String) {
    PLUS("+"),
    MINUS("-"),
    TIMES("*"),
    DIV("/"),
    LT("<"),
    GT(">"),
    LTE("<="),
    GTE(">="),
    EQ("=="),
    NEQ("!=")
}

sealed class UntypedStatement {
    class Block(val statements: List<UntypedStatement>): UntypedStatement()
    class Return(val expression: UntypedExpression): UntypedStatement()
    class Expression(val expression: UntypedExpression): UntypedStatement()
    class VariableDecleration(val type: UntypedType,
                                       val identifier: Identifier,
                                       val final: Boolean,
                                       val expression: UntypedExpression): UntypedStatement()
    class If(val if_: UntypedStatement, val else_: UntypedStatement?): UntypedStatement()
    class While(val block: UntypedStatement): UntypedStatement()
}

class UntypedStruct(
        val identifier: Identifier,
        val fields: List<Pair<UntypedType, Identifier>>,
        val templates: List<TypeName>
)

sealed class UntypedType {
    class PrimitiveType(val type: PrimitiveTypeEnum): UntypedType()
    class StructType(val id: Identifier, val templates: List<UntypedType>): UntypedType()
    class TemplateType(val typename: TypeName): UntypedType()
    class PointerType(val subtype: UntypedType, val mutable: Boolean): UntypedType()
}

enum class PrimitiveTypeEnum(vararg val names: String) {
    INT("int","int32")
}