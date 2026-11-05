#include <vector>
#include <iterator>
#include <algorithm>
int main() {
    int input[] = { 2, 4 };
    std::vector<int> output;
    std::copy(input, input + 2, std::back_inserter(output));
    return output.size() == 2 && output[1] == 4 ? 0 : 1;
}
