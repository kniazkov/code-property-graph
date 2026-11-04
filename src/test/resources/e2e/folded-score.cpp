#include <iostream>

class Demo {
public:
    static void main() {
        int score = 1;
        score = 5;
        std::cout << score << std::endl;
    }
};

int main() {
    Demo::main();
    return 0;
}
