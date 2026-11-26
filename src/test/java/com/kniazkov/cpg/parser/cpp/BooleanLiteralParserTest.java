package com.kniazkov.cpg.parser.cpp;

import com.kniazkov.cpg.tree.Node;
import com.kniazkov.cpg.tree.Visitor;
import com.kniazkov.cpg.tree.cpp.CppBooleanLiteral;
import com.kniazkov.cpg.tree.cpp.CppType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves the boolean literal statements CPP03-2.13.5-002 through
 * CPP03-2.13.5-004.
 */
class BooleanLiteralParserTest {

    private static CppBooleanLiteral parse(String source) {
        return (CppBooleanLiteral) new LiteralParser().parse(source);
    }

    @Test
    void statement_002_trueIsAKeyword() {
        CppBooleanLiteral literal = parse("true");

        assertTrue(literal.value());
        assertEquals("true", literal.data());
        assertEquals("BooleanLiteral", literal.name());
        assertTrue(literal.children().isEmpty());
    }

    @Test
    void statement_002_falseIsAKeyword() {
        CppBooleanLiteral literal = parse("false");

        assertFalse(literal.value());
        assertEquals("false", literal.data());
    }

    @Test
    void statement_003_booleanLiteralsHaveTypeBool() {
        assertEquals(CppType.BOOL, parse("true").type());
        assertEquals(CppType.BOOL, parse("false").type());
        assertEquals("bool", parse("true").typeName());
        assertEquals("bool", parse("false").typeName());
    }

    @Test
    void statement_004_booleanLiteralsAreNotLvalues() {
        assertFalse(parse("true").isLvalue());
        assertFalse(parse("false").isLvalue());
    }

    @Test
    void exposesFragmentAndVisitor() {
        CppBooleanLiteral literal = parse("true");

        assertEquals("true", literal.fragment().code());
        assertEquals("BooleanLiteral", literal.accept(new Visitor<String>() {
            @Override
            public String visit(Node node) {
                return node.name();
            }
        }));
    }
}
