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


Some sample code:
```c
func [T, R] cast(T t) R {}// intrinsic

struct[T] List {
    Int size;
    Int allocated;
    T* data;
} // structs are monomorphized by templated types

struct[A, B] Pair {
    A a;
    B b;
} // can support storing templated types by value

struct[T] Foo {
    List[Pair[T, T]] list;
}

func[T] malloc(UInt count) T* {
    return cast::[UInt, T*](sizeof::[T]() * count);
    // just returns the number of bytes in the size thing
    // for testing
}

func print(Char x) {} // intrinsic
func print(Char* x) { // prints zero-terminated strings
    loop {
        if(*x == cast::[Int, Char*](0)) break;
        print(*x);
        x = x + 1;
    }
} // not an intrinsic

func foo() {
   val cond = 7;
   val Int x = if(cond == 0) { // if expressions. The 'Int' is the type of 'x'
        print("true!");
        break 7;
   } else {
        print("not true");
        break 8;
   };

   val Pair[Int*, Char*] pair = struct Pair[Int*, Char*] {
        .a = malloc::[Int](3u),
        .b = malloc::[Char](7u)
   }; // struct literal syntax
   *((&pair)->a) = 7;
   *pair.a = 7;
}


func[T] realloc(T* ptr, Int size) T*  {
    return ptr // who cares honestly lolol
}

func[T] newList() List[T] {
    return struct List[T] { // the toknes 'List[T]' are optional when the struct type can be inferred
        .size      = 0,
        .allocated = 10,
        .data      = malloc::[T](10u),
    };
}

func[T] (List[T]* self).add(T element) { // member call syntax
    if(self->size == self->allocated) {
        self->allocated = self->allocated * 2;
        self->data = realloc::[T](self->data, self->allocated);
    };
    self->size = self->size + 1;
    self->data[self->size] = element;
}

func main() UInt {
   val List[Int] numbers = newList::[Int]();
   add::[Int](&numbers, 7); // call with normal function syntax
   add::[Int](&numbers, 8);
   (&numbers).add::[Int](3); // call with method syntax (same as the above)
    return 0u; // unsigned
}
```
