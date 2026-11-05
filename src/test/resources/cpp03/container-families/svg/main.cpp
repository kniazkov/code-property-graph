#include <list>
int main() {
    std::list<int> source;
    source.push_back(4);
    std::list<int> destination;
    std::list<int>::iterator item = source.begin();
    int& reference = *item;
    destination.splice(destination.end(), source, item);
    reference = 9;
    return source.empty() && destination.front() == 9 ? 0 : 1;
}
