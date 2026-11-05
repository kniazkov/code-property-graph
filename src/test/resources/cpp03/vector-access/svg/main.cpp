#include <vector>
int main() {
    std::vector<int> a;
    a.reserve(8);
    a.push_back(1);
    int& element = a.front();
    a.insert(a.begin() + 1, 3);
    std::vector<int> b(1, 4);
    a.swap(b);
    element = 7;
    return b.front() == 7 && a.front() == 4 ? 0 : 1;
}
