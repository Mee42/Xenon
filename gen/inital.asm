extern printf
extern malloc
; display/i $pc
section .text
    global main
main:
    push 10
    push 20
    call add_
    ; al has a bunch of garbage data in eax and rax segments
    ; we need to upcast it
    ; to an integer
    movsx rbx, al ; move with sign extension
    call println
    add rsp, 16
    ret

add_: ; return value in register al
    ; argument 0 (a) in register BYTE [rbp + 24]
    ; argument 1 (b) in register BYTE [rbp + 16]
    push rbp
    mov rbp, rsp
    sub rsp, QWORD 0
    mov al, [rbp + 24] ; pulling variable a
    push rax
    mov al, [rbp + 16] ; pulling variable b
    pop rbx
    add al, bl
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
