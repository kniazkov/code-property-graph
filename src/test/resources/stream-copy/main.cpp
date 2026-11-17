#include <sstream>
int main() {
    std::ostringstream a, b;
    std::wostringstream wa, wb;
    a.width(7); b.copyfmt(a);
    wa.width(9); wb.copyfmt(wa);
    return b.width() == 7 && wb.width() == 9 ? 0 : 1;
}
