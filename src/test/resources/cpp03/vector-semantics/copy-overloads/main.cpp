#include <vector>
int mutable_copies = 0;
int const_copies = 0;
struct Record {
    Record() {}
    Record(Record&) { ++mutable_copies; }
    Record(const Record&) { ++const_copies; }
};
int main() {
    Record r;
    std::vector<Record> a;
    a.push_back(r);
    if (mutable_copies != 0 || const_copies == 0) return 1;
    int before_fill = const_copies;
    std::vector<Record> b(2, r);
    if (mutable_copies != 0 || const_copies <= before_fill) return 2;
    int before_copy = const_copies;
    std::vector<Record> c = b;
    if (mutable_copies != 0 || const_copies <= before_copy) return 3;
    return 0;
}
