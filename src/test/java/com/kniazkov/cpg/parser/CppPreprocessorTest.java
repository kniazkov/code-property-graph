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

    @Test void syntheticSourceNamesArePortableAndRemainSeparateFromPhysicalSources() {
        var build = unit(List.of(), List.of(new Build.MacroOperation(true, "VALUE=7")));
        var result = CppPreprocessor.preprocess(build, """
                #include <cassert>
                #include <cstddef>
                #include <cmath>
                #include <ctime>
                #include <cwchar>
                #include <cerrno>
                #include <cstdlib>
                #include <climits>
                #include <cfloat>
                #include <csignal>
                VALUE __cplusplus
                """);
        assertTrue(result.definitions().stream().anyMatch(d -> d.originKind().equals("predefined")));
        assertTrue(result.definitions().stream().anyMatch(d -> d.originKind().equals("command-line")));
        assertTrue(result.definitions().stream().anyMatch(d -> d.originKind().equals("library")));
        for (var definition : result.definitions()) {
            Path path = Path.of(definition.range().file());
            String name = path.getFileName().toString();
            assertTrue(name.startsWith(".cpg-"), name);
            assertFalse(name.chars().anyMatch(c -> c < 32 || "<>:\"/\\|?*".indexOf(c) >= 0), name);
            assertNotEquals(build.source().path(), path);
            assertFalse(result.sources().containsKey(path.toString()));
            assertFalse(Files.exists(path));
        }
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
    @Test void buildMacroOriginsUseDistinctSyntheticBuffersAndPhysicalInvocations() {
        var build = unit(List.of(), List.of(new Build.MacroOperation(true, "VALUE=1"),
                new Build.MacroOperation(false, "VALUE"), new Build.MacroOperation(true, "VALUE=3"),
                new Build.MacroOperation(true, "FLAG")));
        var result = CppPreprocessor.preprocess(build, "VALUE FLAG");
        assertEquals("3 1", text(result));
        var value = result.expansions().getFirst();
        var flag = result.expansions().getLast();
        String physical = build.source().path().toString();
        assertEquals(directory.resolve(".cpg-command-line-3").toString(), value.definition().file());
        assertEquals(directory.resolve(".cpg-command-line-4").toString(), flag.definition().file());
        for (var event : result.expansions()) {
            assertNotEquals(physical, event.definition().file());
            assertEquals(physical, event.invocation().file());
            assertEquals(result.includes().getFirst(), event.include());
            assertTrue(event.replacement().stream().allMatch(f -> f.range().file().equals(event.definition().file())));
        }
        assertEquals("VALUE", "VALUE FLAG".substring(value.invocation().start().offset(), value.invocation().end().offset()));
        assertEquals(value.definition().file(), significant(result).getFirst().token().range().file());
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
        assertEquals("x", expand("#if 1\nx\n#endif"));
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
        assertEquals(result.includes().getFirst(), result.expansions().getLast().include());
        assertEquals(List.of("OUT", "MUL"), multiplication.origins().stream().map(CppPreprocessor.MacroExpansion::name).toList());
        var sevens = significant(result).stream().filter(t -> t.token().spelling().equals("7")).toList();
        assertEquals(2, sevens.size()); assertEquals(sevens.get(0).token().range(), sevens.get(1).token().range());
        assertEquals(directory.resolve("main.cpp").toString(), sevens.get(0).token().range().file());
        assertThrows(UnsupportedOperationException.class, () -> result.tokens().clear());
    }
    @Test void expansionEventsHaveStableIdentityAcrossRepeatedCallsAndRedefinitions() {
        String source = "#define A 1\nA A\n#undef A\n#define A 2\nA";
        var result = preprocess(source);
        var events = result.expansions();
        assertEquals(List.of(1L, 2L, 3L), events.stream().map(CppPreprocessor.ExpansionEvent::id).toList());
        assertEquals(events, preprocess(source).expansions());
        assertNotEquals(events.get(0).invocation(), events.get(1).invocation());
        assertEquals(events.get(0).definition(), events.get(1).definition());
        assertNotEquals(events.get(0).definition(), events.get(2).definition());
        assertEquals(List.of("1", "1", "2"), events.stream().map(e -> e.replacement().getFirst().spelling()).toList());
        assertEquals(List.of(List.of(1L), List.of(2L), List.of(3L)), significant(result).stream().map(CppPreprocessor.ExpandedToken::expansionIds).toList());
        assertEquals("A 1", source.substring(events.getFirst().definition().start().offset(), events.getFirst().definition().end().offset()));
    }

    @Test void eventParentsFollowPrescanAndRescanWithoutDuplicatingReusedArguments() {
        var result = preprocess("#define A 7\n#define F(x) x+x\n#define G F\nG(A)");
        assertEquals("7 + 7", text(result));
        var events = result.expansions();
        assertEquals(List.of("G", "F", "A"), events.stream().map(CppPreprocessor.ExpansionEvent::name).toList());
        assertNull(events.get(0).parentId());
        assertEquals(1L, events.get(1).parentId());
        assertEquals(2L, events.get(2).parentId());
        var sevens = significant(result).stream().filter(t -> t.token().spelling().equals("7")).toList();
        assertEquals(List.of(1L, 2L, 3L), sevens.getFirst().expansionIds());
        assertEquals(sevens.getFirst().expansionIds(), sevens.getLast().expansionIds());
        assertEquals(sevens.getFirst().token().range(), sevens.getLast().token().range());
        assertEquals("A", events.get(1).arguments().getFirst().getFirst().spelling());
        for (var event : events) if (event.parentId() != null) assertTrue(event.parentId() < event.id());
        var nested = preprocess("#define A 7\n#define F(x) x\n#define O F(A)\nO").expansions();
        assertEquals(List.of("O", "F", "A"), nested.stream().map(CppPreprocessor.ExpansionEvent::name).toList());
        assertEquals(2L, nested.getLast().parentId());
    }

    @Test void stringizeAndChainedPasteKeepAllPhysicalParticipants() {
        var result = preprocess("#define STR(x) #x\n#define CAT(a,b,c) a##b##c\nSTR(hello + world) CAT(pre,fix,ed)");
        assertEquals("\"hello + world\" prefixed", text(result));
        var stringize = result.expansions().get(0).operations().getFirst();
        assertEquals("stringize", stringize.kind());
        assertEquals(List.of("hello", " ", "+", " ", "world"), stringize.fragments().stream().map(CppPreprocessor.Fragment::spelling).toList());
        assertEquals(1, stringize.operator().start().line());
        var pastes = result.expansions().get(1).operations();
        assertEquals(2, pastes.size());
        assertEquals(List.of("pre", "fix", "ed"), pastes.getLast().fragments().stream().map(CppPreprocessor.Fragment::spelling).toList());
        assertEquals("prefixed", pastes.getLast().spelling());
        assertNotEquals(pastes.getFirst().operator(), pastes.getLast().operator());
        assertTrue(pastes.getLast().fragments().stream().allMatch(f -> f.range().start().line() == 3));
    }

    @Test void boundariesCoverPartialExpressionsMultiStatementsAndEmptyInvocations() {
        var result = preprocess("#define OP +\n#define BOTH a();b();\n#define EMPTY\nx OP y; EMPTY BOTH EMPTY");
        assertEquals("x + y ; a ( ) ; b ( ) ;", text(result));
        var events = result.expansions();
        assertEquals(List.of("OP", "EMPTY", "BOTH", "EMPTY"), events.stream().map(CppPreprocessor.ExpansionEvent::name).toList());
        var op = events.get(0);
        assertEquals(op.startToken() + 1, op.endToken());
        assertEquals("+", result.tokens().get(op.startToken()).token().spelling());
        var empty = events.get(1);
        assertEquals(empty.startToken(), empty.endToken());
        var both = events.get(2);
        assertTrue(empty.startToken() <= both.startToken());
        assertEquals("a();b();", String.join("", result.tokens().subList(both.startToken(), both.endToken()).stream().map(t -> t.token().spelling()).toList()));
        var trailing = events.get(3);
        assertEquals(trailing.startToken(), trailing.endToken());
        assertTrue(trailing.startToken() >= both.endToken());
        assertTrue(trailing.endToken() < result.tokens().size());
    }

    @Test void nestedEmptyExpansionsRetainAnchorsAndDoNotInventWhitespace() {
        var result = preprocess("#define E\n#define ID(x) x\n#define S(x) #x\n#define X(x) S(x)\nID(E) X(a+b)");
        assertEquals("\"a+b\"", text(result));
        assertEquals(List.of("ID", "E", "X", "S"), result.expansions().stream().map(CppPreprocessor.ExpansionEvent::name).toList());
        for (var event : result.expansions().subList(0, 2)) {
            assertEquals(0, event.startToken()); assertEquals(0, event.endToken());
        }
    }

    @Test void includeOperandEventsAreAnchoredAndDefinitionIncludesAreDistinct() throws Exception {
        Path definitions = header("defs.h", "#define A 1\n");
        var result = preprocess("#define H \"defs.h\"\n#include H\nA\n#undef A\n#include H\nA");
        assertEquals("1 1", text(result));
        assertEquals(List.of("H", "A", "H", "A"), result.expansions().stream().map(CppPreprocessor.ExpansionEvent::name).toList());
        assertEquals(0, result.expansions().getFirst().startToken());
        assertEquals(0, result.expansions().getFirst().endToken());
        var first = result.expansions().get(1).replacement().getFirst();
        var second = result.expansions().get(3).replacement().getFirst();
        assertEquals(definitions.toString(), first.range().file());
        assertEquals(first.range(), second.range());
        assertNotEquals(first.include(), second.include());
    }

    @Test void provenanceCollectionsAreDeeplyImmutable() {
        var result = preprocess("#define F(x) #x\nF(hello)");
        var event = result.expansions().getFirst();
        var padded = preprocess("#define F(x) #x\nF( hello )").expansions().getFirst();
        assertEquals(List.of(" ", "hello", " "), padded.arguments().getFirst().stream().map(CppPreprocessor.Fragment::spelling).toList());
        assertThrows(UnsupportedOperationException.class, () -> result.expansions().clear());
        assertThrows(UnsupportedOperationException.class, () -> event.arguments().clear());
        assertThrows(UnsupportedOperationException.class, () -> event.arguments().getFirst().clear());
        assertThrows(UnsupportedOperationException.class, () -> event.replacement().clear());
        assertThrows(UnsupportedOperationException.class, () -> event.operations().clear());
        assertThrows(UnsupportedOperationException.class, () -> event.operations().getFirst().fragments().clear());
        assertThrows(UnsupportedOperationException.class, () -> significant(result).getFirst().expansionIds().clear());
    }

    @Test void conditionsExpandMacrosButPresenceChecksDoNotExpandValues() {
        var result = preprocess("""
                #define UNUSED(x) x
                #define RETRY_LIMIT 7
                #if defined(UNUSED) && RETRY_LIMIT > 3
                selected
                #elif RETRY_LIMIT / 0
                wrong
                #else
                wrong
                #endif
                #ifndef ABSENT
                other
                #endif
                """);
        assertEquals("selected other", text(result));
        assertEquals(List.of("RETRY_LIMIT"), result.expansions().stream().map(CppPreprocessor.ExpansionEvent::name).toList());
        assertEquals(List.of("UNUSED", "ABSENT"), result.presenceChecks().stream().map(CppPreprocessor.PresenceCheck::name).toList());
        assertTrue(result.presenceChecks().getFirst().present());
        assertTrue(result.presenceChecks().getLast().conditionResult());
        assertFalse(result.branches().get(1).evaluated());
        assertEquals(result.definitions().stream().filter(d -> d.name().equals("RETRY_LIMIT")).findFirst().orElseThrow().id(),
                result.expansions().getFirst().definitionId());
    }
    @Test void skippedGroupsRetainMalformedSyntaxWithoutExpandingOrIncluding() {
        var result = preprocess("""
                #if 0
                #if broken ( / 0
                #include "absent.h"
                #define NOT_EXECUTED 8
                #elif defined(
                #endif nonsense
                #else
                good
                #endif
                """);
        assertEquals("good", text(result));
        assertTrue(result.expansions().isEmpty()); assertTrue(result.presenceChecks().isEmpty());
        assertEquals(1, result.includes().size());
        assertTrue(result.definitions().stream().noneMatch(d -> d.name().equals("NOT_EXECUTED")));
        assertTrue(result.branches().getFirst().rawSource().contains("#include \"absent.h\""));
        assertFalse(result.branches().get(1).evaluated());
        failure("#if 0\n/* unterminated", "comment");
    }
    @Test void branchesRetainPartialExpressionsAndOccurrenceSpecificActivity() throws Exception {
        header("part.h", "#if PICK\n+ 2\n#else\n* 3\n#endif\n");
        var result = preprocess("#define PICK 1\n1\n#include \"part.h\"\n#undef PICK\n#define PICK 0\n1\n#include \"part.h\"\n");
        assertEquals("1 + 2 1 * 3", text(result));
        assertEquals(4, result.branches().size());
        var first = result.branches().getFirst(); var second = result.branches().get(2);
        assertTrue(first.active()); assertFalse(second.active());
        assertNotEquals(first.include().occurrence(), second.include().occurrence());
        assertTrue(first.rawSource().contains("+ 2"));
        assertEquals(first.rawSource(), result.sources().get(first.range().file()).substring(first.range().start().offset(), first.range().end().offset()));
        assertTrue(first.endToken() > first.startToken());
        assertEquals(second.startToken(), second.endToken());
    }
    @Test void definitionsKeepUnusedRedefinedAndRawReplacementAndFinalState() {
        var result = preprocess("#define RETRY_LIMIT 7\n#define X(a, b) a  /* keep */ + b\n#undef RETRY_LIMIT\n#define RETRY_LIMIT 9\n");
        var retry = result.definitions().stream().filter(d -> d.name().equals("RETRY_LIMIT")).toList();
        assertEquals(2, retry.size()); assertNotEquals(retry.get(0).id(), retry.get(1).id());
        var x = result.definitions().stream().filter(d -> d.name().equals("X")).findFirst().orElseThrow();
        assertTrue(x.functionLike()); assertEquals(List.of("a", "b"), x.parameters());
        assertEquals("a  /* keep */ + b", x.rawReplacement());
        assertEquals(retry.get(1).id(), result.includeStates().getLast().activeDefinitions().get("RETRY_LIMIT"));
        assertTrue(result.expansions().isEmpty());
    }
    @Test void lineControlAndBuiltinsKeepLogicalCoordinatesSeparateFromPhysicalRanges() {
        var result = preprocess("#define LOCATION __LINE__\n#line 41 \"logical.cpp\"\nLOCATION __FILE__ __cplusplus\n__LINE__\n");
        assertEquals("41 \"logical.cpp\" 199711L 42", text(result));
        assertTrue(result.tokens().stream().filter(t -> t.token().spelling().equals("41")).findFirst().orElseThrow().expansionRange().file().endsWith("main.cpp"));
        assertTrue(result.logicalLocations().stream().anyMatch(l -> l.file().equals("logical.cpp") && l.line() == 41));
        assertEquals("ok", expand("#if __cplusplus == 199711L && defined(__LINE__) && !defined(__STDC__)\nok\n#endif"));
        failure("#line 0\nx", "1..32767"); failure("#line 32768\nx", "1..32767");
        failure("#define __LINE__ 2", "undefined behavior"); failure("#undef defined", "undefined behavior");
    }
    @Test void dateAndTimeAreTranslationTimeConstantsAndUnusedCatalogueIsStable() {
        var unit = unit(List.of(), List.of());
        var time = java.time.Instant.parse("2020-01-02T03:04:05Z");
        var result = CppPreprocessor.preprocess(unit, "__DATE__ __TIME__", null, time);
        assertEquals("\"Jan  2 2020\" \"03:04:05\"", text(result));
        var early = CppPreprocessor.preprocess(unit, "plain", null, time);
        var later = CppPreprocessor.preprocess(unit, "plain", null, time.plusSeconds(86400));
        assertEquals(early.definitions(), later.definitions());
    }
    @Test void nullPragmaErrorAndElifStructuresFollowCpp03() {
        assertEquals("kept", expand("#\n#pragma arbitrary tokens (\n#if 0\n#error not reached\n#elif 2\nkept\n#else\nwrong\n#endif"));
        failure("#error hello world", "#error hello world");
        failure("#elif 1", "Unmatched");
        failure("#if 0\n#else\n#elif 1\n#endif", "Unmatched");
        failure("#if defined()\n#endif", "Expected macro name");
        failure("#define D defined\n#if D(X)\n#endif", "Macro-generated defined");
        assertEquals("yes", expand("#if 1 || 1/0\nyes\n#endif"));
        assertEquals("yes", expand("#if 0 ? 1/0 : 4\nyes\n#endif"));
    }

    @Test void lineControlCountsPhysicalNewlinesAndRestoresParentContext() throws Exception {
        header("logical.h", "#line 80 \"header-name\"\n__LINE__ __FILE__\n");
        var result = preprocess("#line 20 \"parent-name\"\r#include \"logical.h\"\r__LINE__ __FILE__\r");
        assertEquals("80 \"header-name\" 21 \"parent-name\"", text(result));
        assertEquals("3", expand("\n\\\n__LINE__"));
        var escaped = significant(preprocess("#line 10 \"a\\nb\"\n__FILE__")).getFirst().token();
        assertFalse(escaped.spelling().contains("\n"));
        assertEquals(STRING_LITERAL, CppLexer.scan("quoted.cpp", escaped.spelling()).tokens().getFirst().kind());
        var error = assertThrows(ParseException.class, () -> preprocess("#line 72 \"presumed.cpp\"\n#error stop\n"));
        assertEquals("presumed.cpp", error.file()); assertEquals(72, error.line());
        var expressionError = assertThrows(ParseException.class, () -> preprocess("#line 72 \"presumed.cpp\"\n#if 1/0\n#endif\n"));
        assertEquals("presumed.cpp", expressionError.file()); assertEquals(72, expressionError.line());
        var macroError = assertThrows(ParseException.class, () -> preprocess("#define ZERO 1/0\n#line 90 \"presumed.cpp\"\n#if ZERO\n#endif\n"));
        assertEquals("presumed.cpp", macroError.file()); assertEquals(90, macroError.line());
        assertEquals("\"a\\\\b\"", expand("#line 4 \"a\\\\b\"\n__FILE__"));
    }
    @Test void commandLineDefinitionsKeepOperationOrdinalsAndPresenceLinks() {
        var result = CppPreprocessor.preprocess(unit(List.of(), List.of(
                new Build.MacroOperation(true, "P=1"), new Build.MacroOperation(false, "P"),
                new Build.MacroOperation(true, "P=  2 /* raw */ + 3"))), "#ifdef P\nP\n#endif");
        var definitions = result.definitions().stream().filter(d -> d.name().equals("P")).toList();
        assertEquals(List.of("1", "3"), definitions.stream().map(CppPreprocessor.Definition::sourceKey).toList());
        assertEquals("2 /* raw */ + 3", definitions.getLast().rawReplacement());
        assertEquals(definitions.getLast().id(), result.presenceChecks().getFirst().definitionId());
        assertEquals(definitions.getLast().id(), result.expansions().getFirst().definitionId());
    }

    @Test void conditionalMacroExpansionRetainsWhitespaceForStringizationAndFragments() {
        var result = preprocess("#define DROP(x) 1\n#define MAKE(x) DROP(#x)\n#if MAKE(a b) && defined ( MAKE )\nyes\n#endif");
        assertEquals("yes", text(result));
        var make = result.expansions().stream().filter(e -> e.name().equals("MAKE")).findFirst().orElseThrow();
        assertEquals("\"a b\"", make.operations().getFirst().spelling());
        assertTrue(make.arguments().getFirst().stream().anyMatch(f -> f.spelling().equals(" ")));
    }

}
