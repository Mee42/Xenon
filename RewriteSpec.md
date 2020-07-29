the idea is that we can learn and make a much better structured compiler to make things easier, so we can add templates.

The passes will turn into this:

- lex
same deal, but remove regex eventually
- parse
convert to an UntypedAST. This is pretty much `[UntypedStruct]` and `[UntypedFunction]`.
- type
This is the fun part



