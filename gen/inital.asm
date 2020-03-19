extern printf
extern malloc

section .text
    global main

main:
    mov rbx, 10
    call foo
    add rbx, 1
    call foo
    ret

foo: ; return value in register eax
    ; argument 0 (a) in register ebx
    push rbx
    push rbx
    mov eax, ebx
    mov ebx, eax
    call double
    pop rbx
    mov ebx, eax
    call println
    pop rbx
    ret

double: ; return value in register eax
    ; argument 0 (a) in register ebx
    mov eax, ebx
    push rbx
    mov ebx, eax
    mov eax, ebx
    mul ebx
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
    mov rax, rbx
    ret


section .data
    string: db "%i", 10, 0
