package dev.mee42


sealed class Type {
    data class Builtin(val name: TypeIdentifier): Type() // Int, Char
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
