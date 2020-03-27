package dev.mee42.parser

import dev.mee42.lexer.Token

data class Argument(val name: String, val type: Type)

class AssemblyFunction(name: String, arguments: List<Argument>, returnType: Type, id: String): Function(name, arguments, returnType, id)
class XenonFunction(name: String, arguments: List<Argument>, returnType: Type, id: String, val content: Block): Function(name, arguments, returnType, id) {
    override fun toString(): String {
        return "Function-$name$id($arguments)$returnType{$content}"
    }
}
abstract class Function(val name: String, val arguments: List<Argument>, val returnType: Type, val id: String) {
    val identifier: String = name + id
}


data class InitialFunction(val name: String,
                           val arguments: List<Argument>,
                           val attributes: List<String>,
                           val returnType: Type,
                           val content: List<Token>?,
                           val id: String) {
    fun toDefString() : String {
        return "function $name" + arguments.map { "" + it.type + " " + it.name }.joinToString(",","(",")") + returnType
    }

    companion object {
        var i = 0
        fun genRandomID(): String {
            return (++i).toString()
        }
    }
    val identifier: String = name + id
}
data class InitialAST(val functions: List<InitialFunction>) {
    fun withOther(other: InitialAST): InitialAST = InitialAST(this.functions + other.functions)
}