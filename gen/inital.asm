extern printf
extern malloc

section .text
    global main

main:
    ;mov rbx, 702
    mov rdi, 8 ; 8 bytes, one register

    mov rax, 0
    call malloc

    mov [rax + 72], WORD 0x1111
    mov rbx, 10
    mov rbx, [rax + 72]
    call println
    ret



println: ; function println(int64 i)
          ; returns the output of printf
          ; i is stored in rbx
          ; this overwrites rdi and rsi

    mov rdi, string
    mov rsi, rbx
    mov rax, 0
    call printf
    ret


section .data
    string: db "0x%x", 10, 0
