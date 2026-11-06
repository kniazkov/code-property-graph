#include <typeinfo>
void* operator new(unsigned long size, void* address) { return address; }
struct S { char c; int i; };
struct Base { int base; };
struct Child : Base { int child; };
union Slot { int alignment; char bytes[sizeof(int)]; };
int main() {
    char buffer[sizeof(S) + 1];
    if (sizeof(buffer) != sizeof(S) + 1) return 1;
    S* s = new S();
    if (s->c != 0 || s->i != 0) return 2;
    delete s;
    int n = 2;
    int* values = new int[n++]();
    if (n != 3 || values[0] != 0 || values[1] != 0) return 3;
    delete[] values;
    Slot slot;
    int* placed = new (slot.bytes) int(7);
    if (*placed != 7) return 4;
    int x = 5;
    const int* constant = &x;
    int* mutablePointer = const_cast<int*>(constant);
    void* erased = reinterpret_cast<void*>(mutablePointer);
    int* restored = static_cast<int*>(erased);
    if (*restored != 5 || (int)3.9 != 3 || int(4.9) != 4) return 5;
    Child child;
    Base* base = dynamic_cast<Base*>(&child);
    if (base == 0) return 6;
    (void)typeid(x++);
    (void)typeid(int);
    (void)sizeof(x++);
    if (x != 5) return 7;
    unsigned int wrapped = 4294967295U + 1U;
    if (wrapped != 0U) return 8;
    bool skipped = false && ++x;
    int selected = true ? ++x : ++x;
    if (skipped || selected != 6 || x != 6) return 9;
    return 0;
}
