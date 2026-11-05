#include <new>
void allocation(void* p){::operator new(1);::operator new[](1);::operator new(1,std::nothrow);::operator new[](1,std::nothrow);::operator new(1,p);::operator new[](1,p);::operator delete(p);::operator delete[](p);::operator delete(p,std::nothrow);::operator delete[](p,std::nothrow);::operator delete(p,p);::operator delete[](p,p);}
