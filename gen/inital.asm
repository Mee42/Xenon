extern printf

section .text
    global main

main:
    mov ebx, 1075
    call println
    call double
    mov ebx, eax
    call println
    ret

double: ; return value in register eax
    ; argument 0 (x) in register ebx
    push rcx
    mov eax, ebx
    push rax
    mov eax, ebx
    pop rcx
    add eax, ecx
    pop rcx
    mov eax, eax
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
    string: db "%i", 10, 0
