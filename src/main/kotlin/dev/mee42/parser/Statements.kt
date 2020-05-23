package dev.mee42.parser

import kotlin.math.max

private val Type.maxStructSize: Int
    get() = when(this) {
        is BaseType, is PointerType -> 0
        is StructType -> this.struct.size.bytes
    }
private val Expression.maxStructSize: Int
    get() = when(this) {
        is VariableAccessExpression -> this.type.maxStructSize
        is DereferencePointerExpression -> this.type.maxStructSize
        is MemberAccessExpression -> this.type.maxStructSize
        is TypelessBlock -> this.expressions.map { it.maxStructSize }.max() ?: 0
        is BlockExpression -> this.statements.map { it.maxStructSize }.max() ?: 0
        is RefExpression -> 0
        is IntegerValueExpression -> 0
        is StringLiteralExpression -> 0
        is ComparisonExpression -> max(var1.maxStructSize, var2.maxStructSize)
        is AssigmentExpression -> max(this.setLocation.maxStructSize, value.maxStructSize)
        is FunctionCallExpression -> max(this.arguments.map { it.maxStructSize }.max() ?: 0, this.type.maxStructSize)
        is StructInitExpression -> this.type.maxStructSize
    }

sealed class Statement {
    abstract val localVariableMaxBytes: Int
    abstract val maxStructSize: Int
}

data class Block(val statements: List<Statement>): Statement() {
    override val localVariableMaxBytes: Int = statements.sumBy { it.localVariableMaxBytes }
    override val maxStructSize: Int = statements.map { it.maxStructSize }.max() ?: 0
}
data class ReturnStatement(val expression: Expression): Statement(){
    override val localVariableMaxBytes: Int = 0
    override val maxStructSize: Int = expression.maxStructSize
}
object NoOpStatement: Statement() {
    override val localVariableMaxBytes: Int = 0
    override val maxStructSize: Int = 0

    override fun toString() = "NOP"
}
data class ExpressionStatement(val expression: Expression, val needed: Boolean = false): Statement() {
    override val localVariableMaxBytes: Int = 0
    override val maxStructSize: Int = expression.maxStructSize
}

data class DeclareVariableStatement(val variableName: String, val final: Boolean, val expression: Expression) : Statement() {
    override val localVariableMaxBytes: Int = expression.type.size.bytes
    override val maxStructSize: Int = expression.maxStructSize
}

data class IfStatement(val conditional: Expression, val block: Block): Statement() {
    override val localVariableMaxBytes: Int = block.localVariableMaxBytes
    override val maxStructSize: Int = max(conditional.maxStructSize, block.maxStructSize)
}

data class WhileStatement(val conditional: Expression, val block: Block): Statement() {
    override val localVariableMaxBytes: Int = block.localVariableMaxBytes
    override val maxStructSize: Int = max(conditional.maxStructSize, block.maxStructSize)
}