extern printf
extern malloc
; display/i $pc
section .text
    global main

main: ; return value in register eax
    push rbp
    mov rbp, rsp
    sub rsp, QWORD 0
    mov eax, DWORD 1000000000
    push rax ; argument bytes
    call malloc_int
    add rsp, QWORD 8
    push rax ; argument ptr
    call println_ptr
    add rsp, QWORD 8
    nop
    call main
    add rsp, QWORD 0
    nop


; standard library asm functions
malloc_int:
    push rbp
    mov rbp, rsp
    mov edi, [rbp + 16] ; pulling the first argument
    call malloc
    pop rbp
    ret

println_ptr:
    mov rdi, string_ptr
    jmp println_main

println:
    mov rdi, string
    jmp println_main

println_main: ; takes in a 64-bit argument and prints it
    push rbp
    mov rbp, rsp
    ;mov rdi, string
    mov rsi, [rbp + 16]
    xor rax, rax
    call printf
    xor rax, rax
    pop rbp
    ret


section .data
    string: db "%i", 10, 0
    string_ptr: db "%p", 10, 0
