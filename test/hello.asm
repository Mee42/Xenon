extern printf

section .text
;    global say_hi
    global main

main:
    ;push sp ; push the stack
    xor rax, rax
    xor rbx, rbx
    xor rcx, rcx
    xor rdx, rdx

    mov rax, 3
    mov ecx, 7
    call double
    ;mov rax, 5
    ; prints rax
    mov rdi, string
    mov rsi, rax
    mov rax, 0
    call printf

    mov rax, 0  ; rax is the return value
    ret

double: ; return value in register eax
    ; argument 0 (x) in register ecx
    push rbx
    push rdx
    mov ebx, ecx
    push rbx
    mov ebx, ecx
    pop rdx
    add ebx, edx
    pop rdx
    mov eax, ebx
    pop rbx
    ret

section .data
    string: db "rax: %i", 10, 0
