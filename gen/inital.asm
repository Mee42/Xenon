extern printf

section .text
    global main

main:
    mov ebx7
    mov ecx, 107
    call divide
    mov ebx, eax
    call println
    ret

divide: ; return value in register eax
    ; argument 0 (a) in register ebx
    ; argument 1 (b) in register ecx
    mov eax, ebx
    push rbx
    mov ebx, eax
    mov eax, ecx
    xor eax, ebx
    xor ebx, eax
    xor eax, ebx
    push rdx
    xor rdx, rdx
    div ebx
    pop rdx
    pop rbx
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
