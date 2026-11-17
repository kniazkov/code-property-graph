#include <ios>
#include <limits>
int main() {
    return std::numeric_limits<std::streamsize>::is_integer &&
        std::numeric_limits<std::streamsize>::is_signed &&
        !__is_enum(std::streamsize) && __is_enum(std::ios_base::seekdir) ? 0 : 1;
}
