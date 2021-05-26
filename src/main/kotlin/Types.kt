package dev.mee42


sealed class Type {
    data class Generic(val identifier: TypeIdentifier): Type() // T, R
    data class Pointer(val inner: Type): Type() // <type>*
    data class Struct(val name: TypeIdentifier, val genericTypes: List<Type>): Type() // Foo[Int]
    object Nothing: Type() // Nothing
    object Unit: Type() // Unit

    override fun toString(): String= when(this) {
        is Builtin -> name
        is Generic -> identifier
        is Pointer -> "$inner*"
        is Struct -> this.name + if(this.genericTypes.isEmpty()) "" else this.genericTypes.joinToString(", ", "[", "]")
        Nothing -> "Nothing"
        Unit -> "Unit"
    }


    sealed class BuiltinInteger(val signed: Boolean,
                                size: Int,
                                name: String): Builtin(size, name)

    sealed class Builtin(val size: Int, val name: String): Type() {// size is always measured in bytes (8 bit)
        object BYTE: BuiltinInteger(true, 1, "Byte")
        object UBYTE: BuiltinInteger(false, 1, "UByte")
        object INT: BuiltinInteger(true, 2, "Int")
        object UINT: BuiltinInteger(false, 2, "UInt")

        object LONG: BuiltinInteger(true, 4, "LOng")
        object ULONG: BuiltinInteger(false, 4, "ULong")

        object BOOL: Builtin(1, "Bool")
        object CHAR: BuiltinInteger(true, 1, "Char")
        companion object {
            val all = setOf(BYTE, UBYTE, INT, UINT, LONG, ULONG, BOOL, CHAR)
            val allMap = all.map { it.name to it }.toMap()
            val intTypes = setOf(BYTE, UBYTE, INT, UINT, LONG, ULONG, CHAR)
        }
    }
}



fun Type.containsGeneric(): Boolean = when(this) {
    is Type.Generic -> true
    is Type.Pointer -> this.inner.containsGeneric()
    is Type.Struct -> this.genericTypes.any { it.containsGeneric() }
    else -> false
}

fun Type.assertNoneGeneric() {
    if(this.containsGeneric()) {
        error("Internal Compiler Error: was expecting type '${this}' to not contain any generics")
    }
}
