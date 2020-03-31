package dev.mee42.parser

import dev.mee42.Config


sealed class Statement{
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
data class ExpressionStatement(val expression: Expression): Statement() {
    override val localVariableMaxBytes: Int = 0
}


data class DeclareVariableStatement(val variableName: String, val final: Boolean, val expression: Expression) : Statement() {
    override val localVariableMaxBytes: Int = expression.type.size.bytes
}
data class AssignVariableStatement(val variableName: String, val expression: Expression) : Statement() {
    override val localVariableMaxBytes: Int = expression.type.size.bytes
}

data class MemoryWriteStatement(val location: Expression, val value: Expression): Statement() {
    override val localVariableMaxBytes: Int = 0
}

data class IfStatement(val conditional: Expression, val block: Block): Statement() {
    override val localVariableMaxBytes: Int = block.localVariableMaxBytes
    companion object {
        fun create(conditional: Expression, block: Block): Statement {
            return if(conditional is IntegerValueExpression && conditional.value == 0 && Config.optimize) {
                NoOpStatement // 0 is falsy
            } else {
                IfStatement(conditional, block)
            }
        }
    }
}

data class WhileStatement(val conditional: Expression, val block: Block): Statement() {
    override val localVariableMaxBytes: Int = block.localVariableMaxBytes
}

