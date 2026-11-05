// Independent strict-C++03 overload probes. Never executed: allocations and facet
// operations below check declarations, not library effects or host constants.
#include <utility>
#include <memory>
#include <functional>
#include <exception>
#include <new>
#include <stdexcept>
#include <typeinfo>
#include <limits>
#include <bitset>
#include <complex>
#include <valarray>
#include <locale>
#include <sstream>

namespace remaining_cpp03 {
int unary(int x) { return x; }
int binary(int x, int y) { return x+y; }
int byref(const int& x) { return x; }
struct callable { int f(){return 1;} int g(int x){return x;} int cf()const{return 1;} int cg(int x)const{return x;} };
void utilities(std::allocator<int>& a, const std::allocator<int>& ca, int& i, const int& ci) {
    std::pair<int,long> p; std::pair<int,long> q(i,2L); std::pair<long,long> r(q);
    p=std::make_pair(i,2L); bool b=p==q || p!=q || p<q || p>q || p<=q || p>=q;
    int* ap=a.address(i); const int* cap=ca.address(ci);
    ap=a.allocate(2); ap=a.allocate(2,cap); a.construct(ap,i); a.destroy(ap); a.deallocate(ap,2); a.max_size();
    std::allocator<int>::rebind<long>::other la; b=a==la; b=a!=la;
    std::auto_ptr<int> x(new int(1)), y(x); y=x; y.get(); y.release(); y.reset(); y.reset(ap); *y; y.operator->();
    std::pair<int*,std::ptrdiff_t> temp=std::get_temporary_buffer<int>(3); std::return_temporary_buffer(temp.first);
    std::uninitialized_copy(ap,ap,ap); std::uninitialized_fill(ap,ap,i); std::uninitialized_fill_n(ap,0,i);
    std::raw_storage_iterator<int*,int> raw(ap); *raw=i; ++raw; raw++;
    (void)b;(void)r;
}
void functional(callable& c, const callable& cc) {
    std::plus<int>()(1,2); std::minus<int>()(1,2); std::multiplies<int>()(1,2); std::divides<int>()(1,2);
    std::modulus<int>()(1,2); std::negate<int>()(1); std::equal_to<int>()(1,2); std::not_equal_to<int>()(1,2);
    std::less<int>()(1,2); std::greater<int>()(1,2); std::less_equal<int>()(1,2); std::greater_equal<int>()(1,2);
    std::logical_and<int>()(1,2); std::logical_or<int>()(1,2); std::logical_not<int>()(1);
    std::not1(std::logical_not<int>())(1); std::not2(std::less<int>())(1,2);
    std::bind1st(std::plus<int>(),1)(2); std::bind2nd(std::plus<int>(),1)(2);
    std::ptr_fun(&unary)(1); std::ptr_fun(&binary)(1,2);
    std::mem_fun(&callable::f)(&c); std::mem_fun(&callable::g)(&c,1);
    std::mem_fun(&callable::cf)(&cc); std::mem_fun(&callable::cg)(&cc,1);
    std::mem_fun_ref(&callable::f)(c); std::mem_fun_ref(&callable::g)(c,1);
    std::mem_fun_ref(&callable::cf)(cc); std::mem_fun_ref(&callable::cg)(cc,1);
}
void (*terminate_function)()=&std::terminate;
void (*unexpected_function)()=&std::unexpected;
bool (*uncaught_function)()=&std::uncaught_exception;
std::terminate_handler (*terminate_setter)(std::terminate_handler)=&std::set_terminate;
std::unexpected_handler (*unexpected_setter)(std::unexpected_handler)=&std::set_unexpected;
void* (*new_plain)(std::size_t)=&::operator new;
void* (*new_array)(std::size_t)=&::operator new[];
void* (*new_nothrow)(std::size_t,const std::nothrow_t&)=&::operator new;
void* (*new_array_nothrow)(std::size_t,const std::nothrow_t&)=&::operator new[];
void* (*new_placement)(std::size_t,void*)=&::operator new;
void* (*new_array_placement)(std::size_t,void*)=&::operator new[];
void (*delete_plain)(void*)=&::operator delete;
void (*delete_array)(void*)=&::operator delete[];
void (*delete_nothrow)(void*,const std::nothrow_t&)=&::operator delete;
void (*delete_array_nothrow)(void*,const std::nothrow_t&)=&::operator delete[];
void (*delete_placement)(void*,void*)=&::operator delete;
void (*delete_array_placement)(void*,void*)=&::operator delete[];
void exceptions(const std::type_info& a,const std::type_info& b) {
    std::exception e; std::bad_exception be; std::bad_alloc ba; std::bad_cast bc; std::bad_typeid bt;
    e=be; e.what();ba.what();bc.what();bt.what(); (void)(a==b); (void)(a!=b); a.before(b); a.name();
    std::string s("error"); std::logic_error le(s); std::domain_error de(s); std::invalid_argument ia(s);
    std::length_error len(s); std::out_of_range oor(s); std::runtime_error re(s);
    std::range_error range(s); std::overflow_error over(s); std::underflow_error under(s);
    le.what(); re.what();
}
template<class T> void limits() {
    T (*minimum)()=&std::numeric_limits<T>::min; T (*maximum)()=&std::numeric_limits<T>::max;
    T (*eps)()=&std::numeric_limits<T>::epsilon; T (*rounding)()=&std::numeric_limits<T>::round_error;
    T (*inf)()=&std::numeric_limits<T>::infinity; T (*quiet)()=&std::numeric_limits<T>::quiet_NaN;
    T (*signaling)()=&std::numeric_limits<T>::signaling_NaN; T (*denorm)()=&std::numeric_limits<T>::denorm_min;
    (void)minimum;(void)maximum;(void)eps;(void)rounding;(void)inf;(void)quiet;(void)signaling;(void)denorm;
}
void scalars() {
    limits<bool>();limits<char>();limits<signed char>();limits<unsigned char>();limits<wchar_t>();
    limits<short>();limits<unsigned short>();limits<int>();limits<unsigned int>();limits<long>();limits<unsigned long>();
    limits<float>();limits<double>();limits<long double>();
}
void bits(std::bitset<8>& b,const std::bitset<8>& cb,std::istream& in,std::ostream& out) {
    std::bitset<8> a; std::bitset<8> v(3UL); std::bitset<8> s(std::string("101"),0,3);
    b.set();b.set(1);b.set(1,false);b.reset();b.reset(1);b.flip();b.flip(1);
    bool bit=cb[0]; std::bitset<8>::reference r=b[0];r=true;r=b[1];r.flip();bit=~r;
    b&=cb;b|=cb;b^=cb;b<<=1;b>>=1; b=cb&cb;b=cb|cb;b=cb^cb;b=~cb;b=cb<<1;b=cb>>1;
    b.to_ulong();b.to_string<char,std::char_traits<char>,std::allocator<char> >();b.count();b.size();b.test(1);b.any();b.none();
    bit=b==cb;bit=b!=cb;in>>b;out<<b;(void)bit;
}
template<class T> void complex_values(std::complex<T> z,T x,std::istream& in,std::ostream& out) {
    z+z;z+x;x+z;z-z;z-x;x-z;z*z;z*x;x*z;z/z;z/x;x/z;
    (void)(z==z);(void)(z==x);(void)(x==z);(void)(z!=z);(void)(z!=x);(void)(x!=z);+z;-z;
    z=x;z+=x;z-=x;z*=x;z/=x;z+=z;z-=z;z*=z;z/=z;
    z.real();z.imag();std::real(z);std::imag(z);std::abs(z);std::arg(z);std::norm(z);std::conj(z);
    std::polar(x);std::polar(x,x);std::pow(z,2);std::pow(z,x);std::pow(z,z);std::pow(x,z);
    std::cos(z);std::cosh(z);std::exp(z);std::log(z);std::log10(z);std::sin(z);std::sinh(z);std::sqrt(z);std::tan(z);std::tanh(z);
    in>>z;out<<z;
}
void complex_specializations(std::istream& in,std::ostream& out) {
    complex_values(std::complex<float>(),1.0F,in,out);complex_values(std::complex<double>(),1.0,in,out);
    complex_values(std::complex<long double>(),1.0L,in,out);
}
void arrays(std::valarray<int>& v,const std::valarray<int>& cv) {
    int source[]={1,2};std::valarray<int> a;std::valarray<int> b(2);std::valarray<int> c(1,2);std::valarray<int> d(source,2);std::valarray<int> e(cv);
    std::slice s(0,1,1);std::valarray<std::size_t> dimensions(std::size_t(1),1);std::gslice gs(0,dimensions,dimensions);
    std::valarray<bool> mask(true,2);std::valarray<std::size_t> indices(std::size_t(0),1);
    std::valarray<int> f(v[s]),g(v[gs]),h(v[mask]),i(v[indices]);
    a=cv[s];a=cv[gs];a=cv[mask];a=cv[indices];a=v[s];a=v[gs];a=v[mask];a=v[indices];a=1;
    v[s]=1;v[gs]=1;v[mask]=1;v[indices]=1;v[s]+=a;v[gs]+=a;v[mask]+=a;v[indices]+=a;
    int x=cv[0];v[0]=x;v.size();v.sum();v.min();v.max();v.shift(1);v.cshift(-1);
    v.apply(&unary);v.apply(&byref);v.resize(3);v.resize(3,1);
    a=v+v;a=v+1;a=1+v;a=v-v;a=v*v;a=v/v;a=v%v;a=v^v;a=v&v;a=v|v;a=v<<1;a=v>>1;
    mask=v==v;mask=v!=v;mask=v<v;mask=v<=v;mask=v>v;mask=v>=v;mask=v&&v;mask=v||v;
    a=+v;a=-v;a=~v;mask=!v;
}
void facets(const std::locale& loc,std::ios_base& io) {
    std::locale a;std::locale b(loc);std::locale c("C");std::locale d(loc,"C",std::locale::numeric);std::locale e(loc,c,std::locale::numeric);
    std::has_facet<std::ctype<char> >(loc);std::use_facet<std::ctype<char> >(loc);loc.name();(void)(a==loc);(void)(a!=loc);loc(std::string("a"),std::string("b"));
    const std::ctype<char>& ct=std::use_facet<std::ctype<char> >(loc);char buf[4]="abc";std::ctype_base::mask masks[4];
    ct.is(std::ctype_base::alpha,'a');ct.is(buf,buf+3,masks);ct.scan_is(std::ctype_base::alpha,buf,buf+3);ct.scan_not(std::ctype_base::alpha,buf,buf+3);
    ct.toupper('a');ct.toupper(buf,buf+3);ct.tolower('A');ct.tolower(buf,buf+3);ct.widen('a');ct.widen(buf,buf+3,buf);ct.narrow('a','?');ct.narrow(buf,buf+3,'?',buf);
    const std::codecvt<wchar_t,char,std::mbstate_t>& cv=std::use_facet<std::codecvt<wchar_t,char,std::mbstate_t> >(loc);
    std::mbstate_t state=std::mbstate_t();wchar_t wide[4];const wchar_t* wf=wide;wchar_t* wt=wide;const char* from=buf;char* to=buf;
    cv.out(state,wide,wide+3,wf,buf,buf+3,to);cv.in(state,buf,buf+3,from,wide,wide+3,wt);cv.unshift(state,buf,buf+3,to);
    cv.encoding();cv.always_noconv();cv.length(state,buf,buf+3,3);cv.max_length();
    const std::numpunct<char>& np=std::use_facet<std::numpunct<char> >(loc);np.decimal_point();np.thousands_sep();np.grouping();np.truename();np.falsename();
    const std::collate<char>& co=std::use_facet<std::collate<char> >(loc);co.compare(buf,buf+3,buf,buf+3);co.transform(buf,buf+3);co.hash(buf,buf+3);
    const std::moneypunct<char>& mp=std::use_facet<std::moneypunct<char> >(loc);mp.decimal_point();mp.thousands_sep();mp.grouping();mp.curr_symbol();mp.positive_sign();mp.negative_sign();mp.frac_digits();mp.pos_format();mp.neg_format();
    const std::messages<char>& ms=std::use_facet<std::messages<char> >(loc);std::messages_base::catalog cat=ms.open("unused",loc);ms.get(cat,1,1,std::string());ms.close(cat);
    (void)io;
}
}

namespace remaining_cpp03 {
void numeric_time_money(const std::locale& loc,std::ios_base& io) {
    typedef std::istreambuf_iterator<char> input; typedef std::ostreambuf_iterator<char> output;
    input first,last; output dest((std::streambuf*)0); std::ios_base::iostate err=std::ios_base::goodbit;
    const std::num_get<char>& ng=std::use_facet<std::num_get<char> >(loc);
    bool b=false;long l=0;unsigned short us=0;unsigned int ui=0;unsigned long ul=0;float f=0;double d=0;long double ld=0;void* p=0;
    ng.get(first,last,io,err,b);ng.get(first,last,io,err,l);ng.get(first,last,io,err,us);ng.get(first,last,io,err,ui);ng.get(first,last,io,err,ul);
    ng.get(first,last,io,err,f);ng.get(first,last,io,err,d);ng.get(first,last,io,err,ld);ng.get(first,last,io,err,p);
    const std::num_put<char>& np=std::use_facet<std::num_put<char> >(loc);
    np.put(dest,io,' ',b);np.put(dest,io,' ',l);np.put(dest,io,' ',ul);np.put(dest,io,' ',d);np.put(dest,io,' ',ld);np.put(dest,io,' ',p);
    const std::time_get<char>& tg=std::use_facet<std::time_get<char> >(loc);std::tm tm=std::tm();
    tg.date_order();tg.get_time(first,last,io,err,&tm);tg.get_date(first,last,io,err,&tm);tg.get_weekday(first,last,io,err,&tm);tg.get_monthname(first,last,io,err,&tm);tg.get_year(first,last,io,err,&tm);
    const std::time_put<char>& tp=std::use_facet<std::time_put<char> >(loc);const char* format="%Y";
    tp.put(dest,io,' ',&tm,format,format+2);tp.put(dest,io,' ',&tm,'Y');tp.put(dest,io,' ',&tm,'Y','E');
    const std::money_get<char>& mg=std::use_facet<std::money_get<char> >(loc);std::string digits;
    mg.get(first,last,false,io,err,ld);mg.get(first,last,false,io,err,digits);
    const std::money_put<char>& mp=std::use_facet<std::money_put<char> >(loc);mp.put(dest,false,io,' ',ld);mp.put(dest,false,io,' ',digits);
}
}

namespace remaining_cpp03 {
void array_math(const std::valarray<double>& v) {
    std::valarray<double> a;
    a=std::abs(v);a=std::acos(v);a=std::asin(v);a=std::atan(v);a=std::cos(v);a=std::cosh(v);a=std::exp(v);a=std::log(v);a=std::log10(v);
    a=std::sin(v);a=std::sinh(v);a=std::sqrt(v);a=std::tan(v);a=std::tanh(v);
    a=std::atan2(v,v);a=std::atan2(v,1.0);a=std::atan2(1.0,v);a=std::pow(v,v);a=std::pow(v,1.0);a=std::pow(1.0,v);
}
}
