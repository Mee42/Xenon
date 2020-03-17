extern printf

section .text
;    global say_hi
    global main

main:
    ;push sp ; push the stack
    mov r9,  0xFFFFFFFFFFFFFFFF
    mov r9d, 0x11111111
    mov r9w, 0x2222
    mov r9b, 0x33

    ;call doubled
    mov rdx, r9

    mov rdi, format    ; rdi is the first parameter
    mov rsi, message   ; rsi is the second parameter

    ;xor rdx, rdx ; zero rdx
    ;mov edx, eax ; move eax to the LSBs of rdx
    mov rax, 0 ; this needs to be zero
    call printf
    ;pop rbp ; pop stack
    mov rax, 0  ; rax is the return value
    ret

doubled: ; return value in register r9
          ; argument 0 (a) in register r10
    add r9d, r9d
    mov r10d, r9d
    ret

;say_hi:
;    mov eax, 4
;    mov ebx, 1
;    mov ecx, hello
;    mov edx, len
;    int 0x80
;    ret


section .data
    message:  db 'r9 is', 0
    format: db "%s %x", 10, 0