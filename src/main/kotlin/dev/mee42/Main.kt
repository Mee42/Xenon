package dev.mee42

import dev.mee42.lexer.Token
import dev.mee42.parser.*
import java.lang.RuntimeException

open class CompilerException(message: String = ""): RuntimeException(message)

class InternalCompilerException(message: String): CompilerException(message)

// TODO figure out where to store variables
//   https://stackoverflow.com/questions/36529449/why-are-rbp-and-rsp-called-general-purpose-registers
//   https://stackoverflow.com/questions/41912684/what-is-the-purpose-of-the-rbp-register-in-x86-64-assembler




fun main() {
    val text = """
        
function foo(byte a, byte b) byte {
    return a + b
}
    """.trimIndent()
    try {
        println("\n-- asm --\n" + compile(text))
    } catch (e: CompilerException) {
        System.err.println("${e.javaClass.name}: ${e.message}\n\n\n")
        e.printStackTrace()
    }
}
val standardLibrary = listOf(
    InitialFunction(
        name = "println",
        arguments = listOf(Argument(
            name = "i",
            type = BaseType(TypeEnum.INT32)
        )),
        returnType = BaseType(TypeEnum.VOID),
        content = null, // TODO build a dsl for this
        attributes = emptyList()
    )
)

private fun compile(string: String): String {
    val preprocessed = dev.mee42.xpp.preprocess(string)

    val tokens = dev.mee42.lexer.lex(preprocessed)
    println("-- tokens --")
    tokens.map(Token::content).map { "$it "}.forEach(::print)
    println("\n")

    val initialAST = parsePass1(tokens).withLibrary(standardLibrary)

    val ast = parsePass2(initialAST)

    val optimized = dev.mee42.opt.optimize(ast)

    val asm = dev.mee42.asm.assemble(optimized)

    return asm.fold("") {a,b  -> "$a\n$b" }.trim()
}