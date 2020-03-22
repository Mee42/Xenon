extern printf

section .text
    global main

main:
    call foo
    add rsp, 0
    mov rbx, rax
    call println
    mov rax, 0
    ret



foo: ; return value in register eax
    push rbp
    mov rbp, rsp
    sub rsp, QWORD 0
    mov eax, DWORD 7
    add rsp, QWORD 0
    pop rbp
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
