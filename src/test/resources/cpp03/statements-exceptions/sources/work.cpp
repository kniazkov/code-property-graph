extern bool shouldThrow;
void work() { if (shouldThrow) throw 7; }
void quiet() throw() {}
