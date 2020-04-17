package dev.mee42.parser

import dev.mee42.asm.RegisterSize


enum class TypeEnum(val registerSize: RegisterSize, val names: List<String>, val isUnsigned: Boolean,val numberRange: LongRange) {
    INT8(RegisterSize.BIT8,  -128..127,"int8","byte"),
    INT16(RegisterSize.BIT16, -32768..32767,"int16","short"),
    INT32(RegisterSize.BIT32, -2147483648..2147483647,"int32","int"),
    INT64(RegisterSize.BIT64, ((-9223372036854775807L - 1L)..9223372036854775807L), "int64","long"),

    UINT8(RegisterSize.BIT8,0..255, "uint8","ubyte", isUnsigned = true),
    CHAR(RegisterSize.BIT8, 0..255, "char", isUnsigned = true),
    UINT16(RegisterSize.BIT16, 0..65535, "uint16","ushort", isUnsigned = true),
    UINT32(RegisterSize.BIT32, 0..4294967295,"uint32","uint", isUnsigned = true),
    UINT64(RegisterSize.BIT64,0..9223372036854775807L, "uint64","ulong", isUnsigned = true),

    BOOLEAN(RegisterSize.BIT8, 0..1,"bool"),
    VOID(RegisterSize.BIT8, 0..0, "void")
    ;

    constructor(size: RegisterSize, numberRange: LongRange,vararg names: String, isUnsigned: Boolean = false): this(size, names.toList(), isUnsigned, numberRange)
    constructor(size: RegisterSize, numberRange: IntRange,vararg names: String, isUnsigned: Boolean = false): this(size, names.toList(), isUnsigned, numberRange.first.rangeTo(numberRange.last.toLong()))

    companion object {
        fun of(str: String): TypeEnum {
            return values().first { it.names.contains(str) }
        }
    }
}

sealed class Type(val size: RegisterSize)
data class BaseType(val type: TypeEnum): Type(type.registerSize)
data class PointerType(val type: Type, val writeable: Boolean): Type(RegisterSize.BIT64)
