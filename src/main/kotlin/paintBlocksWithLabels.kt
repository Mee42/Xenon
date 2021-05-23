package dev.mee42



// return null to continue recursing, return a value to stop and use that value instead
fun UntypedExpr.tw(cond: (UntypedExpr, (UntypedExpr) -> UntypedExpr) -> UntypedExpr?):UntypedExpr {
    val callback = { it: UntypedExpr -> it.tw(cond) }
    val result = cond(this, callback)
    if(result != null) return result
    
    
    // helper
    fun UntypedExpr.tw() = this.tw(cond)
    return when(this) {
        is UntypedExpr.Assignment -> UntypedExpr.Assignment(left.tw(), right.tw())
        is UntypedExpr.BinaryOp -> UntypedExpr.BinaryOp(left.tw(), right.tw(), op)
        is UntypedExpr.Block -> UntypedExpr.Block(sub.map(UntypedExpr::tw), label)
        is UntypedExpr.FunctionCall -> UntypedExpr.FunctionCall(functionName, arguments.map(UntypedExpr::tw), generics)
        is UntypedExpr.If -> UntypedExpr.If(this.cond.tw(), ifBlock.tw(), elseBlock?.tw())
        is UntypedExpr.Loop -> UntypedExpr.Loop(block.tw() as UntypedExpr.Block) // TODO how to formalize this a bit better?
        is UntypedExpr.PrefixOp -> UntypedExpr.PrefixOp(right.tw(), op)
        is UntypedExpr.Return -> UntypedExpr.Return(expr.tw())
        is UntypedExpr.VariableDefinition -> UntypedExpr.VariableDefinition(variableName, value?.tw(), type, isConst)
        is UntypedExpr.Yield -> UntypedExpr.Yield(value?.tw(), label)
        is UntypedExpr.VariableAccess,
        is UntypedExpr.NumericalLiteral, 
        is UntypedExpr.StringLiteral, 
        is UntypedExpr.CharLiteral,
        is UntypedExpr.Continue -> this
        is UntypedExpr.MemberAccess -> UntypedExpr.MemberAccess(expr.tw(), memberName, isArrow)
        is UntypedExpr.StructDefinition -> UntypedExpr.StructDefinition(type, members.map { (name, expr) -> name to expr.tw() })
    }
}



private var labelIDIncr = 0;
fun genRandomLabelID(): LabelIdentifier {
    return "l_${labelIDIncr++}_"
}


fun paintAllYields(ast: UntypedAST): UntypedAST {
    return ast.copy(functions = ast.functions.map {
        it.copy(body = paintBlocks(it.body))
    })
}

// this fills any blocks without labels with pregenerated ones. This ONLY labels `yield` statements
private fun paintBlocks(block: UntypedExpr.Block): UntypedExpr.Block = block.tw { expr, callback ->

    //if(expr == block) return@tw null // don't match on the input block. This input block is always the top level function block
    // the above statement was removed so that function blocks are given labels

    if(expr !is UntypedExpr.Block) return@tw null // not what we want to match, continue recurring
    if(expr.label != null) return@tw null
    val newLabel = genRandomLabelID()

    val newBlock = expr.tw innerTW@{ subExpr, subCallback ->
        if (subExpr == expr) return@innerTW null
        if (subExpr is UntypedExpr.Block) {
            // if it's a block, we want to stop recurring.
            return@innerTW subExpr // so we just have this as the base case
        }
        if (subExpr is UntypedExpr.Loop) {
            // in the case of a loop, yields are different.
            // TODO figure this part out
            return@innerTW subExpr // we'll just base case out for now?
        }
        if (subExpr !is UntypedExpr.Yield) return@innerTW null // recur on any non-yield case
        if (subExpr.label != null) return@innerTW null // if it has a label, then recur, there might be one nested

        return@innerTW UntypedExpr.Yield(
            label = newLabel,
            value = subExpr.value?.let(subCallback) // callback on the value, for nested reasons
        )
    } as UntypedExpr.Block
    callback(UntypedExpr.Block(
        newBlock.sub,
        newLabel
    )) // rerun, it'll hit default base case (we have a label) so we won't recur infinitely, but we will hit nested blocks
} as UntypedExpr.Block

