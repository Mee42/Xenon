package dev.mee42

import dev.mee42.lexer.Token
import java.lang.RuntimeException

open class CompilerException(message: String = ""): RuntimeException(message)

class InternalCompilerException(message: String): CompilerException(message)

fun main() {
//    val text = """
//function poly(int a, int b, int c, int x) int {
//    return (a * x * x) + (b * x) + c
//}
//    """.trimIndent()
    val text = """
function deref(int**** a, int one) int {
    return (****a) + one;
}
    """.trimIndent()
    try {
        println("\n-- asm --\n" + compile(text))
    } catch (e: CompilerException) {
        System.err.println("${e.javaClass.name}: ${e.message}\n\n\n")
        e.printStackTrace()
    }
}

private fun compile(string: String): String {
    val preprocessed = dev.mee42.xpp.preprocess(string)

    val tokens = dev.mee42.lexer.lex(preprocessed)
    println("-- tokens --")
    tokens.map(Token::content).map { "$it "}.forEach(::print)
//    tokens.forEach { print(it.type.name + "(${it.content}) ") }
    println()
    val ast = dev.mee42.parser.parse(tokens)
    println("-- ast --")
    println(ast)

    val optimized = dev.mee42.opt.optimize(ast)

    val asm = dev.mee42.asm.assemble(optimized)

    return asm.fold("") {a,b  -> "$a\n$b" }.trim()
}