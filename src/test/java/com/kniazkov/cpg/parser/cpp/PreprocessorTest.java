package com.kniazkov.cpg.parser.cpp;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Proves trigraph replacement (2.3) and line splicing (2.1-003).
 */
class PreprocessorTest {

    private final Preprocessor preprocessor = new Preprocessor();

    @Test
    void rejectsNull() {
        assertThrows(NullPointerException.class, () -> preprocessor.normalize(null));
    }

    @Test
    void replacesEveryTrigraph() {
        assertEquals("#\\^[]|{}~", preprocessor.normalize("??=??/??'??(??)??!??<??>??-"));
    }

    @Test
    void leavesOtherQuestionMarksAlone() {
        assertEquals("?", preprocessor.normalize("?"));
        assertEquals("??", preprocessor.normalize("??"));
        assertEquals("??x", preprocessor.normalize("??x"));
        assertEquals("?ab", preprocessor.normalize("?ab"));
    }

    @Test
    void splicesBackslashNewline() {
        assertEquals("ab", preprocessor.normalize("a\\\nb"));
        assertEquals("ab", preprocessor.normalize("a\\\r\nb"));
        assertEquals("ab", preprocessor.normalize("a\\\rb"));
        assertEquals("a", preprocessor.normalize("a\\\r"));
    }

    @Test
    void keepsBackslashesThatDoNotSplice() {
        assertEquals("a\\b", preprocessor.normalize("a\\b"));
        assertEquals("a\\", preprocessor.normalize("a\\"));
    }

    @Test
    void replacesTrigraphsBeforeSplicing() {
        assertEquals("ab", preprocessor.normalize("a??/\nb"));
    }

    @Test
    void preprocessTokenizesTheNormalizedText() {
        List<Token> tokens = preprocessor.preprocess("in\\\nt??=x");

        assertEquals(List.of("int", "#", "x"),
                tokens.stream().map(Token::text).toList());
    }
}
