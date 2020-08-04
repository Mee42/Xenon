package dev.mee42

class TypedAST(val functions: List<Function>, val structs: List<Struct>)

class Function(
        val identifier: Identifier,
        val id: Int,
        val templates: List<Type>,
        val body: Statement.Body
)
class Struct(
        val identifier: Identifier,
        val id: Int,
        val templates: List<Type>,
        val fields: AList<Type, Identifier>
)

sealed class Statement {
    class Body (val statements: List<Statement>): Statement()
}

sealed class Type