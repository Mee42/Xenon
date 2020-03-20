package dev.mee42.parser

import dev.mee42.asm.RegisterSize


enum class TypeEnum(val registerSize: RegisterSize, val names: List<String>, val isUnsigned: Boolean) {
    INT8(RegisterSize.BIT8, "int8","byte"),
    INT16(RegisterSize.BIT16, "int16","short"),
    INT32(RegisterSize.BIT32, "int32","int"),
    INT64(RegisterSize.BIT64, "int64","long"),

    UINT8(RegisterSize.BIT8,"uint8","ubyte", isUnsigned = true),
    UINT16(RegisterSize.BIT16, "uint16","ushort", isUnsigned = true),
    UINT32(RegisterSize.BIT32, "uint32","uint", isUnsigned = true),
    UINT64(RegisterSize.BIT64, "uint64","ulong", isUnsigned = true),

    BOOLEAN(RegisterSize.BIT8, "bool"),
    VOID(RegisterSize.BIT8, "void")
    ;

    constructor(size: RegisterSize, vararg names: String, isUnsigned: Boolean = false): this(size, names.toList(), isUnsigned)
}

sealed class Type(val size: RegisterSize)
data class BaseType(val type: TypeEnum): Type(type.registerSize)
data class PointerType(val type: Type): Type(RegisterSize.BIT64)
