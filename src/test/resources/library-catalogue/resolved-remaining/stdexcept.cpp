#include <stdexcept>
void errors(const std::logic_error& a,const std::domain_error& b,const std::invalid_argument& c,const std::length_error& d,const std::out_of_range& e,const std::runtime_error& f,const std::range_error& g,const std::overflow_error& h,const std::underflow_error& i){a.what();b.what();c.what();d.what();e.what();f.what();g.what();h.what();i.what();}
