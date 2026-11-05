#include <vector>
int other();
int main() {
    std::vector<int> numbers;
    std::vector<double> fractions;
    numbers.push_back(7);
    return numbers.size() + other() - 1;
}
