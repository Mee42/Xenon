extern printf

section .text
;    global say_hi
    global main

main:
    ;push sp ; push the stack
    mov rbx, 0x50

    mov rdi, string    ; rdi is the first parameter
    mov rsi, rbx   ; rsi is the second parameter
    ;mov rsi, rbx       ; rdx is the third parameter

    ;xor rdx, rdx ; zero rdx
    ;mov edx, eax ; move eax to the LSBs of rdx
    mov rax, 0 ; this needs to be zero
    call printf
    ;pop rbp ; pop stack
    ;pop rbx
    mov rax, 0  ; rax is the return value
    ret

section .data
    string: db "0x50 == 0x%x", 10, 0