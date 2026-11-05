namespace shop { typedef int OrderId; }
namespace archive { typedef long OrderId; }
namespace selected = shop;
using namespace shop;
OrderId order = 7;
struct Base {
    virtual int value() { return 1; }
};
struct Derived : Base {
    Derived();
    ~Derived();
    virtual int value() { return 7; }
};
Derived::Derived() {}
Derived::~Derived() {}
int main() {
    selected::OrderId alias_order = order;
    Derived derived;
    Base &base = derived;
    return alias_order == 7 && base.value() == 7 ? 0 : 1;
}
