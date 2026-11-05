struct Cell { int value; };

void update(int& target) { target = 7; }
void maybe_update(int& target, bool enabled) { if (enabled) target = 9; }
void two_aliases(int& first, int& second) { first = 3; second = first + 1; }
int read_back(int& source) { return source; }
int* returned_alias(int* source) { return source; }
void left(int& target, int depth);
void right(int& target, int depth) {
    if (depth > 0) left(target, depth - 1);
    else target = 13;
}
void left(int& target, int depth) {
    if (depth > 0) right(target, depth - 1);
    else target = 11;
}
void write_then_throw(int& target) { target = 17; throw 1; }

int main() {
    int first_object = 1;
    int second_object = 2;
    int* writer = &first_object;
    int* reader = writer;
    *writer = 5;
    if (*reader != 5) return 1;
    update(first_object);
    if (read_back(first_object) != 7 || second_object != 2) return 2;
    update(second_object);
    maybe_update(first_object, false);
    if (first_object != 7 || second_object != 7) return 3;
    maybe_update(first_object, true);
    if (first_object != 9) return 4;
    two_aliases(first_object, first_object);
    if (first_object != 4) return 5;
    int* returned = returned_alias(&second_object);
    *returned = 6;
    if (second_object != 6) return 6;
    Cell cell;
    cell.value = 1;
    int* field = &cell.value;
    update(*field);
    if (cell.value != 7) return 7;
    int elements[2] = {1, 2};
    int* element = &elements[1];
    update(*element);
    if (elements[0] != 1 || elements[1] != 7) return 8;
    left(first_object, 3);
    if (first_object != 13) return 9;
    try { write_then_throw(second_object); }
    catch (int) { if (second_object != 17) return 10; }
    return 0;
}
