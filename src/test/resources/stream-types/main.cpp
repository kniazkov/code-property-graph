#include <sstream>
int main() {
    std::streamsize count = 3;
    std::ios_base::seekdir direction = std::ios_base::beg;
    std::stringstream stream;
    stream.seekg(0, direction);
    stream.width(count);
    std::wstringstream wide;
    wide.seekg(0, direction);
    wide.width(count);
    return stream.width() == 3 && wide.width() == 3 ? 0 : 1;
}
