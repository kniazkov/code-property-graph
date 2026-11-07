/* Built-in C++03 declaration data, never owned by a Project. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import java.util.*;

/**
 * Host-independent C++03 interface declarations, not implementation headers or behavior.
 * All 51 standard headers and 18 compatibility spellings are inventoried; declaration
 * coverage is explicitly inventoried (see docs/library-catalogue.en.md).
 * The dataset is not owned by a Project and contains no callable implementation bodies.
 */
public final class StandardLibraryCatalogue {
    private StandardLibraryCatalogue() {}
    private static final List<String> CPP = List.of("algorithm", "bitset", "complex", "deque", "exception", "fstream", "functional", "iomanip", "ios", "iosfwd", "iostream", "istream", "iterator", "limits", "list", "locale", "map", "memory", "new", "numeric", "ostream", "queue", "set", "sstream", "stack", "stdexcept", "streambuf", "string", "strstream", "typeinfo", "utility", "valarray", "vector");
    private static final List<String> C = List.of("assert", "ctype", "errno", "float", "iso646", "limits", "locale", "math", "setjmp", "signal", "stdarg", "stddef", "stdio", "stdlib", "string", "time", "wchar", "wctype");
    /** Standard C++ headers and their deprecated C compatibility spellings. */
    public static Set<String> headers() {
        var result = new LinkedHashSet<String>(CPP);
        C.forEach(name -> { result.add("c" + name); result.add(name + ".h"); });
        return Collections.unmodifiableSet(result);
    }
    public static String canonicalHeader(String header) {
        return header.endsWith(".h") && C.contains(header.substring(0, header.length()-2)) ? "c"+header.substring(0,header.length()-2) : header;
    }
    public static Set<String> dependencies(String header) {
        if (header.endsWith(".h") && !canonicalHeader(header).equals(header)) {
            var result = new LinkedHashSet<String>();
            for (String dependency : dependencies(canonicalHeader(header)))
                result.add(dependency.startsWith("c") && C.contains(dependency.substring(1))
                    ? dependency.substring(1) + ".h" : dependency);
            return Collections.unmodifiableSet(result);
        }
        if (StandardLibraryC.supports(header)) return Collections.unmodifiableSet(new TreeSet<>(StandardLibraryC.dependencies(header)));
        if (StandardLibraryStreams.supports(header)) return Collections.unmodifiableSet(new TreeSet<>(StandardLibraryStreams.dependencies(header)));
        return Collections.unmodifiableSet(new TreeSet<>(switch(canonicalHeader(header)) {
            case "iterator" -> Set.of("cstddef", "iosfwd");
            case "memory" -> Set.of("cstddef", "iterator", "utility");
            case "new" -> Set.of("cstddef", "exception");
            case "valarray" -> Set.of("cstddef");
            case "complex" -> Set.of("iosfwd");
            case "vector", "deque", "list" -> Set.of("memory", "iterator");
            case "string" -> Set.of("memory", "iterator", "iosfwd", "cwchar");
            case "bitset" -> Set.of("cstddef", "string", "stdexcept", "iosfwd");
            case "map" -> Set.of("memory", "functional", "utility", "iterator");
            case "set" -> Set.of("memory", "functional", "utility", "iterator");
            case "queue" -> Set.of("deque", "vector", "functional");
            case "stack" -> Set.of("deque");
            case "stdexcept" -> Set.of("exception", "string");
            case "typeinfo" -> Set.of("exception");
            case "algorithm", "numeric" -> Set.of("iterator", "utility");
            default -> Set.of();
        }));
    }
    private static String typeList(String header) {
        if (StandardLibraryC.supports(canonicalHeader(header))) return String.join(" ", new TreeSet<>(StandardLibraryC.typeNames(canonicalHeader(header))));
        if (StandardLibraryStreams.supports(header)) return String.join(" ", new TreeSet<>(StandardLibraryStreams.typeNames(header)));
        return switch(canonicalHeader(header)) {
            case "cstddef" -> "size_t ptrdiff_t";
            case "memory" -> "allocator auto_ptr auto_ptr_ref raw_storage_iterator";
            case "vector" -> "vector"; case "list" -> "list"; case "deque" -> "deque";
            case "map" -> "map multimap"; case "set" -> "set multiset";
            case "queue" -> "queue priority_queue"; case "stack" -> "stack";
            case "utility" -> "pair";
            case "functional" -> "unary_function binary_function plus minus multiplies divides modulus negate equal_to not_equal_to greater less greater_equal less_equal logical_and logical_or logical_not unary_negate binary_negate binder1st binder2nd pointer_to_unary_function pointer_to_binary_function mem_fun_t mem_fun1_t mem_fun_ref_t mem_fun1_ref_t const_mem_fun_t const_mem_fun1_t const_mem_fun_ref_t const_mem_fun1_ref_t";
            case "iterator" -> "input_iterator_tag output_iterator_tag forward_iterator_tag bidirectional_iterator_tag random_access_iterator_tag iterator iterator_traits reverse_iterator back_insert_iterator front_insert_iterator insert_iterator istream_iterator ostream_iterator istreambuf_iterator ostreambuf_iterator";
            case "string" -> "char_traits basic_string string wstring";
            case "exception" -> "exception bad_exception terminate_handler unexpected_handler"; case "new" -> "bad_alloc nothrow_t new_handler";
            case "stdexcept" -> "logic_error domain_error invalid_argument length_error out_of_range runtime_error range_error overflow_error underflow_error";
            case "typeinfo" -> "type_info bad_cast bad_typeid";
            case "limits" -> "numeric_limits float_round_style float_denorm_style"; case "bitset" -> "bitset"; case "complex" -> "complex";
            case "valarray" -> "valarray slice gslice slice_array gslice_array mask_array indirect_array";
            default -> "";
        };
    }
    public static Set<String> typeNames(String header) {
        var result = new LinkedHashSet<String>();
        for(String name:typeList(header).split(" ")) if(!name.isEmpty()) {
            result.add(name); if (!header.endsWith(".h")) result.add("std::"+name);
        }
        return Collections.unmodifiableSet(result);
    }
    public static Set<String> templateNames(String header) {
        if (StandardLibraryC.supports(canonicalHeader(header))) return Set.of();
        if (StandardLibraryStreams.supports(header)) return names(String.join(" ", new TreeSet<>(StandardLibraryStreams.templateNames(header))));
        return switch(canonicalHeader(header)) {
            case "memory", "vector", "deque", "list", "map", "set", "queue", "stack", "utility", "functional", "bitset", "complex" -> typeNames(header);
            case "limits" -> names("numeric_limits");
            case "string" -> names("char_traits basic_string");
            case "iterator" -> names("iterator iterator_traits reverse_iterator back_insert_iterator front_insert_iterator insert_iterator istream_iterator ostream_iterator istreambuf_iterator ostreambuf_iterator");
            case "valarray" -> names("valarray slice_array gslice_array mask_array indirect_array");
            default -> Set.of();
        };
    }
    /** Parser visibility of namespace function templates, separate from the type index. */
    public static Set<String> callableTemplateNames(String header) {
        String comparisons = "operator== operator!= operator< operator> operator<= operator>= ";
        String math = "abs acos asin atan cos cosh exp log log10 sin sinh sqrt tan tanh ";
        return names(switch (canonicalHeader(header)) {
            case "utility" -> comparisons + "make_pair rel_ops::operator!= rel_ops::operator> rel_ops::operator<= rel_ops::operator>=";
            case "memory" -> "operator== operator!= get_temporary_buffer return_temporary_buffer uninitialized_copy uninitialized_fill uninitialized_fill_n";
            case "vector", "deque", "list", "map", "set" -> comparisons + "swap";
            case "queue", "stack" -> comparisons;
            case "string" -> comparisons + "swap operator+ operator>> operator<< getline";
            case "iterator" -> comparisons + "operator+ operator- advance distance back_inserter front_inserter inserter";
            case "functional" -> "not1 not2 bind1st bind2nd ptr_fun mem_fun mem_fun_ref";
            case "numeric" -> "accumulate inner_product partial_sum adjacent_difference";
            case "algorithm" -> "swap for_each find find_if find_end find_first_of adjacent_find count count_if mismatch equal search search_n copy copy_backward swap_ranges iter_swap transform replace replace_if replace_copy replace_copy_if fill fill_n generate generate_n remove remove_if remove_copy remove_copy_if unique unique_copy reverse reverse_copy rotate rotate_copy random_shuffle partition stable_partition sort stable_sort partial_sort partial_sort_copy nth_element lower_bound upper_bound equal_range binary_search merge inplace_merge includes set_union set_intersection set_difference set_symmetric_difference push_heap pop_heap make_heap sort_heap min max min_element max_element lexicographical_compare next_permutation prev_permutation";
            case "bitset" -> "operator& operator| operator^ operator>> operator<<";
            case "complex" -> "operator+ operator- operator* operator/ operator== operator!= operator>> operator<< real imag abs arg norm conj polar pow cos cosh exp log log10 sin sinh sqrt tan tanh";
            case "valarray" -> comparisons + math + "atan2 pow operator* operator/ operator% operator+ operator- operator^ operator& operator| operator<< operator>> operator&& operator||";
            case "locale" -> "use_facet has_facet isspace isprint iscntrl isupper islower isalpha isdigit ispunct isxdigit isalnum isgraph toupper tolower";
            case "iosfwd" -> "operator== operator!=";
            case "istream" -> "operator>> ws";
            case "ostream" -> "operator<< endl ends flush";
            case "iomanip" -> "setfill operator>> operator<<";
            default -> "";
        });
    }
    /** Member identifiers only; these must not introduce unqualified free functions. */
    public static Set<String> memberCallableTemplateNames(String header) {
        String names = switch(canonicalHeader(header)) {
            case "bitset" -> "to_string";
            case "locale" -> "combine operator()";
            case "memory" -> "operator=";
            case "complex" -> "operator= operator+= operator-= operator*= operator/=";
            case "vector", "deque" -> "assign insert";
            case "list" -> "assign insert remove_if unique merge sort";
            case "map", "set" -> "insert";
            case "string" -> "append assign insert replace";
            default -> "";
        };
        return names.isEmpty() ? Set.of() : Set.of(names.split(" "));
    }
    private static Set<String> names(String names) { var out=new LinkedHashSet<String>(); for(String n:names.split(" ")){if(n.isEmpty())continue;out.add(n);out.add("std::"+n);}return Set.copyOf(out); }
    /** Namespace-relative member type paths for parser visibility, never namespace declarations. */
    public static Set<String> nestedTypeNames(String header) {
        String h = canonicalHeader(header);
        if (StandardLibraryStreams.supports(h)) return StandardLibraryStreams.nestedTypeNames(h);
        var result = new TreeSet<String>();
        if (Set.of("vector", "deque", "list", "map", "set", "string").contains(h)) {
            for (String root : typeList(h).split(" ")) {
                if (root.equals("char_traits")) continue;
                addNested(result,root,"value_type allocator_type size_type difference_type reference const_reference pointer const_pointer iterator const_iterator reverse_iterator const_reverse_iterator");
                if (h.equals("map") || h.equals("set")) addNested(result,root,"key_type key_compare value_compare");
                if (h.equals("map")) addNested(result,root,"mapped_type");
                if (h.equals("string")) addNested(result,root,"traits_type");
            }
        }
        if (h.equals("queue") || h.equals("stack")) for (String root : typeList(h).split(" "))
            addNested(result,root,"value_type reference const_reference size_type container_type");
        if (h.equals("memory")) {
            addNested(result,"allocator","value_type pointer const_pointer reference const_reference size_type difference_type rebind rebind::other");
            addNested(result,"auto_ptr","element_type");
        }
        if (h.equals("string")) addNested(result,"char_traits","char_type int_type off_type pos_type state_type");
        if (h.equals("bitset")) result.add("bitset::reference");
        if (h.equals("utility")) addNested(result,"pair","first_type second_type");
        if (h.equals("complex") || h.equals("valarray")) for (String root : typeList(h).split(" "))
            if (!Set.of("slice","gslice").contains(root)) addNested(result,root,"value_type");
        if (h.equals("functional")) {
            addNested(result,"unary_function","argument_type result_type");
            addNested(result,"binary_function","first_argument_type second_argument_type result_type");
        }
        if (h.equals("iterator")) {
            for (String root : List.of("iterator","iterator_traits")) addNested(result,root,"iterator_category value_type difference_type pointer reference");
            addNested(result,"reverse_iterator","iterator_type difference_type reference pointer");
            for (String root : List.of("back_insert_iterator","front_insert_iterator","insert_iterator")) addNested(result,root,"container_type");
            for (String root : List.of("istream_iterator","ostream_iterator","istreambuf_iterator","ostreambuf_iterator")) {
                addNested(result,root,"char_type traits_type");
                addNested(result,root,root.startsWith("i") ? "istream_type" : "ostream_type");
                if (root.contains("buf")) addNested(result,root,"streambuf_type");
            }
            addNested(result,"istreambuf_iterator","int_type");
        }
        return Collections.unmodifiableSet(result);
    }
    private static void addNested(Set<String> result,String root,String members) {
        for (String member : members.split(" ")) result.add(root + "::" + member);
    }
    private static String ns(String text) { return "namespace std {\n"+text+"\n}\n"; }
    /** Declarations are parsed only in an isolated candidate catalogue, without filesystem reads. */
    public static String declarations(String header, PlatformProfile profile) {
        String h=canonicalHeader(header), size=profile.sizeType();
        // Real glibc ABI declaration, not a standard-library function or CPG body.
        // Keep C linkage and global namespace under both header spellings.
        if (h.equals("cerrno") && profile.cRuntime().equals("glibc"))
            return "extern \"C\" int* __errno_location() throw();";
        if (h.equals("cassert") && profile.cRuntime().equals("glibc"))
            return "extern \"C\" void __assert_fail(const char*, const char*, unsigned int, const char*) throw();";
        if (StandardLibraryC.supports(h)) {
            String data = StandardLibraryC.declarations(h);
            if ((h.equals("cwchar") || h.equals("cwctype")) && profile.wideInteger().isPresent())
                data = data.replace("__cpg_opaque_integer_wint_t", profile.wideInteger().orElseThrow().wintType());
            String declarations = header.endsWith(".h") ? data : ns(data);
            // Keep ordinary cstdlib declarations and its canonical size_t dependency.
            // The opt-in contract establishes this exact underlying return type.
            if (h.equals("cstdlib") && !profile.mbCurMaxBinding().equals("unavailable"))
                declarations += "\nextern \"C\" " + size + " __ctype_get_mb_cur_max() throw();";
            if (h.equals("cstdio") && !profile.standardStreamsBinding().equals("unavailable")) {
                String fileType = header.endsWith(".h") ? "::FILE" : "std::FILE";
                declarations += "\nextern \"C\" { extern " + fileType + "* stdin; extern " + fileType
                        + "* stdout; extern " + fileType + "* stderr; }";
            }
            return declarations;
        }
        if (StandardLibraryStreams.supports(h)) return ns(StandardLibraryStreams.declarations(h));
        String body=switch(h) {
            // The profile currently has no independent ptrdiff_t setting. This is a
            // conventional signed-size counterpart policy, not an ISO ABI guarantee.
            case "cstddef" -> "typedef "+size+" size_t; typedef "+size.replace("unsigned ","")+" ptrdiff_t;";
            case "memory" -> "template<class T> class allocator { public: typedef T value_type; typedef T* pointer; typedef const T* const_pointer; typedef T& reference; typedef const T& const_reference; typedef size_t size_type; typedef ptrdiff_t difference_type; template<class U> struct rebind { typedef allocator<U> other; }; allocator() throw(); allocator(const allocator&) throw(); template<class U> allocator(const allocator<U>&) throw(); ~allocator() throw(); pointer address(reference x) const; const_pointer address(const_reference x) const; pointer allocate(size_type n, const void* hint = 0); void deallocate(pointer p, size_type n); size_type max_size() const throw(); void construct(pointer p, const T& value); void destroy(pointer p); }; template<> class allocator<void> { public: typedef void value_type; typedef void* pointer; typedef const void* const_pointer; template<class U> struct rebind { typedef allocator<U> other; }; }; template<class T,class U> bool operator==(const allocator<T>& x,const allocator<U>& y) throw(); template<class T,class U> bool operator!=(const allocator<T>& x,const allocator<U>& y) throw(); template<class Y> struct auto_ptr_ref; template<class T> class auto_ptr { public: typedef T element_type; explicit auto_ptr(T* p = 0) throw(); auto_ptr(auto_ptr& x) throw(); template<class Y> auto_ptr(auto_ptr<Y>& x) throw(); auto_ptr& operator=(auto_ptr& x) throw(); template<class Y> auto_ptr& operator=(auto_ptr<Y>& x) throw(); auto_ptr& operator=(auto_ptr_ref<T> r) throw(); auto_ptr(auto_ptr_ref<T> r) throw(); template<class Y> operator auto_ptr_ref<Y>() throw(); template<class Y> operator auto_ptr<Y>() throw(); ~auto_ptr() throw(); T& operator*() const throw(); T* operator->() const throw(); T* get() const throw(); T* release() throw(); void reset(T* p = 0) throw(); }; " + """
                template<class OutputIterator,class T> class raw_storage_iterator : public iterator<output_iterator_tag,void,void,void,void> {
                    public: explicit raw_storage_iterator(OutputIterator x); raw_storage_iterator& operator*();
                    raw_storage_iterator& operator=(const T& element); raw_storage_iterator& operator++(); raw_storage_iterator operator++(int);
                };
                template<class T> pair<T*,ptrdiff_t> get_temporary_buffer(ptrdiff_t n);
                template<class T> void return_temporary_buffer(T* p);
                template<class InputIterator,class ForwardIterator> ForwardIterator uninitialized_copy(InputIterator first,InputIterator last,ForwardIterator result);
                template<class ForwardIterator,class T> void uninitialized_fill(ForwardIterator first,ForwardIterator last,const T& value);
                template<class ForwardIterator,class Size,class T> void uninitialized_fill_n(ForwardIterator first,Size n,const T& value);
                """;
            case "vector" -> vector() + vectorBoolean();
            case "utility" -> "template<class T1, class T2> struct pair { typedef T1 first_type; typedef T2 second_type; T1 first; T2 second; pair(); pair(const T1& x, const T2& y); template<class U, class V> pair(const pair<U,V>& x); }; template<class T1, class T2> pair<T1,T2> make_pair(T1 x,T2 y);" + pairRelations() + relOpsRelations();
            case "functional" -> functional();
            case "iterator" -> iterators();
            case "deque", "list" -> sequence(h);
            case "map" -> associative("map",true)+associative("multimap",true);
            case "set" -> associative("set",false)+associative("multiset",false);
            case "queue" -> adaptor("queue") + adaptor("priority_queue");
            case "stack" -> adaptor("stack");
            case "string" -> strings();
            case "exception" -> "class exception { public: exception() throw(); exception(const exception&) throw(); exception& operator=(const exception&) throw(); virtual ~exception() throw(); virtual const char* what() const throw(); }; class bad_exception : public exception { public: bad_exception() throw(); bad_exception(const bad_exception&) throw(); bad_exception& operator=(const bad_exception&) throw(); virtual ~bad_exception() throw(); virtual const char* what() const throw(); }; typedef void (*terminate_handler)(); typedef void (*unexpected_handler)(); terminate_handler set_terminate(terminate_handler f) throw(); unexpected_handler set_unexpected(unexpected_handler f) throw(); void terminate(); void unexpected(); bool uncaught_exception() throw();";
            case "new" -> "class bad_alloc : public exception { public: bad_alloc() throw(); bad_alloc(const bad_alloc&) throw(); bad_alloc& operator=(const bad_alloc&) throw(); virtual ~bad_alloc() throw(); virtual const char* what() const throw(); }; struct nothrow_t {}; extern const nothrow_t nothrow; typedef void (*new_handler)(); new_handler set_new_handler(new_handler f) throw();";
            case "stdexcept" -> exceptions();
            case "typeinfo" -> "class type_info { public: virtual ~type_info(); bool operator==(const type_info& rhs) const; bool operator!=(const type_info& rhs) const; bool before(const type_info& rhs) const; const char* name() const; private: type_info(const type_info&); type_info& operator=(const type_info&); }; class bad_cast : public exception { public: bad_cast() throw(); bad_cast(const bad_cast&) throw(); bad_cast& operator=(const bad_cast&) throw(); virtual ~bad_cast() throw(); virtual const char* what() const throw(); }; class bad_typeid : public exception { public: bad_typeid() throw(); bad_typeid(const bad_typeid&) throw(); bad_typeid& operator=(const bad_typeid&) throw(); virtual ~bad_typeid() throw(); virtual const char* what() const throw(); };";
            case "limits" -> limits();
            case "bitset" -> bitset();
            case "complex" -> complex();
            case "valarray" -> valarray();
            case "algorithm" -> algorithms();
            case "numeric" -> numeric();
            default -> "";
        };
        return header.endsWith(".h") ? body : ns(body) + (h.equals("new") ? allocationFunctions() : "");
    }
    private static String allocationFunctions() {
        return """
            void* operator new(std::size_t size) throw(std::bad_alloc);
            void* operator new[](std::size_t size) throw(std::bad_alloc);
            void operator delete(void* pointer) throw(); void operator delete[](void* pointer) throw();
            void* operator new(std::size_t size,const std::nothrow_t& tag) throw();
            void* operator new[](std::size_t size,const std::nothrow_t& tag) throw();
            void operator delete(void* pointer,const std::nothrow_t& tag) throw();
            void operator delete[](void* pointer,const std::nothrow_t& tag) throw();
            void* operator new(std::size_t size,void* pointer) throw();
            void* operator new[](std::size_t size,void* pointer) throw();
            void operator delete(void* pointer,void* placement) throw();
            void operator delete[](void* pointer,void* placement) throw();
            """;
    }
    private static String vector() {
        return "template<class T,class Allocator=allocator<T> > class vector { public: typedef T value_type; typedef Allocator allocator_type; typedef __cpg_opaque_integer_size_type size_type; typedef __cpg_opaque_integer_difference_type difference_type; typedef T& reference; typedef const T& const_reference; typedef typename Allocator::pointer pointer; typedef typename Allocator::const_pointer const_pointer; typedef __cpg_opaque_unspecified_iterator iterator; typedef __cpg_opaque_unspecified_const_iterator const_iterator; typedef std::reverse_iterator<iterator> reverse_iterator; typedef std::reverse_iterator<const_iterator> const_reverse_iterator; explicit vector(const Allocator& alloc=Allocator()); explicit vector(size_type n,const T& value=T(),const Allocator& alloc=Allocator()); template<class InputIterator> vector(InputIterator first,InputIterator last,const Allocator& alloc=Allocator()); vector(const vector& x); ~vector(); vector& operator=(const vector& x); allocator_type get_allocator() const; iterator begin(); const_iterator begin() const; iterator end(); const_iterator end() const; reverse_iterator rbegin(); const_reverse_iterator rbegin() const; reverse_iterator rend(); const_reverse_iterator rend() const; size_type size() const; size_type max_size() const; void resize(size_type n,T value=T()); size_type capacity() const; bool empty() const; void reserve(size_type n); reference operator[](size_type n); const_reference operator[](size_type n) const; reference at(size_type n); const_reference at(size_type n) const; reference front(); const_reference front() const; reference back(); const_reference back() const; template<class InputIterator> void assign(InputIterator first,InputIterator last); void assign(size_type n,const T& value); void push_back(const T& value); void pop_back(); iterator insert(iterator position,const T& value); void insert(iterator position,size_type n,const T& value); template<class InputIterator> void insert(iterator position,InputIterator first,InputIterator last); iterator erase(iterator position); iterator erase(iterator first,iterator last); void swap(vector& x); void clear(); };" + containerRelations("vector", "class T,class Allocator", "T,Allocator");
    }
    private static String vectorBoolean() {
        String text = vector();
        text = text.substring(0,text.indexOf("};template<class T,class Allocator> bool operator==") + 2);
        text = text.replace("template<class T,class Allocator=allocator<T> > class vector", "template<class Allocator> class vector<bool,Allocator>")
            .replaceAll("\\bT\\b", "bool")
            .replace("typedef bool& reference;", "class reference { friend class vector; reference(); public: ~reference(); operator bool() const; reference& operator=(const bool x); reference& operator=(const reference& x); void flip(); };")
            .replace("typedef const bool& const_reference;", "typedef bool const_reference;")
            .replace("typedef typename Allocator::pointer pointer;", "typedef __cpg_opaque_unspecified_pointer pointer;")
            .replace("typedef typename Allocator::const_pointer const_pointer;", "typedef __cpg_opaque_unspecified_const_pointer const_pointer;")
            .replace("void clear();", "static void swap(reference x,reference y); void flip(); void clear();");
        return text + containerRelations("vector","class Allocator","bool,Allocator");
    }
    private static String sequence(String name) {
        String text = vector().replace("vector", name)
            .replace("size_type capacity() const;", "")
            .replace("void reserve(size_type n);", "");
        text = text.replace("void pop_back();", "void pop_back(); void push_front(const T& value); void pop_front();");
        if (name.equals("list")) {
            text = text.replace("reference operator[](size_type n); const_reference operator[](size_type n) const; reference at(size_type n); const_reference at(size_type n) const;", "");
            text = text.replace("void clear();", """
                void clear();
                void splice(iterator position,list& x);
                void splice(iterator position,list& x,iterator i);
                void splice(iterator position,list& x,iterator first,iterator last);
                void remove(const T& value);
                template<class Predicate> void remove_if(Predicate pred);
                void unique(); template<class BinaryPredicate> void unique(BinaryPredicate pred);
                void merge(list& x); template<class Compare> void merge(list& x,Compare comp);
                void sort(); template<class Compare> void sort(Compare comp); void reverse();
                """);
        }
        return text;
    }
    private static String associative(String name,boolean mapped) {
        String parameters = "class Key," + (mapped ? "class T," : "") + "class Compare,class Allocator";
        String arguments = "Key," + (mapped ? "T," : "") + "Compare,Allocator";
        String text = "template<class Key," + (mapped ? "class T," : "")
            + "class Compare=less<Key>,class Allocator=allocator<" + (mapped ? "pair<const Key,T> " : "Key") + "> > class " + name + " { public: "
            + "typedef Key key_type; " + (mapped ? "typedef T mapped_type; typedef pair<const Key,T> value_type;" : "typedef Key value_type;")
            + """
                typedef Compare key_compare; typedef Allocator allocator_type;
                typedef typename Allocator::reference reference;
                typedef typename Allocator::const_reference const_reference;
                typedef typename Allocator::pointer pointer;
                typedef typename Allocator::const_pointer const_pointer;
                typedef __cpg_opaque_integer_size_type size_type; typedef __cpg_opaque_integer_difference_type difference_type;
                typedef __cpg_opaque_unspecified_iterator iterator; typedef __cpg_opaque_unspecified_const_iterator const_iterator;
                typedef std::reverse_iterator<iterator> reverse_iterator;
                typedef std::reverse_iterator<const_iterator> const_reverse_iterator;
                """
            + (mapped ? "class value_compare : public binary_function<value_type,value_type,bool> { friend class " + name + "; protected: Compare comp; value_compare(Compare c); public: bool operator()(const value_type& x,const value_type& y) const; };" : "typedef Compare value_compare;")
            + "explicit " + name + "(const Compare& comp=Compare(),const Allocator& a=Allocator());"
            + "template<class InputIterator> " + name + "(InputIterator first,InputIterator last,const Compare& comp=Compare(),const Allocator& a=Allocator());"
            + name + "(const " + name + "& x); ~" + name + "(); " + name + "& operator=(const " + name + "& x);"
            + """
                allocator_type get_allocator() const;
                iterator begin(); const_iterator begin() const; iterator end(); const_iterator end() const;
                reverse_iterator rbegin(); const_reverse_iterator rbegin() const; reverse_iterator rend(); const_reverse_iterator rend() const;
                bool empty() const; size_type size() const; size_type max_size() const;
                """
            + (name.startsWith("multi") ? "iterator" : "pair<iterator,bool>") + " insert(const value_type& x);"
            + "iterator insert(iterator position,const value_type& x); template<class InputIterator> void insert(InputIterator first,InputIterator last);"
            + "void erase(iterator position); size_type erase(const key_type& x); void erase(iterator first,iterator last); void swap(" + name + "& x); void clear();"
            + """
                key_compare key_comp() const; value_compare value_comp() const;
                iterator find(const key_type& x); const_iterator find(const key_type& x) const;
                size_type count(const key_type& x) const;
                iterator lower_bound(const key_type& x); const_iterator lower_bound(const key_type& x) const;
                iterator upper_bound(const key_type& x); const_iterator upper_bound(const key_type& x) const;
                pair<iterator,iterator> equal_range(const key_type& x);
                pair<const_iterator,const_iterator> equal_range(const key_type& x) const;
                """
            + (name.equals("map") ? "T& operator[](const key_type& x);" : "") + "};";
        return text + containerRelations(name,parameters,arguments);
    }
    private static String adaptor(String name) {
        String text = "template<class T,class Container=" + (name.equals("priority_queue") ? "vector<T>,class Compare=less<typename Container::value_type>" : "deque<T>")
            + " > class " + name + " { public: "
            + """
                typedef typename Container::value_type value_type;
                typedef typename Container::reference reference;
                typedef typename Container::const_reference const_reference;
                typedef typename Container::size_type size_type;
                typedef Container container_type;
                protected: Container c;
                """
            + (name.equals("priority_queue") ? "Compare comp; public: explicit priority_queue(const Compare& compare=Compare(),const Container& x=Container()); template<class InputIterator> priority_queue(InputIterator first,InputIterator last,const Compare& compare=Compare(),const Container& x=Container());" : "public: explicit " + name + "(const Container& x=Container());")
            + "bool empty() const; size_type size() const; void push(const value_type& x); void pop();"
            + (name.equals("queue") ? "reference front(); const_reference front() const; reference back(); const_reference back() const;" : (name.equals("stack") ? "reference top();" : "") + "const_reference top() const;") + "};";
        if (!name.equals("priority_queue")) text += containerRelations(name,"class T,class Container","T,Container")
            .replace("template<class T,class Container> void swap(" + name + "<T,Container>& x," + name + "<T,Container>& y);", "");
        return text;
    }
    private static String characterTraits() {
        StringBuilder text = new StringBuilder();
        for (String type : List.of("CharT", "char", "wchar_t")) {
            boolean generic = type.equals("CharT");
            text.append(generic ? "template<class CharT> struct char_traits {" : "template<> struct char_traits<" + type + "> {")
                .append("typedef ").append(type).append(" char_type;");
            if (generic) text.append("""
                typedef __cpg_opaque_integer_int_type int_type;
                typedef __cpg_opaque_integer_off_type off_type;
                typedef __cpg_opaque_object_pos_type pos_type;
                typedef __cpg_opaque_object_state_type state_type;
                """);
            else text.append("typedef ").append(type.equals("char") ? "int" : "wint_t")
                .append(" int_type; typedef streamoff off_type; typedef ").append(type.equals("char") ? "streampos" : "wstreampos")
                .append(" pos_type; typedef mbstate_t state_type;");
            text.append("""
                static void assign(char_type& c1,const char_type& c2);
                static bool eq(const char_type& c1,const char_type& c2);
                static bool lt(const char_type& c1,const char_type& c2);
                static int compare(const char_type* s1,const char_type* s2,size_t n);
                static size_t length(const char_type* s);
                static const char_type* find(const char_type* s,size_t n,const char_type& a);
                static char_type* move(char_type* s1,const char_type* s2,size_t n);
                static char_type* copy(char_type* s1,const char_type* s2,size_t n);
                static char_type* assign(char_type* s,size_t n,char_type a);
                static int_type not_eof(const int_type& c);
                static char_type to_char_type(const int_type& c);
                static int_type to_int_type(const char_type& c);
                static bool eq_int_type(const int_type& c1,const int_type& c2);
                static int_type eof();
                };
                """);
        }
        return text.toString();
    }
    private static String strings() {
        return characterTraits() + """
            template<class CharT,class Traits=char_traits<CharT>,class Allocator=allocator<CharT> >
            class basic_string {
            public:
            typedef             Traits                     traits_type;
            typedef CharT             value_type;
            typedef             Allocator                  allocator_type;
            typedef typename Allocator::size_type          size_type;
            typedef typename Allocator::difference_type difference_type;
            typedef typename Allocator::reference       reference;
            typedef typename Allocator::const_reference const_reference;
            typedef typename Allocator::pointer         pointer;
            typedef typename Allocator::const_pointer   const_pointer;
            typedef __cpg_opaque_unspecified_iterator iterator;
            typedef __cpg_opaque_unspecified_const_iterator const_iterator;
            typedef std::reverse_iterator<iterator> reverse_iterator;
            typedef std::reverse_iterator<const_iterator> const_reverse_iterator;
            static const size_type npos = -1;
            explicit basic_string(const Allocator& a = Allocator());
            basic_string(const basic_string& str);
            basic_string(const basic_string& str, size_type pos, size_type n = npos,
            const Allocator& a = Allocator());
            basic_string(const CharT* s,
            size_type n, const Allocator& a = Allocator());
            basic_string(const CharT* s, const Allocator& a = Allocator());
            basic_string(size_type n, CharT c, const Allocator& a = Allocator());
            template<class InputIterator>
            basic_string(InputIterator begin, InputIterator end,
            const Allocator& a = Allocator());
            ~basic_string();
            basic_string& operator=(const basic_string& str);
            basic_string& operator=(const CharT* s);
            basic_string& operator=(CharT c);
            iterator             begin();
            const_iterator begin() const;
            iterator             end();
            const_iterator end() const;
            reverse_iterator       rbegin();
            const_reverse_iterator rbegin() const;
            reverse_iterator       rend();
            const_reverse_iterator rend() const;
            size_type size() const;
            size_type length() const;
            size_type max_size() const;
            void resize(size_type n, CharT c);
            void resize(size_type n);
            size_type capacity() const;
            void reserve(size_type res_arg = 0);
            void clear();
            bool empty() const;
            const_reference operator[](size_type pos) const;
            reference              operator[](size_type pos);
            const_reference at(size_type n) const;
            reference              at(size_type n);
            basic_string& operator+=(const basic_string& str);
            basic_string& operator+=(const CharT* s);
            basic_string& operator+=(CharT c);
            basic_string& append(const basic_string& str);
            basic_string& append(const basic_string& str, size_type pos,
            size_type n);
            basic_string& append(const CharT* s, size_type n);
            basic_string& append(const CharT* s);
            basic_string& append(size_type n, CharT c);
            template<class InputIterator>
            basic_string& append(InputIterator first, InputIterator last);
            void push_back(CharT c);
            basic_string& assign(const basic_string& str);
            basic_string& assign(const basic_string& str, size_type pos,
            size_type n);
            basic_string& assign(const CharT* s, size_type n);
            basic_string& assign(const CharT* s);
            basic_string& assign(size_type n, CharT c);
            template<class InputIterator>
            basic_string& assign(InputIterator first, InputIterator last);
            basic_string& insert(size_type pos1, const basic_string& str);
            basic_string& insert(size_type pos1, const basic_string& str,
            size_type pos2, size_type n);
            basic_string& insert(size_type pos, const CharT* s, size_type n);
            basic_string& insert(size_type pos, const CharT* s);
            basic_string& insert(size_type pos, size_type n, CharT c);
            iterator insert(iterator p, CharT c);
            void     insert(iterator p, size_type n, CharT c);
            template<class InputIterator>
            void insert(iterator p, InputIterator first, InputIterator last);
            basic_string& erase(size_type pos = 0, size_type n = npos);
            iterator erase(iterator position);
            iterator erase(iterator first, iterator last);
            basic_string& replace(size_type pos1, size_type n1,
            const basic_string& str);
            basic_string& replace(size_type pos1, size_type n1,
            const basic_string& str,
            size_type pos2, size_type n2);
            basic_string& replace(size_type pos, size_type n1, const CharT* s,
            size_type n2);
            basic_string& replace(size_type pos, size_type n1, const CharT* s);
            basic_string& replace(size_type pos, size_type n1, size_type n2,
            CharT c);
            basic_string& replace(iterator i1, iterator i2,
            const basic_string& str);
            basic_string& replace(iterator i1, iterator i2, const CharT* s,
            size_type n);
            basic_string& replace(iterator i1, iterator i2, const CharT* s);
            basic_string& replace(iterator i1, iterator i2,
            size_type n, CharT c);
            template<class InputIterator>
            basic_string& replace(iterator i1, iterator i2,
            InputIterator j1, InputIterator j2);
            size_type copy(CharT* s, size_type n, size_type pos = 0) const;
            void swap(basic_string& str);
            const CharT* c_str() const;
            const CharT* data() const;
            allocator_type get_allocator() const;
            size_type find (const basic_string& str, size_type pos = 0) const;
            size_type find (const CharT* s, size_type pos, size_type n) const;
            size_type find (const CharT* s, size_type pos = 0) const;
            size_type find (CharT c, size_type pos = 0) const;
            size_type rfind(const basic_string& str, size_type pos = npos) const;
            size_type rfind(const CharT* s, size_type pos, size_type n) const;
            size_type rfind(const CharT* s, size_type pos = npos) const;
            size_type rfind(CharT c, size_type pos = npos) const;
            size_type find_first_of(const basic_string& str,
            size_type pos = 0) const;
            size_type find_first_of(const CharT* s,
            size_type pos, size_type n) const;
            size_type find_first_of(const CharT* s, size_type pos = 0) const;
            size_type find_first_of(CharT c, size_type pos = 0) const;
            size_type find_last_of (const basic_string& str,
            size_type pos = npos) const;
            size_type find_last_of (const CharT* s,
            size_type pos, size_type n) const;
            size_type find_last_of (const CharT* s, size_type pos = npos) const;
            size_type find_last_of (CharT c, size_type pos = npos) const;
            size_type find_first_not_of(const basic_string& str,
            size_type pos = 0) const;
            size_type find_first_not_of(const CharT* s, size_type pos,
            size_type n) const;
            size_type find_first_not_of(const CharT* s, size_type pos = 0) const;
            size_type find_first_not_of(CharT c, size_type pos = 0) const;
            size_type find_last_not_of (const basic_string& str,
            size_type pos = npos) const;
            size_type find_last_not_of (const CharT* s, size_type pos,
            size_type n) const;
            size_type find_last_not_of (const CharT* s,
            size_type pos = npos) const;
            size_type find_last_not_of (CharT c, size_type pos = npos) const;
            basic_string substr(size_type pos = 0, size_type n = npos) const;
            int compare(const basic_string& str) const;
            int compare(size_type pos1, size_type n1,
            const basic_string& str) const;
            int compare(size_type pos1, size_type n1,
            const basic_string& str,
            size_type pos2, size_type n2) const;
            int compare(const CharT* s) const;
            int compare(size_type pos1, size_type n1,
            const CharT* s) const;
            int compare(size_type pos1, size_type n1,
            const CharT* s, size_type n2) const;
            };
            typedef basic_string<char> string; typedef basic_string<wchar_t> wstring;
            """ + stringNonmembers();
    }
    private static String stringNonmembers() {
        String parameters = "class CharT,class Traits,class Allocator";
        String type = "basic_string<CharT,Traits,Allocator>";
        String prefix = "template<" + parameters + "> ";
        StringBuilder text = new StringBuilder(containerRelations("basic_string",parameters,"CharT,Traits,Allocator"));
        for (String op : List.of("==", "!=", "<", ">", "<=", ">=")) {
            text.append(prefix).append("bool operator").append(op).append("(const CharT* left,const ").append(type).append("& right);");
            text.append(prefix).append("bool operator").append(op).append("(const ").append(type).append("& left,const CharT* right);");
        }
        for (String[] operands : List.of(new String[]{"const " + type + "&", "const " + type + "&"},
                new String[]{"const CharT*", "const " + type + "&"},new String[]{"CharT", "const " + type + "&"},
                new String[]{"const " + type + "&", "const CharT*"},new String[]{"const " + type + "&", "CharT"}))
            text.append(prefix).append(type).append(" operator+(").append(operands[0]).append(" left,").append(operands[1]).append(" right);");
        return text + """
            template<class CharT,class Traits,class Allocator> basic_istream<CharT,Traits>& operator>>(basic_istream<CharT,Traits>& is,basic_string<CharT,Traits,Allocator>& str);
            template<class CharT,class Traits,class Allocator> basic_ostream<CharT,Traits>& operator<<(basic_ostream<CharT,Traits>& os,const basic_string<CharT,Traits,Allocator>& str);
            template<class CharT,class Traits,class Allocator> basic_istream<CharT,Traits>& getline(basic_istream<CharT,Traits>& is,basic_string<CharT,Traits,Allocator>& str,CharT delim);
            template<class CharT,class Traits,class Allocator> basic_istream<CharT,Traits>& getline(basic_istream<CharT,Traits>& is,basic_string<CharT,Traits,Allocator>& str);
            """;
    }
    private static String iterators() {
        String text = """
            struct input_iterator_tag {}; struct output_iterator_tag {};
            struct forward_iterator_tag : public input_iterator_tag {};
            struct bidirectional_iterator_tag : public forward_iterator_tag {};
            struct random_access_iterator_tag : public bidirectional_iterator_tag {};
            template<class Category,class T,class Distance=ptrdiff_t,class Pointer=T*,class Reference=T&>
            struct iterator { typedef Category iterator_category; typedef T value_type; typedef Distance difference_type; typedef Pointer pointer; typedef Reference reference; };
            template<class Iterator> struct iterator_traits {
                typedef typename Iterator::difference_type difference_type;
                typedef typename Iterator::value_type value_type;
                typedef typename Iterator::pointer pointer;
                typedef typename Iterator::reference reference;
                typedef typename Iterator::iterator_category iterator_category;
            };
            template<class T> struct iterator_traits<T*> {
                typedef ptrdiff_t difference_type; typedef T value_type; typedef T* pointer;
                typedef T& reference; typedef random_access_iterator_tag iterator_category;
            };
            template<class T> struct iterator_traits<const T*> {
                typedef ptrdiff_t difference_type; typedef T value_type; typedef const T* pointer;
                typedef const T& reference; typedef random_access_iterator_tag iterator_category;
            };
            template<class InputIterator,class Distance> void advance(InputIterator& i,Distance n);
            template<class InputIterator> typename iterator_traits<InputIterator>::difference_type distance(InputIterator first,InputIterator last);
            template<class Iterator> class reverse_iterator : public iterator<typename iterator_traits<Iterator>::iterator_category,typename iterator_traits<Iterator>::value_type,typename iterator_traits<Iterator>::difference_type,typename iterator_traits<Iterator>::pointer,typename iterator_traits<Iterator>::reference> {
                protected: Iterator current;
                public:
                typedef Iterator iterator_type;
                typedef typename iterator_traits<Iterator>::difference_type difference_type;
                typedef typename iterator_traits<Iterator>::reference reference;
                typedef typename iterator_traits<Iterator>::pointer pointer;
                reverse_iterator(); explicit reverse_iterator(Iterator x);
                template<class U> reverse_iterator(const reverse_iterator<U>& x);
                Iterator base() const;
                reference operator*() const; pointer operator->() const;
                reverse_iterator& operator++(); reverse_iterator operator++(int);
                reverse_iterator& operator--(); reverse_iterator operator--(int);
                reverse_iterator operator+(difference_type n) const;
                reverse_iterator& operator+=(difference_type n);
                reverse_iterator operator-(difference_type n) const;
                reverse_iterator& operator-=(difference_type n);
                reference operator[](difference_type n) const;
            };
            template<class Iterator> typename reverse_iterator<Iterator>::difference_type operator-(const reverse_iterator<Iterator>& x,const reverse_iterator<Iterator>& y);
            template<class Iterator> reverse_iterator<Iterator> operator+(typename reverse_iterator<Iterator>::difference_type n,const reverse_iterator<Iterator>& x);
            """;
        for (String op : List.of("==", "!=", "<", ">", "<=", ">="))
            text += "template<class Iterator> bool operator" + op + "(const reverse_iterator<Iterator>& x,const reverse_iterator<Iterator>& y);";
        for (String name : List.of("back_insert_iterator", "front_insert_iterator", "insert_iterator")) {
            text += "template<class Container> class " + name + " : public iterator<output_iterator_tag,void,void,void,void> { protected: Container* container;"
                + (name.equals("insert_iterator") ? "typename Container::iterator iter;" : "")
                + "public: typedef Container container_type; "
                + (name.equals("insert_iterator") ? "insert_iterator(Container& x,typename Container::iterator i);" : "explicit " + name + "(Container& x);")
                + name + "& operator=(const typename Container::value_type& value); "
                + name + "& operator*(); " + name + "& operator++(); "
                + (name.equals("insert_iterator") ? name + "&" : name) + " operator++(int); };";
        }
        return text + """
            template<class Container> back_insert_iterator<Container> back_inserter(Container& x);
            template<class Container> front_insert_iterator<Container> front_inserter(Container& x);
            template<class Container,class Iterator> insert_iterator<Container> inserter(Container& x,Iterator i);
            """ + streamIterators();
    }
    private static String limits() {
        StringBuilder text = new StringBuilder("""
            enum float_round_style { round_indeterminate=-1, round_toward_zero=0, round_to_nearest=1, round_toward_infinity=2, round_toward_neg_infinity=3 };
            enum float_denorm_style { denorm_indeterminate=-1, denorm_absent=0, denorm_present=1 };
            """);
        String members = """
            public:
            static const bool is_specialized;
            static T min() throw(); static T max() throw();
            static const int digits; static const int digits10;
            static const bool is_signed; static const bool is_integer; static const bool is_exact; static const int radix;
            static T epsilon() throw(); static T round_error() throw();
            static const int min_exponent; static const int min_exponent10; static const int max_exponent; static const int max_exponent10;
            static const bool has_infinity; static const bool has_quiet_NaN; static const bool has_signaling_NaN;
            static const float_denorm_style has_denorm; static const bool has_denorm_loss;
            static T infinity() throw(); static T quiet_NaN() throw(); static T signaling_NaN() throw(); static T denorm_min() throw();
            static const bool is_iec559; static const bool is_bounded; static const bool is_modulo;
            static const bool traps; static const bool tinyness_before; static const float_round_style round_style;
            """;
        text.append("template<class T> class numeric_limits {").append(members).append("};");
        for (String type : List.of("bool", "char", "signed char", "unsigned char", "wchar_t", "short", "unsigned short", "int", "unsigned int", "long", "unsigned long", "float", "double", "long double"))
            text.append("template<> class numeric_limits<").append(type).append("> {").append(members.replace(" T ", " " + type + " ")).append("};");
        return text.toString();
    }
    private static String bitset() {
        return """
            template<size_t N> class bitset {
                public:
                class reference { friend class bitset; reference(); public: ~reference();
                    reference& operator=(bool x); reference& operator=(const reference& x);
                    bool operator~() const; operator bool() const; reference& flip();
                };
                bitset(); bitset(unsigned long value);
                template<class CharT,class Traits,class Allocator> explicit bitset(const basic_string<CharT,Traits,Allocator>& str,
                    typename basic_string<CharT,Traits,Allocator>::size_type pos=0,
                    typename basic_string<CharT,Traits,Allocator>::size_type n=basic_string<CharT,Traits,Allocator>::npos);
                bitset& operator&=(const bitset& rhs); bitset& operator|=(const bitset& rhs); bitset& operator^=(const bitset& rhs);
                bitset& operator<<=(size_t pos); bitset& operator>>=(size_t pos);
                bitset& set(); bitset& set(size_t pos,bool value=true); bitset& reset(); bitset& reset(size_t pos);
                bitset operator~() const; bitset& flip(); bitset& flip(size_t pos);
                bool operator[](size_t pos) const; reference operator[](size_t pos);
                unsigned long to_ulong() const;
                template<class CharT,class Traits,class Allocator> basic_string<CharT,Traits,Allocator> to_string() const;
                size_t count() const; size_t size() const;
                bool operator==(const bitset& rhs) const; bool operator!=(const bitset& rhs) const;
                bool test(size_t pos) const; bool any() const; bool none() const;
                bitset operator<<(size_t pos) const; bitset operator>>(size_t pos) const;
            };
            template<size_t N> bitset<N> operator&(const bitset<N>& x,const bitset<N>& y);
            template<size_t N> bitset<N> operator|(const bitset<N>& x,const bitset<N>& y);
            template<size_t N> bitset<N> operator^(const bitset<N>& x,const bitset<N>& y);
            template<class CharT,class Traits,size_t N> basic_istream<CharT,Traits>& operator>>(basic_istream<CharT,Traits>& is,bitset<N>& x);
            template<class CharT,class Traits,size_t N> basic_ostream<CharT,Traits>& operator<<(basic_ostream<CharT,Traits>& os,const bitset<N>& x);
            """;
    }
    private static String complex() {
        StringBuilder text = new StringBuilder("template<class T> class complex; template<> class complex<float>; template<> class complex<double>; template<> class complex<long double>;");
        List<String> scalar = List.of("float", "double", "long double");
        for (String type : List.of("T", "float", "double", "long double")) {
            boolean generic = type.equals("T");
            text.append(generic ? "template<class T> class complex {" : "template<> class complex<" + type + "> {")
                .append("public: typedef ").append(type).append(" value_type;")
                .append(generic ? "complex(const T& real=T(),const T& imag=T()); complex(const complex& x); template<class X> complex(const complex<X>& x);" : "complex(" + type + " real=0," + type + " imag=0);");
            if (!generic) for (String from : scalar) if (!from.equals(type))
                text.append(scalar.indexOf(from) > scalar.indexOf(type) ? "explicit " : "").append("complex(const complex<").append(from).append(">& x);");
            text.append(type).append(" real() const; ").append(type).append(" imag() const; complex& operator=(const complex& x);");
            for (String op : List.of("=", "+=", "-=", "*=", "/=")) {
                text.append("complex& operator").append(op).append('(').append(generic ? "const T&" : type).append(" x);");
                text.append("template<class X> complex& operator").append(op).append("(const complex<X>& x);");
            }
            text.append("};");
        }
        for (String op : List.of("+", "-", "*", "/", "==", "!=")) {
            String result = op.equals("==") || op.equals("!=") ? "bool" : "complex<T>";
            for (String[] operands : List.of(new String[]{"complex<T>","complex<T>"},new String[]{"complex<T>","T"},new String[]{"T","complex<T>"}))
                text.append("template<class T> ").append(result).append(" operator").append(op).append("(const ").append(operands[0]).append("& x,const ").append(operands[1]).append("& y);");
        }
        for (String name : List.of("real", "imag", "abs", "arg", "norm"))
            text.append("template<class T> T ").append(name).append("(const complex<T>& x);");
        for (String name : List.of("conj", "cos", "cosh", "exp", "log", "log10", "sin", "sinh", "sqrt", "tan", "tanh"))
            text.append("template<class T> complex<T> ").append(name).append("(const complex<T>& x);");
        return text + """
            template<class T> complex<T> operator+(const complex<T>& x);
            template<class T> complex<T> operator-(const complex<T>& x);
            template<class T> complex<T> polar(const T& rho,const T& theta=0);
            template<class T> complex<T> pow(const complex<T>& x,int n);
            template<class T> complex<T> pow(const complex<T>& x,const T& y);
            template<class T> complex<T> pow(const complex<T>& x,const complex<T>& y);
            template<class T> complex<T> pow(const T& x,const complex<T>& y);
            template<class T,class CharT,class Traits> basic_istream<CharT,Traits>& operator>>(basic_istream<CharT,Traits>& is,complex<T>& x);
            template<class T,class CharT,class Traits> basic_ostream<CharT,Traits>& operator<<(basic_ostream<CharT,Traits>& os,const complex<T>& x);
            """;
    }
    private static String valarray() {
        String text = """
            class slice; class gslice; template<class T> class valarray;
            template<class T> class slice_array; template<class T> class gslice_array;
            template<class T> class mask_array; template<class T> class indirect_array;
            template<class T> class valarray {
            public:
            typedef T value_type;
            valarray();
            explicit valarray(size_t);
            valarray(const T&, size_t);
            valarray(const T*, size_t);
            valarray(const valarray&);
            valarray(const slice_array<T>&);
            valarray(const gslice_array<T>&);
            valarray(const mask_array<T>&);
            valarray(const indirect_array<T>&);
            ~valarray();
            valarray<T>& operator=(const valarray<T>&);
            valarray<T>& operator=(const T&);
            valarray<T>& operator=(const slice_array<T>&);
            valarray<T>& operator=(const gslice_array<T>&);
            valarray<T>& operator=(const mask_array<T>&);
            valarray<T>& operator=(const indirect_array<T>&);
            const T&                  operator[](size_t) const;
            T&                        operator[](size_t);
            valarray<T>               operator[](slice) const;
            slice_array<T>            operator[](slice);
            valarray<T>               operator[](const gslice&) const;
            gslice_array<T>           operator[](const gslice&);
            valarray<T>               operator[](const valarray<bool>&) const;
            mask_array<T>             operator[](const valarray<bool>&);
            valarray<T>               operator[](const valarray<size_t>&) const;
            indirect_array<T> operator[](const valarray<size_t>&);
            valarray<T> operator+() const;
            valarray<T> operator-() const;
            valarray<T> operator~() const;
            valarray<bool> operator!() const;
            valarray<T>& operator*= (const T&);
            valarray<T>& operator/= (const T&);
            valarray<T>& operator%= (const T&);
            valarray<T>& operator+= (const T&);
            valarray<T>& operator-= (const T&);
            valarray<T>& operator^= (const T&);
            valarray<T>& operator&= (const T&);
            valarray<T>& operator|= (const T&);
            valarray<T>& operator<<=(const T&);
            valarray<T>& operator>>=(const T&);
            valarray<T>& operator*= (const valarray<T>&);
            valarray<T>& operator/= (const valarray<T>&);
            valarray<T>& operator%= (const valarray<T>&);
            valarray<T>& operator+= (const valarray<T>&);
            valarray<T>& operator-= (const valarray<T>&);
            valarray<T>& operator^= (const valarray<T>&);
            valarray<T>& operator|= (const valarray<T>&);
            valarray<T>& operator&= (const valarray<T>&);
            valarray<T>& operator<<=(const valarray<T>&);
            valarray<T>& operator>>=(const valarray<T>&);
            size_t size() const;
            T       sum() const;
            T       min() const;
            T       max() const;
            valarray<T> shift (int) const;
            valarray<T> cshift(int) const;
            valarray<T> apply(T func(T)) const;
            valarray<T> apply(T func(const T&)) const;
            void resize(size_t sz, T c = T());
            };
            class slice { public: slice(); slice(size_t start,size_t length,size_t stride); size_t start() const; size_t size() const; size_t stride() const; };
            class gslice { public: gslice(); gslice(size_t start,const valarray<size_t>& lengths,const valarray<size_t>& strides); size_t start() const; valarray<size_t> size() const; valarray<size_t> stride() const; };
            """;
        for (String name : List.of("slice_array", "gslice_array", "mask_array", "indirect_array")) {
            text += "template<class T> class " + name + " { public: typedef T value_type; "
                + name + "(const " + name + "& x); ~" + name + "(); " + name + "& operator=(const " + name + "& x); void operator=(const T& value) const;";
            for (String op : List.of("=", "*=", "/=", "%=", "+=", "-=", "^=", "&=", "|=", "<<=", ">>="))
                text += "void operator" + op + "(const valarray<T>& values) const;";
            text += "private: " + name + "(); };";
        }
        for (String op : List.of("*", "/", "%", "+", "-", "^", "&", "|", "<<", ">>", "&&", "||", "==", "!=", "<", ">", "<=", ">=")) {
            String result = Set.of("&&", "||", "==", "!=", "<", ">", "<=", ">=").contains(op) ? "valarray<bool>" : "valarray<T>";
            for (String[] operands : List.of(new String[]{"valarray<T>","valarray<T>"},new String[]{"valarray<T>","T"},new String[]{"T","valarray<T>"}))
                text += "template<class T> " + result + " operator" + op + "(const " + operands[0] + "& x,const " + operands[1] + "& y);";
        }
        for (String name : List.of("abs", "acos", "asin", "atan", "cos", "cosh", "exp", "log", "log10", "sin", "sinh", "sqrt", "tan", "tanh"))
            text += "template<class T> valarray<T> " + name + "(const valarray<T>& x);";
        for (String name : List.of("atan2", "pow"))
            for (String[] operands : List.of(new String[]{"valarray<T>","valarray<T>"},new String[]{"valarray<T>","T"},new String[]{"T","valarray<T>"}))
                text += "template<class T> valarray<T> " + name + "(const " + operands[0] + "& x,const " + operands[1] + "& y);";
        return text;
    }
    private static String streamIterators() {
        return """
            template<class T,class CharT=char,class Traits=char_traits<CharT>,class Distance=ptrdiff_t>
            class istream_iterator : public iterator<input_iterator_tag,T,Distance,const T*,const T&> {
                public: typedef CharT char_type; typedef Traits traits_type; typedef basic_istream<CharT,Traits> istream_type;
                istream_iterator(); istream_iterator(istream_type& s); istream_iterator(const istream_iterator& x); ~istream_iterator();
                const T& operator*() const; const T* operator->() const;
                istream_iterator& operator++(); istream_iterator operator++(int);
            };
            template<class T,class CharT,class Traits,class Distance> bool operator==(const istream_iterator<T,CharT,Traits,Distance>& x,const istream_iterator<T,CharT,Traits,Distance>& y);
            template<class T,class CharT,class Traits,class Distance> bool operator!=(const istream_iterator<T,CharT,Traits,Distance>& x,const istream_iterator<T,CharT,Traits,Distance>& y);
            template<class T,class CharT=char,class Traits=char_traits<CharT> >
            class ostream_iterator : public iterator<output_iterator_tag,void,void,void,void> {
                public: typedef CharT char_type; typedef Traits traits_type; typedef basic_ostream<CharT,Traits> ostream_type;
                ostream_iterator(ostream_type& s); ostream_iterator(ostream_type& s,const CharT* delimiter);
                ostream_iterator(const ostream_iterator& x); ~ostream_iterator();
                ostream_iterator& operator=(const T& value); ostream_iterator& operator*();
                ostream_iterator& operator++(); ostream_iterator& operator++(int);
            };
            template<class CharT,class Traits=char_traits<CharT> >
            class istreambuf_iterator : public iterator<input_iterator_tag,CharT,typename Traits::off_type,CharT*,CharT&> {
                public: typedef CharT char_type; typedef Traits traits_type; typedef typename Traits::int_type int_type;
                typedef basic_streambuf<CharT,Traits> streambuf_type; typedef basic_istream<CharT,Traits> istream_type;
                istreambuf_iterator() throw(); istreambuf_iterator(istream_type& s) throw(); istreambuf_iterator(streambuf_type* s) throw();
                istreambuf_iterator(const __cpg_opaque_unspecified_postincrement_result& p) throw();
                CharT operator*() const; istreambuf_iterator& operator++();
                __cpg_opaque_unspecified_postincrement_result operator++(int);
                bool equal(const istreambuf_iterator& other) const;
            };
            template<class CharT,class Traits> bool operator==(const istreambuf_iterator<CharT,Traits>& x,const istreambuf_iterator<CharT,Traits>& y);
            template<class CharT,class Traits> bool operator!=(const istreambuf_iterator<CharT,Traits>& x,const istreambuf_iterator<CharT,Traits>& y);
            template<class CharT,class Traits=char_traits<CharT> >
            class ostreambuf_iterator : public iterator<output_iterator_tag,void,void,void,void> {
                public: typedef CharT char_type; typedef Traits traits_type;
                typedef basic_streambuf<CharT,Traits> streambuf_type; typedef basic_ostream<CharT,Traits> ostream_type;
                ostreambuf_iterator(ostream_type& s) throw(); ostreambuf_iterator(streambuf_type* s) throw();
                ostreambuf_iterator& operator=(CharT c); ostreambuf_iterator& operator*();
                ostreambuf_iterator& operator++(); ostreambuf_iterator& operator++(int); bool failed() const throw();
            };
            """;
    }
    private static String functional() {
        StringBuilder s=new StringBuilder("template<class Arg,class Result> struct unary_function { typedef Arg argument_type; typedef Result result_type; }; template<class Arg1,class Arg2,class Result> struct binary_function { typedef Arg1 first_argument_type; typedef Arg2 second_argument_type; typedef Result result_type; };");
        for(String name:List.of("plus","minus","multiplies","divides","modulus"))s.append("template<class T> struct ").append(name).append(" : public binary_function<T,T,T> { T operator()(const T& x,const T& y) const; };");
        for(String name:List.of("equal_to","not_equal_to","greater","less","greater_equal","less_equal","logical_and","logical_or"))s.append("template<class T> struct ").append(name).append(" : public binary_function<T,T,bool> { bool operator()(const T& x,const T& y) const; };");
        return s+"template<class T> struct negate : public unary_function<T,T> { T operator()(const T& x) const; }; template<class T> struct logical_not : public unary_function<T,bool> { bool operator()(const T& x) const; };" + functionalAdaptors();
    }
    private static String functionalAdaptors() {
        StringBuilder text = new StringBuilder("""
            template<class Predicate> class unary_negate : public unary_function<typename Predicate::argument_type,bool> {
                public: explicit unary_negate(const Predicate& pred); bool operator()(const typename Predicate::argument_type& x) const;
            };
            template<class Predicate> unary_negate<Predicate> not1(const Predicate& pred);
            template<class Predicate> class binary_negate : public binary_function<typename Predicate::first_argument_type,typename Predicate::second_argument_type,bool> {
                public: explicit binary_negate(const Predicate& pred);
                bool operator()(const typename Predicate::first_argument_type& x,const typename Predicate::second_argument_type& y) const;
            };
            template<class Predicate> binary_negate<Predicate> not2(const Predicate& pred);
            template<class Arg,class Result> class pointer_to_unary_function : public unary_function<Arg,Result> {
                public: explicit pointer_to_unary_function(Result (*f)(Arg)); Result operator()(Arg x) const;
            };
            template<class Arg,class Result> pointer_to_unary_function<Arg,Result> ptr_fun(Result (*f)(Arg));
            template<class Arg1,class Arg2,class Result> class pointer_to_binary_function : public binary_function<Arg1,Arg2,Result> {
                public: explicit pointer_to_binary_function(Result (*f)(Arg1,Arg2)); Result operator()(Arg1 x,Arg2 y) const;
            };
            template<class Arg1,class Arg2,class Result> pointer_to_binary_function<Arg1,Arg2,Result> ptr_fun(Result (*f)(Arg1,Arg2));
            """);
        for (boolean first : List.of(true,false)) {
            String suffix = first ? "1st" : "2nd";
            String bound = first ? "first" : "second", free = first ? "second" : "first";
            text.append("template<class Operation> class binder").append(suffix)
                .append(" : public unary_function<typename Operation::").append(free).append("_argument_type,typename Operation::result_type> {")
                .append("protected: Operation op; typename Operation::").append(bound).append("_argument_type value; public: binder").append(suffix)
                .append("(const Operation& x,const typename Operation::").append(bound).append("_argument_type& y);")
                .append("typename Operation::result_type operator()(const typename Operation::").append(free).append("_argument_type& x) const;")
                .append("typename Operation::result_type operator()(typename Operation::").append(free).append("_argument_type& x) const; };")
                .append("template<class Operation,class T> binder").append(suffix).append("<Operation> bind").append(suffix).append("(const Operation& op,const T& x);");
        }
        for (boolean constant : List.of(false,true)) for (boolean reference : List.of(false,true)) for (boolean argument : List.of(false,true)) {
            String name = (constant ? "const_" : "") + "mem_fun" + (argument ? "1" : "") + (reference ? "_ref" : "") + "_t";
            String parameters = "class S,class T" + (argument ? ",class A" : "");
            String types = "S,T" + (argument ? ",A" : "");
            String object = (constant ? "const " : "") + "T" + (reference ? "&" : "*");
            String base = (argument ? "binary_function<" : "unary_function<")
                + (reference ? "T" : object) + (argument ? ",A" : "") + ",S>";
            String member = "S (T::*p)(" + (argument ? "A" : "") + ")" + (constant ? " const" : "");
            text.append("template<").append(parameters).append("> class ").append(name).append(" : public ").append(base)
                .append(" { public: explicit ").append(name).append('(').append(member).append("); S operator()(").append(object).append(" p")
                .append(argument ? ",A x" : "").append(") const; };")
                .append("template<").append(parameters).append("> ").append(name).append('<').append(types).append("> mem_fun")
                .append(reference ? "_ref" : "").append('(').append(member).append(");");
        }
        return text.toString();
    }
    private static String exceptions() {
        StringBuilder text = new StringBuilder();
        for (String name : List.of("logic_error", "domain_error", "invalid_argument", "length_error", "out_of_range", "runtime_error", "range_error", "overflow_error", "underflow_error")) {
            boolean root = Set.of("logic_error", "runtime_error").contains(name);
            String base = root ? "exception" : Set.of("domain_error", "invalid_argument", "length_error", "out_of_range").contains(name) ? "logic_error" : "runtime_error";
            text.append("class ").append(name).append(" : public ").append(base).append(" { public: explicit ").append(name).append("(const string& what);");
            if (root) text.append("virtual const char* what() const throw();");
            text.append("};");
        }
        return text.toString();
    }
    private static String algorithms() {
        return """
            template<class T> void swap(T& x,T& y);
            template<class InputIterator,class Function> Function for_each(InputIterator first,InputIterator last,Function f);
            template<class InputIterator,class T> InputIterator find(InputIterator first,InputIterator last,const T& value);
            template<class InputIterator,class Predicate> InputIterator find_if(InputIterator first,InputIterator last,Predicate pred);
            template<class ForwardIterator1,class ForwardIterator2> ForwardIterator1 find_end(ForwardIterator1 first1,ForwardIterator1 last1,ForwardIterator2 first2,ForwardIterator2 last2);
            template<class ForwardIterator1,class ForwardIterator2,class BinaryPredicate> ForwardIterator1 find_end(ForwardIterator1 first1,ForwardIterator1 last1,ForwardIterator2 first2,ForwardIterator2 last2,BinaryPredicate pred);
            template<class InputIterator,class ForwardIterator> InputIterator find_first_of(InputIterator first1,InputIterator last1,ForwardIterator first2,ForwardIterator last2);
            template<class InputIterator,class ForwardIterator,class BinaryPredicate> InputIterator find_first_of(InputIterator first1,InputIterator last1,ForwardIterator first2,ForwardIterator last2,BinaryPredicate pred);
            template<class ForwardIterator> ForwardIterator adjacent_find(ForwardIterator first,ForwardIterator last);
            template<class ForwardIterator,class BinaryPredicate> ForwardIterator adjacent_find(ForwardIterator first,ForwardIterator last,BinaryPredicate pred);
            template<class InputIterator,class T> typename iterator_traits<InputIterator>::difference_type count(InputIterator first,InputIterator last,const T& value);
            template<class InputIterator,class Predicate> typename iterator_traits<InputIterator>::difference_type count_if(InputIterator first,InputIterator last,Predicate pred);
            template<class InputIterator1,class InputIterator2> pair<InputIterator1,InputIterator2> mismatch(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2);
            template<class InputIterator1,class InputIterator2,class BinaryPredicate> pair<InputIterator1,InputIterator2> mismatch(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2,BinaryPredicate pred);
            template<class InputIterator1,class InputIterator2> bool equal(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2);
            template<class InputIterator1,class InputIterator2,class BinaryPredicate> bool equal(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2,BinaryPredicate pred);
            template<class ForwardIterator1,class ForwardIterator2> ForwardIterator1 search(ForwardIterator1 first1,ForwardIterator1 last1,ForwardIterator2 first2,ForwardIterator2 last2);
            template<class ForwardIterator1,class ForwardIterator2,class BinaryPredicate> ForwardIterator1 search(ForwardIterator1 first1,ForwardIterator1 last1,ForwardIterator2 first2,ForwardIterator2 last2,BinaryPredicate pred);
            template<class ForwardIterator,class Size,class T> ForwardIterator search_n(ForwardIterator first,ForwardIterator last,Size count,const T& value);
            template<class ForwardIterator,class Size,class T,class BinaryPredicate> ForwardIterator search_n(ForwardIterator first,ForwardIterator last,Size count,const T& value,BinaryPredicate pred);
            template<class InputIterator,class OutputIterator> OutputIterator copy(InputIterator first,InputIterator last,OutputIterator result);
            template<class BidirectionalIterator1,class BidirectionalIterator2> BidirectionalIterator2 copy_backward(BidirectionalIterator1 first,BidirectionalIterator1 last,BidirectionalIterator2 result);
            template<class ForwardIterator1,class ForwardIterator2> ForwardIterator2 swap_ranges(ForwardIterator1 first1,ForwardIterator1 last1,ForwardIterator2 first2);
            template<class ForwardIterator1,class ForwardIterator2> void iter_swap(ForwardIterator1 a,ForwardIterator2 b);
            template<class InputIterator,class OutputIterator,class UnaryOperation> OutputIterator transform(InputIterator first,InputIterator last,OutputIterator result,UnaryOperation op);
            template<class InputIterator1,class InputIterator2,class OutputIterator,class BinaryOperation> OutputIterator transform(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2,OutputIterator result,BinaryOperation op);
            template<class ForwardIterator,class T> void replace(ForwardIterator first,ForwardIterator last,const T& old_value,const T& new_value);
            template<class ForwardIterator,class Predicate,class T> void replace_if(ForwardIterator first,ForwardIterator last,Predicate pred,const T& new_value);
            template<class InputIterator,class OutputIterator,class T> OutputIterator replace_copy(InputIterator first,InputIterator last,OutputIterator result,const T& old_value,const T& new_value);
            template<class InputIterator,class OutputIterator,class Predicate,class T> OutputIterator replace_copy_if(InputIterator first,InputIterator last,OutputIterator result,Predicate pred,const T& new_value);
            template<class ForwardIterator,class T> void fill(ForwardIterator first,ForwardIterator last,const T& value);
            template<class OutputIterator,class Size,class T> void fill_n(OutputIterator first,Size count,const T& value);
            template<class ForwardIterator,class Generator> void generate(ForwardIterator first,ForwardIterator last,Generator gen);
            template<class OutputIterator,class Size,class Generator> void generate_n(OutputIterator first,Size count,Generator gen);
            template<class ForwardIterator,class T> ForwardIterator remove(ForwardIterator first,ForwardIterator last,const T& value);
            template<class ForwardIterator,class Predicate> ForwardIterator remove_if(ForwardIterator first,ForwardIterator last,Predicate pred);
            template<class InputIterator,class OutputIterator,class T> OutputIterator remove_copy(InputIterator first,InputIterator last,OutputIterator result,const T& value);
            template<class InputIterator,class OutputIterator,class Predicate> OutputIterator remove_copy_if(InputIterator first,InputIterator last,OutputIterator result,Predicate pred);
            template<class ForwardIterator> ForwardIterator unique(ForwardIterator first,ForwardIterator last);
            template<class ForwardIterator,class BinaryPredicate> ForwardIterator unique(ForwardIterator first,ForwardIterator last,BinaryPredicate pred);
            template<class InputIterator,class OutputIterator> OutputIterator unique_copy(InputIterator first,InputIterator last,OutputIterator result);
            template<class InputIterator,class OutputIterator,class BinaryPredicate> OutputIterator unique_copy(InputIterator first,InputIterator last,OutputIterator result,BinaryPredicate pred);
            template<class BidirectionalIterator> void reverse(BidirectionalIterator first,BidirectionalIterator last);
            template<class BidirectionalIterator,class OutputIterator> OutputIterator reverse_copy(BidirectionalIterator first,BidirectionalIterator last,OutputIterator result);
            template<class ForwardIterator> void rotate(ForwardIterator first,ForwardIterator middle,ForwardIterator last);
            template<class ForwardIterator,class OutputIterator> OutputIterator rotate_copy(ForwardIterator first,ForwardIterator middle,ForwardIterator last,OutputIterator result);
            template<class RandomAccessIterator> void random_shuffle(RandomAccessIterator first,RandomAccessIterator last);
            template<class RandomAccessIterator,class RandomNumberGenerator> void random_shuffle(RandomAccessIterator first,RandomAccessIterator last,RandomNumberGenerator& rand);
            template<class BidirectionalIterator,class Predicate> BidirectionalIterator partition(BidirectionalIterator first,BidirectionalIterator last,Predicate pred);
            template<class BidirectionalIterator,class Predicate> BidirectionalIterator stable_partition(BidirectionalIterator first,BidirectionalIterator last,Predicate pred);
            """ + orderedAlgorithms();
    }
    private static String orderedAlgorithms() {
        StringBuilder s = new StringBuilder();
        for (String name : List.of("sort", "stable_sort", "push_heap", "pop_heap", "make_heap", "sort_heap")) {
            s.append("template<class RandomAccessIterator> void ").append(name).append("(RandomAccessIterator first,RandomAccessIterator last);");
            s.append("template<class RandomAccessIterator,class Compare> void ").append(name).append("(RandomAccessIterator first,RandomAccessIterator last,Compare comp);");
        }
        for (String name : List.of("partial_sort", "nth_element")) {
            s.append("template<class RandomAccessIterator> void ").append(name).append("(RandomAccessIterator first,RandomAccessIterator middle,RandomAccessIterator last);");
            s.append("template<class RandomAccessIterator,class Compare> void ").append(name).append("(RandomAccessIterator first,RandomAccessIterator middle,RandomAccessIterator last,Compare comp);");
        }
        for (String name : List.of("lower_bound", "upper_bound", "equal_range", "binary_search")) {
            String result = name.equals("equal_range") ? "pair<ForwardIterator,ForwardIterator>" : name.equals("binary_search") ? "bool" : "ForwardIterator";
            s.append("template<class ForwardIterator,class T> ").append(result).append(' ').append(name).append("(ForwardIterator first,ForwardIterator last,const T& value);");
            s.append("template<class ForwardIterator,class T,class Compare> ").append(result).append(' ').append(name).append("(ForwardIterator first,ForwardIterator last,const T& value,Compare comp);");
        }
        for (String name : List.of("merge", "set_union", "set_intersection", "set_difference", "set_symmetric_difference")) {
            s.append("template<class InputIterator1,class InputIterator2,class OutputIterator> OutputIterator ").append(name).append("(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2,InputIterator2 last2,OutputIterator result);");
            s.append("template<class InputIterator1,class InputIterator2,class OutputIterator,class Compare> OutputIterator ").append(name).append("(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2,InputIterator2 last2,OutputIterator result,Compare comp);");
        }
        for (String name : List.of("includes", "lexicographical_compare")) {
            s.append("template<class InputIterator1,class InputIterator2> bool ").append(name).append("(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2,InputIterator2 last2);");
            s.append("template<class InputIterator1,class InputIterator2,class Compare> bool ").append(name).append("(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2,InputIterator2 last2,Compare comp);");
        }
        for (String name : List.of("next_permutation", "prev_permutation")) {
            s.append("template<class BidirectionalIterator> bool ").append(name).append("(BidirectionalIterator first,BidirectionalIterator last);");
            s.append("template<class BidirectionalIterator,class Compare> bool ").append(name).append("(BidirectionalIterator first,BidirectionalIterator last,Compare comp);");
        }
        for (String name : List.of("min", "max")) {
            s.append("template<class T> const T& ").append(name).append("(const T& a,const T& b);");
            s.append("template<class T,class Compare> const T& ").append(name).append("(const T& a,const T& b,Compare comp);");
            s.append("template<class ForwardIterator> ForwardIterator ").append(name).append("_element(ForwardIterator first,ForwardIterator last);");
            s.append("template<class ForwardIterator,class Compare> ForwardIterator ").append(name).append("_element(ForwardIterator first,ForwardIterator last,Compare comp);");
        }
        return s + """
            template<class InputIterator,class RandomAccessIterator> RandomAccessIterator partial_sort_copy(InputIterator first,InputIterator last,RandomAccessIterator result_first,RandomAccessIterator result_last);
            template<class InputIterator,class RandomAccessIterator,class Compare> RandomAccessIterator partial_sort_copy(InputIterator first,InputIterator last,RandomAccessIterator result_first,RandomAccessIterator result_last,Compare comp);
            template<class BidirectionalIterator> void inplace_merge(BidirectionalIterator first,BidirectionalIterator middle,BidirectionalIterator last);
            template<class BidirectionalIterator,class Compare> void inplace_merge(BidirectionalIterator first,BidirectionalIterator middle,BidirectionalIterator last,Compare comp);
            """;
    }
    private static String numeric() {
        return "template<class InputIterator,class T> T accumulate(InputIterator first,InputIterator last,T init); "
            + "template<class InputIterator,class T,class BinaryOperation> T accumulate(InputIterator first,InputIterator last,T init,BinaryOperation op); "
            + "template<class InputIterator1,class InputIterator2,class T> T inner_product(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2,T init); "
            + "template<class InputIterator1,class InputIterator2,class T,class BinaryOperation1,class BinaryOperation2> T inner_product(InputIterator1 first1,InputIterator1 last1,InputIterator2 first2,T init,BinaryOperation1 op1,BinaryOperation2 op2); "
            + "template<class InputIterator,class OutputIterator> OutputIterator partial_sum(InputIterator first,InputIterator last,OutputIterator result); "
            + "template<class InputIterator,class OutputIterator,class BinaryOperation> OutputIterator partial_sum(InputIterator first,InputIterator last,OutputIterator result,BinaryOperation op); "
            + "template<class InputIterator,class OutputIterator> OutputIterator adjacent_difference(InputIterator first,InputIterator last,OutputIterator result); "
            + "template<class InputIterator,class OutputIterator,class BinaryOperation> OutputIterator adjacent_difference(InputIterator first,InputIterator last,OutputIterator result,BinaryOperation op);";
    }
    private static String containerRelations(String name, String parameters, String arguments) {
        String type = name + "<" + arguments + ">";
        StringBuilder s = new StringBuilder();
        for (String op : List.of("==", "!=", "<", ">", "<=", ">="))
            s.append("template<").append(parameters).append("> bool operator").append(op)
                .append("(const ").append(type).append("& x,const ").append(type).append("& y);");
        return s + "template<" + parameters + "> void swap(" + type + "& x," + type + "& y);";
    }
    /** C++03 20.2.1: opt-in relational operator templates; intentionally declaration-only. */
    private static String relOpsRelations() {
        StringBuilder result = new StringBuilder("namespace rel_ops {");
        for (String op : List.of("!=", ">", "<=", ">=")) {
            result.append("template<class T> bool operator").append(op)
                    .append("(const T& x, const T& y);");
        }
        return result.append("}").toString();
    }

    private static String pairRelations() {
        StringBuilder text = new StringBuilder();
        for (String op : List.of("==", "!=", "<", ">", "<=", ">="))
            text.append("template<class T1,class T2> bool operator").append(op).append("(const pair<T1,T2>& x,const pair<T1,T2>& y);");
        return text.toString();
    }

}
