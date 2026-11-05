#include <vector>
#include <memory>
#include <cstddef>

struct UserSequence {
    int value;
    UserSequence() : value(0) {}
    void push_back(int x) { value = x + 1; }
};

int copies = 0;
int mutable_copies = 0;
struct Record {
    int* pointer;
    explicit Record(int* p) : pointer(p) {}
    Record(Record& other) : pointer(other.pointer) { ++mutable_copies; }
    Record(const Record& other) : pointer(other.pointer) { ++copies; }
};

int allocations = 0;
template<class T> struct CountingAllocator : std::allocator<T> {
    template<class U> struct rebind { typedef CountingAllocator<U> other; };
    CountingAllocator() {}
    template<class U> CountingAllocator(const CountingAllocator<U>&) {}
    T* allocate(std::size_t count, const void* hint = 0) {
        ++allocations;
        return std::allocator<T>::allocate(count, hint);
    }
};

int ordinary_values() {
    int source[3] = {4, 5, 6};
    using std::vector;
    typedef vector<int> Integers;
    Integers empty;
    Integers counted(2);
    Integers filled(3, 7);
    Integers ranged(source, source + 3);
    Integers copied = ranged;
    copied.push_back(9);
    if (!empty.empty() || counted.size() != 2 || counted[0] != 0) return 1;
    if (filled.size() != 3 || filled[0] != 7 || ranged[2] != 6) return 2;
    if (ranged.size() != 3 || copied.size() != 4) return 3;
    Integers& same_object = filled;
    filled = copied;
    if (same_object.size() != 4 || same_object[3] != 9) return 4;
    copied.assign(2, 8);
    if (copied.size() != 2 || copied[1] != 8 || filled.size() != 4) return 5;
    filled.assign(source, source + 2);
    if (same_object.size() != 2 || same_object[0] != 4 || same_object[1] != 5) return 6;
    UserSequence user;
    user.push_back(10);
    if (user.value != 11) return 7;
    return 0;
}

int shared_pointee() {
    int value = 1;
    Record record(&value);
    std::vector<Record> original;
    original.push_back(record);
    if (copies == 0) return 8;
    int before_copy = copies;
    std::vector<Record> separate = original;
    if (copies <= before_copy) return 9;
    separate.push_back(record);
    if (original.size() != 1 || separate.size() != 2) return 10;
    *separate[0].pointer = 17;
    if (*original[0].pointer != 17 || value != 17 || mutable_copies != 0) return 11;
    return 0;
}

int distinct_proxy_and_allocator() {
    std::vector<bool> flags(1, false);
    std::vector<bool>::reference proxy = flags[0];
    bool independent = proxy;
    proxy = true;
    if (!flags[0] || independent) return 12;
    std::vector<int, CountingAllocator<int> > custom;
    custom.push_back(42);
    if (custom[0] != 42 || allocations == 0) return 13;
    return 0;
}

int main() {
    int values = ordinary_values();
    if (values) return values;
    int records = shared_pointee();
    if (records) return records;
    return distinct_proxy_and_allocator();
}
