/* Small C++ input for the CPG demonstration. */
#include <iostream>
class Program {
public:
    static void main() {
        int a = 2;
        int b = 3;
        int result = (a + b) * 4;
        result = result + (2 + 3) * 4;
        std::cout << result << std::endl;
    }
};
int main() {
    Program::main();
    return 0;
}
