package dev.mee42





private var labelIDIncr = 0;
fun genRandomLabelID(): LabelIdentifier {
    return "l_${labelIDIncr++}_"
}


fun paintAllBreaks(ast: UntypedAST): UntypedAST {
    return ast.copy(functions = ast.functions.map {
        it.copy(body = paintBlocks(it.body))
    })
}

// this fills any blocks without labels with pregenerated ones. This ONLY labels `break` statements
private fun paintBlocks(block: UntypedExpr.Block): UntypedExpr.Block = block.tw { expr, callback ->

    //if(expr == block) return@tw null // don't match on the input block. This input block is always the top level function block
    // the above statement was removed so that function blocks are given labels
    val isLoop = expr is UntypedExpr.Loop

    val loopExpr = if(isLoop) (expr as UntypedExpr.Loop).block else expr

    if(loopExpr !is UntypedExpr.Block) return@tw null // not what we want to match, continue recurring
//    if(loopExpr.label != null) return@tw null
    val label = loopExpr.label ?: genRandomLabelID()

    val newBlock = loopExpr.tw innerTW@ { subExpr, subCallback ->
        if (subExpr == loopExpr) return@innerTW null
        if (subExpr is UntypedExpr.Block) {
            // if it's a block, we want to stop recurring, as everything that's deeper refers to a different block
            return@innerTW subExpr // so we just have this as the base case
        }
        if (subExpr is UntypedExpr.Loop) {
            // this works because we just process the block like we'd process any other block, and then stick it back into UntypedExpr.Loop
            return@innerTW UntypedExpr.Loop(subCallback(subExpr.block) as UntypedExpr.Block)
        }
        return@innerTW when (subExpr) {
            is UntypedExpr.Break -> {
                if (subExpr.label != null) null // if it has a label, then recur, there might be one nested
                else UntypedExpr.Break(label = label, value = subExpr.value?.let(subCallback))
                // callback on the value, for nested reasons (tho that'd be a bit odd)
            }
            is UntypedExpr.Continue -> {
                if (subExpr.label != null) null // if it has a label, then recur, there might be one nested
                else UntypedExpr.Continue(label)
            }
            else -> return@innerTW null
        }
    } as UntypedExpr.Block

   fun addLoop(block: UntypedExpr.Block) = if(isLoop) UntypedExpr.Loop(block) else block
    val passOnInnerLines = UntypedExpr.Block(newBlock.sub.map(callback), label)
    // rerun, this is so we don't hit the same case again
    addLoop(passOnInnerLines)
} as UntypedExpr.Block
