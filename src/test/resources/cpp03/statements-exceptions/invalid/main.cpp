struct Guard { Guard() {} ~Guard() {} };
int main() { goto done; Guard guard; done: return 0; }
