extern printf

section .text
;    global say_hi
    global main

main:
    ;push sp ; push the stack
    mov rdi, format ; rdi is the first parameter
    mov rsi, hello ; rsi is the second parameter
    mov rax, 0   ; ?
    ;push format
    ;push hello
    ;push 0
    call printf
    ;pop rbp ; pop stack
    mov rax,  ; rax is the return value
    ret

;say_hi:
;    mov eax, 4
;    mov ebx, 1
;    mov ecx, hello
;    mov edx, len
;    int 0x80
;    ret


section .data
    hello:  db 'Hello World from assembly', 0
    format: db "%s", 10, 0