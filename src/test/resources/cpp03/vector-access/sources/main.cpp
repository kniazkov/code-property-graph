#include <vector>
#include <stdexcept>

int access_and_capacity() {
    std::vector<int> values(3, 10);
    values.front() = 1;
    values[1] = 2;
    values.back() = 3;
    const std::vector<int>& readonly = values;
    if (readonly.front() != 1 || readonly[1] != 2 || readonly.back() != 3) return 1;
    bool caught = false;
    try { values.at(100) = 7; }
    catch (const std::out_of_range&) { caught = true; }
    if (!caught || values.at(1) != 2) return 2;
    values.reserve(20);
    if (values.size() != 3 || values.capacity() < 20 || values.max_size() < values.size()) return 3;
    values.resize(5, 9);
    if (values.size() != 5 || values[3] != 9 || values.back() != 9) return 4;
    values.resize(3);
    if (values.size() != 3 || values.front() != 1 || values.back() != 3) return 5;
    return 0;
}

int modifiers_and_iteration() {
    std::vector<int> values;
    values.reserve(20);
    values.push_back(1);
    values.push_back(2);
    int& prefix = values.front();
    std::vector<int>::iterator inserted = values.insert(values.begin() + 1, 8);
    prefix = 4;
    if (*inserted != 8 || values.front() != 4) return 1;
    values.insert(values.end(), 2, 6);
    int extra[2] = {7, 9};
    values.insert(values.begin() + 2, extra, extra + 2);
    // 4,8,7,9,2,6,6 -> 4,9,2,6,6 -> 4,9,6,6 -> 4,9,6.
    std::vector<int>::iterator after_range = values.erase(values.begin() + 1, values.begin() + 3);
    if (*after_range != 9) return 2;
    std::vector<int>::iterator after_one = values.erase(values.begin() + 2);
    if (*after_one != 6) return 3;
    values.pop_back();
    std::vector<int>::iterator later;
    later = values.begin();
    later[0] = 5;
    if (values.front() != 5) return 8;
    *later = 4;
    int forward = 0;
    for (std::vector<int>::iterator it = values.begin(); it != values.end(); ++it) forward += *it;
    int reverse = 0;
    for (std::vector<int>::reverse_iterator it = values.rbegin(); it != values.rend(); ++it) reverse += *it;
    if (forward != 19 || reverse != 19 || *values.rbegin() != 6) return 4;
    const std::vector<int>& readonly = values;
    int constant = 0;
    for (std::vector<int>::const_iterator it = readonly.begin(); it != readonly.end(); ++it) constant += *it;
    if (constant != 19) return 5;
    std::vector<int> other(2, 50);
    std::vector<int>& same_container = values;
    int& old_element = values.front();
    values.swap(other);
    old_element = 41;
    if (same_container.size() != 2 || same_container.front() != 50 || other.size() != 3 || other.front() != 41) return 6;
    other.clear();
    if (!other.empty() || other.size() != 0) return 7;
    return 0;
}

void potentially_invalid_reference() {
    std::vector<int> values(1, 4);
    int& old_element = values.front();
    values.reserve(100);
    // Inspect possible invalidation in the graph; never dereference old_element here.
}

int main() {
    return access_and_capacity() == 0 && modifiers_and_iteration() == 0 ? 0 : 1;
}
