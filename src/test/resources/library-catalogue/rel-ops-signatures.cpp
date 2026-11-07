// Independent strict C++03 interface and truth-table probe. MIT License.
#include <utility>
struct Value { int n; };
bool operator==(const Value& x, const Value& y) { return x.n == y.n; }
bool operator<(const Value& x, const Value& y) { return x.n < y.n; }
int main() {
    typedef bool (*Relation)(const Value&, const Value&);
    Relation ne = &std::rel_ops::operator!=<Value>;
    Relation gt = &std::rel_ops::operator><Value>;
    Relation le = &std::rel_ops::operator<=<Value>;
    Relation ge = &std::rel_ops::operator>=<Value>;
    for (int i = -1; i <= 1; ++i) {
        for (int j = -1; j <= 1; ++j) {
            Value x = {i}; Value y = {j};
            if (ne(x,y) != !(i==j) || gt(x,y) != (j<i)
                || le(x,y) != !(j<i) || ge(x,y) != !(i<j)) return 1;
            using namespace std::rel_ops;
            if ((x!=y) != !(i==j) || (x>y) != (j<i)
                || (x<=y) != !(j<i) || (x>=y) != !(i<j)) return 2;
        }
    }
    return 0;
}
