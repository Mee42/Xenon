package dev.mee42

import dev.mee42.lexer.Token
import dev.mee42.parser.*
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
function foo(int a) int {
    return println(double(a));
}
function double(int a) int {
    return a * a;
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


    val initialAST = dev.mee42.parser.parsePass1(tokens).withLibrary(standardLibrary)

    val ast = dev.mee42.parser.parsePass2(initialAST)

    val optimized = dev.mee42.opt.optimize(ast)

    val asm = dev.mee42.asm.assemble(optimized)

    return asm.fold("") {a,b  -> "$a\n$b" }.trim()
}