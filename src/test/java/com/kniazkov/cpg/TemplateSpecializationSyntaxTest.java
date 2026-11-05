/* C++03 specialization and instantiation syntax. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateSpecializationSyntaxTest {
    private SourceFile parse(String text) { return Parser.parse("specializations.cpp", text, Language.CPP, "c++03").build(); }
    @Test void classSpecializationsRetainArgumentsAndGenericParameters() {
        var file = parse("template<class T> struct Box {}; template<> struct Box<int> { int value; }; template<class T> struct Box<T*> { T value; }; template class Box<char>;");
        assertEquals(TemplateKind.PRIMARY, ((ClassDeclaration)file.items().get(0)).templateKind());
        var full = (ClassDeclaration)file.items().get(1);
        assertEquals("Box", full.name()); assertEquals(TemplateKind.FULL_SPECIALIZATION, full.templateKind());
        assertEquals(1, full.templateArguments().size()); assertTrue(full.templateParameters().isEmpty());
        var partial = (ClassDeclaration)file.items().get(2);
        assertEquals(TemplateKind.PARTIAL_SPECIALIZATION, partial.templateKind());
        assertEquals(1, partial.templateParameters().size());
        assertInstanceOf(PointerType.class, partial.templateArguments().getFirst().type().orElseThrow());
        var explicit = (ClassDeclaration)file.items().get(3);
        assertEquals(TemplateKind.EXPLICIT_INSTANTIATION, explicit.templateKind()); assertFalse(explicit.isDefinition());
        var copy = full.edit().build();
        assertEquals(full.templateKind(), copy.templateKind());
        assertNotSame(full.templateArguments().getFirst(), copy.templateArguments().getFirst());
    }
    @Test void functionSpecializationsAndExplicitInstantiations() {
        var file = parse("template<class T> T id(T x) { return x; } template<> int id<int>(int x) { return x; } template<> char id(char x); template int id<int>(int); template char id(char);");
        var full = (CallableDeclaration)file.items().get(1);
        assertEquals("id", full.name()); assertEquals(TemplateKind.FULL_SPECIALIZATION, full.templateKind());
        assertTrue(full.hasTemplateArguments()); assertEquals(1, full.templateArguments().size());
        assertEquals(TemplateKind.FULL_SPECIALIZATION, ((CallableDeclaration)file.items().get(2)).templateKind());
        for (int i=3;i<5;i++) assertEquals(TemplateKind.EXPLICIT_INSTANTIATION, ((CallableDeclaration)file.items().get(i)).templateKind());
        assertEquals(full.templateKind(), ((CallableDeclaration)full.edit().build()).templateKind());
    }
    @Test void emptyWrittenArgumentListAndExportArePreserved() {
        var file = parse("export template<class T> T id(T); template<> int id<>(int); export template<class T> struct Box {};");
        assertTrue(((CallableDeclaration)file.items().getFirst()).exportedTemplate());
        var full = (CallableDeclaration)file.items().get(1);
        assertTrue(full.hasTemplateArguments()); assertTrue(full.templateArguments().isEmpty());
        assertTrue(((ClassDeclaration)file.items().getLast()).exportedTemplate());
    }
    @Test void nonTypeClassSpecializationKeepsParenthesizedComparisons() {
        var file = parse("template<int N> struct Box {}; template<> struct Box<(3 > 2)> {}; template class Box<(4 > 2)>;");
        assertEquals(TemplateKind.FULL_SPECIALIZATION, ((ClassDeclaration)file.items().get(1)).templateKind());
        assertEquals(TemplateKind.EXPLICIT_INSTANTIATION, ((ClassDeclaration)file.items().get(2)).templateKind());
    }
    @Test void qualifiedStaticTemplateMemberIsACallNotConstruction() {
        var file = parse("template<class T> struct Box { static int value() { return 1; } }; int f() { return Box<int>::value(); }");
        var function = (CallableDeclaration)file.items().getLast();
        var result = (ReturnStatement)function.body().orElseThrow().items().getFirst();
        var call = assertInstanceOf(CallExpression.class, result.value().orElseThrow());
        var member = assertInstanceOf(ScopeMemberExpression.class, call.callee());
        assertTrue(assertInstanceOf(NameExpression.class, member.receiver()).hasTemplateArguments());
    }
    @Test void rejectsForbiddenPartialFunctionSpecializationAndInvalidForms() {
        for (String text : new String[] {
                "template<class T> T id<T*>(T* x);",
                "template<> struct Box {};",
                "template<> struct Missing<int> {};",
                "template<class T = int> struct Box<T*> {};",
                "template class Box<int> {};",
                "template int id<int>(int x) { return x; }",
                "export template<> int id<int>(int);",
                "export int value;",
                "template<template<> class C> struct Box {};" })
            assertThrows(ParseException.class, () -> parse(text), text);
    }
}
