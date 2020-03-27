package dev.mee42.asm

import dev.mee42.asm.RegisterSize.*



sealed class AssemblyInstruction(strIn: String) {
    open val str = "    $strIn"
    class Call(labelName: String): AssemblyInstruction("call $labelName")
    object Ret: AssemblyInstruction("ret")
    object Nop: AssemblyInstruction("nop")

    class Mov(val reg1: AdvancedRegister, val reg2: AdvancedRegister): AssemblyInstruction("mov $reg1, $reg2") {
        constructor(reg1: Register, reg2: Register, size: RegisterSize) : this(
            reg1 = SizedRegister(size, reg1).advanced(),
            reg2 = SizedRegister(size, reg2).advanced()
        )
    }
    class MovSX(reg1: SizedRegister, reg2: AdvancedRegister): AssemblyInstruction("movsx $reg1, $reg2")
    class MovZX(reg1: SizedRegister, reg2: AdvancedRegister): AssemblyInstruction("movzx $reg1, $reg2")


    class Add(reg1: AdvancedRegister, reg2: AdvancedRegister): AssemblyInstruction("add $reg1, $reg2")
    class Sub(reg1: AdvancedRegister, reg2: AdvancedRegister): AssemblyInstruction("sub $reg1, $reg2")

    //class IMul(reg1: AdvancedRegister): AssemblyInstruction("    imul $reg1")
    class Mul(reg1: AdvancedRegister): AssemblyInstruction("mul $reg1")
    class Div(reg1: AdvancedRegister): AssemblyInstruction("div $reg1")
    class IDiv(reg1: AdvancedRegister): AssemblyInstruction("idiv $reg1")

    object ConvertToOctoword: AssemblyInstruction("cqo")
    object ConvertToQuadword: AssemblyInstruction("cdq")
    object ConvertToDoublword: AssemblyInstruction("cwd")

    class Push(register: Register): AssemblyInstruction("push ${SizedRegister(BIT64, register)}")
    class Pop(register: Register): AssemblyInstruction("pop ${SizedRegister(BIT64, register)}")

    class Custom(str: String): AssemblyInstruction(str)

    class Xor(reg1: SizedRegister, reg2: AdvancedRegister): AssemblyInstruction("xor $reg1, $reg2") {
        companion object {
            fun useToZero(register: SizedRegister): Xor {
                return Xor(
                    reg1 = register,
                    reg2 = register.advanced()
                )
            }
        }
    }
    class Label(val name: String): AssemblyInstruction("$name:") {
        companion object {
            private var used = 0
            fun next():Label {
                return Label("L" + ++used)
            }
        }

        override val str: String = "$name:"
    }
    class Jump(to: String): AssemblyInstruction("jmp $to")
    class ConditionalJump(conditional: ComparisonOperator, label: Label): AssemblyInstruction("${conditional.jump} ${label.name}")
    class Comment(content: String): AssemblyInstruction("; $content")
    class CommentedLine(line: AssemblyInstruction, comment: String): AssemblyInstruction("OHNO") {
        override val str: String = "$line ; $comment"
    }

    class SetCC(comparison: ComparisonOperator, register: Register):
        AssemblyInstruction("${comparison.setcc} ${SizedRegister(BIT8,register)}")

    class Compare(reg1: SizedRegister, reg2: AdvancedRegister):AssemblyInstruction("cmp $reg1, $reg2"){
        init {
            if(reg1.size != reg2.size) error("uh, no")
        }
    }

    override fun toString() =  str


    companion object {
        // note: will trash rdx
        // note: the A register better be prepared better have the right
        fun divOf(size: RegisterSize, divisor: SizedRegister, signed: Boolean) : List<AssemblyInstruction> {
            return when(size){
                BIT64 -> buildList {
                    if(signed){
                        this += ConvertToOctoword
                        this += IDiv(divisor.advanced())
                    } else {
                        this += Xor.useToZero(SizedRegister(BIT64, Register.D))
                        this += Div(divisor.advanced())
                    }
                }
                BIT32 -> buildList {
                    if(signed) {
                        this += ConvertToQuadword
                        this += IDiv(divisor.advanced())
                    } else {
                        this += Xor.useToZero(SizedRegister(BIT32, Register.D))
                        this += Div(divisor.advanced())
                    }
                }
                BIT16 -> buildList {
                    if(signed) {
                        this += ConvertToDoublword
                        this += IDiv(divisor.advanced())
                    } else {
                        this += Xor.useToZero(SizedRegister(size, Register.D))
                        this += Div(divisor.advanced())
                    }
                }
                BIT8 -> buildList {
                    if(signed) {
                        this += MovSX(
                            reg1 = SizedRegister(BIT16, Register.A),
                            reg2 = SizedRegister(BIT8, Register.A).advanced()
                        )
                        this += IDiv(divisor.advanced())
                    } else {
                        this += Custom("xor ah, ah")
                        this += Div(divisor.advanced())
                    }
                }
            }
        }
    }
}


enum class ComparisonOperator(val setcc: String, val jump: String) {
    EQUALS("sete", "je"),
    NOT_EQUALS("setne","jne")
}