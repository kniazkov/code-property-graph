#include <fstream>
int main() {
    std::ifstream a; std::ofstream b; std::fstream c;
    std::wifstream wa; std::wofstream wb; std::wfstream wc;
    const std::filebuf buffer; const std::wfilebuf wbuffer;
    return a.is_open() || b.is_open() || c.is_open() || wa.is_open() || wb.is_open() || wc.is_open() || buffer.is_open() || wbuffer.is_open();
}
