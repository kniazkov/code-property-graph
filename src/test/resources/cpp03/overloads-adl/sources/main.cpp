int effects = 0;
int constructed = 0;
int f(int value) { return 1; }
int f(long value) { return 2; }
namespace finance {
struct Money {
    int cents;
    explicit Money(int value) : cents(value) {}
};
Money operator+(const Money& left, const Money& right) {
    effects++;
    return Money(left.cents + right.cents);
}
bool operator&&(const Money& left, const Money& right) {
    effects++;
    return left.cents != 0 && right.cents != 0;
}
int tagged(const Money& value) { return value.cents; }
Money makeMoney(int value) { effects++; return Money(value); }
}
namespace colors {
enum Tone { low = 1, high = 2 };
Tone operator+(Tone left, Tone right) { effects++; return high; }
}
struct Amount {
    int value;
    Amount(int initial) : value(initial) {}
    operator int() const { effects++; return value; }
};
struct Box {
    int value;
    Box(int initial) : value(initial) { constructed++; }
};
struct Explicit {
    int value;
    explicit Explicit(int initial) : value(initial) {}
};
int take(int value) { return value; }
int unbox(Box value) { return value.value; }
int explicitValue(Explicit value) { return value.value; }
int main() {
    if (f(1) != 1 || f(1L) != 2 || f(1L + 0L) != 2) return 1;
    finance::Money price(3);
    finance::Money tax(4);
    finance::Money total = price + tax;
    if (total.cents != 7 || effects != 1 || tagged(price) != 3) return 2;
    effects = 0;
    bool combined = finance::makeMoney(0) && finance::makeMoney(1);
    if (combined || effects != 3) return 3;
    int count = 0;
    bool skipped = false && ++count;
    if (skipped || count != 0) return 4;
    Amount amount(8);
    effects = 0;
    if (take(amount) != 8 || effects != 1) return 5;
    if (unbox(9) != 9 || constructed != 1) return 6;
    if (explicitValue(Explicit(6)) != 6) return 7;
    effects = 0;
    colors::Tone shade = colors::low + colors::low;
    if (shade != colors::high || effects != 1) return 8;
    return 0;
}
