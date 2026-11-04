/* Code Property Graph: C++03 lexical phase tests. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.Language;
import com.kniazkov.cpg.model.SourcePosition;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;

class CppLexerTest {
    private static CppLexer.Result scan(String source) { return CppLexer.scan("test.cpp", source); }
    private static List<PreprocessingToken> significant(String source) {
        return scan(source).tokens().stream().filter(t -> t.kind() != WHITESPACE && t.kind() != NEWLINE && t.kind() != EOF).toList();
    }
    private static List<String> spellings(String source) { return significant(source).stream().map(PreprocessingToken::spelling).toList(); }

    @Test void replacesAllTrigraphsBeforeSplicingAndPreservesPhysicalRanges() {
        var result = scan("a??/\r\nb ??= ??' ??( ??) ??! ??< ??> ??-\r\n");
        assertEquals("ab # ^ [ ] | { } ~\n", result.text());
        var first = result.tokens().getFirst();
        assertEquals("ab", first.spelling());
        assertEquals(new SourcePosition(1, 1, 0), first.range().start());
        assertEquals(new SourcePosition(2, 2, 7), first.range().end());
        assertEquals(new SourcePosition(2, 1, 6), result.characterRanges().get(1).start());
        var hash = significant("??=").getFirst();
        assertEquals(3, hash.range().end().offset());
        assertEquals(3, scan("??=").characterRanges().getFirst().end().offset());
        assertEquals(new SourcePosition(3, 1, 41), result.eof());
    }

    @Test void splicingIsSinglePassAndTrigraphRecognitionIsNotRepeated() {
        assertEquals("\\\nx", scan("\\\\\n\nx").text());
        assertEquals("??=", scan("?\\\n?=").text());
        assertEquals(List.of("?", "?", "="), spellings("?\\\n?="));
        assertEquals(List.of("ab"), spellings("a\\\nb"));
        assertEquals(List.of("a"), spellings("a\\\n"));
        assertEquals(new SourcePosition(2, 1, 3), scan("a\\\n").tokens().getLast().range().start());
    }

    @Test void commentsAreSpacesWithoutMergingTokensOrEndingDirectives() {
        var result = scan("# /*two\nlines*/ include <x>\na/**/b 1/**/e+2\n//continued??/\n@\nz");
        assertEquals("#   include <x>\na b 1 e+2\n \nz", result.text());
        assertEquals(HEADER_NAME, result.tokens().stream().filter(t -> t.spelling().equals("<x>")).findFirst().orElseThrow().kind());
        assertEquals(List.of("a", "b", "1", "e", "+", "2"), spellings("a/**/b 1/**/e+2"));
        var comment = scan("a/* x\ny */b").tokens().get(1);
        assertEquals(" ", comment.spelling());
        assertEquals(1, comment.range().start().offset());
        assertEquals(10, comment.range().end().offset());
        assertEquals(1, comment.logicalEnd() - comment.logicalStart());
    }

    @Test void maximalMunchUsesCpp03PreprocessingNumberAndOperatorGrammar() {
        assertEquals(List.of("1Ex", "1E+foo", ".5", "0xFE", "012UL", "1.2.3", "1p", "+", "2", "0x1p", "-", "2"),
                spellings("1Ex 1E+foo .5 0xFE 012UL 1.2.3 1p+2 0x1p-2"));
        assertEquals(List.of("x", "++", "++", "+", "y", ">>=", "->*", ".*", "...", "%:%:", "<:", ":", "X", ">", ">>"),
                spellings("x+++++y >>= ->* .* ... %:%: <::X> >>"));
        assertTrue(significant("1Ex .5 1E+foo").stream().allMatch(t -> t.kind() == PP_NUMBER));
    }

    @Test void alternativesKeepSpellingForStringization() {
        var tokens = significant("<% %> <: :> %: %:%: and and_eq bitand bitor compl not not_eq or or_eq xor xor_eq");
        assertEquals(List.of("{", "}", "[", "]", "#", "##", "&&", "&=", "&", "|", "~", "!", "!=", "||", "|=", "^", "^="),
                tokens.stream().map(PreprocessingToken::canonicalSpelling).toList());
        assertTrue(tokens.stream().allMatch(t -> t.kind() == OPERATOR));
        assertEquals("<%", tokens.getFirst().spelling());
        assertEquals(IDENTIFIER, significant("android").getFirst().kind());
        assertEquals(List.of(OPERATOR, OPERATOR, IDENTIFIER, IDENTIFIER), significant("new delete class int").stream().map(PreprocessingToken::kind).toList());
    }

    @Test void headersExistOnlyInIncludeContextAndDoNotInterpretCommentMarkers() {
        assertEquals(List.of(HEADER_NAME, HEADER_NAME), scan("#include <a/*b>\n%: include \"a//b\"\n").tokens().stream()
                .filter(t -> t.kind() == HEADER_NAME).map(PreprocessingToken::kind).toList());
        assertEquals(List.of("a", "<", "b", ">", "\"x\""), spellings("a <b> \"x\""));
        assertEquals(STRING_LITERAL, significant("#define X \"x\"").getLast().kind());
        assertEquals(List.of("#", "include", "MACRO", "<", "x", ">"), spellings("#include MACRO <x>"));
        assertFalse(scan("x #include <x>").tokens().stream().anyMatch(t -> t.kind() == HEADER_NAME));
    }

    @Test void ordinaryAndWideLiteralsAreSingleTokensWithoutPhaseFiveEvaluation() {
        var tokens = significant("'a' L'\\n' \"a\\\"/*b*/\" L\"c\" \"d\" u8\"modern\" 12foo");
        assertEquals(List.of(CHARACTER_LITERAL, CHARACTER_LITERAL, STRING_LITERAL, STRING_LITERAL,
                STRING_LITERAL, IDENTIFIER, STRING_LITERAL, PP_NUMBER), tokens.stream().map(PreprocessingToken::kind).toList());
        assertEquals("\"a\\\"/*b*/\"", tokens.get(2).spelling());
        assertEquals("\"ab\"", significant("\"a??/\nb\"").getFirst().spelling());
    }

    @Test void unicodeAndUcnIdentifiersUseCpp03AnnexEInsteadOfJavaOrModernUnicodeRules() {
        var tokens = significant("caf\\u00e9 café \\U000003b1 _name42 1\\u00e9");
        assertEquals(List.of("café", "café", "α", "_name42", "1\\u00e9"), tokens.stream().map(PreprocessingToken::canonicalSpelling).toList());
        assertEquals("caf\\u00e9", tokens.getFirst().spelling());
        assertEquals(PP_NUMBER, tokens.getLast().kind());
        assertEquals(PP_NUMBER, significant("1\\u0300").getFirst().kind());
        for (String bad : List.of("\\u0061", "\\u0024", "\\uD800", "\\U00110000", "\\u0300", "\\u2004", "\\U0001F600", "\\u12", "\\u12xz"))
            assertThrows(ParseException.class, () -> scan(bad), bad);
        assertEquals(OTHER, significant("$").getFirst().kind());
        assertEquals(IDENTIFIER, significant("\u0384").getFirst().kind());
        assertEquals(OTHER, significant("\u0300").getFirst().kind());
        assertEquals(OTHER, significant("\u2004").getFirst().kind());
        assertEquals(PP_NUMBER, significant("1\\u00a0").getFirst().kind());
        assertEquals(List.of("1\u00a0"), spellings("1\u00a0"));
    }

    @Test void partialTokensReportTheirPhysicalBeginningAfterEarlierTransformations() {
        assertFailure("??/\n    \"oops", 2, 5, "Unterminated string literal");
        assertFailure("x\n  L'bad", 2, 3, "Unterminated character literal");
        assertFailure("x\n  /*bad", 2, 3, "Unterminated comment");
        assertFailure("#include <bad\n", 1, 10, "Unterminated header name");
        assertFailure("#include \"bad", 1, 10, "Unterminated header name");
        assertFailure("'\n'", 1, 1, "Unterminated character literal");
        assertThrows(ParseException.class, () -> scan("''"));
        assertThrows(ParseException.class, () -> scan("#include <>"));
    }
    private static void assertFailure(String source, int line, int column, String reason) {
        var error = assertThrows(ParseException.class, () -> scan(source));
        assertEquals(line, error.line()); assertEquals(column, error.column()); assertEquals(reason, error.reason());
    }

    @Test void logicalCharacterMappingsAreImmutableAndCoverEveryCharacter() {
        var result = scan("a??=\\\r\nb/*c*/\r\nz");
        assertEquals(result.text().length(), result.characterRanges().size());
        for (var token : result.tokens()) assertEquals(token.spelling(), result.text().substring(token.logicalStart(), token.logicalEnd()));
        assertThrows(UnsupportedOperationException.class, () -> result.tokens().clear());
        assertThrows(UnsupportedOperationException.class, () -> result.characterRanges().clear());
        assertEquals(new SourcePosition(1, 1, 0), scan("").eof());
    }

    @Test void parserUsesCppPhasesWhileJavaKeepsItsPreviousLexer() {
        assertDoesNotThrow(() -> Parser.parse("test.cpp", "#/**/include /*a\nb*/<iostream>\nint ma??/\nin() <% int café = 2; return caf\\u00e9 not_eq 2; %>", Language.CPP));
        assertThrows(ParseException.class, () -> Parser.parse("Test.java", "class Test ??< public static void main() {} ??>", Language.JAVA));
        assertDoesNotThrow(() -> Parser.parse("Test.java", "class Test { public static void main(String[] args) { int $a = 1; } }", Language.JAVA));
        assertThrows(ParseException.class, () -> Parser.parse("test.cpp", "int main() { return 1e+2; }", Language.CPP));
        assertThrows(ParseException.class, () -> Parser.parse("test.cpp", "int main() { return \"a\"; }", Language.CPP));
    }
}
