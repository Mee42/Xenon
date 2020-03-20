extern printf
extern malloc
; display/i $pc
section .text
    global main

main:
    mov ebx, 2
    mov ecx, 3
    mov edx, 4
    call main_
    ret

trace: ; return value in register eax
    ; argument 0 (a) in register ebx
    push rbx
    mov eax, ebx ; pulling variable a
    mov ebx, eax ; argument i
    call println
    pop rbx
    ;
    mov eax, ebx ; pulling variable a
    ret


main_: ; return value in register eax
    ; argument 0 (a) in register ebx
    ; argument 1 (b) in register ecx
    ; argument 2 (c) in register edx
    push rbx
    push r8 ; starting add/sub 44
    push rbx
    ; starting mult 44
    mov eax, ebx ; pulling variable a
    push rbx
    mov ebx, eax
    mov eax, ecx ; pulling variable b
    push rdx
    mul ebx
    pop rdx
    pop rbx ; ending mult 44
    mov ebx, eax ; argument a
    call trace
    pop rbx
    push rax
    push rbx
    ; starting mult 84
    mov eax, ecx ; pulling variable b
    push rbx
    mov ebx, eax
    mov eax, edx ; pulling variable c
    push rdx
    mul ebx
    pop rdx
    pop rbx ; ending mult 84
    mov ebx, eax ; argument a
    call trace
    pop rbx
    pop r8
    add eax, r8d
    pop r8 ; ending add/sub 44
    mov ebx, eax ; argument i
    call println
    pop rbx
    ;
    mov eax, ebx ; pulling variable a
    ret

println: ; function println(int64 i)
          ; returns the output of printf
          ; i is stored in rbx
          ; this overwrites rdi and rsi
    mov rdi, string
    mov rsi, rbx
    mov rax, 0
    push rcx
    push rdx
    call printf
    pop rdx
    pop rcx
    mov rax, rbx
    ret


section .data
    string: db "%i", 10, 0
