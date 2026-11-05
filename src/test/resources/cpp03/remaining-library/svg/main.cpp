#include <sstream>
#include <string>
int main() {
    std::string input("17");
    std::istringstream stream(input);
    int value = 0;
    stream >> value;
    return value == 17 && !stream.fail() ? 0 : 1;
}
