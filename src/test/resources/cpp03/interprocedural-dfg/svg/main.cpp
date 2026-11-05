void update(int& target) { target = 7; }
int main() {
    int value = 1;
    update(value);
    return value == 7 ? 0 : 1;
}
