package dev.mee42.asm

import dev.mee42.parser.*
import dev.mee42.parser.Function
import kotlin.math.exp

fun assemble(ast: AST):List<AssemblyInstruction> {
    return ast.functions.map { assemble(it, ast) }.flatten()
}
private class Variable(val name: String, val type: Type, val register: SizedRegister, isFinal: Boolean)

private fun assemble(function: Function, ast: AST): List<AssemblyInstruction> {
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
        val statement = function.content.statements.first()
        // alright, now let's look at our first instruction
        // only one statement is supported right now lol
        if(statement !is ReturnStatement){
            error("only return statements allowed right now")
        }
        // statement is ReturnStatement: everything after this line assumes that, everything before this line should act like it could be not
        val expression = statement.expression
        this += assembleExpression(variableBindings, ast, expression, accumulatorRegister)
        // next line is not needed if the returnRegister is the same as the accumulator register
        //this += AssemblyInstruction.Mov(returnRegister.advanced(), SizedRegister(returnRegister.size, accumulatorRegister).advanced())
        this += AssemblyInstruction.Ret
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
            when (expression.mathType) {
                in listOf(MathType.ADD, MathType.SUB) -> {
                    buildList {
                        val (newBindings, reservedRegister) = variableBindings.reserveOne(expression.type)
                        this += AssemblyInstruction.Push(reservedRegister.register)

                        this += assembleExpression(newBindings, ast, expression.var1, accumulatorRegister)
                        this += AssemblyInstruction.Push(accumulatorRegister)

                        this += assembleExpression(newBindings, ast, expression.var2, accumulatorRegister)
                        this += AssemblyInstruction.Pop(reservedRegister.register)

                        this += when (expression.mathType) {
                            MathType.ADD -> AssemblyInstruction::Add
                            MathType.SUB -> AssemblyInstruction::Sub
                            else -> error("internal error")
                        }(
                            SizedRegister(expression.type.size, accumulatorRegister).advanced(),
                            reservedRegister.advanced()
                        )

                        this += AssemblyInstruction.Pop(reservedRegister.register)
                    }
                }
                MathType.MULT -> {
                    buildList {
                        if(accumulatorRegister != Register.A) error("need special code to deal with this")
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
                        this += AssemblyInstruction.Mul(SizedRegister(expression.type.size, Register.B).advanced())
                        this += AssemblyInstruction.Pop(Register.B)
                    }
                }
                MathType.DIV -> {
                    // apparently div needs some extra treatment
                    buildList {
                        if(accumulatorRegister != Register.A) error("need special code to deal with this")
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
                        this += AssemblyInstruction.Pop(Register.B)
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
            ))
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
    }

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