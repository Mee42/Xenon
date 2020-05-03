package dev.mee42.parser


sealed class Statement {
    abstract val localVariableMaxBytes: Int
}

data class Block(val statements: List<Statement>): Statement() {
    override val localVariableMaxBytes: Int = statements.sumBy { it.localVariableMaxBytes }
}
data class ReturnStatement(val expression: Expression): Statement(){
    override val localVariableMaxBytes: Int = 0
}
object NoOpStatement: Statement() {
    override val localVariableMaxBytes: Int = 0
    override fun toString() = "NOP"
}
data class ExpressionStatement(val expression: Expression, val needed: Boolean = false): Statement() {
    override val localVariableMaxBytes: Int = 0
}

data class DeclareVariableStatement(val variableName: String, val final: Boolean, val expression: Expression) : Statement() {
    override val localVariableMaxBytes: Int = expression.type.size.bytes
}

data class IfStatement(val conditional: Expression, val block: Block): Statement() {
    override val localVariableMaxBytes: Int = block.localVariableMaxBytes
}

data class WhileStatement(val conditional: Expression, val block: Block): Statement() {
    override val localVariableMaxBytes: Int = block.localVariableMaxBytes
}