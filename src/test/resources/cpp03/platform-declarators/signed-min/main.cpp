#define PRESERVE 1
int pick(int value) { return 1; }
int pick(long value) { return 2; }
int main() { return pick(-1073741824 * 2); }
