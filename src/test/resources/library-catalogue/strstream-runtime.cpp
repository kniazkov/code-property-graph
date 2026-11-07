// Strict C++03 native lifecycle fixture. MIT License.
#include <strstream>

int main() {
    const char input[] = "17 23";
    std::istrstream source(input, 5);
    int first = 0;
    int second = 0;
    source >> first >> second;
    if (first != 17 || second != 23) return 1;

    char output[32] = {0};
    std::ostrstream sink(output, 32);
    sink << 42 << ' ';
    if (sink.pcount() != 3) return 2;
    char* fixed = sink.str();
    if (fixed[0] != '4' || fixed[1] != '2' || fixed[2] != ' ') return 3;
    sink.freeze(false);

    char duplex[32] = {0};
    std::strstream both(duplex, 32, std::ios_base::in | std::ios_base::out);
    both << 19 << ' ';
    int roundtrip = 0;
    both >> roundtrip;
    if (roundtrip != 19 || both.pcount() != 3) return 4;

    std::ostrstream dynamic;
    dynamic << 73 << ' ';
    char* text = dynamic.str();
    int failed = 0;
    if (dynamic.pcount() != 3) failed = 5;
    if (text[0] != '7' || text[1] != '3' || text[2] != ' ') failed = 6;
    dynamic.freeze(false);
    return failed;
}
