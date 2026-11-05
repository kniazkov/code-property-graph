int trace = 0;
int copies = 0;
int assignments = 0;
int staticConstructions = 0;
void event(int value) { trace = trace * 10 + value; }
struct Base {
    Base() { event(1); }
    ~Base() { event(8); }
};
struct Field {
    int id;
    Field(int n) : id(n) { event(n); }
    ~Field() { event(9 - id); }
};
class Ordered : public Base {
    Field first;
    Field second;
public:
    Ordered() : second(3), first(2), Base() { event(4); }
    ~Ordered() { event(5); }
};
struct Copy {
    int value;
    Copy(int n) : value(n) {}
    Copy(const Copy& other) : value(other.value) { copies += 1; }
    Copy& operator=(const Copy& other) { value = other.value; assignments += 1; return *this; }
};
struct Aggregate { int first; int second; };
union Storage { int integer; char character; };
class Counter {
    static int total;
    mutable int visits;
public:
    Counter() : visits(0) { total += 1; }
    int read() const { visits += 1; return total + visits; }
    friend int count();
};
int Counter::total = 0;
int count() { return Counter::total; }
struct Once { Once() { staticConstructions += 1; } };
void useStatic() { static Once once; }
struct Temporary {
    Temporary(int n) { event(n); }
    ~Temporary() { event(9); }
};
int main() {
    { Ordered value; if (trace != 1234) return 1; }
    if (trace != 12345678) return 2;
    Copy original(4); Copy copied = original; Copy direct(original);
    copied = direct;
    if (copies != 2 || assignments != 1 || copied.value != 4) return 3;
    Aggregate aggregate = {3}; Storage storage = {4};
    if (aggregate.first != 3 || aggregate.second != 0 || storage.integer != 4) return 4;
    Counter counter; if (counter.read() != 2 || count() != 1) return 5;
    useStatic(); useStatic(); if (staticConstructions != 1) return 6;
    trace = 0;
    { const Temporary& extended = Temporary(6); if (trace != 6) return 7; }
    if (trace != 69) return 8;
    trace = 0; Temporary(7); if (trace != 79) return 9;
    return 0;
}
