extern printf
extern malloc
; display/i $pc
section .text
    global main

main: ; return value in register eax
    mov rbp, rsp
    mov rax, user_string_1
    mov [rax], BYTE 42
    push rax
    call println
    add rsp, 8
    ret



println: ; takes in a single string argument
    push rbp
    mov rbp, rsp
    mov rdi, [rbp + 16]
    xor rax, rax
    call printf
    xor rax, rax
    pop rbp
    ret


section .data
    user_string_1: db "characters", 10, "more", 10
