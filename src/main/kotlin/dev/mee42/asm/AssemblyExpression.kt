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

data class ExpressionState(val variableBindings: List<Variable>,
                           val ast: AST,
                           val topLocal: Int,
                           val structLocal: Int,
                           val returnInstructions: List<AssemblyInstruction>)

fun assembleLValue(expression: LValueExpression, s: ExpressionState) = buildAssembly {
    when(expression) {
        is VariableAccessExpression -> {
            val varRegister: AdvancedRegister = s.variableBindings.first { it.name == expression.variableName }.register
            this += AssemblyInstruction.Custom("lea rax, " + varRegister.toStringNoSize())
        }
        is DereferencePointerExpression -> {
            this += assembleExpression(expression.pointerExpression, s)
        }
        is MemberAccessExpression -> {
            this += assembleExpression(expression.struct, s)
            val offset =  (expression.struct.type as StructType).struct.offsetOf(expression.member)
            // add the offset lmao
            this += AssemblyInstruction.Add(
                    SizedRegister(RegisterSize.BIT64, Register.A).advanced(),
                    StaticValueAdvancedRegister(offset, RegisterSize.BIT64)
            )
        }
    }
}
fun assembleExpression(expression: Expression, s: ExpressionState, needsValue: Boolean = true): Assembly = buildAssembly {
    when(expression) {
        is VariableAccessExpression -> {
            val variable = s.variableBindings.firstOrNull { it.name == expression.variableName } ?: error("can't find variable ${expression.variableName}")
            if(variable.type is StructType) {
                this += AssemblyInstruction.Custom("lea rax, " + variable.register.toStringNoSize())
            } else {
                this += AssemblyInstruction.Mov(
                        reg1 = SizedRegister(variable.register.size, Register.A).advanced(),
                        reg2 = variable.register
                ).comment("pulling variable ${expression.variableName}")
            }
        }
        is DereferencePointerExpression -> {
            if(expression.pointerExpression.type !is PointerType) error("assertion failed")
            this += assembleExpression(expression.pointerExpression, s)
            this += AssemblyInstruction.Mov(
                    reg1 = SizedRegister(expression.type.size.fitToRegister(), Register.A).advanced(),
                    reg2 = AdvancedRegister(
                            register = SizedRegister(RegisterSize.BIT64,  Register.A),
                            isMemory = true,
                            size = expression.type.size.fitToRegister())
            )
        }
        is TypelessBlock -> {
            for(elem in expression.expressions) {
                this += assembleExpression(elem, s, needsValue = false)
            }
        }
        is BlockExpression -> {
            assembleBlock(s.variableBindings, s.ast, Block(expression.statements), s.topLocal, s.structLocal,  s.returnInstructions)

        }
        is RefExpression -> {
            this += assembleLValue(expression.lvalue, s) // returns the pointer to the thing
        }
        is IntegerValueExpression -> {
            this += AssemblyInstruction.Mov(
                    reg1 = SizedRegister(expression.type.size.fitToRegister(), Register.A).advanced(),
                    reg2 = StaticValueAdvancedRegister(expression.value, expression.type.size.fitToRegister())
            )
        }
        is StringLiteralExpression -> {
            val (label, dataEntry) = StringInterner.labelString(expression.value)
            if(dataEntry != null) {
                this += dataEntry
            }
            this += AssemblyInstruction.MovToLabel(Register.A, label)
        }
        is ComparisonExpression -> {
            this += assembleComparisonExpression(expression, s, needsValue)
        }
        is AssigmentExpression -> {
            this += assembleLValue(expression.setLocation, s)
            this += AssemblyInstruction.Custom("push rax ; push the rax value")
            this += assembleExpression(expression.value, s)
            this += AssemblyInstruction.Pop(Register.B)
            // what's the size?
            this += AssemblyInstruction.Mov(
                    AdvancedRegister(SizedRegister(RegisterSize.BIT64,Register.B), true,expression.value.type.size.toRegisterSize()),
                    SizedRegister(expression.value.type.size.toRegisterSize(), Register.A).advanced()
            )
        }
        is FunctionCallExpression -> {
            for(expr in expression.arguments){
                this += assembleExpression(expr, s)
//                this += AssemblyInstruction.Push(Register.A)
                // so it's in the A register, but unknown size
                val size = expr.type.size
                this += AssemblyInstruction.Custom("sub rsp, " + size .bytes)
                if(expr.type is StructType) {
                    // copy memory to sp
                    this += AssemblyInstruction.Custom("mov rcx, " + size.bytes) // this many bytes
                    this += AssemblyInstruction.Custom("mov rsi, rax")
//                    val a = AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.BP),true,  RegisterSize.BIT64, structLocal)
                    this += AssemblyInstruction.Custom("mov rdi, rsp")
                    this += AssemblyInstruction.Custom("rep movsb")
                } else {
                    this += AssemblyInstruction.Mov(
                            AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.SP), true, size.toRegisterSize()),
                            SizedRegister(size.toRegisterSize(), Register.A).advanced()
                    )
                }
            }
            this += AssemblyInstruction.Call(expression.functionIdentifier)
            val argumentSize = expression.arguments.sumBy { it.type.size.bytes }
            this += AssemblyInstruction.Add(
                    reg1 = SizedRegister(RegisterSize.BIT64, Register.SP).advanced(),
                    reg2 = StaticValueAdvancedRegister(argumentSize, RegisterSize.BIT64)
            )
        }
        is MemberAccessExpression -> {
            this += assembleExpression(expression.struct, s)
            val offset =  (expression.struct.type as StructType).struct.offsetOf(expression.member)
            // add the offset lmao
            this += AssemblyInstruction.Add(
                    SizedRegister(RegisterSize.BIT64, Register.A).advanced(),
                    StaticValueAdvancedRegister(offset, RegisterSize.BIT64)
            )
            if(expression.type !is StructType) { // we need to put the value in the register
                // it's expected in the register, dummy
                this += AssemblyInstruction.Mov(
                        AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.A), false, RegisterSize.BIT64),
                        AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.A), true, RegisterSize.BIT64)
                )
            }
        }
        is StructInitExpression -> {
            // okay great, so lets just allocate some memory into the structLocal register
             // IMPROVE zero memory on init (lol)
            // at the moment though, fuck what memory we use

            // outputs the memory address that the struct exists at, which is the structLocal
            this += AssemblyInstruction.Custom("lea rax, " +
                    AdvancedRegister(SizedRegister(RegisterSize.BIT64, Register.BP), true, RegisterSize.BIT64, s.structLocal).toStringNoSize()
            )
        }

    }
}

fun assembleExpression(variableBindings: List<Variable>,
                       ast: AST,
                       expression: Expression,
                       topLocal: Int,
                       structLocal: Int,
                       returnInstructions: List<AssemblyInstruction>, needsValue: Boolean = true): Assembly {
    return assembleExpression(expression, ExpressionState(variableBindings, ast, topLocal,structLocal,  returnInstructions), needsValue = needsValue)
}

private fun assembleComparisonExpression(expression: ComparisonExpression, s: ExpressionState, needsValue: Boolean = true): Assembly = buildAssembly {
    this += assembleExpression(expression.var1, s)
    this += AssemblyInstruction.Push(Register.A)
    this += assembleExpression(expression.var2, s)
    val size = expression.type.size.fitToRegister()
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
                    this += AssemblyInstruction.Shl(SizedRegister(size,Register.A), shift)
                }
            }
            this += AssemblyInstruction.Add(
                    reg1 = SizedRegister(size, Register.A).advanced(),
                    reg2 = SizedRegister(size, Register.B).advanced()
            )
        }
        MathType.SUB -> {
            this += AssemblyInstruction.Mov(
                    reg1 = Register.B,
                    reg2 = Register.A,
                    size = size
            ).zeroIfNeeded()
            this += AssemblyInstruction.Pop(Register.A)
            this += AssemblyInstruction.Sub(
                    reg1 = SizedRegister(size, Register.A).advanced(),
                    reg2 = SizedRegister(size, Register.B).advanced()
            )
        }
        MathType.MULT -> {
            this += AssemblyInstruction.Pop(Register.B)
            this += AssemblyInstruction.Mul(reg1 = SizedRegister(size, Register.B).advanced())
        }
        MathType.DIV -> {
            this += AssemblyInstruction.Xor(
                    reg1 = SizedRegister(RegisterSize.BIT64, Register.D),
                    reg2 = SizedRegister(RegisterSize.BIT64, Register.D).advanced())
            val type = expression.type
            type as? BaseType ?: error("debug this later")
            if(size == RegisterSize.BIT8) {
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
                this += AssemblyInstruction.Mov(reg1 = Register.B, reg2 = Register.A, size = size)
            }
            this += AssemblyInstruction.Pop(Register.A)
            this += AssemblyInstruction.divOf(
                    size = size,
                    signed = !type.type.isUnsigned,
                    divisor = SizedRegister(size, Register.B))
        }
        MathType.NOT_EQUALS, MathType.EQUALS -> {
            this += AssemblyInstruction.Pop(Register.B)

            this += AssemblyInstruction.Compare(
                    SizedRegister(size, Register.A),
                    SizedRegister(size, Register.B).advanced()
            )
            this += AssemblyInstruction.SetCC(if(expression.mathType == MathType.NOT_EQUALS) ComparisonOperator.NOT_EQUALS else ComparisonOperator.EQUALS, Register.A)
        }
    }

}