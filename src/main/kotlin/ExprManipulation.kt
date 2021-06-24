package dev.mee42


// =======================
// tw
// =======================

//treewalk-able
interface TWAble
private interface TWer<T: TWAble> {
    fun mapChildren(t: T, f: (T) -> T): T
}
private fun <T: TWAble> T.twInternal(f: (T, (T) -> T) -> T?, tWer: TWer<T>): T {
    val callback = { it: T -> it.twInternal(f, tWer) }
    val result = f(this, callback)
    if(result != null) return result
    return tWer.mapChildren(this, callback)
}

// below is a haskell implementation of twInternal, with examples
/*

class TWable a where
    mapChildren :: a -> (a -> a) -> a

data Expr = AddExpr Expr Expr | SubExpr Expr Expr | IntLit Int


instance TWable Expr where
    mapChildren (IntLit i) _ = IntLit i
    mapChildren (AddExpr a b) f = AddExpr (f a) (f b)
    mapChildren (SubExpr a b) f = SubExpr (f a) (f b)


tw :: (Expr -> (Expr -> Expr) -> Maybe Expr) -> Expr -> Expr
tw f x = ret
    where callback = tw f
          result = f x callback
          recurse (AddExpr a b) = AddExpr (tw f a) (tw f b)
          recurse (IntLit i) = IntLit i
          recurse (SubExpr a b) = SubExpr (tw f a) (tw f b)
          ret = case result of
              (Just e) -> e
              Nothing -> recurse x

-- this is the generic version
tw' :: TWable a => (a -> (a -> a) -> Maybe a) -> a -> a
tw' f x = ret
    where callback = tw' f
          result = f x callback
          ret = case result of
              (Just e) -> e
              Nothing -> mapChildren x (tw' f)

-- specalized, just to verify
tw'' :: (Expr -> (Expr -> Expr) -> Maybe Expr) -> Expr -> Expr
tw'' = tw'


tw_constrained ::TWable a => (a -> Maybe a) -> a -> a
tw_constrained f x = ret
    where result = f x
          ret = case result of
              (Just e) -> e
              Nothing -> mapChildren x (tw_constrained f)

tw''' :: TWable a => (a -> (a -> a) -> Maybe a) -> a -> a
tw''' f e = callback e
        where callback = tw_constrained $ \x -> f x callback

-- doble all int literals that are the right hand of add exprs
-- (3 +5 ) + 7 ---> (3 + 10) + 14
doubleAdds :: Expr -> Expr
doubleAdds = tw' $ \e callback -> case e of
    (AddExpr a (IntLit i)) -> Just $ AddExpr (callback a) (IntLit $ i * 2)
    _ -> Nothing

-- replace all subs with adds
-- (a + b) + (c - d)   --->  (a - b) - (c - d)
change :: Expr -> Expr
change = tw' $ \e callback -> case e of
    (SubExpr a b) -> Just $ AddExpr (callback a) (callback b)
    _ -> Nothing



 */




// impl for untyped functions
private val untypedExprTWer = object: TWer<UntypedExpr> {
    override fun mapChildren(t: UntypedExpr, f: (UntypedExpr) -> UntypedExpr): UntypedExpr = when(t) {
        is UntypedExpr.Assignment -> UntypedExpr.Assignment(f(t.left), f(t.right))
        is UntypedExpr.BinaryOp -> UntypedExpr.BinaryOp(f(t.left), f(t.right), t.op)
        is UntypedExpr.Block -> UntypedExpr.Block(t.sub.map(f), t.label)
        is UntypedExpr.FunctionCall -> UntypedExpr.FunctionCall(t.functionName, t.arguments.map(f), t.generics)
        is UntypedExpr.If -> UntypedExpr.If(f(t.cond), f(t.ifBlock), t.elseBlock?.let(f))
        is UntypedExpr.Loop -> UntypedExpr.Loop(f(t.block) as UntypedExpr.Block)
        is UntypedExpr.PrefixOp -> UntypedExpr.PrefixOp(f(t.right), t.op)
        is UntypedExpr.Return -> UntypedExpr.Return(t.expr?.let(f))
        is UntypedExpr.VariableDefinition -> UntypedExpr.VariableDefinition(t.variableName, t.value?.let(f), t.type, t.isConst)
        is UntypedExpr.Break -> UntypedExpr.Break(t.value?.let(f), t.label)
        is UntypedExpr.VariableAccess,
        is UntypedExpr.NumericalLiteral,
        is UntypedExpr.StringLiteral,
        is UntypedExpr.CharLiteral,
        is UntypedExpr.Continue -> t
        is UntypedExpr.MemberAccess -> UntypedExpr.MemberAccess(f(t.expr), t.memberName, t.isArrow)
        is UntypedExpr.StructDefinition -> UntypedExpr.StructDefinition(t.type, t.members.map { (name, expr) -> name to f(expr) })
    }
}
fun UntypedExpr.tw(f: (UntypedExpr, (UntypedExpr) -> UntypedExpr) -> UntypedExpr?): UntypedExpr = this.twInternal(f, untypedExprTWer)













// folding into an arbitrary datatype
fun <T> Expr.fold(f: (Expr) -> T?, mf: (T, T) -> T, d: T): T = f(this) ?: when(this) {
    is Expr.Assignment -> mf(left.fold(f, mf, d), right.fold(f, mf, d))
    is Expr.BinaryOp -> mf(left.fold(f, mf, d), right.fold(f, mf, d))
    is Expr.Block -> contents.map { it.fold(f, mf, d) }.takeUnless(List<T>::isEmpty)?.foldNonEmpty(mf) ?: d
    is Expr.Break -> value.fold(f, mf, d)
    is Expr.CharLiteral -> d
    is Expr.Continue -> d
    is Expr.Deref -> expr.fold(f, mf, d)
    is Expr.FunctionCall -> arguments.map { it.fold(f, mf, d) }.takeUnless(List<T>::isEmpty)?.foldNonEmpty(mf) ?: d
    is Expr.If -> {
        val a = mf(cond.fold(f, mf, d), ifBlock.fold(f, mf, d))
        if(elseBlock == null) {
            a
        } else {
            mf(a, elseBlock.fold(f, mf, d))
        }
    }
    is Expr.Loop -> block.fold(f, mf, d)
    is Expr.MemberAccess -> expr.fold(f, mf, d)
    is Expr.NumericalLiteral -> d
    is Expr.Ref -> expr.fold(f, mf, d)
    is Expr.StringLiteral -> d
    is Expr.StructDefinition -> this.members
        .map { it.second }
        .map { it.fold(f, mf, d) }
        .takeUnless(List<T>::isEmpty)
        ?.foldNonEmpty(mf) ?: d
    is Expr.Unit -> d
    is Expr.VariableAccess -> d
    is Expr.VariableDefinition -> value.fold(f, mf, d)
}

private fun <T> List<T>.foldNonEmpty(merger: (T, T) -> T): T = when(this.size) {
    0 -> error("can't foldNonEmpty on empty list")
    1 -> first()
    else -> {
        // for optimization purposes
        var t = first()
        var first = true
        for(e in this.iterator()) {
            if(!first) { // don't do merger(first(), first())
                t = merger(t, e)
            }
            first = false
        }
        t
    }
}