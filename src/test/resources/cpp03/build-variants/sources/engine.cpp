#ifdef SERVER
class Active { public: static void main() { int marker = 11; } };
int run() { Active::main(); return 11; }
#else
class Active { public: static void main() { int marker = 22; } };
int run() { Active::main(); return 22; }
#endif
