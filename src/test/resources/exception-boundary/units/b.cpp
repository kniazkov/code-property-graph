#include <exception>
std::unexpected_handler exchange(std::unexpected_handler p)throw(){return std::set_unexpected(p);}
