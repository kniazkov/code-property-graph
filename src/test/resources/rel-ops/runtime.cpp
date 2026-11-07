#include <utility>
struct Value {
    int n;
    explicit Value(int x) : n(x) {}
    bool operator==(const Value& x) const { return n == x.n; }
    bool operator<(const Value& x) const { return n < x.n; }
};
int main() {
    using namespace std::rel_ops;
    for (int i = 0; i < 3; ++i) {
        for (int j = 0; j < 3; ++j) {
            Value a(i), b(j);
            if (std::rel_ops::operator!=(a,b) != !(i == j)) return 1;
            if (std::rel_ops::operator>(a,b) != (j < i)) return 2;
            if (std::rel_ops::operator<=(a,b) != !(j < i)) return 3;
            if (std::rel_ops::operator>=(a,b) != !(i < j)) return 4;
            if ((a != b) != !(i == j)) return 5;
            if ((a > b) != (j < i)) return 6;
            if ((a <= b) != !(j < i)) return 7;
            if ((a >= b) != !(i < j)) return 8;
        }
    }
    return 0;
}
