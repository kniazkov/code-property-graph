namespace shop {
    typedef long RawId;
    typedef RawId OrderId;
    typedef int Count;
}
namespace shop { enum State { ready, done = 2 }; }
namespace market = shop;
using namespace shop;
OrderId id;
Count count;
shop::OrderId qualified;
market::OrderId via_alias;
using shop::RawId;
RawId imported;
class Order;
Order *current;
class Order { int id; };
Order *later;
extern "C" int c_api();
extern "C" int c_api() { return 0; }
typedef int ApiFunction();
ApiFunction alias_api;
int alias_api() { return 0; }
int a();
int b();
int main() { return a() + b() + c_api() + alias_api(); }
