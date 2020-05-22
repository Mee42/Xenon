@file:Suppress("TrailingComma")

package dev.mee42.parser


class AssemblyFunction(name: String, arguments: List<Argument>, returnType: Type, id: String, attributes: List<String>, pure: Boolean = false):
        Function(name, arguments, returnType, id, attributes, pure)

class XenonFunction(name: String, arguments: List<Argument>, returnType: Type, id: String, val content: Block, attributes: List<String>, pure: Boolean = false):
        Function(name, arguments, returnType, id, attributes, pure) {
    override fun toString(): String {
        return "Function-$name$id($arguments)$returnType{$content}"
    }
    fun copy(newContent: Block):XenonFunction = XenonFunction(name, arguments, returnType, id, newContent, attributes, verifiedPure)
}

abstract class Function(val name: String,
                        val arguments: List<Argument>,
                        val returnType: Type,
                        val id: String,
                        val attributes: List<String>,
                        val verifiedPure: Boolean = false) {
    val identifier: String = name + id
}




data class InitialAST(val functions: List<InitialFunction>, val structs: List<Struct>) {
    fun withOther(other: InitialAST): InitialAST = InitialAST(this.functions + other.functions, structs)
}