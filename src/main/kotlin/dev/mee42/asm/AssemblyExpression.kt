package dev.mee42.asm

import dev.mee42.parser.*


fun assembleExpression(variableBindings: List<Variable>, ast: AST, expression: Expression, accumulatorRegister: Register): List<AssemblyInstruction> {
    return when(expression) {
        is MathExpression -> {
            buildList {
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
                        this += AssemblyInstruction.Mul(
                            reg1 = SizedRegister(expression.type.size, Register.B).advanced()
                        )
                    }
                    MathType.DIV -> {
                        this += AssemblyInstruction.Xor(
                            reg1 = SizedRegister(RegisterSize.BIT64, Register.D).advanced(),
                            reg2 = SizedRegister(RegisterSize.BIT64, Register.D).advanced()
                        )
                        this += AssemblyInstruction.Mov(
                            reg1 = Register.B,
                            reg2 = Register.A,
                            size = expression.type.size
                        )
                        this += AssemblyInstruction.Pop(Register.A)
                        this += AssemblyInstruction.Div(
                            reg1 = SizedRegister(expression.type.size, Register.B).advanced()
                        )
                    }
                }

            }
        }
        is VariableAccessExpression -> {
            val variable = variableBindings.first { it.name == expression.variableName }
            listOf(AssemblyInstruction.Mov(
                reg1 = SizedRegister(variable.register.size, accumulatorRegister).advanced(),
                reg2 = variable.register
            ).comment( "pulling variable ${expression.variableName}"))
        }
        is DereferencePointerExpression -> {
            buildList {
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
        }
        is FunctionCallExpression -> {
            buildList {
                // pretty much, just push all the arguments, call the function, then clean the stack
                val argumentSize = expression.arguments.size * 8

                expression.arguments.forEachIndexed { i,expr ->
                    // evaluate and put the value in the register
                    this += assembleExpression(variableBindings, ast, expr, accumulatorRegister)
                    this += AssemblyInstruction.Push(accumulatorRegister).comment("argument ${expression.argumentNames[i]}")
                }
                // alright, everything is in the right registers
                this += AssemblyInstruction.Call(expression.function)
                this += AssemblyInstruction.Add(
                    reg1 = SizedRegister(RegisterSize.BIT64, Register.SP).advanced(),
                    reg2 = StaticValueAdvancedRegister(argumentSize, RegisterSize.BIT64)
                )
            }
        }
        is IntegerValueExpression -> buildList {
            this += AssemblyInstruction.Mov(
                reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(),
                reg2 = StaticValueAdvancedRegister(expression.value, expression.type.size)
            )
        }
    }
}