int trace = 0;
int destroyed = 0;
int throwId = 0;
bool shouldThrow = false;
void work();
void quiet() throw();
struct Guard {
    int id;
    Guard(int value) : id(value) {
        if (id == throwId) throw id;
        trace = trace * 10 + id;
    }
    ~Guard() throw() { trace = trace * 10 + id; destroyed++; }
};
void normalPair() { Guard a(1); Guard b(2); work(); }
void unknownPath() { try { Guard g(1); work(); } catch (...) { trace = trace * 10 + 9; } }
void quietPath() { try { Guard g(1); quiet(); } catch (...) { trace = trace * 10 + 9; } }
int returning() { Guard g(1); return 7; }
void gotoCleanup() { { Guard g(1); goto done; } done:; }
void loopCleanup() { for (int i = 0; i < 3; ++i) { Guard g(1); if (i < 2) continue; break; } }
struct Pair {
    Guard first;
    Guard second;
    Pair() try : first(1), second(2) {} catch (...) { trace = trace * 10 + 8; throw; }
};
struct Owner {
    Guard first;
    Guard second;
    Owner() : first(1), second(2) {}
    ~Owner() try { work(); } catch (...) { trace = trace * 10 + 8; throw; }
};
int nextValue = 2;
int next() { return nextValue--; }
int statements() {
    int sum = 0;
    ;
    for (int i = 0; i < 5; ++i) {
        switch (i) {
            case 0: continue;
            case 1: sum += 1; break;
            case 2: sum += 2;
            case 3: sum += 3; break;
            default: sum += 4;
        }
        if (i == 3) break;
    }
    do { sum--; } while (sum > 7);
    while (int value = next()) { sum += value; }
    if (int value = 2) sum += value;
    switch (int value = 1) { case 1: sum++; default: sum++; }
    goto end;
    sum += 100;
    end:;
    return sum;
}
int handlers() {
    try { try { throw 3; } catch (double value) { return 1; } catch (const int& value) { if (value != 3) return 2; throw; } }
    catch (int value) { return value; }
    catch (...) { return 4; }
}
int wrapped() try { work(); return 1; } catch (int value) { return 2; } catch (...) { return 3; }
void reset() { trace = 0; destroyed = 0; throwId = 0; shouldThrow = false; }
int main() {
    reset(); normalPair(); if (trace != 1221 || destroyed != 2) return 1;
    reset(); shouldThrow = true; try { normalPair(); } catch (...) { trace = trace * 10 + 9; }
    if (trace != 12219 || destroyed != 2) return 2;
    reset(); throwId = 2; try { normalPair(); } catch (...) { trace = trace * 10 + 9; }
    if (trace != 119 || destroyed != 1) return 3;
    reset(); throwId = 2; try { Pair pair; } catch (...) { trace = trace * 10 + 9; }
    if (trace != 1189 || destroyed != 1) return 4;
    reset(); shouldThrow = true; unknownPath(); if (trace != 119 || destroyed != 1) return 5;
    reset(); shouldThrow = true; quietPath(); if (trace != 11 || destroyed != 1) return 6;
    reset(); if (returning() != 7 || trace != 11 || destroyed != 1) return 7;
    reset(); gotoCleanup(); if (trace != 11 || destroyed != 1) return 8;
    reset(); loopCleanup(); if (trace != 111111 || destroyed != 3) return 9;
    if (statements() != 14 || handlers() != 3) return 10;
    shouldThrow = true; if (wrapped() != 2) return 11;
    reset(); { Owner owner; } if (trace != 1221 || destroyed != 2) return 12;
    reset(); shouldThrow = true; try { Owner owner; } catch (...) { trace = trace * 10 + 9; }
    if (trace != 122189 || destroyed != 2) return 13;
    return 0;
}
