// Independent interface inventory transcribed against ISO/IEC 14882:2003, pp. 711-720.
// Only interface signatures, not standard prose or implementation bodies.
#include <streambuf>
#include <istream>
#include <ostream>
#include <cstddef>
#include <cstdio>
namespace std {
// D.7.1 synopsis; D.7.1.1:1-8 constructors/destructor.
class strstreambuf : public basic_streambuf<char> {
public:
  explicit strstreambuf(streamsize = 0);
  strstreambuf(void* (*)(size_t), void (*)(void*));
  strstreambuf(char*, streamsize, char* = 0);
  strstreambuf(const char*, streamsize);
  strstreambuf(signed char*, streamsize, signed char* = 0);
  strstreambuf(const signed char*, streamsize);
  strstreambuf(unsigned char*, streamsize, unsigned char* = 0);
  strstreambuf(const unsigned char*, streamsize);
  virtual ~strstreambuf();
// D.7.1.2:1-4; pcount const follows detailed signature, not non-const synopsis.
  void freeze(bool = true);
  char* str();
  int pcount() const;
protected:
// D.7.1.3:1-19. setbuf follows synopsis streambuf*; detailed streambuf<char>* is ill-formed.
  virtual int_type overflow(int_type = EOF);
  virtual int_type pbackfail(int_type = EOF);
  virtual int_type underflow();
  virtual pos_type seekoff(off_type, ios_base::seekdir, ios_base::openmode = ios_base::in | ios_base::out);
  virtual pos_type seekpos(pos_type, ios_base::openmode = ios_base::in | ios_base::out);
  virtual streambuf* setbuf(char*, streamsize);
};
// D.7.2 synopsis, D.7.2.1:1-2, D.7.2.2:1-2. Synopsis includes mutable length overload.
class istrstream : public basic_istream<char> {
public:
  explicit istrstream(const char*);
  explicit istrstream(char*);
  istrstream(const char*, streamsize);
  istrstream(char*, streamsize);
  virtual ~istrstream();
  strstreambuf* rdbuf() const;
  char* str();
};
// D.7.3 synopsis, D.7.3.1:1-2, D.7.3.2:1-4.
class ostrstream : public basic_ostream<char> {
public:
  ostrstream();
  ostrstream(char*, int, ios_base::openmode = ios_base::out);
  virtual ~ostrstream();
  strstreambuf* rdbuf() const;
  void freeze(bool = true);
  char* str();
  int pcount() const;
};
// D.7.4 synopsis, D.7.4.1:1-2, D.7.4.2:1-2, D.7.4.3:1-3.
class strstream : public basic_iostream<char> {
public:
  typedef char char_type;
  typedef char_traits<char>::int_type int_type;
  typedef char_traits<char>::pos_type pos_type;
  typedef char_traits<char>::off_type off_type;
  strstream();
  strstream(char*, int, ios_base::openmode = ios_base::in | ios_base::out);
  virtual ~strstream();
  strstreambuf* rdbuf() const;
  void freeze(bool = true);
  int pcount() const;
  char* str();
};
}
