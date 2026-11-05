#define JOIN(a,b) a ## b
#define EXPAND(a,b) JOIN(a,b)
int common() { return EXPAND(+, BAD); }
