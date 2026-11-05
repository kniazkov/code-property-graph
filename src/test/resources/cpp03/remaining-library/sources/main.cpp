#include <sstream>
#include <string>
#include <iterator>
#include <iomanip>

int stream_state() {
    std::string input("12 34");
    std::istringstream stream(input);
    int first = 0;
    int second = 0;
    stream >> first >> second;
    if (first != 12 || second != 34 || stream.fail()) return 1;
    int missing = 0;
    stream >> missing;
    if (!stream.fail() || !stream.eof()) return 2;
    stream.clear();
    stream.str(input);
    if (stream.fail() || stream.eof()) return 3;
    stream >> first;
    if (first != 12) return 4;
    std::ostringstream output;
    output << first << ' ' << second;
    std::string rendered = output.str();
    if (rendered != "12 34") return 5;
    return 0;
}

int stream_iterators() {
    std::string input("5 7");
    std::istringstream source(input);
    std::istream_iterator<int> item(source);
    std::istream_iterator<int> end;
    if (item == end || *item != 5) return 1;
    ++item;
    if (item == end || *item != 7) return 2;
    ++item;
    if (item != end || !source.fail() || !source.eof()) return 3;
    std::ostringstream destination;
    std::ostream_iterator<int> sink(destination, ",");
    *sink++ = 5;
    *sink++ = 7;
    std::string result = destination.str();
    if (result != "5,7,") return 4;
    return 0;
}

int formatted_streams() {
    std::ostringstream formatted;
    formatted << std::fixed << std::setprecision(2) << 1.5;
    std::string text = formatted.str();
    if (text != "1.50") return 1;
    std::wstring input(L"9 10");
    std::wistringstream wide(input);
    int left = 0;
    int right = 0;
    wide >> left >> right;
    if (left != 9 || right != 10 || wide.fail()) return 2;
    return 0;
}

int main() {
    if (stream_state() != 0) return 10;
    if (stream_iterators() != 0) return 20;
    if (formatted_streams() != 0) return 30;
    return 0;
}
