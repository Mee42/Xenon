package dev.mee42.asm

import dev.mee42.parser.*

class DataEntry(val name: String, val data: List<Int>)

class Assembly(val asm: List<AssemblyInstruction>, val data: List<DataEntry>) {
    operator fun plus(other: Assembly) : Assembly {
        return Assembly(asm + other.asm, data + other.data)
    }
}


fun buildAssembly(block: AssemblyBuilder.() -> Unit): Assembly {
    return AssemblyBuilder().apply(block).build()
}

class AssemblyBuilder {
    private val data = mutableListOf<DataEntry>()
    private val asm = mutableListOf<AssemblyInstruction>()
    operator fun plusAssign(other: Assembly){
        this.data += other.data
        this.asm += other.asm
    }
    operator fun plusAssign(data: DataEntry) {
        this.data += data
    }
    operator fun plusAssign(instruction: AssemblyInstruction) {
        this.asm += instruction
    }
    operator fun plusAssign(instructions: List<AssemblyInstruction>) {
        this.asm += instructions
    }
    fun build(): Assembly = Assembly(asm, data)
}

private class ExpressionExistsState(val variableBindings: List<Variable>,
                                    val ast: AST,
                                    val accumulatorRegister: Register,
                                    val topLocal: Int,
                                    val returnInstructions: List<AssemblyInstruction>) {

    private fun assembleExpression(expression: MathExpression): Assembly = buildAssembly {
        this += assembleExpression(expression.var1)
        this += AssemblyInstruction.Push(accumulatorRegister)
        this += assembleExpression(expression.var2)
        when(expression.mathType) {
            MathType.ADD -> {
                this += AssemblyInstruction.Pop(Register.B)

                if(expression.type is PointerType) {
                    val addMultiplier = expression.type.type.size.bytes
                    val shift = when(addMultiplier) {
                        1 -> 0
                        2 -> 1
                        4 -> 2
                        8 -> 3
                        else -> error("can't support things of that size yet")
                    }
                    if(shift != 0) {
                        this += AssemblyInstruction.Shl(SizedRegister(expression.type.type.size,accumulatorRegister), shift)
                    }
                }
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


    private fun assembleExpression(expression: VariableAccessExpression): Assembly = buildAssembly {
        val variable = variableBindings.first { it.name == expression.variableName }
        this += AssemblyInstruction.Mov(
            reg1 = SizedRegister(variable.register.size, accumulatorRegister).advanced(),
            reg2 = variable.register
        ).comment( "pulling variable ${expression.variableName}")
    }

    private fun assembleExpression(expression: DereferencePointerExpression): Assembly = buildAssembly {
        if(expression.pointerExpression.type !is PointerType) error("assertion failed")
        this += assembleExpression(expression.pointerExpression)
        this += AssemblyInstruction.Mov(
            reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(),
            reg2 = AdvancedRegister(
                register = SizedRegister(RegisterSize.BIT64,  accumulatorRegister),
                isMemory = true,
                size = expression.type.size)
        )
    }

    private fun assembleExpression(expression: FunctionCallExpression): Assembly = buildAssembly {
        // pretty much, just push all the arguments, call the function, then clean the stack
        val argumentSize = expression.arguments.size * 8

        expression.arguments.forEachIndexed { i,expr ->
            // evaluate and put the value in the register
            this += assembleExpression(expr)
            this += AssemblyInstruction.Push(accumulatorRegister).comment("argument ${expression.argumentNames[i]}")
        }
        // alright, everything is in the right registers
        this += AssemblyInstruction.Call(expression.functionIdentifier)
        this += AssemblyInstruction.Add(
            reg1 = SizedRegister(RegisterSize.BIT64, Register.SP).advanced(),
            reg2 = StaticValueAdvancedRegister(argumentSize, RegisterSize.BIT64)
        )
    }

    private fun assembleExpression(expression: IntegerValueExpression): Assembly = buildAssembly {
        this += AssemblyInstruction.Mov(
            reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(),
            reg2 = StaticValueAdvancedRegister(expression.value, expression.type.size)
        )
    }

    private fun assembleExpression(expression: EqualsExpression): Assembly = buildAssembly {
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
        this += AssemblyInstruction.SetCC(if(expression.negate) ComparisonOperator.NOT_EQUALS else ComparisonOperator.EQUALS, accumulatorRegister)
    }
    private fun assembleExpression(expression: StringLiteralExpression): Assembly = buildAssembly {
        val (label, dataEntry) = StringInterner.labelString(expression.value)
        if(dataEntry != null) {
            this += dataEntry
        }
        this += AssemblyInstruction.MovToLabel(accumulatorRegister, label)
    }

    private fun assembleBlockExpression(expression: BlockExpression): Assembly = buildAssembly {
        this += assembleBlock(variableBindings, ast, accumulatorRegister, Block(expression.statements + ExpressionStatement(expression.last)), topLocal, returnInstructions)
    }

    fun assembleExpression(expression: Expression): Assembly {
        return when(expression) {
            is MathExpression -> assembleExpression(expression)
            is VariableAccessExpression -> assembleExpression(expression)
            is DereferencePointerExpression -> assembleExpression(expression)
            is FunctionCallExpression -> assembleExpression(expression)
            is IntegerValueExpression -> assembleExpression(expression)
            is EqualsExpression -> assembleExpression(expression)
            is StringLiteralExpression -> assembleExpression(expression)
            is BlockExpression -> assembleBlockExpression(expression)
        }
    }

}

object StringInterner {
    private val map = mutableMapOf<String, String>()
    private const val prefix = "str_n"
    private var i: Int = 0
    fun labelString(stringLiteral: String): Pair<String,DataEntry?> {
        return when(val f = map[stringLiteral]) {
            null -> {
                val label = prefix + (++i)
                map[stringLiteral] = label
                label to DataEntry(
                        name = label,
                        data = stringLiteral.map { it.toByte().toInt() } + listOf(0) // strings are zero-terminated
                )
            }
            else -> f to null
        }
    }
    fun reset(){
        map.clear()
    }
}

fun assembleExpression(variableBindings: List<Variable>,
                       ast: AST,
                       expression: Expression,
                       accumulatorRegister: Register,
                       topLocal: Int,
                       returnInstructions: List<AssemblyInstruction>): Assembly {
    return ExpressionExistsState(variableBindings, ast, accumulatorRegister, topLocal, returnInstructions).assembleExpression(expression)
}