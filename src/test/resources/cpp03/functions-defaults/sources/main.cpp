int counter = 0;
int total = 0;
int next() { counter += 1; return counter; }
void log(int level = next());
void log(int level) { total += level; }
int sum(int left, int right = 4);
int sum(int left = 3, int right);
int sum(int left, int right) { return left + right; }
inline int identity(int &value) { return value; }
int first(const int values[], int size = 1) { return values[0] + size - 1; }
int variadic(int value, ...) { return value; }
typedef int (*Unary)(int);
int twice(int value) { return value * 2; }
int main() {
    log(); log(2); log();
    if (counter != 2 || total != 5) return 1;
    if (sum() != 7 || sum(5) != 9 || sum(1, 2) != 3) return 2;
    int values[1]; values[0] = 8;
    if (first(values) != 8) return 3;
    int base = 6; int &alias = base;
    if (identity(alias) != 6) return 4;
    Unary pointer = twice;
    if (pointer(3) != 6 || (*pointer)(4) != 8) return 5;
    if (variadic(9, 1.0f, 'A') != 9) return 6;
    return 0;
}
