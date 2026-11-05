int trace = 0;
struct Base {
    virtual int run(int value = 1) { return value; }
    virtual ~Base() {}
};
struct Derived : Base {
    int run(int value = 2);
};
int Derived::run(int value) { return value * 10; }
struct Printer { virtual int reset() { return 1; } };
struct Scanner { virtual int reset() { return 2; } };
struct Device : Printer, Scanner {
    int reset() { return 3; }
    int reset(int value) { return value; }
};
struct Pure { virtual int value() = 0; virtual ~Pure() {} };
int Pure::value() { return 99; }
struct Concrete : Pure { int value() { return 7; } };
struct V {
    int x;
    V() : x(4) { trace = trace * 10 + 1; }
    ~V() { trace = trace * 10 + 1; }
};
struct Left : virtual V {
    Left() { trace = trace * 10 + 2; }
    ~Left() { trace = trace * 10 + 2; }
};
struct Right : virtual V {
    Right() { trace = trace * 10 + 3; }
    ~Right() { trace = trace * 10 + 3; }
};
struct Diamond : Left, Right {
    Diamond() : V(), Left(), Right() { trace = trace * 10 + 4; }
    ~Diamond() { trace = trace * 10 + 4; }
    int read() { return x; }
};
int destroyed = 0;
struct ConstructionBase {
    int observed;
    ConstructionBase() : observed(0) { observed = run(); }
    virtual int run() { return 1; }
    virtual ~ConstructionBase() { destroyed = run(); }
};
struct ConstructionDerived : ConstructionBase { int run() { return 2; } };
int viaBase(Base& value) { return value.run(); }
int viaPrinter(Printer& value) { return value.reset(); }
int viaScanner(Scanner& value) { return value.reset(); }
int viaPure(Pure& object) { return object.value(); }
int main() {
    Derived derived;
    if (viaBase(derived) != 10 || derived.run() != 20 || derived.Base::run() != 1) return 1;
    Device device;
    if (viaPrinter(device) != 3 || viaScanner(device) != 3 || device.reset(9) != 9) return 2;
    Concrete concrete;
    if (viaPure(concrete) != 7) return 3;
    {
        Diamond diamond;
        if (trace != 1234 || diamond.x != 4 || diamond.read() != 4) return 4;
    }
    if (trace != 12344321) return 5;
    {
        ConstructionDerived object;
        if (object.observed != 1 || object.run() != 2) return 6;
    }
    if (destroyed != 1) return 7;
    return 0;
}
