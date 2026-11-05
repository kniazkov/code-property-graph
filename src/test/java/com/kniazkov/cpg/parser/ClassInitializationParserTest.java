/* C++03 class syntax and ownership. MIT License. */
package com.kniazkov.cpg.parser;
import com.kniazkov.cpg.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ClassInitializationParserTest {
    private SourceFile parse(String source) { return Parser.parse("classes.cpp", source, Language.CPP).build(); }
    @Test void specialMembersAndAccessSectionsHaveOwnedWrittenSyntax() {
        var file = parse("class C { int first; public: C(int n) : first(n) {} C(const C& other) : first(other.first) {} ~C() {} C& operator=(const C& other) { first = other.first; return *this; } protected: mutable int last; private: static int count; }; int C::count = 0;");
        var cls = (ClassDeclaration) file.items().getFirst();
        assertEquals(List.of("private", "public", "public", "public", "public", "protected", "private"), cls.members().stream().map(d -> d.access().orElseThrow()).toList());
        var constructor = (MethodDeclaration) cls.members().get(1);
        assertEquals(CallableDeclaration.Kind.CONSTRUCTOR, constructor.kind());
        assertTrue(constructor.returnType().isEmpty());
        assertEquals("first", constructor.initializers().getFirst().name());
        assertEquals(CallableDeclaration.Kind.DESTRUCTOR, ((MethodDeclaration) cls.members().get(3)).kind());
        assertEquals("operator=", ((MethodDeclaration) cls.members().get(4)).name());
        assertEquals("C::count", ((VariableDeclaration) file.items().getLast()).name());
        var copied = (ClassDeclaration) file.edit().build().items().getFirst();
        assertNotSame(constructor.initializers().getFirst(), ((MethodDeclaration) copied.members().get(1)).initializers().getFirst());
    }
    @Test void initializerFormsAndMostVexingParseRemainDistinct() {
        var file = parse("struct S { int a; int b; }; S aggregate = {1, 2}; S direct(aggregate); S copy = aggregate; S function(); S temporary = S(); int zero = int();");
        assertEquals(InitializationExpression.Form.AGGREGATE, ((InitializationExpression)((VariableDeclaration)file.items().get(1)).initializer().orElseThrow()).form());
        assertEquals(InitializationExpression.Form.DIRECT, ((InitializationExpression)((VariableDeclaration)file.items().get(2)).initializer().orElseThrow()).form());
        assertInstanceOf(NameExpression.class, ((VariableDeclaration)file.items().get(3)).initializer().orElseThrow());
        assertInstanceOf(FunctionDeclaration.class, file.items().get(4));
        assertEquals(InitializationExpression.Form.TEMPORARY, ((InitializationExpression)((VariableDeclaration)file.items().get(5)).initializer().orElseThrow()).form());
        assertThrows(ParseException.class, () -> parse("struct S {}; S bad{};"));
    }
    @Test void functionalInitializationIsAValidExpressionStatement() {
        assertDoesNotThrow(() -> parse("int main() { int(); int(3); unsigned(2); return 0; }"));
        assertThrows(ParseException.class, () -> parse("int main() { unsigned int(2); return 0; }"));
    }
    @Test void bitFieldsNestedTypesFriendsAndExternalDefinitions() {
        var file = parse("struct S { unsigned int bits : 3; unsigned int : 0; struct Nested { int value; }; friend int inspect(const S&); S(); ~S(); }; S::S() : bits(1) {} S::~S() {}");
        var cls = (ClassDeclaration)file.items().getFirst();
        assertEquals(3, ((IntegerLiteral)((VariableDeclaration)cls.members().getFirst()).bitWidth().orElseThrow()).value());
        assertEquals("", ((VariableDeclaration)cls.members().get(1)).name());
        assertTrue(cls.members().get(3).isFriend());
        assertEquals(CallableDeclaration.Kind.CONSTRUCTOR, ((MethodDeclaration)file.items().get(1)).kind());
    }
}
