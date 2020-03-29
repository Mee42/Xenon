#include <stdio.h>
#include <stdlib.h>

typedef struct {
	int a;
} foo;


int main() {
	//foo foo = { 1 };
	foo bar = { 2 };
	bar = bar;
    return 0;
}