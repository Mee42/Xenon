package dev.mee42.parser

fun willTypeFit(inType: Type, outType: Type): Boolean {
    return when {
        inType is BaseType && outType is BaseType -> inType.type == outType.type
        inType is PointerType && outType is PointerType ->
            (inType.writeable || !outType.writeable) && willTypeFit(inType.type, outType.type)
        else -> false
    }
}