extern printf

section .text
    global main

main:
    push 218841438
    call foo
    add rsp, 8
    mov rbx, rax
    call println
    mov rax, 0
    ret



foo: ; return value in register eax
    ; argument 0 (a) in register DWORD [rbp + 16]
    push rbp
    mov rbp, rsp
    sub rsp, QWORD 0
    mov eax, [rbp + 16] ; pulling variable a
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