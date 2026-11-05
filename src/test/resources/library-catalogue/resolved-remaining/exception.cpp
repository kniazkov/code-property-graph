#include <exception>
#include <new>
#include <typeinfo>
void errors(std::exception& e,std::bad_alloc& a,const std::type_info& t,const std::type_info& u){e.what();a.what();t.before(u);t.name();(void)std::uncaught_exception();std::set_terminate(0);std::set_unexpected(0);std::set_new_handler(0);}
