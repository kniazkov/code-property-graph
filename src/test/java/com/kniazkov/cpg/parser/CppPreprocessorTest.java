/* Code Property Graph: translation phase 4 regressions. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.build.Build;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;

class CppPreprocessorTest {
    @TempDir Path directory;
    private Build.TranslationUnit unit(List<Build.IncludePath> paths, List<Build.MacroOperation> macros) {
        return new Build.TranslationUnit(new Build.PhysicalSource(directory.resolve("main.cpp")), directory, paths, macros, "c++03");
    }
    private CppPreprocessor.Result preprocess(String source) { return CppPreprocessor.preprocess(unit(List.of(), List.of()), source); }
    private List<CppPreprocessor.ExpandedToken> significant(CppPreprocessor.Result result) {
        return result.tokens().stream().filter(t -> t.token().kind() != WHITESPACE && t.token().kind() != NEWLINE && t.token().kind() != EOF).toList();
    }
    private String text(CppPreprocessor.Result result) { return String.join(" ", significant(result).stream().map(t -> t.token().spelling()).toList()); }
    private String expand(String source) { return text(preprocess(source)); }
    private void failure(String source, String message) { assertTrue(assertThrows(ParseException.class, () -> preprocess(source)).reason().contains(message)); }
    private Path header(String path, String source) throws Exception {
        Path file = directory.resolve(path); Files.createDirectories(file.getParent()); Files.writeString(file, source); return file;
    }

    @Test void objectFunctionEmptyReplacementUndefAndInvocationDistinction() {
        assertEquals("7 3 ( ( 2 + 1 ) * ( 2 + 1 ) ) F 9", expand("""
                #define A 7
                #define F(x) ((x)*(x))
                #define EMPTY(x)
                #define ZERO() 9
                A EMPTY(hello) 3 F(2+1) F ZERO( /* empty parameter list */ )
                #undef A
                #define A 4
                """));
        assertEquals("A 4", expand("#define A 2\n#undef A\nA\n#define A 4\nA"));
        assertEquals("( x ) ( 8 )", expand("#define F/**/(x)\nF(8)"));
    }
    @Test void argumentsRespectNestedParenthesesAndCallsMaySpanNewlines() {
        assertEquals("( a , b ) + 3", expand("#define SUM(a,b) a+b\nSUM\n((a,b),\n3)"));
        assertEquals("2 + 2 + 2 + 2", expand("#define TWICE(x) x+x\nTWICE(TWICE(2))"));
        failure("#define F(a,b) a+b\nF(1)", "expects 2 arguments");
        failure("#define F(a) a\nF((1)", "Unterminated invocation");
    }
    @Test void argumentPrescanDoesNotUseFollowingTokensAndRescanDoes() {
        assertEquals("7", expand("#define F(x) x\n#define G F\nG(7)"));
        assertEquals("9", expand("#define ID(x) x\n#define G() 9\nID(G)()"));
        assertEquals("7 + 1", expand("#define F(x) x + 1\n#define ID(x) x\nID(F)(7)"));
        assertEquals("", expand("#define EMPTY\n#define F(x) x\nF(EMPTY)"));
    }
    @Test void recursiveMacrosTerminateAndDisabledTokensStayDisabled() {
        assertEquals("SELF A F ( 1 )", expand("#define SELF SELF\n#define A B\n#define B A\n#define F(x) F(x)\nSELF A F(1)"));
        // Historical [cpp.scope] example, including nested same-macro calls and
        // a replacement list that opens a call closed by subsequent source text.
        assertEquals("f ( 2 * ( y + 1 ) ) + f ( 2 * ( f ( 2 * ( z [ 0 ] ) ) ) ) % f ( 2 * ( 0 ) ) + t ( 1 ) ; f ( 2 * ( 2 + ( 3 , 4 ) - 0 , 1 ) ) | f ( 2 * ( ~ 5 ) ) & f ( 2 * ( 0 , 1 ) ) ^ m ( 0 , 1 ) ;", expand("""
                #define x 3
                #define f(a) f(x * (a))
                #undef x
                #define x 2
                #define g f
                #define z z[0]
                #define h g(~
                #define m(a) a(w)
                #define w 0,1
                #define t(a) a
                f(y+1) + f(f(z)) % t(t(g)(0) + t)(1);
                g(x+(3,4)-w) | h 5) & m(f)^m(m);
                """));
    }
    @Test void stringizationUsesRawSpellingAndCollapsesOnlyWhitespace() {
        assertEquals("\"A + A\" \"7 + 7\" \"a+b\" \"and <: \\\\u03b1\"".replace("\\\\u", "\\u"), expand("""
                #define A 7
                #define STR(x) #x
                #define XSTR(x) STR(x)
                STR( A /**/ +   A ) XSTR(A + A) STR(a+b) STR(and <: \u03b1)
                """.replace("α", "\\u03b1")));
        assertEquals("\"\\\"a\\\\b\\\" '\\\"'\"", expand("#define S(x) #x\nS(\"a\\b\" '\"')"));
    }
    @Test void pasteUsesRawArgumentsThenRescansAndNeverPastesAnArgumentHashHash() {
        assertEquals("42 7 9 x ## y", expand("""
                #define A foo
                #define Abar 42
                #define foobar 7
                #define CAT(a,b) a##b
                #define XCAT(a,b) CAT(a,b)
                #define OBJECT a##b
                #define ab 9
                #define ID(x) x
                CAT(A,bar) XCAT(A,bar) OBJECT ID(x ## y)
                """));
        assertEquals("prefix tail", expand("#define CAT(a,b) a ## b\nCAT(pre,fix tail)"));
        assertEquals("L\"x\" 1e+2", expand("#define CAT(a,b) a ## b\nCAT(L,\"x\") CAT(1e+,2)"));
        failure("#define CAT(a,b) a##b\nCAT(+,*)", "Invalid token paste");
        failure("#define CAT(a,b) a##b\nCAT(/,*)", "Invalid token paste");
    }
    @Test void rejectsMalformedAndNonCpp03MacroForms() {
        failure("#define F(x,x) x", "Duplicate macro parameter");
        failure("#define F(...) 1", "variadic macros");
        failure("#define F(x) #other", "# must precede");
        failure("#define F ##x", "## cannot begin");
        failure("#define F(x) x##", "## cannot begin");
        failure("#define F(x) x\nF()", "Empty macro argument");
        failure("#define F(a,b) a##b\nF(,b)", "Empty macro argument");
        failure("#define and 7", "Expected macro name");
        failure("#define A 1\n#define A 2", "Incompatible redefinition");
        assertEquals("1", expand("#define A 1\n#define A 1\nA"));
    }
    @Test void buildMacrosAreOrderedAndTranslationUnitsAreIndependent() {
        var build = unit(List.of(), List.of(new Build.MacroOperation(true, "VALUE=1"), new Build.MacroOperation(false, "VALUE"), new Build.MacroOperation(true, "VALUE=3"), new Build.MacroOperation(true, "FLAG"), new Build.MacroOperation(true, "F(x)=x+x")));
        assertEquals("3 1 2 + 2", text(CppPreprocessor.preprocess(build, "VALUE FLAG F(2)")));
        assertEquals("VALUE FLAG F ( 2 )", expand("VALUE FLAG F(2)"));
    }
    @Test void includesFollowQuoteIquoteIAndSystemSearchOrder() throws Exception {
        header("same.h", "local\n"); header("quote/same.h", "quote\n"); header("user/same.h", "user\n"); header("system/same.h", "system\n");
        var build = unit(List.of(new Build.IncludePath("-isystem", directory.resolve("system")), new Build.IncludePath("-I", directory.resolve("user")), new Build.IncludePath("-iquote", directory.resolve("quote"))), List.of());
        assertEquals("local user", text(CppPreprocessor.preprocess(build, "#include \"same.h\"\n#include <same.h>\n")));
        Files.delete(directory.resolve("same.h"));
        assertEquals("quote", text(CppPreprocessor.preprocess(build, "#include \"same.h\"")));
        Files.delete(directory.resolve("user/same.h"));
        assertEquals("system", text(CppPreprocessor.preprocess(build, "#include <same.h>")));
    }
    @Test void macroIncludesAndGuardedCyclesExecuteInTheCurrentContext() throws Exception {
        header("a.h", "#ifndef A_H\n#define A_H\n#include \"b.h\"\n#define SQUARE(x) ((x)*(x))\n#endif\n");
        header("b.h", "#ifndef B_H\n#define B_H\n#include \"a.h\"\n#endif\n");
        var result = preprocess("#define HEADER \"a.h\"\n#include HEADER\n#include \"a.h\"\nSQUARE(3)");
        assertEquals("( ( 3 ) * ( 3 ) )", text(result));
        assertEquals(5, result.includes().size());
        header("context.h", "#ifdef SELECT\nfirst\n#else\nsecond\n#endif\n");
        assertEquals("second first", expand("#include \"context.h\"\n#define SELECT\n#include \"context.h\""));
        header("user/angle.h", "angle\n");
        assertEquals("angle", text(CppPreprocessor.preprocess(unit(List.of(new Build.IncludePath("-I", directory.resolve("user"))), List.of()), "#define ANGLE <angle.h>\n#include ANGLE")));
    }
    @Test void macroHeaderNamesRetainInteriorSpaces() throws Exception {
        header("user/a b.h", "found\n");
        assertEquals("found", text(CppPreprocessor.preprocess(unit(List.of(new Build.IncludePath("-I", directory.resolve("user"))), List.of()), "#define HEADER <a b.h>\n#include HEADER")));
    }
    @Test void phasesOneThroughThreeRunBeforeEveryHeaderAndMacroDirective() throws Exception {
        header("defs.h", "??=define VA??/\nLUE 9\r\n");
        assertEquals("9", expand("??=include \"defs.h\"\r\nVA??/\nLUE"));
        assertEquals("# include < iostream >", expand("#define HASH #\nHASH include <iostream>"));
    }
    @Test void missingHeadersAndUnguardedCyclesGiveIncludeChains() throws Exception {
        header("a.h", "#include \"b.h\"\n"); header("b.h", "#include <missing.h>\n");
        ParseException failure = assertThrows(ParseException.class, () -> preprocess("#include \"a.h\""));
        assertTrue(failure.reason().contains("Header not found: missing.h"));
        assertTrue(failure.reason().contains("main.cpp -> " + directory.resolve("a.h") + " -> " + directory.resolve("b.h")));
        assertEquals(directory.resolve("b.h").toString(), failure.file());
        header("loop.h", "#include \"loop.h\"\n");
        failure("#include \"loop.h\"", "Include nesting limit");
    }
    @Test void onlyExplicitlyModeledMissingSystemHeadersRemainOpaque() throws Exception {
        var token = significant(preprocess("#include <iostream>")).getFirst();
        assertEquals(HEADER_NAME, token.token().kind());
        assertEquals("<iostream>", token.token().spelling());
        failure("#include <arbitrary_missing_header>", "Header not found");
        header("iostream", "custom\n");
        assertEquals("custom", text(CppPreprocessor.preprocess(unit(List.of(new Build.IncludePath("-I", directory)), List.of()), "#include <iostream>")));
    }
    @Test void conditionalGuardsCheckStructureAndSkipInactiveContent() {
        assertEquals("yes", expand("#ifndef MISSING\nyes\n#else\n#include <absent>\n#if unsupported expression\nno\n#endif\n#endif"));
        failure("#else", "Unmatched"); failure("#endif", "Unmatched"); failure("#ifdef X\n", "Unterminated conditional");
        failure("#if 1\nx\n#endif", "#if expressions");
    }
    @Test void inactiveNestedConditionalsIgnoreOperandsButKeepTheirStructure() {
        assertEquals("yes", expand("""
                #ifdef MISSING
                #ifdef NOT A VALID IDENTIFIER LIST
                bad
                #else ignored operands
                bad
                #endif ignored operands
                #ifndef
                bad
                #endif
                #else
                yes
                #endif
                """));
        failure("#ifdef NOT A VALID IDENTIFIER LIST\n#endif", "Expected one macro name");
        failure("#ifndef\n#endif", "Expected one macro name");
        failure("#ifdef MISSING\n#else extra\n#endif", "Unmatched or malformed #else");
        failure("#ifdef MISSING\n#endif extra", "Unmatched or malformed #endif");
        failure("#ifdef MISSING\n#ifdef X\n#else\n#else\n#endif\n#endif", "Unmatched or malformed #else");
    }
    @Test void preservesPhysicalSpellingInvocationAndIncludeOccurrenceOrigins() throws Exception {
        Path path = header("defs.h", "#define MUL(x) ((x)*(x))\n#define OUT(x) MUL(x)\n");
        String source = "#include \"defs.h\"\nOUT(7)\n";
        var result = preprocess(source);
        var multiplication = significant(result).stream().filter(t -> t.token().spelling().equals("*")).findFirst().orElseThrow();
        assertEquals(path.toString(), multiplication.token().range().file());
        assertEquals(1, multiplication.token().range().start().line());
        assertEquals(path, multiplication.include().source().path());
        assertEquals(directory.resolve("main.cpp").toString(), multiplication.expansionRange().file());
        assertEquals(2, multiplication.expansionRange().start().line());
        assertEquals("OUT(7)", source.substring(multiplication.expansionRange().start().offset(), multiplication.expansionRange().end().offset()));
        assertEquals(List.of("OUT", "MUL"), multiplication.origins().stream().map(CppPreprocessor.MacroExpansion::name).toList());
        var sevens = significant(result).stream().filter(t -> t.token().spelling().equals("7")).toList();
        assertEquals(2, sevens.size()); assertEquals(sevens.get(0).token().range(), sevens.get(1).token().range());
        assertEquals(directory.resolve("main.cpp").toString(), sevens.get(0).token().range().file());
        assertThrows(UnsupportedOperationException.class, () -> result.tokens().clear());
    }
}
