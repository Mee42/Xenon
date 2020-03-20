package dev.mee42.asm

import dev.mee42.parser.*
import kotlin.math.abs
import kotlin.random.Random

fun assemble(ast: AST):List<AssemblyInstruction> {
    return ast.functions
        .filterIsInstance<XenonFunction>()
        .map { assemble(it, ast) }.flatten()
}
private class Variable(val name: String, val type: Type, val register: SizedRegister, isFinal: Boolean)

private fun assemble(function: XenonFunction, ast: AST): List<AssemblyInstruction> {
    println("compiling function ${function.name}, ast: ${function.content}")
    return buildList {

        // these are the registers needed to call this function:
        if(function.arguments.size > Register.argumentRegisters.size) error("can't support more then ${Register.argumentRegisters.size} arguments to a function as of right now, lol")
        val variableBindings = mutableListOf<Variable>()
        val returnRegister = SizedRegister(function.returnType.size, Register.A)
        val accumulatorRegister = returnRegister.register

        // so we don't use these variables on accident
        variableBindings.add(Variable("_ret", function.returnType, returnRegister, true))
        variableBindings.add(Variable("_acc",DynamicType, SizedRegister(RegisterSize.BIT64, accumulatorRegister), true))

        this += AssemblyInstruction.CommentedLine(
            line = AssemblyInstruction.Label(function.name),
            comment = "return value in register $returnRegister")
        // so the argument registers are
        function.arguments.forEachIndexed { i, it ->
            val size = it.type.size
            val register = SizedRegister(size, Register.argumentRegisters[i])
            variableBindings.add(Variable(it.name, it.type, register, true))
            this += AssemblyInstruction.Comment("argument $i (${it.name}) in register $register")
        }
        for(statement in function.content.statements) {
            when (statement) {
                is ReturnStatement -> {
                    val expression = statement.expression
                    this += assembleExpression(variableBindings, ast, expression, accumulatorRegister)
                    this += AssemblyInstruction.Ret
                }
                is ExpressionStatement -> {
                    this += assembleExpression(variableBindings, ast, statement.expression, accumulatorRegister)
                }
                else -> TODO("can't support that type of statement")
            }
            this += AssemblyInstruction.Comment("")
        }


    }
}

// output assembly that, using the variables in the registers
// computes the value of the expression
// when this assembly completes, the following must be true
// - only the rbx register has changed values
// - the rbx register is has the evaluated value of this expression
// - the stack is in the same state it entered in
private fun assembleExpression(variableBindings: List<Variable>, ast: AST, expression: Expression, accumulatorRegister: Register): List<AssemblyInstruction> {
    return when(expression) {
        is MathExpression -> {
            // alright, here's the real test
            // reserve a free register
            val id = abs(Random.nextInt() % 100)
            when (expression.mathType) {
                in listOf(MathType.ADD, MathType.SUB) -> {
                    buildList {
                        val (newBindings, reservedRegister) = variableBindings.reserveOne(expression.type)
                        this += AssemblyInstruction.Push(reservedRegister.register).comment("starting add/sub $id")

                        this += assembleExpression(newBindings, ast, expression.var1, accumulatorRegister)
                        this += AssemblyInstruction.Push(accumulatorRegister)

                        this += assembleExpression(newBindings, ast, expression.var2, accumulatorRegister)
                        this += AssemblyInstruction.Pop(reservedRegister.register)
                        if(expression.mathType == MathType.SUB) {
                            this += AssemblyInstruction.Push(accumulatorRegister)
                            this += AssemblyInstruction.Push(reservedRegister.register)
                            this += AssemblyInstruction.Pop(accumulatorRegister)
                            this += AssemblyInstruction.Pop(reservedRegister.register)
                        }
                        this += when (expression.mathType) {
                            MathType.ADD -> AssemblyInstruction::Add
                            MathType.SUB -> AssemblyInstruction::Sub
                            else -> error("internal error")
                        }(
                            SizedRegister(expression.type.size, accumulatorRegister).advanced(),
                            reservedRegister.advanced()
                        )

                        this += AssemblyInstruction.Pop(reservedRegister.register).comment("ending add/sub $id")
                    }
                }
                MathType.MULT -> {
                    buildList {
                        if(accumulatorRegister != Register.A) error("need special code to deal with this")
                        this += AssemblyInstruction.Comment("starting mult $id")
                        this += assembleExpression(variableBindings, ast, expression.var1, accumulatorRegister)
                        // we need to use B as a temporary variable
                        this += AssemblyInstruction.Push(Register.B)
                        // move A to B
                        this += AssemblyInstruction.Mov(
                            reg1 = SizedRegister(expression.type.size, Register.B).advanced(),
                            reg2 = SizedRegister(expression.type.size, accumulatorRegister).advanced()
                        )
                        this += assembleExpression(variableBindings, ast, expression.var2, accumulatorRegister)
                        // run the math thing
                        this += AssemblyInstruction.Push(Register.D)
                        this += AssemblyInstruction.Mul(SizedRegister(expression.type.size, Register.B).advanced())
                        this += AssemblyInstruction.Pop(Register.D)

                        this += AssemblyInstruction.Pop(Register.B).comment("ending mult $id")
                    }
                }
                MathType.DIV -> {
                    // apparently div needs some extra treatment
                    buildList {
                        if(accumulatorRegister != Register.A) error("need special code to deal with this")
                        this += AssemblyInstruction.Comment("starting div $id")
                        this += assembleExpression(variableBindings, ast, expression.var1, accumulatorRegister)
                        // we need to use B as a temporary variable
                        this += AssemblyInstruction.Push(Register.B)
                        // move A to B
                        this += AssemblyInstruction.Mov(
                            reg1 = SizedRegister(expression.type.size, Register.B).advanced(),
                            reg2 = SizedRegister(expression.type.size, accumulatorRegister).advanced()
                        )
                        this += assembleExpression(variableBindings, ast, expression.var2, accumulatorRegister)
                        // guess we gotta switch A and B
                        this += AssemblyInstruction.Xor(
                            reg1 = SizedRegister(expression.type.size, Register.A).advanced(),
                            reg2 = SizedRegister(expression.type.size, Register.B).advanced()
                        )
                        this += AssemblyInstruction.Xor(
                            reg1 = SizedRegister(expression.type.size, Register.B).advanced(),
                            reg2 = SizedRegister(expression.type.size, Register.A).advanced()
                        )

                        this += AssemblyInstruction.Xor(
                            reg1 = SizedRegister(expression.type.size, Register.A).advanced(),
                            reg2 = SizedRegister(expression.type.size, Register.B).advanced()
                        )

                        /* this is some special div shit */
                        this += AssemblyInstruction.Push(Register.D)
                        this += AssemblyInstruction.Xor(
                            reg1 = SizedRegister(RegisterSize.BIT64,Register.D).advanced(),
                            reg2 = SizedRegister(RegisterSize.BIT64, Register.D).advanced()
                        )
                        this += AssemblyInstruction.Div(SizedRegister(expression.type.size, Register.B).advanced())
                        this += AssemblyInstruction.Pop(Register.D)
                        this += AssemblyInstruction.Pop(Register.B).comment("ending div $id")
                    }
                }
                else -> TODO()
            } // all this math code
        }
        is VariableAccessExpression -> {
            val variable = variableBindings.first { it.name == expression.variableName }
            listOf(AssemblyInstruction.Mov(
                reg1 = SizedRegister(variable.register.size, accumulatorRegister).advanced(),
                reg2 = variable.register.advanced()
            ).comment( "pulling variable ${expression.variableName}"))
        }
        is DereferencePointerExpression -> {
            buildList {
                if(expression.pointerExpression.type !is PointerType) error("assertion failed")
                this += assembleExpression(variableBindings, ast, expression.pointerExpression, accumulatorRegister)
                this += AssemblyInstruction.Mov(
                    reg1 = SizedRegister(expression.type.size, accumulatorRegister).advanced(false),
                    reg2 = SizedRegister(RegisterSize.BIT64,  accumulatorRegister).advanced(true)
                )
            }
        }
        is FunctionCallExpression -> {
            buildList {
                // alright, this is more of a pain
                val argumentRegisters = expression.arguments
                    .mapIndexed { index, expression -> Register.argumentRegisters[index] to expression }
                // push all of the argument registers used
                argumentRegisters.forEach { (reg, _) ->
                    this += AssemblyInstruction.Push(reg)
                }
                argumentRegisters.forEachIndexed { i, (reg, expr) ->
                    // evaluate and put the value in the register
                    this += assembleExpression(variableBindings, ast, expr, accumulatorRegister)
                    this += AssemblyInstruction.Mov(
                        reg1 = SizedRegister(expr.type.size, reg).advanced(),
                        reg2 = SizedRegister(expr.type.size, accumulatorRegister).advanced()
                    ).comment("argument ${expression.argumentNames[i]}")
                }
                // alright, everything is in the right registers
                this += AssemblyInstruction.Call(expression.function)
                // call it
                // returns in rax, so it's already perfect
                argumentRegisters.asReversed().forEach { (reg,_) ->
                    this += AssemblyInstruction.Pop(reg)
                }
            }
        }
    }

}
private fun AssemblyInstruction.comment(str: String): AssemblyInstruction {
    return AssemblyInstruction.CommentedLine(this, str)
}

private fun List<Variable>.reserveOne(type: Type): Pair<List<Variable>, SizedRegister> {
    val register = Register.usable.firstOrNull { this.none { variable -> variable.register.register == it } }
        ?: error("ran out of registers oof")
    fun Int.toReservedRegisterName(): String = "_reg_reserved_$this"
    val name = (0..1000).first {
        this.none { variable -> variable.name == it.toReservedRegisterName() }
    }.toReservedRegisterName()
    val sized =  SizedRegister(type.size, register)
    return (this + Variable(name,type,sized, true)) to sized
}

private fun SizedRegister.advanced(isMemory: Boolean = false): AdvancedRegister {
    return AdvancedRegister(
        isMemory = isMemory,
        register = this)
}
private fun <T> buildList(block: MutableList<T>.() -> Unit): List<T> {
    val list = mutableListOf<T>()
    block(list)
    return list
}