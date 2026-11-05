#include <string>
#include <vector>
#include <iterator>
#include <algorithm>
#include <numeric>

int predicate_calls = 0;
int transform_calls = 0;
int comparison_calls = 0;
int accumulation_calls = 0;
struct IsEven { bool operator()(int value) const { ++predicate_calls; return value % 2 == 0; } };
struct AddOne { int operator()(int value) const { ++transform_calls; return value + 1; } };
struct Less { bool operator()(int left, int right) const { ++comparison_calls; return left < right; } };
struct Sum { long operator()(long left, int right) const { ++accumulation_calls; return left + right; } };

int strings() {
    const char buffer[] = "a\0bc";
    std::string text(buffer, 4);
    if (text.size() != 4 || text[1] != '\0' || text.at(3) != 'c') return 1;
    if (text.find('\0') != 1 || text.find('b') != 2) return 2;
    std::string part = text.substr(1, 2);
    if (part.size() != 2 || part[0] != '\0' || part[1] != 'b') return 3;
    text.append(buffer, 3);
    if (text.size() != 7 || text[5] != '\0' || text[6] != 'b') return 4;
    part[1] = 'z';
    if (text[2] != 'b' || part[1] != 'z') return 5;
    return 0;
}

int algorithms() {
    int source[] = { 4, 1, 4, 2, 3 };
    std::vector<int> values;
    std::copy(source, source + 5, std::back_inserter(values));
    if (values.size() != 5 || values[0] != 4 || values[4] != 3) return 1;
    std::vector<int>::iterator position = values.begin();
    std::advance(position, 2);
    if (*position != 4 || std::distance(values.begin(), values.end()) != 5) return 2;
    std::vector<int>::reverse_iterator reverse = values.rbegin();
    if (*reverse != 3) return 3;
    IsEven even;
    std::vector<int>::iterator found = std::find_if(values.begin(), values.end(), even);
    if (found != values.begin() || predicate_calls == 0) return 4;
    AddOne increment;
    std::transform(values.begin(), values.end(), values.begin(), increment);
    if (transform_calls != 5 || values[0] != 5 || values[4] != 4) return 5;
    Less less;
    std::sort(values.begin(), values.end(), less);
    if (values[0] != 2 || values[1] != 3 || values[2] != 4 || values[4] != 5 || comparison_calls == 0) return 6;
    std::vector<int>::iterator logical_end = std::remove(values.begin(), values.end(), 5);
    if (values.size() != 5 || std::distance(values.begin(), logical_end) != 3) return 7;
    values.erase(logical_end, values.end());
    if (values.size() != 3 || values[0] != 2 || values[2] != 4) return 8;
    long total = std::accumulate(values.begin(), values.end(), 2147483640L);
    if (total != 2147483649L) return 9;
    Sum sum;
    long custom_total = std::accumulate(values.begin(), values.end(), 1000L, sum);
    if (custom_total != 1009L || accumulation_calls != 3) return 10;
    return 0;
}
int main() {
    if (strings() != 0) return 10;
    if (algorithms() != 0) return 20;
    return 0;
}
