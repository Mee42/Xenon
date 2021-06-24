package dev.mee42.tvm

import dev.mee42.*
import dev.mee42.Function

class TVMBackend: Backend {
    override val name: String = "TVM"

    override fun process(ast: AST) = asm {
        // return String, probably
        this += Instr.Mov(Source.LabelValue("start_"), Register.RF)
        val functionsASM = asm inner@ {
            for (function in ast.functions.sortedBy { if (it.header.name == "main") 1 else 0 }) {
                this@inner += compileFunction(function, ast)
            }
        }
        // we need to store all the strings
        this += Instr.Label("start_")
        this += Instr.Call(Source.LabelValue("main"))
        this += Instr.Mov(Source.LabelValue("halt_"), Register.RF)
        for((string, key) in stringLookup) {
            this += Instr.Label("str_${key}_")
            this += Instr.Bytes(string.encodeToByteArray().toList().map { it.toUByte() } + listOf(0x00u))
        }
        this += functionsASM
        this += Instr.Label("halt_")
        this += Instr.Mov(Source.LabelValue("halt_"), Register.RF)
    }

}

val outFunction = asm {
    this += Instr.Label("out")
    this += Instr.PushW(Register.RD)
    this += Instr.Mov(Register.RE, Register.RD)
    // we'll use r0 for scrap
    this += Instr.OLoadB(addr = Register.RD, i = Source.Immediate(4), reg = Register.R0)
    this += Instr.Out(Register.R0)
    this += Instr.PopW(Register.RD)
    this += Instr.PopW(Register.RF)
}

/*
r0, r1                     - accumulator register. Used for return value
r2, r3, r4, r5             - preserved by callee
r6, r7, r8, r9, rA, rB, rC - trashed by callee
rD - stack frame pointer
rE - stack pointer
rF - instruction pointer

 */

// TODO get a better abi

private fun compileFunction(function: Function, ast: AST): Assembly {
    if(function.header.name == "out") return outFunction
    println("compiling ${function.header.name}...")


    val arguments = mutableMapOf<VariableIdentifier, ValueLocation.MemoryOffset>()
    var location = 4 // start at location +4
    for(arg in function.header.arguments.reversed()) {
        // the first argument is pushed first, then the last argument is given a location first
        arguments[arg.name] = ValueLocation.MemoryOffset(location, Register.RD)
        location += sizeOfType(arg.type, ast)
    }
    val preScope = Scope(variables = arguments, ast, function.header.name, null, 0)

    val variableSpaceSize = preScope.variableSizeSum(function.body)
    println("variable space size: $variableSpaceSize")
    val scope = Scope(variables = arguments, ast, function.header.name, null, variableSpaceSize)
    return asm {
        this += Instr.Label(function.header.name)
        this += Instr.PushW(Register.RD)
        this += Instr.Mov(Register.RE, Register.RD)
        this += Instr.Add(Register.RE, Source.Immediate(variableSpaceSize), Register.RE) // re = re + variableSpaceSize
        this += scope.compileExpr(function.body)
        this += Instr.Sub(Register.RE, Source.Immediate(variableSpaceSize), Register.RE) // re = re - variableSpaceSize
        this += Instr.PopW(Register.RD)
        this += Instr.PopW(Register.RF)
    }
}

private fun Scope.variableSizeSum(expr: Expr.Block): Int {
    // concatination of immutable sets
    // also flatMap doesn't do set stuff, it should for performance
    val blocks = expr.fold({ e: Expr ->
        if (e == expr) null else when(e) {
            is Expr.Block -> listOf(variableSizeSum(e))
            else -> null
        } }, { a, b -> a + b }, emptyList())

    val variables = expr.fold({
        if(it == expr) null else when(it) {
            is Expr.VariableDefinition -> listOf(sizeOfType(it.type))
            is Expr.Block -> emptyList()
            else -> null
        }
    }, { a, b -> a + b }, listOf())

    val variableSize =  variables.sum()
    val blockSize = blocks.maxOrNull() ?: 0
    return variableSize + blockSize
}



private fun Scope.sizeOfType(type: Type) = sizeOfType(type, this.ast)

// returns the number of bytes
private fun sizeOfType(type: Type, ast: AST): Int = when(type){
    is Type.Builtin -> type.size
    is Type.Generic -> error("generic types in backend???")
    Type.Nothing -> 0
    is Type.Pointer -> 2
    is Type.Struct -> {
        val structFields = fieldsFor(ast.structs.first { it.name == type.name }, type)
        structFields.map { (_, it) -> sizeOfType(it, ast) }.fold(0) { a, b -> a + b }
    }
    Type.Unit -> 0
}


// represents all places that values can exist in
// TODO support values that are not 16 bits
sealed interface ValueLocation {
    data class Register(val reg: dev.mee42.tvm.Register): ValueLocation // TODO16 bits
    data class MemoryOffset(val offset: Int,val reg: dev.mee42.tvm.Register): ValueLocation // this is a 16 bit value that's at offset from a register
}

private data class Scope(
    private val variables: Map<VariableIdentifier, ValueLocation.MemoryOffset>,
    val ast: AST,
    val currentFunctionName: VariableIdentifier,
    val parent: Scope?,
    val freeVariableSpace: Int,
    ) {

    fun lookupVariable(id: VariableIdentifier): ValueLocation.MemoryOffset? {
        return variables[id] ?: parent?.lookupVariable(id)
    }
}

private fun Scope.compileLValueExpr(expr: Expr): Assembly = when (expr) {
    is Expr.Deref -> {
        // *x
        // evaluate as lvalue
        // just evaluate the inner one and leave the address in r0
        compileExpr(expr.expr)
    }
    is Expr.VariableAccess -> asm {
        val location = lookupVariable(expr.variableName)
            ?: error("ICE: can not find variable")
        this += Instr.Add(
            a = location.reg,
            b = Source.Immediate(location.offset),
            c = Register.R0
        )
    }
    else -> error("$expr is not an lvalue")
}




private fun Scope.compileExpr(expr: Expr): Assembly = when(expr) {


    is Expr.Block -> asm {

        val variableDefinitions = expr.fold({
            if(it == expr) null else when(it) {
                is Expr.VariableDefinition -> listOf(it)
                is Expr.Block -> emptyList()
                else -> null
            }
        }, { a, b -> a + b }, emptyList())

        var topLocation = this@compileExpr.freeVariableSpace

        val variables = variableDefinitions.map { variable ->
            val location = ValueLocation.MemoryOffset(topLocation, Register.RD)
            topLocation -= sizeOfType(variable.type)

            variable.variableName to location
        }.toMap()
        println("compiling block, variables: $variables")
        val newScope = Scope(
            variables = variables,
            ast = ast,
            currentFunctionName = currentFunctionName,
            parent = this@compileExpr,
            freeVariableSpace = topLocation
        )

        this += expr.contents.map { newScope.compileExpr(it) }.flatten()
        this += Instr.Label(this@compileExpr.currentFunctionName + "_" + expr.label + "_end_")



    }



    is Expr.Loop -> asm {
        this += Instr.Label(this@compileExpr.currentFunctionName + "_" + expr.block.label + "_start_")
        this += expr.block.contents.map { compileExpr(it) }.flatten()
        this += Instr.Mov(Source.LabelValue(this@compileExpr.currentFunctionName + "_" + expr.block.label + "_start_"), Register.RF)
        this += Instr.Label(this@compileExpr.currentFunctionName + "_" + expr.block.label + "_end_")
    }
    is Expr.CharLiteral -> asm(Instr.Mov(Source.Immediate(expr.char.code), Register.R0))
    is Expr.FunctionCall -> {
        if(expr.header.name == "cast") {
            // then it's a noop, we'll assume it's a valid cast. You made the cast, after all
            // TODO add checking to make sure this isn't invalid
            compileExpr(expr.arguments[0])
        } else asm {
            for ((argExpr, arg) in expr.arguments.zip(expr.header.arguments)) {
                this += compileExpr(argExpr)// puts result into r0
                when {
                    sizeOfType(arg.type) == 1 -> this += Instr.PushB(Register.R0)
                    sizeOfType(arg.type) == 2 -> this += Instr.PushW(Register.R0)
                    else -> error("Don't support >2 byte types yet")
                }
            }

            this += Instr.Call(Source.LabelValue(expr.header.name))
            // puts the returned value in r0, so we're all good
            this += Instr.Add(
                Source.Immediate(expr.header.arguments.map { sizeOfType(it.type) }.sum()),
                Register.RE,
                c = Register.RE
            )
        }
    }

    is Expr.Break -> asm {
        // evaluate and put result in r0, then jump to the end of the block we're in
        this += compileExpr(expr.value)
        this += Instr.Mov(Source.LabelValue(this@compileExpr.currentFunctionName + "_" + expr.label + "_end_"), Register.RF)
    }
    is Expr.BinaryOp -> when(expr.op) {
        Operator.ADD -> asm {
            if ((expr.left.type !is Type.Builtin.INT && expr.left.type !is Type.Pointer) || (expr.right.type !is Type.Builtin.INT && expr.right.type !is Type.Pointer)) {
                TODO("only Int arithmetic supported rn")
            }

            this += compileExpr(expr.left)
            if (expr.right.type is Type.Pointer) {
                // if this is true, then the next one is NOT true
                this += Instr.Mul(
                    Register.R0,
                    Source.Immediate(sizeOfType(expr.right.type.inner)),
                    Register.R1,
                    Register.R0
                ) // store the top bits into r1, which is trashed
            }
            this += Instr.PushW(Register.R0)
            this += compileExpr(expr.right)
            if (expr.left.type is Type.Pointer) {
                // if this is true, then the above one is NOT true
                this += Instr.Mul(
                    Register.R0,
                    Source.Immediate(sizeOfType(expr.left.type.inner)),
                    Register.R1,
                    Register.R0
                ) // store the top bits into r1, which is trashed
            }
            this += Instr.PopW(Register.R1)
            this += Instr.Add(Register.R0, Register.R1, Register.R0)
        }
        Operator.EQUALS -> asm {
            // 0 = false
            // 1 = true
            // this is BITWISE COMPARISON
            if(sizeOfType(expr.left.type) <= 2) { // I think this works with both 8 and 16 bit values? not sure about 8 bit values
                this += compileExpr(expr.left)
                this += Instr.PushW(Register.R0) // push lh value
                this += compileExpr(expr.right)
                this += Instr.PopW(Register.R1) // pop lh value into b
                if(false) { // TODO use once flags work
                    this += Instr.ClearFlag() // clear the flag
                    this += Instr.IfEq(Register.R0, Register.R1); this += Instr.SetFlag()
                    // only run this line if they're equal, so the flag is set to the result

                    this += Instr.Mov(Source.Immediate(0), Register.R0) // set r0 to zero
                    this += Instr.IfFlag(); this += Instr.Mov(Source.Immediate(1), Register.R0)
                    // set r0 to 1 if the flag is set
                } else {
                    // this is a version that doesn't use the flags
                    this += Instr.Mov(Register.R0, Register.R2)
                    this += Instr.Mov(Source.Immediate(0), Register.R0)
                    this += Instr.IfEq(Register.R1, Register.R2); this += Instr.Mov(Source.Immediate(1), Register.R0)
                }
            }  else TODO("can't support types that big yet lol")
        }
        Operator.GREATER_THAN -> asm {
            if(expr.left.type !is Type.Builtin.INT || expr.right.type !is Type.Builtin.INT) {
                TODO("not supported yet")
            }
            this += compileExpr(expr.left)
            this += Instr.PushW(Register.R0)
            this += compileExpr(expr.right)
            this += Instr.Mov(Register.R0, Register.R2)
            this += Instr.PopW(Register.R1)
            // left > right
            // r1 > r2
            this += Instr.Mov(Source.Immediate(0), Register.R0)
            this += Instr.IfGS(Register.R1, Register.R2)
            this += Instr.Mov(Source.Immediate(1), Register.R0)
        }
        Operator.DIVIDE -> asm {
            if(expr.left.type !is Type.Builtin.INT || expr.right.type !is Type.Builtin.INT) {
                TODO("not supported yet")
            }
            this += compileExpr(expr.left)
            this += Instr.PushW(Register.R0)
            this += compileExpr(expr.right)
            this += Instr.PopW(Register.R1)
            // r1 / r0
            this += Instr.IDiv(Register.R1, Register.R0, Register.R0, Register.R1)
        }
        Operator.REM -> asm {
            if(expr.left.type !is Type.Builtin.INT || expr.right.type !is Type.Builtin.INT) {
                TODO("not supported yet")
            }
            this += compileExpr(expr.left)
            this += Instr.PushW(Register.R0)
            this += compileExpr(expr.right)
            this += Instr.PopW(Register.R1)
            // r1 / r0
            this += Instr.IDiv(Register.R1, Register.R0, Register.R1, Register.R0)
        }
        Operator.SUB -> asm {
            if(expr.left.type !is Type.Builtin.INT || expr.right.type !is Type.Builtin.INT) {
                TODO("not supported yet")
            }
            this += compileExpr(expr.left)
            this += Instr.PushW(Register.R0)
            this += compileExpr(expr.right)
            this += Instr.PopW(Register.R1)
            // r1 - r0 -> r0
            this += Instr.Sub(Register.R1, Register.R0, Register.R0)
        }
        else -> TODO("operator ${expr.op} not supported yet")
    }
    is Expr.VariableAccess -> asm {
        val pos = lookupVariable(expr.variableName) ?: error("ICE: Variable not in scope in backend ${expr.variableName}")
        this += Instr.OLoadW(pos.reg, Source.Immediate(pos.offset), reg = Register.R0)
    }

    is Expr.NumericalLiteral -> {
        if(expr.t != Type.Builtin.INT) TODO("only supporting ints right now lol")
        asm {
            this += Instr.Mov(Source.Immediate(expr.i), Register.R0)
        }
    }
    is Expr.Deref -> asm {
        this += compileExpr(expr.expr)
        when (val size = sizeOfType(expr.type)) {
            1 -> {
                this += Instr.LoadB(Register.R0, Register.R0)
            }
            2 -> {
                this += Instr.LoadW(Register.R0, Register.R0)
            }
            else -> error("Can't load type of $size bytes")
        }
    }
    is Expr.Assignment -> asm {
        if(expr.left is Expr.VariableAccess) {
            // if it's in a register, we need to explicitly handle this case
            val location = lookupVariable(expr.left.variableName) ?: error("ICE: variable not defined in scope, but got to tvm backend")
            this += compileExpr(expr.right)
            when (sizeOfType(expr.left.type)) {
                1 -> this += Instr.OStoreB(
                    value = Register.R0,
                    addr = location.reg,
                    offset = Source.Immediate(location.offset)
                )
                2 -> this += Instr.OStoreW(
                    value = Register.R0,
                    addr = location.reg,
                    offset = Source.Immediate(location.offset)
                )
                else -> error("more than two bytes, am helpless")
            }
        } else {
            this += compileLValueExpr(expr.left)
            this += Instr.PushW(Register.R0) // the value of the pointer
            this += compileExpr(expr.right)
            this += Instr.PopW(Register.R1) // the value of the left hand
            when (sizeOfType(expr.left.type)) {
                1 -> this += Instr.StoreB(Register.R0, Register.R1)
                2 -> this += Instr.StoreW(Register.R0, Register.R1)
                else -> error("more than 2 bytes, no!")
            }
        }

    }
    is Expr.StringLiteral -> asm {
        val strAddr = stringIDFor(expr.content)
        this += Instr.Mov(Source.LabelValue("str_${strAddr}_"), Register.R0)
    }
    is Expr.If -> asm {
        val elseLabel = genRandomString() + "_else_"
        val endLabel = genRandomString() + "_end_"

        this += compileExpr(expr.cond) // will evaluate to a boolean
        // 0 = false
        // not 0 = true
        // if ra is 0, then we want to jump
        this += Instr.IfEq(Source.Immediate(0), Register.R0)
        this += Instr.Mov(Source.LabelValue(elseLabel), Register.RF) // jump to else: if it's false
        this += compileExpr(expr.ifBlock) // otherwise, evaluate the if block

        this += Instr.Mov(Source.LabelValue(endLabel), Register.RF) // skip the else block

        this += Instr.Label(elseLabel) // else:
        this += expr.elseBlock?.let { compileExpr(it) } ?: asm{} // if there's no else block, do nothing

        this += Instr.Label(endLabel)
    }
    is Expr.Unit -> asm {}
    is Expr.Ref -> asm {
        // &x
        //
        this += compileLValueExpr(expr.expr)
    }
    is Expr.VariableDefinition -> asm {
        val location = lookupVariable(expr.variableName) ?: error("ICE can't find variable ${expr.variableName}")
        this += compileExpr(expr.value)
        // value is in r0
        this += when(sizeOfType(expr.type)) {
            1 -> Instr.OStoreB(Register.R0, addr = location.reg, offset = Source.Immediate(location.offset))
            2 -> Instr.OStoreW(Register.R0, addr = location.reg, offset = Source.Immediate(location.offset))
            else -> error("structs? bad")
        }
    }

    // we'll get to these
    is Expr.Continue -> TODO()
    is Expr.MemberAccess -> TODO()
    is Expr.StructDefinition -> TODO()
}






