/* C++03 owned template syntax. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TemplateSyntaxTest {
    private SourceFile parse(String source) { return Parser.parse("templates.cpp", source, Language.CPP, "c++03").build(); }
    private <T> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); } }); return result;
    }
    @Test void parameterKindsDefaultsAndCopiesAreOwned() {
        var unit = parse("template<class T = int, int N = 4, template<class> class C = Box> struct Holder { T data[N]; }; ");
        var klass = (ClassDeclaration)unit.items().getFirst();
        assertEquals(List.of(TemplateParameterDeclaration.Kind.TYPE, TemplateParameterDeclaration.Kind.VALUE, TemplateParameterDeclaration.Kind.TEMPLATE), klass.templateParameters().stream().map(TemplateParameterDeclaration::kind).toList());
        assertEquals(1, klass.templateParameters().get(2).templateParameters().size());
        assertEquals("", klass.templateParameters().get(2).templateParameters().getFirst().name());
        assertEquals(3, nodes(unit, TemplateArgument.class).size());
        var copied = unit.edit().build();
        assertNotSame(nodes(unit, TemplateArgument.class).getFirst(), nodes(copied, TemplateArgument.class).getFirst());
        assertEquals(3, nodes(copied, TemplateArgument.class).size());
    }
    @Test void nestedTemplateTypesRetainSeparateArguments() {
        var unit = parse("template<class T> struct Box {}; Box<Box<int> > values; template<class T> typename T::template rebind<Box<int> >::other f(const T& x) { return x.template get<int>(); }");
        var boxes = nodes(unit, NamedType.class).stream().filter(n -> n.name().equals("Box") && n.hasTemplateArguments()).toList();
        assertEquals(3, boxes.size());
        var dependent = nodes(unit, NamedType.class).stream().filter(NamedType::typenameKeyword).findFirst().orElseThrow();
        assertEquals("T::rebind::other", dependent.name());
        assertTrue(dependent.dependent());
        var rebind = dependent.qualifier().orElseThrow();
        assertTrue(rebind.templateKeyword()); assertEquals(1, rebind.templateArguments().size());
        var member = nodes(unit, MemberExpression.class).getFirst();
        assertTrue(member.templateKeyword()); assertEquals(1, member.templateArguments().size());
    }
    @Test void functionsAndMemberTemplatesRetainGenericBodies() {
        var unit = parse("template<int N> int size() { return N; } template<class T> struct Box { template<class U> U get(U value) { return value; } }; int run() { return size<3>(); }");
        assertEquals(1, nodes(unit, NameExpression.class).stream().filter(NameExpression::hasTemplateArguments).count());
        assertEquals(3, nodes(unit, ReturnStatement.class).size());
    }
    @Test void valueArgumentsUseExpressionsAndParenthesesPermitGreaterAndShift() {
        var unit = parse("template<int N> struct Box {}; Box<(8 >> 1)> a; Box<(3 > 2)> b; Box<1 + 2> c;");
        assertEquals(1, nodes(unit, ShiftRightExpression.class).size());
        assertEquals(1, nodes(unit, GreaterExpression.class).size());
        assertEquals(1, nodes(unit, AddExpression.class).size());
    }
    @Test void templateTemporaryIsInitializationNotAnUnresolvedFunctionCall() {
        var unit = parse("template<class T> struct Box {}; void f() { Box<int>(); }");
        assertEquals(1, nodes(unit, InitializationExpression.class).size());
        assertTrue(nodes(unit, CallExpression.class).isEmpty());
    }
    @Test void templateArgumentDependenciesAreExplicit() {
        var unit = parse("template<class T, int N> struct Box {}; template<class T, int N> Box<T, N> f();");
        var type = nodes(unit, NamedType.class).stream().filter(NamedType::hasTemplateArguments).findFirst().orElseThrow();
        assertTrue(type.dependent());
    }
    @Test void validNonTypeParameterFormsRemainAccepted() {
        parse("enum E { first }; typedef E Alias; template<E N, Alias M, int* P, int& R, int A[3], int F()> struct Box {}; template<class T, T Value> struct Dependent {};");
    }
    @Test void rejectsInvalidCpp03TemplateSyntax() {
        for (var source : List.of("template<class T> struct Box {}; Box<Box<int>> values;", "template<class T = int> T f();", "template<int N = 1> int f();", "template<class T = int, class U> struct Box {};", "template<class T> void f() { x.template member; }", "template<template<class> typename C> struct Box {};", "void f() { template<class T> struct Box {}; }", "template<float N> struct Box {};", "template<void N> struct Box {};", "struct Value {}; template<Value N> struct Box {};", "template<template<class T = int, class U> class C> struct Box {};", "template<class T> T::value_type f();", "template<class T> typename T f();", "template<class T> typename T::rebind<int>::type f();"))
            assertThrows(ParseException.class, () -> parse(source), source);
    }
}
