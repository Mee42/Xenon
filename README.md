# Xenon, a native C-like language

This language will eventually have multiple backends, the two primary ones being [TVM](https://github.com/adrian154/tvm) and x86_64.

Immediate TODOs:
- [x] Lexer
- [x] Grammar 
- [x] Parser
- [x] Semantic analysis, type checking (in process)
- [ ] loop support
- [ ] bidirectional type assumption
- [ ] proper integer types

Goals, in no particular order:
- [ ] **[TVM](https://github.com/adrian154/tvm) backend**
- [ ] x86 backend
- [ ] Nice CLI frontend
- [ ] JS support for the compiler
- [ ] formal testing suite
- [ ] nicer website
