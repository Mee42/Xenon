syntax match XComment /\/\/.*$/
syntax keyword XKeyword if return while
syntax keyword XBool false true
syntax keyword XPrimitive byte ubyte short ushort int uint long ulong int8 int16 int32 int64 uint8 uint16 uint32 uint64 char 
syntax keyword XVal val mut
syntax match XInt /-\=[0-9]\+/
syntax match XOperator "\v\*"
syntax match XOperator "\v\+"
syntax match XOperator "\v-"
syntax match XOperator "\v\?"
syntax match XOperator "\v\="
" color scheme colors
"highlight link XComment Comment
"highlight link XKeyword Keyword

" custom colors 
highlight XComment ctermfg=darkgrey
highlight XVal ctermfg=73
highlight XPrimitive ctermfg=220
highlight XInt ctermfg=green
highlight XBool ctermfg=red
highlight XKeyword  ctermfg=cyan
highlight XOperator ctermfg=13
