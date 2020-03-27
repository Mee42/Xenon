package dev.mee42.asm

import dev.mee42.parser.*

typealias Assembly = List<AssemblyInstruction>

private class ExpressionExistsState(val variableBindings: List<Variable>, val ast: AST, val accumulatorRegister: Register) {

    private fun assembleExpression(expression: MathExpression): Assembly = buildList {
        this += assembleExpression(variableBindings, ast, expression.var1, accumulatorRegister)
        this += AssemblyInstruction.Push(accumulatorRegister)
        this += assembleExpression(variableBindings, ast, expression.var2, accumulatorRegister)
        when(expression.mathType) {
            MathType.ADD -> {
                this += AssemblyInstruction.Pop(Register.B)
                this += AssemblyInstruction.Add(
                    reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(),
                    reg2 = SizedRegister(expression.type.size, Register.B).advanced()
                )
            }
            MathType.SUB -> {
                this += AssemblyInstruction.Mov(
                    reg1 = Register.B,
                    reg2 = Register.A,
                    size = expression.type.size
                ).zeroIfNeeded()
                this += AssemblyInstruction.Pop(Register.A)
                this += AssemblyInstruction.Sub(
                    reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(),
                    reg2 = SizedRegister(expression.type.size, Register.B).advanced()
                )
            }
            MathType.MULT -> {
                this += AssemblyInstruction.Pop(Register.B)
                this += AssemblyInstruction.Mul(reg1 = SizedRegister(expression.type.size, Register.B).advanced())
            }
            MathType.DIV -> {
                this += AssemblyInstruction.Xor(
                    reg1 = SizedRegister(RegisterSize.BIT64, Register.D),
                    reg2 = SizedRegister(RegisterSize.BIT64, Register.D).advanced())
                val type = expression.type
                type as? BaseType ?: error("debug this later")
                if(expression.type.size == RegisterSize.BIT8) {
                    if(type.type.isUnsigned) {
                        this += AssemblyInstruction.MovZX(
                            reg1 = SizedRegister(RegisterSize.BIT16, Register.B),
                            reg2  = SizedRegister(RegisterSize.BIT8, Register.A).advanced()
                        )
                    } else {
                        this += AssemblyInstruction.MovSX(
                            reg1 = SizedRegister(RegisterSize.BIT16, Register.B),
                            reg2  = SizedRegister(RegisterSize.BIT8, Register.A).advanced()
                        )
                    }
                } else {
                    this += AssemblyInstruction.Mov(reg1 = Register.B, reg2 = Register.A, size = expression.type.size)
                }
                this += AssemblyInstruction.Pop(Register.A)
                this += AssemblyInstruction.divOf(
                    size = type.size,
                    signed = !type.type.isUnsigned,
                    divisor = SizedRegister(type.size, Register.B))
            }
        }

    }


    private fun assembleExpression(expression: VariableAccessExpression): Assembly = buildList {
        val variable = variableBindings.first { it.name == expression.variableName }
        this += AssemblyInstruction.Mov(
            reg1 = SizedRegister(variable.register.size, accumulatorRegister).advanced(),
            reg2 = variable.register
        ).comment( "pulling variable ${expression.variableName}")
    }

    private fun assembleExpression(expression: DereferencePointerExpression): Assembly = buildList {
        if(expression.pointerExpression.type !is PointerType) error("assertion failed")
        this += assembleExpression(variableBindings, ast, expression.pointerExpression, accumulatorRegister)
        this += AssemblyInstruction.Mov(
            reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(),
            reg2 = AdvancedRegister(
                register = SizedRegister(RegisterSize.BIT64,  accumulatorRegister),
                isMemory = true,
                size = expression.type.size)
        )
    }

    private fun assembleExpression(expression: FunctionCallExpression): Assembly = buildList {
        // pretty much, just push all the arguments, call the function, then clean the stack
        val argumentSize = expression.arguments.size * 8

        expression.arguments.forEachIndexed { i,expr ->
            // evaluate and put the value in the register
            this += assembleExpression(variableBindings, ast, expr, accumulatorRegister)
            this += AssemblyInstruction.Push(accumulatorRegister).comment("argument ${expression.argumentNames[i]}")
        }
        // alright, everything is in the right registers
        this += AssemblyInstruction.Call(expression.functionIdentifier)
        this += AssemblyInstruction.Add(
            reg1 = SizedRegister(RegisterSize.BIT64, Register.SP).advanced(),
            reg2 = StaticValueAdvancedRegister(argumentSize, RegisterSize.BIT64)
        )
    }

    private fun assembleExpression(expression: IntegerValueExpression): Assembly = buildList {
        this += AssemblyInstruction.Mov(
            reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(),
            reg2 = StaticValueAdvancedRegister(expression.value, expression.type.size)
        )
    }

    private fun assembleExpression(expression: EqualsExpression): Assembly = buildList {
        // just like the math expression
        // order is agnostic, so let's do this the normal way
        if(expression.var1.type.size != expression.var2.type.size) error("uh, can't do that")
        this += assembleExpression(expression.var1)
        this += AssemblyInstruction.Push(accumulatorRegister) // push A to the stack
        this += assembleExpression(expression.var2)
        this += AssemblyInstruction.Pop(Register.B)
        this += AssemblyInstruction.Compare(
            SizedRegister(expression.var1.type.size, accumulatorRegister),
            SizedRegister(expression.var2.type.size, Register.B).advanced()
        )
        this += AssemblyInstruction.SetCC(ComparisonOperator.EQUALS, accumulatorRegister)
    }

    fun assembleExpression(expression: Expression): Assembly {
        return when(expression) {
            is MathExpression -> assembleExpression(expression)
            is VariableAccessExpression -> assembleExpression(expression)
            is DereferencePointerExpression -> assembleExpression(expression)
            is FunctionCallExpression -> assembleExpression(expression)
            is IntegerValueExpression -> assembleExpression(expression)
            is EqualsExpression -> assembleExpression(expression)
        }
    }

}

fun assembleExpression(variableBindings: List<Variable>, ast: AST, expression: Expression, accumulatorRegister: Register): Assembly {
    return ExpressionExistsState(variableBindings, ast, accumulatorRegister).assembleExpression(expression)
}
/*
    is FunctionCallExpression -> {
        buildList {

        }
    }
    is IntegerValueExpression -> buildList {
        this += AssemblyInstruction.Mov(
            reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(),
            reg2 = StaticValueAdvancedRegister(expression.value, expression.type.size)
        )
    }
}


    */