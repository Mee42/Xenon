package dev.mee42.parser


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
data class AssignVariableStatement(val variableName: String, val expression: Expression) : Statement() {
    override val localVariableMaxBytes: Int
        get() = expression.type.size.bytes
}


data class IfStatement(val conditional: Expression, val block: Block): Statement() {
    override val localVariableMaxBytes: Int
        get() = block.localVariableMaxBytes
}
