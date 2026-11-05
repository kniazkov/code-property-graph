/* C++03 virtual method spelling. MIT License. */
package com.kniazkov.cpg.parser;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VirtualMethodsParserTest {
    private SourceFile parse(String text) { return Parser.parse("virtual.cpp", text, Language.CPP).build(); }
    @Test void pureSpecifierAndVirtualDestructorAreIndependentOfBodies() {
        var file = parse("struct Base { virtual int run(int n = 1) const = 0; virtual ~Base() = 0; }; Base::~Base() {} struct Derived : Base { int run(int n = 2) const { return n; } ~Derived() {} };");
        var base = (ClassDeclaration) file.items().getFirst();
        var run = (CallableDeclaration) base.members().getFirst();
        assertTrue(run.modifiers().contains("virtual"));
        assertTrue(run.isPureVirtual()); assertTrue(run.body().isEmpty());
        assertTrue(((MethodDeclaration)run).edit().setBody(BlockStatement.create()).build().isPureVirtual());
        var destructor = (CallableDeclaration) base.members().get(1);
        assertEquals(CallableDeclaration.Kind.DESTRUCTOR, destructor.kind());
        assertTrue(destructor.isPureVirtual()); assertTrue(destructor.modifiers().contains("virtual"));
        var definition = (CallableDeclaration) file.items().get(1);
        assertFalse(definition.isPureVirtual()); assertTrue(definition.body().isPresent());
        var copied = (ClassDeclaration) file.edit().build().items().getFirst();
        assertTrue(((CallableDeclaration) copied.members().getFirst()).isPureVirtual());
    }
    @Test void pureOverrideMayOmitVirtualAndInlineMayAccompanyVirtual() {
        assertDoesNotThrow(() -> parse("struct B { inline virtual int run() { return 1; } inline virtual ~B() {} }; struct D : B { int run() = 0; ~D() = 0; };"));
    }
    @Test void rejectsInvalidVirtualAndPureSyntax() {
        for (String text : new String[] {"virtual int f();", "int f() = 0;", "struct C { virtual C(); };", "struct C { virtual int value; };", "struct C { static virtual int f(); };", "struct C { virtual int f() = 1; };", "struct C { virtual int f() = 0 {} };", "struct C { C() = 0; };"})
            assertThrows(ParseException.class, () -> parse(text), text);
    }
}
