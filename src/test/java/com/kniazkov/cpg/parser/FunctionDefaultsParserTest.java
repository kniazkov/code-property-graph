/* C++03 function defaults and signature ownership. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FunctionDefaultsParserTest {
    private SourceFile parse(String text) { return Parser.parse("defaults.cpp", text, Language.CPP).build(); }

    @Test void defaultsOwnAssignmentExpressionsAndPreserveFullParameterRanges() {
        String source = "int seed(); inline int f(int a = seed(), int b = (1, 2), int c = 1 ? 2 : 3);";
        var file = parse(source);
        var function = (FunctionDeclaration) file.items().getLast();
        assertTrue(function.modifiers().contains("inline"));
        assertInstanceOf(CallExpression.class, function.parameters().get(0).defaultValue().orElseThrow());
        assertInstanceOf(CommaExpression.class, function.parameters().get(1).defaultValue().orElseThrow());
        assertInstanceOf(ConditionalExpression.class, function.parameters().get(2).defaultValue().orElseThrow());
        assertEquals(2, function.parameters().getFirst().children().size());
        assertEquals("int a = seed()", source.substring(function.parameters().getFirst().range().orElseThrow().start().offset(),
                function.parameters().getFirst().range().orElseThrow().end().offset()));
        assertNotSame(function.parameters().getFirst().defaultValue().orElseThrow(),
                ((FunctionDeclaration) file.edit().build().items().getLast()).parameters().getFirst().defaultValue().orElseThrow());
    }

    @Test void signaturesRetainReferencesArraysFunctionPointersAndEllipsis() {
        var file = parse("int value; int callback(int); int f(int &a = value, int (*p)(int) = callback, int q[] = 0, ...) { return a; }");
        var function = (FunctionDeclaration) file.items().getLast();
        assertTrue(function.variadic());
        assertTrue(function.body().isPresent());
        assertInstanceOf(ReferenceType.class, function.parameters().get(0).type().orElseThrow());
        assertInstanceOf(PointerType.class, function.parameters().get(1).type().orElseThrow());
        assertInstanceOf(ArrayType.class, function.parameters().get(2).type().orElseThrow());
        assertTrue(function.parameters().stream().allMatch(p -> p.defaultValue().isPresent()));
    }

    @Test void cxx03AcceptsEllipsisWithOrWithoutAComma() {
        var file = parse("int f(int x...); int g(...); int h(int x, ...);");
        for (var item : file.items()) assertTrue(((FunctionDeclaration) item).variadic());
    }

    @Test void defaultsAreNotPartOfFunctionPointerOrTypedefTypes() {
        for (String source : List.of("typedef int F(int x = 1);", "int (*p)(int x = 1);",
                "int f(int (*p)(int x = 1));", "int (*f(int x = 1))(int y = 2);"))
            assertThrows(ParseException.class, () -> parse(source), source);
        assertDoesNotThrow(() -> parse("int (*f(int x = 1))(int);"));
    }
}
