// Joining the two assignments uses their interval hull, not the set {1, 10}.
int joined(int choose) {
    int value;
    if (choose) {
        value = 1;
    } else {
        value = 10;
    }
    // Abstractly possible because five belongs to [1, 10], although neither
    // concrete branch assigns five. Analysis must preserve this written test.
    if (value == 5) {
        return 100;
    }
    return value;
}

int main() {
    if (joined(1) != 1) {
        return 1;
    }
    if (joined(0) != 10) {
        return 2;
    }
    return 0;
}
