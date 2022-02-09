# Xenon, a native C-like language

This language will eventually have multiple backends, the two primary ones being [TVM](https://github.com/adrian154/tvm) and x86_64.

This is a rewrite of an old Xenon compiler, which targetted x86_64 machine code dircetly. See the `old` branch for more detail on that.


Immediate TODOs:
- [x] Lexer
- [x] Grammar 
- [x] Parser
- [x] Semantic analysis, type checking
- [x] Proper integer types
- [x] Loop support 
- [ ] [TVM](https://github.com/adrian154/tvm) backend (in progress)



Xenon 1.1 features:
- [ ] Arrays
- [ ] Bidirectional type assumption

Xenon >1.1 features:
- [ ] Closures/Lambdas
- [ ] Function Types

Longer term goals:
- [ ] x86 backend
- [ ] Nice CLI frontend
- [ ] JS support for the compiler
- [ ] formal testing suite
- [ ] nicer website
