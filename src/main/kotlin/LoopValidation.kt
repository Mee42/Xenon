package dev.mee42

// we just need to verify that continue; statements only happen in loop blocks and not other blocks
// this also checks to make sure there's no references to labels that don't exist in scope

// we already know that types have been validated
enum class IsLoop{YES, NO}
fun loopValidate(expr: Expr, scope: Map<LabelIdentifier, IsLoop>) {
    when(expr) {
        is Expr.Break -> {
            if(scope[expr.label] == null) error("Trying to break@${expr.label}, but label @${expr.label} does not exist in scope")
            loopValidate(expr.value, scope)
        }
        is Expr.Continue -> {
            if(scope[expr.label] == null) error("Trying to break@${expr.label}, but label @${expr.label} does not exist in scope")
            if(scope[expr.label] == IsLoop.NO) error("Trying to use continue for a non-loop block, @${expr.label}")
        }
        is Expr.Block -> {
            if(scope[expr.label] != null) {
                error("Can't define scope @${expr.label} when scope @${expr.label} is already defined")
            }
            val newScope = scope + mapOf(expr.label to IsLoop.NO)
            for(e in expr.contents) {
                loopValidate(e, newScope)
            }
        }
        is Expr.Loop -> {
            if(scope[expr.block.label] != null) {
                error("Can't define scope @${expr.block.label} when scope @${expr.block.label} is already defined")
            }
            val newScope = scope + mapOf(expr.block.label to IsLoop.YES)
            for(e in expr.block.contents) {
                loopValidate(e, newScope)
            }
        }


        // random crap, just recur
        is Expr.Assignment -> {
            loopValidate(expr.left, scope)
            loopValidate(expr.right, scope)
        }
        is Expr.BinaryOp -> {
            loopValidate(expr.left, scope)
            loopValidate(expr.right, scope)
        }
        is Expr.Deref -> loopValidate(expr.expr, scope)
        is Expr.FunctionCall -> expr.arguments.forEach { loopValidate(it, scope) }
        is Expr.If -> {
            loopValidate(expr.cond, scope)
            loopValidate(expr.ifBlock, scope)
            if(expr.elseBlock != null) loopValidate(expr.elseBlock, scope)
        }
        is Expr.MemberAccess -> loopValidate(expr.expr, scope)
        is Expr.Ref -> loopValidate(expr.expr, scope)
        is Expr.StructDefinition -> expr.members.map { it.second }.forEach { loopValidate(it, scope) }
        is Expr.VariableDefinition -> loopValidate(expr.value, scope)
        is Expr.VariableAccess -> {}
        is Expr.CharLiteral -> {}
        is Expr.NumericalLiteral -> {}
        is Expr.StringLiteral -> {}
        is Expr.Unit -> {}
    }
}