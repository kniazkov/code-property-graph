class Order;
Order *current;
int before() { return sizeof(Order); }
class Order { int id; };
int after() { return sizeof(Order*); }
int main() { return after(); }
