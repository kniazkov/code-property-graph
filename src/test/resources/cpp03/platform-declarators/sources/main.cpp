typedef int Count;
Count scalar;
Count *alias_pointer;
int *array_of_pointers[3];
int (*pointer_to_array)[3];
int (*function_pointer)(double);
struct Cell { int value; };
int Cell::*member_pointer;
const int * const qualified_pointer = 0;
int &reference = scalar;
int consume(int (*)[3], int (*)(double));
int main() {
    int *local_array[3], (*local_matrix)[3];
    return sizeof(long) + sizeof(int[3]);
}
