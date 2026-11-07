/* C++03 C compatibility: declarations are not target macro values or implementations. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.StandardLibraryCatalogue;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class CCompatibilityFamiliesTest {
    @TempDir Path directory;
    private static String declarations(String header) {
        return StandardLibraryCatalogue.declarations(header, PlatformProfile.defaults());
    }
    private Project read(String source) throws Exception {
        Files.writeString(directory.resolve("main.cpp"), source);
        return Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
    }
    private static List<CallExpression> calls(Project project) {
        var result = new ArrayList<CallExpression>();
        project.files().getFirst().walk(new NodeVisitor() {
            @Override public void visit(CallExpression call) { result.add(call); }
        });
        return result;
    }
    @Test void allCSpellingsRecognizedWithNamespaceAndDependencyBoundaries() {
        for (String stem : List.of("assert", "ctype", "errno", "float", "iso646", "limits", "locale", "math", "setjmp", "signal", "stdarg", "stddef", "stdio", "stdlib", "string", "time", "wchar", "wctype")) {
            String cpp = "c" + stem, c = stem + ".h";
            assertTrue(StandardLibraryCatalogue.headers().containsAll(List.of(cpp, c)), stem);
            assertEquals(cpp, StandardLibraryCatalogue.canonicalHeader(c));
            assertTrue(declarations(cpp).contains("namespace std"), cpp);
            assertFalse(declarations(c).contains("namespace std"), c);
            assertTrue(StandardLibraryCatalogue.typeNames(c).stream().noneMatch(t -> t.startsWith("std::")), c);
            for (String dependency : StandardLibraryCatalogue.dependencies(c)) assertTrue(dependency.endsWith(".h"), c + ": " + dependency);
        }
    }
    @Test void macroOnlyHeadersDoNotInventObjectsOrFunctions() {
        for (String stem : List.of("assert", "errno", "float", "iso646", "limits")) {
            assertEquals("", declarations(stem + ".h"));
            assertTrue(StandardLibraryCatalogue.typeNames("c" + stem).isEmpty());
        }
        // setjmp and va_* are macros, not library function declarations.
        assertFalse(declarations("csetjmp").contains("int setjmp("));
        for (String macro : List.of("va_start", "va_arg", "va_end", "va_copy")) assertFalse(declarations("cstdarg").contains(macro));
        for (String header : List.of("cstdio", "cerrno", "ctime", "climits", "cfloat", "csignal"))
            for (String value : List.of("EOF =", "errno =", "CLOCKS_PER_SEC =", "INT_MAX =", "FLT_MAX =", "SIGABRT ="))
                assertFalse(declarations(header).contains(value), header + ": " + value);
    }
    @Test void c99AndLaterApisAreNotPresentedAsCpp03() {
        for (String header : List.of("cstdio", "cstdlib", "cmath", "cwchar", "cwctype", "cctype"))
            for (String name : List.of("snprintf(", "vsnprintf(", "strtoll(", "strtoull(", "llabs(", "wcstoll(", "wcstoull(", "hypot(", "round(", "isblank(", "iswblank("))
                assertFalse(declarations(header).contains(name), header + ": " + name);
    }
    @Test void scalarAndCharacterFamiliesResolveUnderBothSpellings() throws Exception {
        var examples = new LinkedHashMap<String,String>();
        examples.put("ctype", "int f(){return NSisdigit(48)+NStoupper(97);}");
        examples.put("stdlib", "long f(){return NSabs(-2L)+NSlabs(-3L);} int g(){return NSatoi(\"12\");}");
        examples.put("math", "double f(){return NSsqrt(4.0)+NSpow(2.0,3);} float g(){return NSfabs(-1.0f);}");
        examples.put("string", "int f(){return NSstrcmp(\"a\",\"b\");} const char* g(const char* p){return NSstrchr(p,97);} char* h(char* p){return NSstrchr(p,97);}");
        examples.put("stdio", "int f(){return NSprintf(\"%d\",1);}");
        examples.put("locale", "NSlconv* f(){return NSlocaleconv();}");
        examples.put("time", "NStime_t f(NStm* p){return NSmktime(p);}");
        examples.put("wchar", "NSsize_t f(const wchar_t* p){return NSwcslen(p);}");
        examples.put("wctype", "NSwctype_t f(){return NSwctype(\"alpha\");}");
        examples.put("signal", "int f(int n){return NSraise(n);}");
        examples.put("setjmp", "void f(NSjmp_buf b){NSlongjmp(b,1);}");
        examples.put("stdarg", "#include <cstdio>\nint f(const char* p, NSva_list a){return std::vprintf(p,a);}");
        for (var example : examples.entrySet()) for (boolean legacy : List.of(false, true)) {
            String header = legacy ? example.getKey()+".h" : "c"+example.getKey();
            // stdarg's consumer must use the same namespace as its va_list declaration.
            String body = example.getValue();
            if (legacy && example.getKey().equals("stdarg")) body = body.replace("<cstdio>", "<stdio.h>").replace("std::vprintf", "vprintf");
            var project = read("#include <"+header+">\n"+body.replace("NS", legacy ? "" : "std::"));
            assertFalse(project.hasErrors(), () -> header+": "+project.semanticDiagnostics()+" / "+project.parseProblems());
            assertFalse(calls(project).isEmpty(), header);
            for (var call : calls(project)) assertTrue(call.target().isPresent(), header+": "+call);
        }
    }
    @Test void mathAndIntegerOverloadsSelectDistinctParameterTypes() throws Exception {
        var project = read("#include <cmath>\n#include <cstdlib>\nvoid f(){std::abs(1);std::abs(1L);std::sqrt(1.0f);std::sqrt(1.0);std::sqrt(1.0L);std::pow(2.0,3);std::pow(2.0,3.0);}");
        assertFalse(project.hasErrors(), () -> project.semanticDiagnostics().toString());
        var calls = calls(project);
        assertEquals(List.of("int", "long", "float", "double", "long double", "double", "double"),
            calls.stream().map(c -> c.target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity()).toList());
        assertEquals("int", calls.get(5).target().orElseThrow().parameters().get(1).type().orElseThrow().canonicalIdentity());
        assertEquals("double", calls.get(6).target().orElseThrow().parameters().get(1).type().orElseThrow().canonicalIdentity());
        for (var call : calls) assertTrue(call.target().orElseThrow().body().isEmpty(), "Known declaration does not imply a modeled implementation");
    }
    @Test void absentTargetMacroDefinitionsRemainUnresolved() throws Exception {
        var project = read("#include <cstdio>\n#include <cerrno>\n#include <ctime>\n#include <climits>\n#include <cfloat>\nint f(){return CLOCKS_PER_SEC+INT_MAX+FLT_RADIX;}");
        var names = new ArrayList<NameExpression>();
        project.files().getFirst().walk(new NodeVisitor() {
            @Override public void visit(NameExpression name) { names.add(name); }
        });
        assertEquals(Set.of("CLOCKS_PER_SEC"), names.stream().map(NameExpression::name).collect(java.util.stream.Collectors.toSet()));
        for (var name : names) assertTrue(name.target().isEmpty(), name.name());
    }
    @Test void cstddefTypesResolveUsingTheSelectedProfile() throws Exception {
        for (boolean legacy : List.of(false, true)) {
            String ns = legacy ? "" : "std::";
            var project = read("#include <"+(legacy ? "stddef.h" : "cstddef")+">\n"+ns+"size_t f("+ns+"ptrdiff_t n){return n;}");
            assertFalse(project.hasErrors(), () -> project.semanticDiagnostics()+" / "+project.parseProblems());
        }
    }
    @Test void cppStringSearchOverloadsPreserveConstness() {
        for (String name : List.of("strchr", "strrchr")) {
            assertTrue(declarations("cstring").contains("const char* "+name+"(const char*, int)"));
            assertTrue(declarations("cstring").contains("char* "+name+"(char*, int)"));
        }
        for (String type : List.of("float", "double", "long double")) {
            assertTrue(declarations("cmath").contains(type+" pow("+type+",int)"));
            assertTrue(declarations("cmath").contains(type+" modf("+type+","+type+"*)"));
        }
    }
    @Test void implementationDefinedTypesRemainOpaqueInsteadOfGuessedLayouts() {
        assertTrue(declarations("cstdio").contains("__cpg_opaque_object_FILE"));
        assertTrue(declarations("cstdio").contains("__cpg_opaque_object_fpos_t"));
        assertTrue(declarations("ctime").contains("__cpg_opaque_arithmetic_time_t"));
        assertTrue(declarations("ctime").contains("__cpg_opaque_arithmetic_clock_t"));
        assertTrue(declarations("cstdarg").contains("__cpg_opaque_object_va_list"));
        assertTrue(declarations("csetjmp").contains("__cpg_opaque_object_jmp_buf"));
        assertTrue(declarations("csignal").contains("__cpg_opaque_integer_sig_atomic_t"));
        assertTrue(declarations("cwchar").contains("__cpg_opaque_object_mbstate_t"));
    }
}
