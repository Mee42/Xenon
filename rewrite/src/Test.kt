package dev.mee42


val testAST = UntypedAST(
        functions = listOf(
                UntypedFunction(
                        identifier = "main",
                        arguments = emptyList(),
                        templated = UntypedFunction.TemplateMode.NoTemplate,
                        block = UntypedStatement.Block(listOf(
                                UntypedStatement.Expression(UntypedExpression.FunctionCall(
                                        arguments = listOf(UntypedExpression.StringLiteral("Hello, World!")),
                                        calledOnReciever = false,
                                        functionIdentifier = "println",
                                        templates = null
                                )),
                                UntypedStatement.Return(UntypedExpression.IntegerLiteral(0))
                        ))
                )
        ),
        structs = emptyList()
)

fun main() {
    val newAST

}