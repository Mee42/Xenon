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