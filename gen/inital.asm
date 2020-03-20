extern printf
extern malloc
; display/i $pc
section .text
    global main

main:
    push 30 ; a
    push 20 ; b
    mov rbp, 40
    call foo
    add rsp, 16
    ; rax has the result
    mov rbx, rax
    call println
    ret

; function foo(int a, int b) int {
;     int c = a + b;
;     int d = a + c;
;     return c + d;
; } // (a + b) + (a + (a + b))
;    // a + a + a + b + b
; foo(30, 20) = 130

;       ~ higher memory ~
; 30
; 20
; ~ call ref ~
; 40 <- rbp
; XX <- c
; XX <- d, rsp
; \/ local stack zone: push/pop is allowed \/
;       ~ lower memory ~

foo:
    push rbp
    mov rbp, rsp
    sub rsp, 16

; int c = a + b:
; get a
mov rax, [rbp + 24] ; this line could be any expression that leaves the result in rax. it can use any register
push rax ; push it

; get b
mov rax, [rbp + 16] ; get b expression (ie, this could be anything that sets rax to the right value). same as above
push rax ; push it

; +
pop rax ; we can use registers as much as we want, but we can not nest "register free use" zones
pop rbx ; because the two things are already on the stack, this is much better
add rax, rbx
mov [rbp - 8], rax  ; set c
;

mov rax, [rbp + 24]
add rax, [rbp + 16]
mov [rbp - 8], rax

    mov rax,  [rbp + 24] ; get a
    add rax, [rbp - 8]   ; get c
    mov [rbp - 16], rax  ; set d

    mov rax, [rbp -8]   ; get c
    add rax, [rbp - 16] ; add d

    ; [rbp - 16] d
    ; [rbp - 8]  c
    ; [rbp]      the old rbp: 40
    ; [rbp + 8]  the ref to the call instruction
    ; [rbp + 24] a
    ; [rbp + 16] b

    ; return
    add rsp, 16
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
