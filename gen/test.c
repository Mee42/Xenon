#include <stdio.h>
#include <stdlib.h>

int main() {
    for(int i = 0; i < 100; i++){
        printf("%p\n",malloc(10));
    }
    return 0;
}