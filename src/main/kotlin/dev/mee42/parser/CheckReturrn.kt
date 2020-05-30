package dev.mee42.parser

fun checkReturn(ast: AST) {
    for(function in ast.functions) {
        if(function is XenonFunction) checkReturn(function)
    }
}
private fun checkReturn(function: XenonFunction) {
    val block = function.content
    if(!returns(block)) throw ParseException("Function ${function.identifier} does not return a value")
    checkAllReturnStatments(block, function.identifier, function.returnType)
}
// if onlySometimes is true, it'll return if there is any chance of returning
private fun returns(block: Block, onlySometimes: Boolean = false): Boolean {
    if(!onlySometimes) {
        for(statement in block.statements) {
            if(statement is ReturnStatement) return true
        }
        return false // easy enough
    }

    // okay this is "will it return, ever?"
    for(statement in block.statements) {
        if(statement is ReturnStatement) return true
        if(statement is IfStatement && returns(statement.block, true)) return true
        if(statement is WhileStatement && returns(statement.block, true)) return true
        if(statement is ExpressionStatement &&
                statement.expression is BlockExpression &&
                returns(Block(statement.expression.statements), true)){
            return true
        }
    }
    return false
}

private fun checkAllReturnStatments(block: Block, funIdentifier: String, retType: Type) {
    for(statement in block.statements){
        if(statement is ReturnStatement) {
            if(statement.expression.type != retType){
                throw ParseException("Can't return ${statement.expression.type} from function $funIdentifier as the return type is $retType")
            }
        }
        if(statement is Block) {
            checkAllReturnStatments(statement, funIdentifier,retType)
        }
        if(statement is ExpressionStatement && statement.expression is BlockExpression) {
            checkAllReturnStatments(Block(statement.expression.statements), funIdentifier, retType)
        }
    }
}