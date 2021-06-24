package dev.mee42

interface Backend {
    val name: String
    fun process(ast: AST): Any
}
