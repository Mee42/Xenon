syntax match XComment /\/\/.*/
syntax match XKeyword /if\|return\|while/
syntax match XBool /false\|true/
syntax match XType /<[a-z][a-zA-Z0-9_]\+>/
syntax match XVal /val\|var/
syntax match XInt /-\=[0-9]\+/
syntax match XFunctionDef /^[a-z][A-Za-z0-9_]\+/
syntax match XFunctionDefType /^[a-z][A-Za-z0-9_]\+()\zs<int32>\ze/

highlight XComment ctermfg=darkgrey
highlight XFunctionDefType ctermfg=220
highlight XFunctionDef ctermfg=63
highlight XVal ctermfg=73
highlight XType ctermfg=220
highlight XInt ctermfg=green
highlight XBool ctermfg=red
highlight XKeyword  ctermfg=cyan

