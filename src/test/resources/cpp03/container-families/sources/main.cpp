#include <deque>
#include <list>
#include <set>
#include <map>
#include <stack>
#include <queue>

struct Item { int count; mutable int observed; Item(int n) : count(n), observed(0) { } };

int comparisons = 0;
struct TrackedLess {
    bool operator()(const int& left, const int& right) const {
        ++comparisons;
        return left < right;
    }
};

int sequences() {
    std::deque<int> values;
    values.push_back(2);
    values.push_front(1);
    values.push_back(3);
    if (values[0] != 1 || values[1] != 2 || values[2] != 3) return 1;
    values.pop_front();
    if (values.front() != 2 || values.back() != 3 || values.end() - values.begin() != 2) return 2;
    std::list<int> source;
    source.push_back(11);
    source.push_back(22);
    std::list<int>& source_object = source;
    std::list<int> destination;
    destination.push_back(5);
    std::list<int>::iterator moved = source.begin();
    int& saved = *moved;
    destination.splice(destination.end(), source, moved);
    saved = 17;
    if (source_object.size() != 1 || source.front() != 22 || destination.size() != 2 || destination.back() != 17 || *moved != 17) return 3;
    destination.splice(destination.begin(), source);
    if (!source.empty() || destination.front() != 22 || saved != 17) return 4;
    Item item(7);
    std::list<Item> records;
    records.push_back(item);
    const std::list<Item>& readonly = records;
    std::list<Item>::const_iterator view = readonly.begin();
    if (view->count != 7) return 5;
    view->observed = 9;
    if (readonly.front().observed != 9) return 6;
    return 0;
}

int associative() {
    std::map<int, int, TrackedLess> dictionary;
    unsigned int before = dictionary.size();
    if (dictionary.find(7) != dictionary.end() || dictionary.size() != before) return 1;
    dictionary[7] = 70;
    if (dictionary.size() != before + 1 || dictionary.find(7)->second != 70) return 2;
    dictionary.find(7)->second = 71;
    int& mapped_reference = dictionary.find(7)->second;
    mapped_reference = 72;
    if (dictionary[7] != 72) return 7;
    std::map<int, int, TrackedLess>::value_type entry(3, 30);
    dictionary.insert(entry);
    if (dictionary.begin()->first != 3 || dictionary.begin()->second != 30) return 3;
    std::set<int, TrackedLess> unique;
    unique.insert(4);
    unique.insert(4);
    std::multiset<int, TrackedLess> repeated;
    repeated.insert(4);
    repeated.insert(4);
    if (unique.size() != 1 || repeated.count(4) != 2) return 4;
    std::multimap<int, int, TrackedLess> duplicates;
    std::multimap<int, int, TrackedLess>::value_type one(4, 1);
    std::multimap<int, int, TrackedLess>::value_type two(4, 2);
    duplicates.insert(one);
    duplicates.insert(two);
    if (duplicates.size() != 2 || duplicates.count(4) != 2) return 5;
    int total = 0;
    for (std::multimap<int, int, TrackedLess>::iterator it = duplicates.begin(); it != duplicates.end(); ++it) total += it->second;
    if (total != 3 || comparisons == 0) return 6;
    return 0;
}

int adapters() {
    std::stack<int> lifo;
    std::queue<int> fifo;
    std::priority_queue<int, std::deque<int>, TrackedLess> priority;
    lifo.push(1); lifo.push(3); lifo.push(2);
    fifo.push(1); fifo.push(3); fifo.push(2);
    int old_comparisons = comparisons;
    priority.push(1); priority.push(3); priority.push(2);
    int stack_order = 0;
    int queue_order = 0;
    int priority_order = 0;
    while (!lifo.empty()) { stack_order = stack_order * 10 + lifo.top(); lifo.pop(); }
    while (!fifo.empty()) { queue_order = queue_order * 10 + fifo.front(); fifo.pop(); }
    while (!priority.empty()) { priority_order = priority_order * 10 + priority.top(); priority.pop(); }
    if (stack_order != 231 || queue_order != 132 || priority_order != 321 || comparisons <= old_comparisons) return 1;
    return 0;
}

int main() {
    return sequences() == 0 && associative() == 0 && adapters() == 0 ? 0 : 1;
}
