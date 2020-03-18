extern printf
extern malloc

section .text
    global main

main:
    ;mov rbx, 702
    mov rdi, 8 ; 8 bytes, one register

    mov rax, 0
    call malloc

    mov [rax], TWORD 0xFFFF222233334444 ; TODO left off here
                                        ; you can't put 64 bits into memory
                                        ; but you can put a 64-bit register into memory
                                        ; we should make r15 a free-to-use register
                                        ; so we don't need to push/pop it every time we want to put 64 bits into memory
                                        ; mov r15, 0xFFFF222233334444
                                        ; mov [rax], r15
                                        ; special cases
                                        ; or, maybe we don't need that and we can just stick it into the accumulator
                                        ; the special case is for writing to memory not reading from memory
                                        ; reading is easy just
                                        ; mov rax, ptrValueExpresionShti
                                        ; mov rax, [rax]
    mov [rax + 2], WORD 0x1111
    mov rbx, 10
    mov rbx, [rax]
    call println
    ret

deref: ; return value in register eax
    ; argument 0 (a) in register rbx
    ; argument 1 (one) in register ecx
    push rdx
    mov rax, rbx
    mov rax, [rax]
    mov rax, [rax]
    mov rax, [rax]
    mov eax, [rax]
    push rax
    mov eax, ecx
    pop rdx
    add eax, edx
    pop rdx
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
