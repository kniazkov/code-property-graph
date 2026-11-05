struct Base { int x; };
struct Left : Base {};
struct Right : Base {};
struct Diamond : Left, Right {};
int main() { Diamond value; return value.x; }
