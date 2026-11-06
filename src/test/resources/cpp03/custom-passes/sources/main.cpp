// The public Java extension replaces this addition in one immutable batch.
int helper() {
    return 1 + 2;
}

// A separate function proves that a scoped rerun retains unrelated results.
int untouched() {
    return 4 + 5;
}

int main() {
    return helper() + untouched() - 12;
}
