package dev.mee42.parser
//
//import dev.mee42.CompilerException
//import dev.mee42.arg.VerboseOption
//import dev.mee42.parser.FunctionLink.*
//import dev.mee42.printTable
//
//
fun markPurity(ast: AST): AST {
    return ast
}
/*    val graph = ast.functions.map { it.identifier to it.purity() }.toMap()
    val new = optGraph(graph).map { (id,purity) -> ast.functions.first { it.identifier == id } to purity }.toMap()
    for((function, purity) in new) {
        if(function.isMarkedPure() && purity == Impure) {
            throw CompilerException("function ${function.name} was marked pure, but contains impure actions")
        }
    }
    if(VerboseOption.PURITY.isSelected()) {
        printTable(
                col1 = new.map { (a,_) -> a.identifier },
                title1 = "Function Name",
                col2 = new.map { (_, b) -> b.toString() },
                title2 = "Purity"
        )
    }
    return ast.copy { XenonFunction(it.name, it.arguments, it.returnType, it.id, it.content, it.attributes, new[it] == Pure) }

fun optGraph(graph: Map<FunctionIdentifier, FunctionLink>): Map<FunctionIdentifier, FunctionLink> {
    val new = graph.toList().map { (id, link) ->
        when(link) {
            is Impure, is Pure -> id to link
            is PossiblyPure -> id to link.links.map { graph.getValue(it) }.fold().map { PossiblyPure.of(it.links.filter { w -> w != id }) }
        }
    }.toMap()
    return if(new == graph) new
    else optGraph(new)
}


fun Function.isMarkedPure() = this.attributes.contains("@pure")
fun Function.isMarkedForcePure() = this.attributes.contains("@forcePure")

typealias FunctionIdentifier = String

sealed class FunctionLink {
    object Impure: FunctionLink(){
        override fun combine(other: FunctionLink) = this
        override fun map(m: (PossiblyPure) -> FunctionLink) = this
        override fun toString(): String = "Impure"
    }
    class PossiblyPure(val links: List<FunctionIdentifier>): FunctionLink() {
        override fun combine(other: FunctionLink)= when(other){
            is PossiblyPure -> PossiblyPure(links + other.links)
            is Impure -> Impure
            is Pure -> this
        }

        override fun map(m: (PossiblyPure) -> FunctionLink) = m(this)

        override fun toString(): String = "PossiblyPure" + links.joinToString(", ", "(",")")
        companion object {
            fun of(links: List<FunctionIdentifier>)= when {
                links.isEmpty() -> Pure
                else -> PossiblyPure(links)
            }
        }
    }
    object Pure: FunctionLink() {
        override fun combine(other: FunctionLink): FunctionLink = when(other){
            is PossiblyPure -> other
            is Impure -> Impure
            is Pure -> this
        }

        override fun map(m: (PossiblyPure) -> FunctionLink): FunctionLink = this
        override fun toString(): String = "Pure"
    }
    abstract fun combine(other: FunctionLink): FunctionLink
    abstract fun map(m: (PossiblyPure) -> FunctionLink): FunctionLink
}

fun List<FunctionLink>.fold(): FunctionLink {
    return when {
        this.isEmpty() -> Pure
        this.size == 1 -> first()
        else -> first().combine(this.drop(1).fold())
    }
}

fun Function.purity(): FunctionLink {
    return when (this) {
        is XenonFunction -> if(this.isMarkedForcePure()) Pure else this.content.purity()
        else -> if(this.isMarkedPure()) Pure else Impure
    }
}
private fun Expression.purity(): FunctionLink {
    return when(this){
        is VariableAccessExpression -> Pure
        is TypelessBlock -> expressions.map { it.purity() }.fold()
        is BlockExpression -> statements.map { it.purity() }.fold()
        is DereferencePointerExpression -> pointerExpression.purity()
        is IntegerValueExpression -> Pure
        is StringLiteralExpression -> Pure
        is ComparisonExpression -> var1.purity().combine(var2.purity())
        is FunctionCallExpression -> PossiblyPure(listOf(this.functionIdentifier))
                .combine(arguments.map { it.purity() }.fold())
        is RefExpression -> Impure
    }
}
private fun Statement.purity(): FunctionLink {
    return when(this) {
        is Block -> statements.map { it.purity() }.fold()
        is ReturnStatement -> expression.purity()
        NoOpStatement -> Pure
        is ExpressionStatement -> expression.purity()
        is DeclareVariableStatement -> expression.purity()
        is AssignVariableStatement -> expression.purity()
        is MemoryWriteStatement -> Impure
        is IfStatement -> conditional.purity().combine(block.purity())
        is WhileStatement -> conditional.purity().combine(block.purity())
    }
}*/