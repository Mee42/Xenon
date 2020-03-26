package dev.mee42.asm

import dev.mee42.asm.RegisterSize.*


enum class RegisterSize(val bytes: Int, val asmName: String) {
    BIT64(8, "QWORD"),
    BIT32(4, "DWORD"),
    BIT16(2, "WORD"),
    BIT8(1, "BYTE"),
}

enum class Register(val bit64: String, val bit32: String, val bit16: String, val bit8: String) {
    A("rax","eax","ax","al"),
    B("rbx","ebx","bx","bl"),
    C("rcx","ecx","cx","cl"),
    D("rdx","edx","dx","dl"),
    SI("rsi","esi","si","sil"),
    DI("rdi","edi","di","dil"),

    BP("rbp","ebp","bp","bpl"),
    SP("rsp","esp","sp","spl"),

    R8(8),
    R9(9),
    R10(10),
    R11(11),
    R12(12),
    R13(13),
    R14(14),
    R15(15),
    ERROR(-1);

    constructor(i: Int): this("r$i","r${i}d","r${i}w","r${i}b")
    companion object {
        val argumentRegisters = listOf(B, C, D, R8, R9)
        val usable = listOf(B, C, D, R8, R9, R10, R11, R12, R13, R14, R15)
    }
}

class SizedRegister(val size: RegisterSize, val register: Register) {
    override fun toString() = when(size) {
        BIT64 -> register.bit64
        BIT32 -> register.bit32
        BIT16 -> register.bit16
        BIT8 -> register.bit8
    }
}

class StaticValueAdvancedRegister(private val value: Long, size: RegisterSize): AdvancedRegister(SizedRegister(size, Register.ERROR), false,size) {
    override fun toString(): String {
        return size.asmName + " $value"
    }
    constructor(value: Int, size: RegisterSize): this(value.toLong(), size)
}
open class AdvancedRegister(val register: SizedRegister, val isMemory: Boolean,  val size: RegisterSize, val offset: Int = 0) {
    override fun toString(): String {
        if(!isMemory) return register.toString()
        if(offset == 0) return "[$register]"
        if(offset > 0) return "[$register + $offset]"
        // offset < 0
        return "[$register - ${-1 * offset}]"
    }
    init {
        if(isMemory && register.size != BIT64) error("all memory access must be 64-bit")
        if(offset != 0 && !isMemory) error("can't have an offset without accessing memory")
    }
}

sealed class AssemblyInstruction(open val str: String) {
    class Call(labelName: String): AssemblyInstruction("    call $labelName")
    object Ret: AssemblyInstruction("    ret")
    object Nop: AssemblyInstruction("    nop")

    class Mov(val reg1: AdvancedRegister, val reg2: AdvancedRegister): AssemblyInstruction("    mov $reg1, $reg2") {
        constructor(reg1: Register, reg2: Register, size: RegisterSize) : this(
            reg1 = SizedRegister(size, reg1).advanced(),
            reg2 = SizedRegister(size, reg2).advanced()
        )
    }
    class MovSX(reg1: SizedRegister, reg2: AdvancedRegister): AssemblyInstruction("    movsx $reg1, $reg2")
    class MovZX(reg1: SizedRegister, reg2: AdvancedRegister): AssemblyInstruction("    movzx $reg1, $reg2")


    class Add(reg1: AdvancedRegister, reg2: AdvancedRegister): AssemblyInstruction("    add $reg1, $reg2")
    class Sub(reg1: AdvancedRegister, reg2: AdvancedRegister): AssemblyInstruction("    sub $reg1, $reg2")

    // NOTE: multiplication trashes the d register
    class IMul(reg1: AdvancedRegister): AssemblyInstruction("    imul $reg1")
    class Mul(reg1: AdvancedRegister): AssemblyInstruction("    mul $reg1")
    // NOTE: you should use the helper function divOf to proper setup the d register
    class Div(reg1: AdvancedRegister): AssemblyInstruction("    div $reg1")
    class IDiv(reg1: AdvancedRegister): AssemblyInstruction("    idiv $reg1")

    object ConvertToOctoword: AssemblyInstruction("    cqo")
    object ConvertToQuadword: AssemblyInstruction("    cdq")
    object ConvertToDoublword: AssemblyInstruction("    cwd")

    class Push(register: Register): AssemblyInstruction("    push ${SizedRegister(BIT64, register)}")
    class Pop(register: Register): AssemblyInstruction("    pop ${SizedRegister(BIT64, register)}")

    class Custom(str: String): AssemblyInstruction("    $str")

    class Xor(reg1: SizedRegister, reg2: AdvancedRegister): AssemblyInstruction("    xor $reg1, $reg2") {
        companion object {
            fun useToZero(register: SizedRegister): Xor {
                return Xor(
                    reg1 = register,
                    reg2 = register.advanced()
                )
            }
        }
    }
    class Label(name: String): AssemblyInstruction("$name:")
    class Jump(to: String): AssemblyInstruction("    jmp $to")
    class Comment(content: String): AssemblyInstruction("    ; $content")
    class CommentedLine(line: AssemblyInstruction, comment: String): AssemblyInstruction("$line ; $comment")


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
