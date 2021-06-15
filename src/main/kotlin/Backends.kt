package dev.mee42

interface Backend {
    val name: String
    fun process(input: AST): Any
}
