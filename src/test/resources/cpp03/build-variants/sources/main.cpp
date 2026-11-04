int run();
int main() {
#ifdef SERVER
    return run() - 11;
#else
    return run() - 22;
#endif
}
