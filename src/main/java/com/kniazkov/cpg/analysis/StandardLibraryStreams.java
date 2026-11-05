/* Portable C++03 interface declarations. No implementation bodies. MIT License. */
package com.kniazkov.cpg.analysis;
import java.util.Set;
final class StandardLibraryStreams {
    private StandardLibraryStreams() {}
    static String declarations(String header) { return switch (header) {
        case "iosfwd" -> """
typedef __cpg_opaque_integer_streamoff streamoff; typedef __cpg_opaque_integer_streamsize streamsize;
template<class CharT> struct char_traits;
template<class T> class allocator;
template<class State> class fpos { public: fpos(streamoff = 0); operator streamoff() const; State state() const; void state(State); fpos& operator+=(streamoff); fpos& operator-=(streamoff); fpos operator+(streamoff) const; fpos operator-(streamoff) const; streamoff operator-(const fpos&) const; };
template<class State> bool operator==(const fpos<State>&, const fpos<State>&);
template<class State> bool operator!=(const fpos<State>&, const fpos<State>&);
typedef fpos<mbstate_t> streampos; typedef fpos<mbstate_t> wstreampos;
template<class CharT, class Traits = char_traits<CharT> > class basic_ios;
typedef basic_ios<char> ios;
typedef basic_ios<wchar_t> wios;
template<class CharT, class Traits = char_traits<CharT> > class basic_streambuf;
typedef basic_streambuf<char> streambuf;
typedef basic_streambuf<wchar_t> wstreambuf;
template<class CharT, class Traits = char_traits<CharT> > class basic_istream;
typedef basic_istream<char> istream;
typedef basic_istream<wchar_t> wistream;
template<class CharT, class Traits = char_traits<CharT> > class basic_ostream;
typedef basic_ostream<char> ostream;
typedef basic_ostream<wchar_t> wostream;
template<class CharT, class Traits = char_traits<CharT> > class basic_iostream;
typedef basic_iostream<char> iostream;
typedef basic_iostream<wchar_t> wiostream;
template<class CharT, class Traits = char_traits<CharT> > class basic_filebuf;
typedef basic_filebuf<char> filebuf;
typedef basic_filebuf<wchar_t> wfilebuf;
template<class CharT, class Traits = char_traits<CharT> > class basic_ifstream;
typedef basic_ifstream<char> ifstream;
typedef basic_ifstream<wchar_t> wifstream;
template<class CharT, class Traits = char_traits<CharT> > class basic_ofstream;
typedef basic_ofstream<char> ofstream;
typedef basic_ofstream<wchar_t> wofstream;
template<class CharT, class Traits = char_traits<CharT> > class basic_fstream;
typedef basic_fstream<char> fstream;
typedef basic_fstream<wchar_t> wfstream;
template<class CharT, class Traits = char_traits<CharT>, class Allocator = allocator<CharT> > class basic_stringbuf;
typedef basic_stringbuf<char> stringbuf;
typedef basic_stringbuf<wchar_t> wstringbuf;
template<class CharT, class Traits = char_traits<CharT>, class Allocator = allocator<CharT> > class basic_istringstream;
typedef basic_istringstream<char> istringstream;
typedef basic_istringstream<wchar_t> wistringstream;
template<class CharT, class Traits = char_traits<CharT>, class Allocator = allocator<CharT> > class basic_ostringstream;
typedef basic_ostringstream<char> ostringstream;
typedef basic_ostringstream<wchar_t> wostringstream;
template<class CharT, class Traits = char_traits<CharT>, class Allocator = allocator<CharT> > class basic_stringstream;
typedef basic_stringstream<char> stringstream;
typedef basic_stringstream<wchar_t> wstringstream;

""";
        case "ios" -> """
class locale;
class ios_base { public:
class failure : public exception { public: explicit failure(const string&); virtual ~failure() throw(); virtual const char* what() const throw(); };
class Init { public: Init(); ~Init(); };
typedef __cpg_opaque_unspecified_fmtflags fmtflags; typedef __cpg_opaque_unspecified_iostate iostate;
typedef __cpg_opaque_unspecified_openmode openmode; typedef __cpg_opaque_unspecified_seekdir seekdir;
static const fmtflags boolalpha, dec, fixed, hex, internal, left, oct, right, scientific, showbase, showpoint, showpos, skipws, unitbuf, uppercase, adjustfield, basefield, floatfield;
static const iostate badbit, eofbit, failbit, goodbit;
static const openmode app, ate, binary, in, out, trunc;
static const seekdir beg, cur, end;
fmtflags flags() const; fmtflags flags(fmtflags); fmtflags setf(fmtflags); fmtflags setf(fmtflags, fmtflags); void unsetf(fmtflags);
streamsize precision() const; streamsize precision(streamsize); streamsize width() const; streamsize width(streamsize);
locale imbue(const locale&); locale getloc() const;
static int xalloc(); long& iword(int); void*& pword(int);
enum event { erase_event, imbue_event, copyfmt_event }; typedef void (*event_callback)(event, ios_base&, int);
void register_callback(event_callback, int); static bool sync_with_stdio(bool = true); virtual ~ios_base();
protected: ios_base(); };
template<class CharT, class Traits> class basic_ios : public ios_base { public:
typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type;
operator void*() const; bool operator!() const; ios_base::iostate rdstate() const; void clear(ios_base::iostate = goodbit); void setstate(ios_base::iostate); bool good() const; bool eof() const; bool fail() const; bool bad() const; ios_base::iostate exceptions() const; void exceptions(ios_base::iostate);
explicit basic_ios(basic_streambuf<CharT, Traits>*); virtual ~basic_ios();
basic_ostream<CharT, Traits>* tie() const; basic_ostream<CharT, Traits>* tie(basic_ostream<CharT, Traits>*);
basic_streambuf<CharT, Traits>* rdbuf() const; basic_streambuf<CharT, Traits>* rdbuf(basic_streambuf<CharT, Traits>*);
basic_ios& copyfmt(const basic_ios&); char_type fill() const; char_type fill(char_type); locale imbue(const locale&); char narrow(char_type, char) const; char_type widen(char) const;
protected: basic_ios(); void init(basic_streambuf<CharT, Traits>*); };
ios_base& boolalpha(ios_base&);
ios_base& noboolalpha(ios_base&);
ios_base& showbase(ios_base&);
ios_base& noshowbase(ios_base&);
ios_base& showpoint(ios_base&);
ios_base& noshowpoint(ios_base&);
ios_base& showpos(ios_base&);
ios_base& noshowpos(ios_base&);
ios_base& skipws(ios_base&);
ios_base& noskipws(ios_base&);
ios_base& uppercase(ios_base&);
ios_base& nouppercase(ios_base&);
ios_base& unitbuf(ios_base&);
ios_base& nounitbuf(ios_base&);
ios_base& internal(ios_base&);
ios_base& left(ios_base&);
ios_base& right(ios_base&);
ios_base& dec(ios_base&);
ios_base& hex(ios_base&);
ios_base& oct(ios_base&);
ios_base& fixed(ios_base&);
ios_base& scientific(ios_base&);

""";
        case "istream" -> """
template<class CharT, class Traits> class basic_istream : virtual public basic_ios<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type;
explicit basic_istream(basic_streambuf<CharT, Traits>*); virtual ~basic_istream();
class sentry { public: explicit sentry(basic_istream&, bool = false); ~sentry(); operator bool() const; private: sentry(const sentry&); sentry& operator=(const sentry&); };
basic_istream& operator>>(bool&);
basic_istream& operator>>(short&);
basic_istream& operator>>(unsigned short&);
basic_istream& operator>>(int&);
basic_istream& operator>>(unsigned int&);
basic_istream& operator>>(long&);
basic_istream& operator>>(unsigned long&);
basic_istream& operator>>(float&);
basic_istream& operator>>(double&);
basic_istream& operator>>(long double&);
basic_istream& operator>>(void*&);
basic_istream& operator>>(basic_istream& (*pf)(basic_istream&));
basic_istream& operator>>(basic_ios<CharT, Traits>& (*pf)(basic_ios<CharT, Traits>&));
basic_istream& operator>>(ios_base& (*pf)(ios_base&));
basic_istream& operator>>(basic_streambuf<CharT, Traits>*);
streamsize gcount() const; int_type get(); basic_istream& get(char_type&); basic_istream& get(char_type*, streamsize); basic_istream& get(char_type*, streamsize, char_type); basic_istream& get(basic_streambuf<CharT, Traits>&); basic_istream& get(basic_streambuf<CharT, Traits>&, char_type);
basic_istream& getline(char_type*, streamsize); basic_istream& getline(char_type*, streamsize, char_type); basic_istream& ignore(streamsize = 1, int_type = Traits::eof()); int_type peek(); basic_istream& read(char_type*, streamsize); streamsize readsome(char_type*, streamsize); basic_istream& putback(char_type); basic_istream& unget(); int sync(); pos_type tellg(); basic_istream& seekg(pos_type); basic_istream& seekg(off_type, ios_base::seekdir);
};
template<class CharT, class Traits> basic_istream<CharT, Traits>& operator>>(basic_istream<CharT, Traits>&, CharT&);
template<class CharT, class Traits> basic_istream<CharT, Traits>& operator>>(basic_istream<CharT, Traits>&, CharT*);
template<class Traits> basic_istream<char, Traits>& operator>>(basic_istream<char, Traits>&, unsigned char&);
template<class Traits> basic_istream<char, Traits>& operator>>(basic_istream<char, Traits>&, signed char&);
template<class Traits> basic_istream<char, Traits>& operator>>(basic_istream<char, Traits>&, unsigned char*);
template<class Traits> basic_istream<char, Traits>& operator>>(basic_istream<char, Traits>&, signed char*);
template<class CharT, class Traits> basic_istream<CharT, Traits>& ws(basic_istream<CharT, Traits>&);
template<class CharT, class Traits> class basic_iostream : public basic_istream<CharT, Traits>, public basic_ostream<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type; explicit basic_iostream(basic_streambuf<CharT, Traits>*); virtual ~basic_iostream(); };
""";
        case "ostream" -> """
template<class CharT, class Traits> class basic_ostream : virtual public basic_ios<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type;
explicit basic_ostream(basic_streambuf<CharT, Traits>*); virtual ~basic_ostream();
class sentry { public: explicit sentry(basic_ostream&); ~sentry(); operator bool() const; private: sentry(const sentry&); sentry& operator=(const sentry&); };
basic_ostream& operator<<(bool);
basic_ostream& operator<<(short);
basic_ostream& operator<<(unsigned short);
basic_ostream& operator<<(int);
basic_ostream& operator<<(unsigned int);
basic_ostream& operator<<(long);
basic_ostream& operator<<(unsigned long);
basic_ostream& operator<<(float);
basic_ostream& operator<<(double);
basic_ostream& operator<<(long double);
basic_ostream& operator<<(const void*);
basic_ostream& operator<<(basic_ostream& (*pf)(basic_ostream&));
basic_ostream& operator<<(basic_ios<CharT, Traits>& (*pf)(basic_ios<CharT, Traits>&));
basic_ostream& operator<<(ios_base& (*pf)(ios_base&));
basic_ostream& operator<<(basic_streambuf<CharT, Traits>*);
basic_ostream& put(char_type); basic_ostream& write(const char_type*, streamsize); basic_ostream& flush(); pos_type tellp(); basic_ostream& seekp(pos_type); basic_ostream& seekp(off_type, ios_base::seekdir);
};
template<class CharT, class Traits> basic_ostream<CharT, Traits>& operator<<(basic_ostream<CharT, Traits>&, CharT);
template<class CharT, class Traits> basic_ostream<CharT, Traits>& operator<<(basic_ostream<CharT, Traits>&, const CharT*);
template<class CharT, class Traits> basic_ostream<CharT, Traits>& operator<<(basic_ostream<CharT, Traits>&, char);
template<class CharT, class Traits> basic_ostream<CharT, Traits>& operator<<(basic_ostream<CharT, Traits>&, const char*);
template<class Traits> basic_ostream<char, Traits>& operator<<(basic_ostream<char, Traits>&, char);
template<class Traits> basic_ostream<char, Traits>& operator<<(basic_ostream<char, Traits>&, const char*);
template<class Traits> basic_ostream<char, Traits>& operator<<(basic_ostream<char, Traits>&, unsigned char);
template<class Traits> basic_ostream<char, Traits>& operator<<(basic_ostream<char, Traits>&, signed char);
template<class Traits> basic_ostream<char, Traits>& operator<<(basic_ostream<char, Traits>&, const unsigned char*);
template<class Traits> basic_ostream<char, Traits>& operator<<(basic_ostream<char, Traits>&, const signed char*);
template<class CharT, class Traits> basic_ostream<CharT, Traits>& endl(basic_ostream<CharT, Traits>&);
template<class CharT, class Traits> basic_ostream<CharT, Traits>& ends(basic_ostream<CharT, Traits>&);
template<class CharT, class Traits> basic_ostream<CharT, Traits>& flush(basic_ostream<CharT, Traits>&);

""";
        case "iostream" -> """
extern istream cin; extern ostream cout; extern ostream cerr; extern ostream clog; extern wistream wcin; extern wostream wcout; extern wostream wcerr; extern wostream wclog;
""";
        case "streambuf" -> """
template<class CharT, class Traits> class basic_streambuf { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type;
virtual ~basic_streambuf(); locale pubimbue(const locale&); locale getloc() const;
basic_streambuf* pubsetbuf(char_type*, streamsize); pos_type pubseekoff(off_type, ios_base::seekdir, ios_base::openmode = ios_base::in | ios_base::out); pos_type pubseekpos(pos_type, ios_base::openmode = ios_base::in | ios_base::out); int pubsync();
streamsize in_avail(); int_type snextc(); int_type sbumpc(); int_type sgetc(); streamsize sgetn(char_type*, streamsize); int_type sputbackc(char_type); int_type sungetc(); int_type sputc(char_type); streamsize sputn(const char_type*, streamsize);
protected: basic_streambuf(); char_type* eback() const; char_type* gptr() const; char_type* egptr() const; void gbump(int); void setg(char_type*, char_type*, char_type*); char_type* pbase() const; char_type* pptr() const; char_type* epptr() const; void pbump(int); void setp(char_type*, char_type*);
virtual void imbue(const locale&); virtual basic_streambuf* setbuf(char_type*, streamsize); virtual pos_type seekoff(off_type, ios_base::seekdir, ios_base::openmode = ios_base::in | ios_base::out); virtual pos_type seekpos(pos_type, ios_base::openmode = ios_base::in | ios_base::out); virtual int sync(); virtual streamsize showmanyc(); virtual streamsize xsgetn(char_type*, streamsize); virtual int_type underflow(); virtual int_type uflow(); virtual int_type pbackfail(int_type = Traits::eof()); virtual streamsize xsputn(const char_type*, streamsize); virtual int_type overflow(int_type = Traits::eof()); };
""";
        case "fstream" -> """
template<class CharT, class Traits> class basic_filebuf : public basic_streambuf<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type; basic_filebuf(); virtual ~basic_filebuf(); bool is_open() const; basic_filebuf* open(const char*, ios_base::openmode); basic_filebuf* close(); protected: virtual streamsize showmanyc(); virtual int_type underflow(); virtual int_type uflow(); virtual int_type pbackfail(int_type = Traits::eof()); virtual int_type overflow(int_type = Traits::eof()); virtual basic_streambuf<CharT, Traits>* setbuf(char_type*, streamsize); virtual pos_type seekoff(off_type, ios_base::seekdir, ios_base::openmode = ios_base::in | ios_base::out); virtual pos_type seekpos(pos_type, ios_base::openmode = ios_base::in | ios_base::out); virtual int sync(); virtual void imbue(const locale&); };
template<class CharT, class Traits> class basic_ifstream : public basic_istream<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type; basic_ifstream(); virtual ~basic_ifstream(); bool is_open() const; explicit basic_ifstream(const char*, ios_base::openmode = ios_base::in); void open(const char*, ios_base::openmode = ios_base::in); void close(); basic_filebuf<CharT, Traits>* rdbuf() const; };
template<class CharT, class Traits> class basic_ofstream : public basic_ostream<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type; basic_ofstream(); virtual ~basic_ofstream(); bool is_open() const; explicit basic_ofstream(const char*, ios_base::openmode = ios_base::out); void open(const char*, ios_base::openmode = ios_base::out); void close(); basic_filebuf<CharT, Traits>* rdbuf() const; };
template<class CharT, class Traits> class basic_fstream : public basic_iostream<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type; basic_fstream(); virtual ~basic_fstream(); bool is_open() const; explicit basic_fstream(const char*, ios_base::openmode = ios_base::in | ios_base::out); void open(const char*, ios_base::openmode = ios_base::in | ios_base::out); void close(); basic_filebuf<CharT, Traits>* rdbuf() const; };

""";
        case "sstream" -> """
template<class CharT, class Traits, class Allocator> class basic_stringbuf : public basic_streambuf<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type; typedef Allocator allocator_type; explicit basic_stringbuf(ios_base::openmode = ios_base::in | ios_base::out); explicit basic_stringbuf(const basic_string<CharT, Traits, Allocator>&, ios_base::openmode = ios_base::in | ios_base::out); virtual ~basic_stringbuf(); basic_string<CharT, Traits, Allocator> str() const; void str(const basic_string<CharT, Traits, Allocator>&); protected: virtual int_type underflow(); virtual int_type pbackfail(int_type = Traits::eof()); virtual int_type overflow(int_type = Traits::eof()); virtual basic_streambuf<CharT, Traits>* setbuf(char_type*, streamsize); virtual pos_type seekoff(off_type, ios_base::seekdir, ios_base::openmode = ios_base::in | ios_base::out); virtual pos_type seekpos(pos_type, ios_base::openmode = ios_base::in | ios_base::out); };
template<class CharT, class Traits, class Allocator> class basic_istringstream : public basic_istream<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type; typedef Allocator allocator_type; explicit basic_istringstream(ios_base::openmode = ios_base::in); explicit basic_istringstream(const basic_string<CharT, Traits, Allocator>&, ios_base::openmode = ios_base::in); virtual ~basic_istringstream(); basic_string<CharT, Traits, Allocator> str() const; void str(const basic_string<CharT, Traits, Allocator>&);basic_stringbuf<CharT, Traits, Allocator>* rdbuf() const; };
template<class CharT, class Traits, class Allocator> class basic_ostringstream : public basic_ostream<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type; typedef Allocator allocator_type; explicit basic_ostringstream(ios_base::openmode = ios_base::out); explicit basic_ostringstream(const basic_string<CharT, Traits, Allocator>&, ios_base::openmode = ios_base::out); virtual ~basic_ostringstream(); basic_string<CharT, Traits, Allocator> str() const; void str(const basic_string<CharT, Traits, Allocator>&);basic_stringbuf<CharT, Traits, Allocator>* rdbuf() const; };
template<class CharT, class Traits, class Allocator> class basic_stringstream : public basic_iostream<CharT, Traits> { public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type; typedef typename Traits::pos_type pos_type; typedef typename Traits::off_type off_type; typedef Allocator allocator_type; explicit basic_stringstream(ios_base::openmode = ios_base::in | ios_base::out); explicit basic_stringstream(const basic_string<CharT, Traits, Allocator>&, ios_base::openmode = ios_base::in | ios_base::out); virtual ~basic_stringstream(); basic_string<CharT, Traits, Allocator> str() const; void str(const basic_string<CharT, Traits, Allocator>&);basic_stringbuf<CharT, Traits, Allocator>* rdbuf() const; };

""";
        case "iomanip" -> """
__cpg_opaque_unspecified_resetiosflags_result resetiosflags(ios_base::fmtflags);
__cpg_opaque_unspecified_setiosflags_result setiosflags(ios_base::fmtflags);
__cpg_opaque_unspecified_setbase_result setbase(int);
__cpg_opaque_unspecified_setprecision_result setprecision(int);
__cpg_opaque_unspecified_setw_result setw(int);
template<class CharT> __cpg_opaque_unspecified_setfill_result setfill(CharT);
""";
        case "locale" -> locale();
        default -> null;
    }; }
    static boolean supports(String h) { return Set.of("iosfwd", "ios", "istream", "ostream", "iostream", "streambuf", "fstream", "sstream", "iomanip", "locale").contains(h); }
    static Set<String> dependencies(String h) { return switch(h) {
        case "iosfwd" -> Set.of("cstddef", "cwchar");
        case "ios" -> Set.of("iosfwd", "exception", "string");
        case "istream", "ostream", "streambuf" -> Set.of("ios");
        case "iostream" -> Set.of("istream", "ostream");
        case "fstream", "sstream" -> Set.of("istream", "ostream", "streambuf");
        case "iomanip" -> Set.of("ios"); case "locale" -> Set.of("string", "iterator", "ctime", "ios"); default -> Set.of(); }; }
    static Set<String> templateNames(String h) {
        var out = new java.util.LinkedHashSet<String>();
        if (h.equals("iosfwd")) {
            out.addAll(Set.of("fpos", "char_traits", "allocator"));
            for (String n : "ios streambuf istream ostream iostream filebuf ifstream ofstream fstream stringbuf istringstream ostringstream stringstream".split(" ")) out.add("basic_" + n);
        } else if (h.equals("ios")) out.add("basic_ios");
        else if (h.equals("istream")) { out.add("basic_istream"); out.add("basic_iostream"); }
        else if (h.equals("ostream") || h.equals("streambuf")) out.add("basic_" + h);
        else if (h.equals("fstream")) out.addAll(Set.of("basic_filebuf", "basic_ifstream", "basic_ofstream", "basic_fstream"));
        else if (h.equals("sstream")) out.addAll(Set.of("basic_stringbuf", "basic_istringstream", "basic_ostringstream", "basic_stringstream"));

        else if (h.equals("locale")) out.addAll(Set.of("ctype", "ctype_byname", "codecvt", "codecvt_byname", "num_get", "num_put", "numpunct", "numpunct_byname", "collate", "collate_byname", "time_get", "time_get_byname", "time_put", "time_put_byname", "moneypunct", "moneypunct_byname", "money_get", "money_put", "messages", "messages_byname"));
        return Set.copyOf(out);
    }
    static Set<String> typeNames(String h) {
        var out = new java.util.LinkedHashSet<String>(templateNames(h));
        if (h.equals("iosfwd")) {
            out.addAll(Set.of("streamoff", "streamsize", "streampos", "wstreampos", "char_traits", "allocator"));
            for (String n : "ios streambuf istream ostream iostream filebuf ifstream ofstream fstream stringbuf istringstream ostringstream stringstream".split(" ")) { out.add(n); out.add("w" + n); }
        } else if (h.equals("ios")) out.addAll(Set.of("ios_base", "locale"));
        else if (h.equals("locale")) out.addAll(Set.of("locale", "ios_base", "ctype_base", "codecvt_base", "time_base", "money_base", "messages_base"));

        return Set.copyOf(out);
    }
    static Set<String> nestedTypeNames(String h) { return switch(h) {
        case "ios" -> Set.of("ios_base::failure", "ios_base::Init", "ios_base::fmtflags", "ios_base::iostate", "ios_base::openmode", "ios_base::seekdir", "ios_base::event", "ios_base::event_callback");
        case "locale" -> Set.of("locale::facet", "locale::id", "locale::category", "ctype_base::mask", "codecvt_base::result", "time_base::dateorder", "money_base::part", "money_base::pattern", "messages_base::catalog");
        default -> Set.of();
    }; }
    private static String locale() {
        var s=new StringBuilder("""
class ios_base;
class locale { public:
class facet { protected: explicit facet(size_t = 0); virtual ~facet(); private: facet(const facet&); facet& operator=(const facet&); };
class id { public: id(); private: id(const id&); id& operator=(const id&); };
typedef __cpg_opaque_integer_locale_category category;
static const category none, collate, ctype, monetary, numeric, time, messages, all;
locale() throw(); locale(const locale&) throw(); explicit locale(const char*); locale(const locale&, const char*, category); locale(const locale&, const locale&, category);
template<class Facet> locale(const locale&, Facet*); ~locale() throw(); const locale& operator=(const locale&) throw();
template<class Facet> locale combine(const locale&) const; string name() const; bool operator==(const locale&) const; bool operator!=(const locale&) const;
template<class CharT, class Traits, class Allocator> bool operator()(const basic_string<CharT, Traits, Allocator>&, const basic_string<CharT, Traits, Allocator>&) const;
static locale global(const locale&); static const locale& classic(); };
template<class Facet> const Facet& use_facet(const locale&); template<class Facet> bool has_facet(const locale&) throw();
class ctype_base { public: typedef __cpg_opaque_unspecified_ctype_mask mask; static const mask space, print, cntrl, upper, lower, alpha, digit, punct, xdigit, alnum, graph; };
template<class CharT> class ctype : public locale::facet, public ctype_base { public: typedef CharT char_type; static locale::id id; explicit ctype(size_t = 0);
bool is(ctype_base::mask, char_type) const; const char_type* is(const char_type*, const char_type*, ctype_base::mask*) const; const char_type* scan_is(ctype_base::mask, const char_type*, const char_type*) const; const char_type* scan_not(ctype_base::mask, const char_type*, const char_type*) const;
char_type toupper(char_type) const; const char_type* toupper(char_type*, const char_type*) const; char_type tolower(char_type) const; const char_type* tolower(char_type*, const char_type*) const; char_type widen(char) const; const char* widen(const char*, const char*, char_type*) const; char narrow(char_type, char) const; const char_type* narrow(const char_type*, const char_type*, char, char*) const;
protected: virtual ~ctype(); virtual bool do_is(ctype_base::mask, char_type) const; virtual const char_type* do_is(const char_type*, const char_type*, ctype_base::mask*) const; virtual const char_type* do_scan_is(ctype_base::mask, const char_type*, const char_type*) const; virtual const char_type* do_scan_not(ctype_base::mask, const char_type*, const char_type*) const;
virtual char_type do_toupper(char_type) const; virtual const char_type* do_toupper(char_type*, const char_type*) const; virtual char_type do_tolower(char_type) const; virtual const char_type* do_tolower(char_type*, const char_type*) const; virtual char_type do_widen(char) const; virtual const char* do_widen(const char*, const char*, char_type*) const; virtual char do_narrow(char_type, char) const; virtual const char_type* do_narrow(const char_type*, const char_type*, char, char*) const; };
template<> class ctype<char> : public locale::facet, public ctype_base { public: typedef char char_type; static locale::id id; static const size_t table_size; explicit ctype(const ctype_base::mask* = 0, bool = false, size_t = 0);
bool is(ctype_base::mask, char) const; const char* is(const char*, const char*, ctype_base::mask*) const; const char* scan_is(ctype_base::mask, const char*, const char*) const; const char* scan_not(ctype_base::mask, const char*, const char*) const;
char toupper(char) const; const char* toupper(char*, const char*) const; char tolower(char) const; const char* tolower(char*, const char*) const; char widen(char) const; const char* widen(const char*, const char*, char*) const; char narrow(char, char) const; const char* narrow(const char*, const char*, char, char*) const;
protected: const ctype_base::mask* table() const throw(); static const ctype_base::mask* classic_table() throw(); virtual ~ctype(); virtual char do_toupper(char) const; virtual const char* do_toupper(char*, const char*) const; virtual char do_tolower(char) const; virtual const char* do_tolower(char*, const char*) const; virtual char do_widen(char) const; virtual const char* do_widen(const char*, const char*, char*) const; virtual char do_narrow(char, char) const; virtual const char* do_narrow(const char*, const char*, char, char*) const; };
class codecvt_base { public: enum result { ok, partial, error, noconv }; };
template<class InternT, class ExternT, class StateT> class codecvt : public locale::facet, public codecvt_base { public:
typedef InternT intern_type; typedef ExternT extern_type; typedef StateT state_type; static locale::id id; explicit codecvt(size_t = 0);
codecvt_base::result out(state_type&, const intern_type*, const intern_type*, const intern_type*&, extern_type*, extern_type*, extern_type*&) const;
codecvt_base::result unshift(state_type&, extern_type*, extern_type*, extern_type*&) const;
codecvt_base::result in(state_type&, const extern_type*, const extern_type*, const extern_type*&, intern_type*, intern_type*, intern_type*&) const;
int encoding() const throw(); bool always_noconv() const throw(); int length(state_type&, const extern_type*, const extern_type*, size_t) const; int max_length() const throw();
protected: virtual ~codecvt(); virtual codecvt_base::result do_out(state_type&, const intern_type*, const intern_type*, const intern_type*&, extern_type*, extern_type*, extern_type*&) const;
virtual codecvt_base::result do_unshift(state_type&, extern_type*, extern_type*, extern_type*&) const;
virtual codecvt_base::result do_in(state_type&, const extern_type*, const extern_type*, const extern_type*&, intern_type*, intern_type*, intern_type*&) const;
virtual int do_encoding() const throw(); virtual bool do_always_noconv() const throw(); virtual int do_length(state_type&, const extern_type*, const extern_type*, size_t) const; virtual int do_max_length() const throw(); };
template<class InternT, class ExternT, class StateT> class codecvt_byname : public codecvt<InternT, ExternT, StateT> { public: explicit codecvt_byname(const char*, size_t = 0); protected: virtual ~codecvt_byname(); };
template<class CharT> class numpunct : public locale::facet { public: typedef CharT char_type; typedef basic_string<CharT> string_type; static locale::id id; explicit numpunct(size_t = 0); char_type decimal_point() const; char_type thousands_sep() const; string grouping() const; string_type truename() const; string_type falsename() const;
protected: virtual ~numpunct(); virtual char_type do_decimal_point() const; virtual char_type do_thousands_sep() const; virtual string do_grouping() const; virtual string_type do_truename() const; virtual string_type do_falsename() const; };
template<class CharT> class collate : public locale::facet { public: typedef CharT char_type; typedef basic_string<CharT> string_type; static locale::id id; explicit collate(size_t = 0); int compare(const char_type*, const char_type*, const char_type*, const char_type*) const; string_type transform(const char_type*, const char_type*) const; long hash(const char_type*, const char_type*) const;
protected: virtual ~collate(); virtual int do_compare(const char_type*, const char_type*, const char_type*, const char_type*) const; virtual string_type do_transform(const char_type*, const char_type*) const; virtual long do_hash(const char_type*, const char_type*) const; };
class time_base { public: enum dateorder { no_order, dmy, mdy, ymd, ydm }; };
class money_base { public: enum part { none, space, symbol, sign, value }; struct pattern { char field[4]; }; };
template<class CharT, bool Intl = false> class moneypunct : public locale::facet, public money_base { public: typedef CharT char_type; typedef basic_string<CharT> string_type; static const bool intl = Intl; static locale::id id; explicit moneypunct(size_t = 0); char_type decimal_point() const; char_type thousands_sep() const; string grouping() const; string_type curr_symbol() const; string_type positive_sign() const; string_type negative_sign() const; int frac_digits() const; money_base::pattern pos_format() const; money_base::pattern neg_format() const;
protected: virtual ~moneypunct(); virtual char_type do_decimal_point() const; virtual char_type do_thousands_sep() const; virtual string do_grouping() const; virtual string_type do_curr_symbol() const; virtual string_type do_positive_sign() const; virtual string_type do_negative_sign() const; virtual int do_frac_digits() const; virtual money_base::pattern do_pos_format() const; virtual money_base::pattern do_neg_format() const; };
template<class CharT, bool Intl = false> class moneypunct_byname : public moneypunct<CharT, Intl> { public: explicit moneypunct_byname(const char*, size_t = 0); protected: virtual ~moneypunct_byname(); };
class messages_base { public: typedef __cpg_opaque_integer_messages_catalog catalog; };
template<class CharT> class messages : public locale::facet, public messages_base { public: typedef CharT char_type; typedef basic_string<CharT> string_type; static locale::id id; explicit messages(size_t = 0); messages_base::catalog open(const basic_string<char>&, const locale&) const; string_type get(messages_base::catalog, int, int, const string_type&) const; void close(messages_base::catalog) const;
protected: virtual ~messages(); virtual messages_base::catalog do_open(const basic_string<char>&, const locale&) const; virtual string_type do_get(messages_base::catalog, int, int, const string_type&) const; virtual void do_close(messages_base::catalog) const; };
""");
        for(String name:new String[]{"ctype","numpunct","collate","messages"}) s.append("template<class CharT> class ").append(name).append("_byname : public ").append(name).append("<CharT> { public: explicit ").append(name).append("_byname(const char*, size_t = 0); protected: virtual ~").append(name).append("_byname(); };\n");
        for(String name:new String[]{"isspace","isprint","iscntrl","isupper","islower","isalpha","isdigit","ispunct","isxdigit","isalnum","isgraph"})s.append("template<class CharT> bool ").append(name).append("(CharT, const locale&);\n");
        for(String name:new String[]{"toupper","tolower"})s.append("template<class CharT> CharT ").append(name).append("(CharT, const locale&);\n");
        for(boolean input:new boolean[]{true,false}) {
            String name=input?"num_get":"num_put", iterator=input?"istreambuf_iterator<CharT>":"ostreambuf_iterator<CharT>";
            s.append("template<class CharT, class Iter = ").append(iterator).append(" > class ").append(name).append(" : public locale::facet { public: typedef CharT char_type; typedef Iter iter_type; static locale::id id; explicit ").append(name).append("(size_t = 0);\n");
            String[] types=input?new String[]{"bool","long","unsigned short","unsigned int","unsigned long","float","double","long double","void*"}:new String[]{"bool","long","unsigned long","double","long double","const void*"};
            for(String type:types)s.append("iter_type ").append(input?"get":"put").append(input?"(iter_type, iter_type, ios_base&, ios_base::iostate&, ":"(iter_type, ios_base&, char_type, ").append(type).append(input?"&":"").append(") const;\n");
            s.append("protected: virtual ~").append(name).append("();\n");
            for(String type:types)s.append("virtual iter_type do_").append(input?"get":"put").append(input?"(iter_type, iter_type, ios_base&, ios_base::iostate&, ":"(iter_type, ios_base&, char_type, ").append(type).append(input?"&":"").append(") const;\n");
            s.append("};\n");
        }
        for(boolean input:new boolean[]{true,false}) {
            String name=input?"time_get":"time_put", iterator=input?"istreambuf_iterator<CharT>":"ostreambuf_iterator<CharT>";
            s.append("template<class CharT, class Iter = ").append(iterator).append(" > class ").append(name).append(" : public locale::facet").append(input?", public time_base":"").append(" { public: typedef CharT char_type; typedef Iter iter_type; static locale::id id; explicit ").append(name).append("(size_t = 0);\n");
            if(input) {
                s.append("time_base::dateorder date_order() const;\n");
                for(String method:new String[]{"time","date","weekday","monthname","year"})s.append("iter_type get_").append(method).append("(iter_type, iter_type, ios_base&, ios_base::iostate&, tm*) const;\n");
            }else s.append("iter_type put(iter_type, ios_base&, char_type, const tm*, const char_type*, const char_type*) const; iter_type put(iter_type, ios_base&, char_type, const tm*, char, char = 0) const;\n");
            s.append("protected: virtual ~").append(name).append("();\n");
            if(input){s.append("virtual time_base::dateorder do_date_order() const;\n"); for(String method:new String[]{"time","date","weekday","monthname","year"})s.append("virtual iter_type do_get_").append(method).append("(iter_type, iter_type, ios_base&, ios_base::iostate&, tm*) const;\n");}
            else s.append("virtual iter_type do_put(iter_type, ios_base&, char_type, const tm*, char, char) const;\n");
            s.append("};\n");
            s.append("template<class CharT, class Iter = ").append(iterator).append(" > class ").append(name).append("_byname : public ").append(name).append("<CharT, Iter> { public: explicit ").append(name).append("_byname(const char*, size_t = 0); protected: virtual ~").append(name).append("_byname(); };\n");
        }
        for(boolean input:new boolean[]{true,false}){
            String name=input?"money_get":"money_put", iterator=input?"istreambuf_iterator<CharT>":"ostreambuf_iterator<CharT>";
            s.append("template<class CharT, class Iter = ").append(iterator).append(" > class ").append(name).append(" : public locale::facet { public: typedef CharT char_type; typedef Iter iter_type; typedef basic_string<CharT> string_type; static locale::id id; explicit ").append(name).append("(size_t = 0);\n");
            for(String type:input?new String[]{"long double&","string_type&"}:new String[]{"long double","const string_type&"})s.append("iter_type ").append(input?"get":"put").append(input?"(iter_type, iter_type, bool, ios_base&, ios_base::iostate&, ":"(iter_type, bool, ios_base&, char_type, ").append(type).append(") const;\n");
            s.append("protected: virtual ~").append(name).append("();\n");
            for(String type:input?new String[]{"long double&","string_type&"}:new String[]{"long double","const string_type&"})s.append("virtual iter_type do_").append(input?"get":"put").append(input?"(iter_type, iter_type, bool, ios_base&, ios_base::iostate&, ":"(iter_type, bool, ios_base&, char_type, ").append(type).append(") const;\n");
            s.append("};\n");
        }
        return s.toString();
    }
}
