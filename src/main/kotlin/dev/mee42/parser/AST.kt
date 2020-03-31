package dev.mee42.parser


data class AST(val functions: List<Function>)



fun printAST(ast: AST) {
    println("ast:")
    for(function in ast.functions) {
        if(function is AssemblyFunction) continue
        printAST(function)
    }
}

fun printAST(function: Function) {
    println(function.returnType.toString() + " " +
            function.name +
            function.arguments.joinToString(",","(",")") + "\n")
    when(function) {
        is XenonFunction -> printAST(function.content,"content:"," ")
        is AssemblyFunction -> println("-- function implementation internal")
    }
}


fun printAST(statement: Statement, prepend: String, indent: String) {
    when(statement) {
        is Block -> {
            println("$indent $prepend block:")
            for((i, s) in statement.statements.withIndex()) {
                printAST(s,"line $i:" ,"$indent|-")
            }
        }
        is ReturnStatement -> {
            println("$indent $prepend return:")
            printAST(statement.expression, "value:", "$indent|-")
        }
        NoOpStatement -> println("$indent $prepend nop")
        is ExpressionStatement -> { println("$indent $prepend expression:"); printAST(statement.expression, "expression:", "$indent|-") }
        is DeclareVariableStatement -> { println("$indent $prepend declared variable ${statement.variableName}"); printAST(statement.expression, "value:", "$indent|-")}
        is AssignVariableStatement -> { println("$indent $prepend assign variable ${statement.variableName}"); printAST(statement.expression, "value:", "$indent|-")}
        is MemoryWriteStatement -> { println("TODO memory write statement") }
        is IfStatement -> {
            println("$indent $prepend if statement")
            printAST(statement.conditional, "conditional", "$indent|-")
            printAST(statement.block, "block", "$indent|-")
        }
        is WhileStatement -> { println("TODO while statement") }
    }
}
fun printAST(expression: Expression, prepend: String, indent: String) {
    when(expression){
        is IntegerValueExpression -> {
            println("$indent $prepend integer value: " + expression.value)
        }
        is VariableAccessExpression -> {
            println("$indent $prepend access variable " + expression.variableName)
        }
        is DereferencePointerExpression -> {
            println("$indent $prepend TODO deref")
        }
        is StringLiteralExpression -> {
            println("$indent $prepend string literal \"" + expression.value + '"')
        }
        is MathExpression -> {
            println("$indent $prepend TODO math")
        }
        is EqualsExpression -> {
            println("$indent $prepend equals. Negate: " + expression.negate)
            printAST(expression.var1, "left arg:", "$indent|-")
            printAST(expression.var2, "left arg:", "$indent|-")
        }
        is FunctionCallExpression ->{
            println("$indent $prepend TODO function call")
        }
    }
}
