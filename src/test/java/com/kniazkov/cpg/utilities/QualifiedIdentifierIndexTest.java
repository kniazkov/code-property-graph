/* Qualifier identity regressions. MIT License. */
package com.kniazkov.cpg.utilities;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class QualifiedIdentifierIndexTest {
    private static final String FILE = "/virtual/qualifier.cpp";
    private Project parse(String source) {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(Path.of(FILE)), Path.of("/virtual"), List.of(), List.of(), "c++03");
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit, source, Map.of(FILE, source))).build());
    }
    private Declaration at(QualifiedIdentifierIndex.Result result, String source, String needle) {
        int offset = source.indexOf(needle);
        return result.targets().entrySet().stream().filter(e -> e.getKey().start().offset() == offset).map(Map.Entry::getValue).findFirst().orElseThrow();
    }
    @Test void scopedQualifiersAliasesAndNamespaceImportsHaveDistinctIdentities() {
        String source = "namespace shop { struct Item {}; } namespace alias = shop;\n"
                + "namespace other { namespace shop { struct Item {}; } shop::Item local; }\n"
                + "shop::Item global; alias::Item indirect; using namespace shop;\n";
        Project project = parse(source); assertFalse(project.hasErrors());
        var result = QualifiedIdentifierIndex.index(project);
        assertTrue(result.unresolved().isEmpty(), result.toString());
        assertTrue(result.unsafe().isEmpty());
        assertNotSame(at(result, source, "shop::Item local"), at(result, source, "shop::Item global"));
        assertInstanceOf(NamespaceAliasDeclaration.class, at(result, source, "alias::Item"));
        assertSame(at(result, source, "shop::Item global"), at(result, source, "shop;"));
        assertEquals(5, result.targets().size());
    }
    @Test void reopenedNamespacesAndOutOfLineClassMembersResolve() {
        String source = "namespace n { struct C { C(); ~C(); static int value; }; }\n"
                + "namespace n { int x; }\nn::C::C() {} n::C::~C() {} int n::C::value = 1;\n";
        Project project = parse(source); assertFalse(project.hasErrors());
        var result = QualifiedIdentifierIndex.index(project);
        assertTrue(result.unresolved().isEmpty(), result.toString());
        assertEquals(6, result.targets().size());
        assertSame(at(result, source, "n::C::C"), at(result, source, "n::C::~C"));
        assertSame(at(result, source, "C::C"), at(result, source, "C::~C"));
    }
    @Test void explicitlyGlobalQualifierDoesNotBindToNestedNames() {
        String source = "namespace n { struct C {}; } namespace other { namespace n { struct C {}; } "
                + "::n::C c; int f() { return 0; } }\n";
        var result = QualifiedIdentifierIndex.index(parse(source));
        assertTrue(result.unresolved().isEmpty(), result.toString());
        assertEquals(1, result.targets().size());
        assertEquals(source.indexOf("namespace n") + 0,
                at(result, source, "n::C c").range().orElseThrow().start().offset());
    }
    @Test void sharedHeaderWithDifferentLexicalBindingsIsRejected() {
        String header = "n::C value;\n";
        String a = "namespace n { struct C {}; }\n#include \"shared.h\"\n";
        String b = "namespace wrapper { namespace n { struct C {}; }\n#include \"shared.h\"\n}\n";
        var sources = Map.of("/virtual/shared.h", header, "/virtual/a.cpp", a, "/virtual/b.cpp", b);
        var builder = Project.create();
        for (String file : List.of("/virtual/a.cpp", "/virtual/b.cpp")) {
            var unit = new Build.TranslationUnit(new Build.PhysicalSource(Path.of(file)), Path.of("/virtual"), List.of(), List.of(), "c++03");
            builder.addFile(Parser.parse(unit, sources.get(file), sources));
        }
        var project = NameResolver.resolve(builder.build()); assertFalse(project.hasErrors());
        var result = QualifiedIdentifierIndex.index(project);
        assertEquals(1, result.unresolved().size()); assertTrue(result.targets().isEmpty());
    }
    @Test void importedScopeQualifiersUseResolvedNamespaceIdentity() {
        String source = "namespace outer { namespace inner { int value; } } using namespace outer; "
                + "int f() { return inner::value; }\n";
        var project = parse(source); assertFalse(project.hasErrors());
        var result = QualifiedIdentifierIndex.index(project);
        assertTrue(result.unresolved().isEmpty(), result.toString());
        assertEquals("inner", ((NamespaceDeclaration) at(result, source, "inner::value")).name());
    }
    @Test void aliasChainAndUsingNamespaceRetainWrittenAliasIdentity() {
        String source = "namespace shop { int value; } namespace original = shop; "
                + "namespace second = original; using namespace original; int f() { return second::value; }\n";
        var project = parse(source); assertFalse(project.hasErrors());
        var result = QualifiedIdentifierIndex.index(project);
        assertTrue(result.unresolved().isEmpty(), result.toString());
        assertEquals("original", ((NamespaceAliasDeclaration) at(result, source, "original;")).name());
        assertEquals(3, result.targets().values().stream().filter(NamespaceAliasDeclaration.class::isInstance).count());
    }
    @Test void inheritedClassQualifierShadowsUnrelatedGlobalClass() {
        String source = "struct N { static int value; }; struct Base { struct N { static int value; }; }; "
                + "struct Derived : Base { int f() { return N::value; } };\n";
        var project = parse(source); assertFalse(project.hasErrors());
        var result = QualifiedIdentifierIndex.index(project);
        assertTrue(result.unresolved().isEmpty(), result.toString());
        assertTrue(at(result, source, "N::value").range().orElseThrow().start().offset() > source.indexOf("struct Base"));
    }
    @Test void macroQualifierIsMarkedUnsafe() {
        String source = "namespace n { struct C {}; }\n#define NS n\nNS::C c;\n";
        Project project = parse(source); assertFalse(project.hasErrors());
        assertFalse(QualifiedIdentifierIndex.index(project).unsafe().isEmpty());
    }
    @Test void unresolvedQualifierIsNotGuessedBySpelling() {
        String source = "namespace n { struct C {}; }\nint f() { return missing::value; }\n";
        var result = QualifiedIdentifierIndex.index(parse(source));
        assertFalse(result.unresolved().isEmpty());
        assertTrue(result.targets().isEmpty());
    }
}
