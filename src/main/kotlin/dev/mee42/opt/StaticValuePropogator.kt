package dev.mee42.opt

import dev.mee42.parser.*
import dev.mee42.type

private class Value(val type: Type, val value: Any)

private class State private constructor(val parent: State?){
    class VariableState(val name: String,
                        val type: Type,
                        knownValue: Value?,
                        canEverBeKnown: Boolean = true) {
        var canEverBeKnown = canEverBeKnown
            private set
        var knownValue = knownValue
            private set
        fun possiblyUpdate(value: Value) {
            if(canEverBeKnown) knownValue = value
        }
        fun ref() {
            canEverBeKnown = false
            knownValue = null
        }
    }
    @Suppress("PropertyName")
    val _variables = mutableListOf<VariableState>()
    fun getVariable(name: String): VariableState {
        return _variables.firstOrNull { it.name == name } ?: parent?.getVariable(name) ?: error("Can't find variable")
    }
    fun markMutated(name: VariableState){
        parent?.getVariable(name.name)?.ref()
    }
    fun insertVariable(variable: VariableState) {
        _variables.add(variable)
    }
    constructor(): this(null)
    fun newSubState(): State{
        return State(this)
    }

    fun possiblyUpdate(variableName: String, v: Value) {
        val parentObject = parent?.getVariable(variableName)
        if(parentObject == null) {
            _variables.firstOrNull { it.name == variableName }?.possiblyUpdate(v) ?: error("can't find object")
        } else {
            parentObject.ref()
        }
    }
}

private fun Expression.predict(state: State): Value? {
    val ret =  when (this) {
        is IntegerValueExpression -> {
            if(this.type == type("bool")) {
                Value(type("bool"), this.value != 0)
            } else Value(this.type, this.value)
        }
        is VariableAccessExpression -> state.getVariable(this.variableName).knownValue
        is DereferencePointerExpression -> null
        is StringLiteralExpression -> null // not sure how to handle this
        is MathExpression -> {
            val v1 = this.var1.predict(state)
            val v2 = this.var2.predict(state)
            if(v1 != null && v2 != null) {
                if(v1.type != type("int") || v2.type != type("int")) null // TODO allow for other operations
                else when(this.mathType) {
                    MathType.ADD -> Value(type("int"), v1.value as Int + v2.value as Int)
                    MathType.SUB -> Value(type("int"), v1.value as Int - v2.value as Int)
                    MathType.MULT -> Value(type("int"), v1.value as Int * v2.value as Int)
                    MathType.DIV -> {
                        if(v2.value as Int == 0) {
                            emitWarning("division by zero")
                            null
                        } else Value(type("int"), v1.value as Int / v2.value as Int)
                    }
                }
            } else null
        }
        is EqualsExpression -> {
            val v1 = this.var1.predict(state)
            val v2 = this.var2.predict(state)
            if(v1 != null && v2 != null) {
                when (v1.type) {
                    type("bool") -> Value(type("bool"), applyIf(negate, Boolean::not)(v1.value as Boolean == v2.value as Boolean))
                    type("int") -> Value(type("bool"),  applyIf(negate, Boolean::not)(v1.value as Int     == v2.value as Int))
                    else -> null
                } // TODO add more integer types
            } else null
        }
        is FunctionCallExpression -> null
    }
    return ret
}

fun <T> id(t: T):T {
    return t
}
fun <T> applyIf(conditional: Boolean, map: (T) -> T) = if(conditional) map else ::id


fun staticValuePropagator(function: XenonFunction): XenonFunction {
    val state = State() // function state
    for(argument in function.arguments) {
        state.insertVariable(State.VariableState(
                name = argument.name,
                type = argument.type,
                knownValue = null,
                canEverBeKnown = false)
        )
    }
    return XenonFunction(
            name = function.name,
            content = optimize(function.content, state) as Block,
            returnType = function.returnType,
            arguments = function.arguments,
            id = function.id)
}

private fun Statement.wrapInBlock(): Block = when(this){
    is Block -> this
    else -> Block(listOf(this))
}

private fun optimize(expression: Expression, state: State): Expression {
    val value =  expression.predict(state)
    return if(value != null) {
        if(value.type == type("bool")) {
            IntegerValueExpression(if(value.value as Boolean) 1 else 0, BaseType(TypeEnum.BOOLEAN))
        } else {
            IntegerValueExpression(value.value as Int, value.type as BaseType)
        }
    } else expression
}

private fun optimize(statement: Statement, state: State): Statement {
    return when(statement){
        is Block -> {
            val subState = state.newSubState()
            val new = statement.statements.map { optimize(it, subState) }
            Block(new)
        }
        is ReturnStatement -> {
            ReturnStatement(optimize(statement.expression, state))
        }
        NoOpStatement -> NoOpStatement
        is ExpressionStatement -> ExpressionStatement(optimize(statement.expression, state))
        is DeclareVariableStatement -> {
            val expr = optimize(statement.expression, state)
            state.insertVariable(State.VariableState(
                    name = statement.variableName,
                    type = statement.expression.type,
                    knownValue = expr.predict(state)))
            DeclareVariableStatement(
                    statement.variableName,
                    statement.final,
                    expr)
        }
        is AssignVariableStatement -> {
            val expr = optimize(statement.expression, state)
            expr.predict(state)?.let {v ->
                state.possiblyUpdate(statement.variableName, v)

            }
            AssignVariableStatement(statement.variableName, statement.expression)
        }
        is MemoryWriteStatement -> {
            MemoryWriteStatement(
                    optimize(statement.location, state),
                    optimize(statement.value, state)
            )
        }
        is IfStatement -> {
            val conditional = optimize(statement.conditional, state)
            val predict = conditional.predict(state)
            if(predict == null){
                val subState = state.newSubState()
                IfStatement(conditional, optimize(statement.block, subState) as Block)
            } else {
                if(predict.value as Boolean) {
                    statement.block
                } else {
                    NoOpStatement
                }
            }
        }
        is WhileStatement -> statement
    }
}