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
    return function.copy(function.content.optimize(state) as Block)
}

private fun Statement.optimize(state: State): Statement {
    return when(this){
        is Block -> {
            val subState = state.newSubState()
            val new = statements.map { it.optimize(subState) }
            Block(new)
        }
        is ReturnStatement -> {
            ReturnStatement(expression.optimize(state))
        }
        NoOpStatement -> NoOpStatement
        is ExpressionStatement -> ExpressionStatement(expression.optimize(state))
        is DeclareVariableStatement -> {
            val expr = expression.optimize(state)
            state.insertVariable(State.VariableState(
                    name = variableName,
                    type = expression.type,
                    knownValue = expr.isFlat()))
            DeclareVariableStatement(variableName, final, expr)
        }
        is AssignVariableStatement -> {
            val expr = expression.optimize(state)
            expr.isFlat()?.let { v ->
                state.possiblyUpdate(variableName, v)
            } ?: state.markMutated(state.getVariable(variableName))
            AssignVariableStatement(variableName, expr)
        }
        is MemoryWriteStatement -> {
            MemoryWriteStatement(
                    location.optimize(state),
                    value.optimize(state)
            )
        }
        is IfStatement -> {
            val conditional = conditional.optimize(state)
            val predict = conditional.isFlat()

            if(predict == null){
                val f = IfStatement(conditional, block.optimize(state.newSubState()) as Block)
                f
            } else {
                if(predict.value as Boolean) {
                    block.optimize(state.newSubState())
                } else {
                    NoOpStatement // don't optimize code that won't be ran, as it'll pollute the namespace
                }
            }
        }
        is WhileStatement -> this // improvable?
    }
}

private fun Expression.isFlat(): Value? {
    return if(this is IntegerValueExpression) {
        if(type == type("bool")) {
            Value(type("bool"),value != 0)
        } else {
            Value(type("int"), value)
        }
    } else null
}
private fun Value.toExpr(): Expression {
    return if(this.type == type("bool")) {
        IntegerValueExpression(if(this.value as Boolean) 1 else 0, BaseType(TypeEnum.BOOLEAN))
    } else {
        IntegerValueExpression(this.value as Int, BaseType(TypeEnum.INT32))
    }
}
private fun Expression.optimize(state: State): Expression {
    return when (this) {
        is TypelessBlock -> TypelessBlock(this.expressions.map { it.optimize(state) })
        is IntegerValueExpression -> this
        is VariableAccessExpression -> state.getVariable(variableName).knownValue?.toExpr() ?: this
        is DereferencePointerExpression -> DereferencePointerExpression(pointerExpression.optimize(state))
        is StringLiteralExpression -> this
        is MathExpression -> {
            val v1 = var1.optimize(state)
            val v2 = var2.optimize(state)
            val f1 = v1.isFlat()
            val f2 = v2.isFlat()
            if(f1 != null && f2 != null) {
                if(v1.type != type("int") || v2.type != type("int")) this // TODO allow for other operations
                else when(mathType) {
                    MathType.ADD -> Value(type("int"), f1.value as Int + f2.value as Int).toExpr()
                    MathType.SUB -> Value(type("int"), f1.value as Int - f2.value as Int).toExpr()
                    MathType.MULT -> Value(type("int"), f1.value as Int * f2.value as Int).toExpr()
                    MathType.DIV -> {
                        if(f2.value as Int == 0) {
                            emitWarning("division by zero")
                            this
                        } else Value(type("int"), f1.value as Int / f2.value).toExpr()
                    }
                }
            } else MathExpression(
                    var1 = v1,
                    var2 = v2,
                    mathType = mathType
            )
        }
        is EqualsExpression -> {
            val v1 = var1.optimize(state)
            val v2 = var2.optimize(state)
            val f1 = v1.isFlat()
            val f2 = v2.isFlat()
            if(f1 != null && f2 != null) {
                when (v1.type) {
                    type("bool") -> Value(type("bool"), applyIf(negate, Boolean::not)(f1.value as Boolean == f2.value as Boolean))
                    type("int") -> Value(type("bool"),  applyIf(negate, Boolean::not)(f1.value as Int     == f2.value as Int))
                    else -> error("oof")
                }.toExpr() // TODO add more integer types
            } else {
                EqualsExpression(var1 = v1, var2 = v2, negate = negate)
            }
        }
        is FunctionCallExpression -> {
            FunctionCallExpression(
                    arguments = arguments.map { it.optimize(state) },
                    returnType = returnType,
                    functionIdentifier = functionIdentifier,
                    argumentNames = argumentNames
            )
        }
        is BlockExpression -> {
            val statementsWithoutNop = statements.filter { it != NoOpStatement }
            if(statementsWithoutNop.size == 1) {
                (statementsWithoutNop[0] as ExpressionStatement).expression.optimize(state)
            } else {
                BlockExpression(statementsWithoutNop)
            }
        }
    }
}
