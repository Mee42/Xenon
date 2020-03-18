extern printf

section .text
    global main

main:

    mov rax, 0  ; rax is the return value
    ret


section .data
    string: db "rax: %i", 10, 0
