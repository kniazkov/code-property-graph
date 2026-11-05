/* Written C++03 template declarations, without template instantiation. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.Build;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.parser.ParseException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FunctionTemplateDeclarationTest {
    @TempDir Path directory;
    private Project resolve(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("template.cpp", source, Language.CPP, "c++03")).build());
    }
    private FunctionDeclaration function(Project p, int index) {
        return (FunctionDeclaration)p.files().getFirst().items().get(index);
    }
    @Test void dependentTypesBindToOwnedParametersAndSurviveCopy() {
        var original = resolve("template<class T> T identity(T value) { return value; }");
        for (var p : List.of(original, original.edit().build())) {
            var f = function(p, 0);
            var parameter = f.templateParameters().getFirst();
            assertEquals("T", parameter.name());
            assertEquals("class", parameter.keyword());
            assertTrue(f.children().contains(parameter));
            assertSame(parameter, ((NamedType)f.returnType().orElseThrow()).declaration().orElseThrow());
            assertSame(parameter, ((NamedType)f.parameters().getFirst().type().orElseThrow()).declaration().orElseThrow());
            var names = new ArrayList<NameExpression>();
            f.walk(new NodeVisitor() { @Override public void visit(NameExpression n) { names.add(n); } });
            assertSame(f.parameters().getFirst(), names.getFirst().target().orElseThrow());
            var errors = new ArrayList<Diagnostic>();
            p.walk(new NodeVisitor() { @Override public void visit(Node n) { n.diagnostics().stream().filter(d -> d.severity() == Diagnostic.Severity.ERROR).forEach(errors::add); } });
            assertTrue(errors.isEmpty(), errors.toString());
        }
        assertNotSame(function(original, 0).templateParameters().getFirst(), function(original.edit().build(), 0).templateParameters().getFirst());
    }
    @Test void distinctTemplateScopesDoNotLeak() {
        var p = resolve("template<class T> T first(T x) { return x; } template<typename T> T second(T y) { return y; } int process(int z) { return z; }");
        assertNotSame(function(p, 0).templateParameters().getFirst(), function(p, 1).templateParameters().getFirst());
        assertEquals("typename", function(p, 1).templateParameters().getFirst().keyword());
        assertTrue(function(p, 2).templateParameters().isEmpty());
        assertThrows(ParseException.class, () -> resolve("template<class T> T first(T x) { return x; } T leaked;"));
    }
    @Test void sourceRegenerationRetainsTemplateHead() {
        var p = resolve("template<class T, typename U> T first(T x, U y) { return x; }");
        String source = p.files().getFirst().source(Language.CPP);
        var reparsed = resolve(source);
        assertEquals(2, function(reparsed, 0).templateParameters().size());
        assertEquals("typename", function(reparsed, 0).templateParameters().get(1).keyword());
    }
    @Test void templateCallsAreNotPretendedToBeInstantiated() {
        var p = resolve("template<class T> int value(int x) { return x; } int process() { return value(1); }");
        var calls = new ArrayList<CallExpression>();
        p.walk(new NodeVisitor() { @Override public void visit(CallExpression call) { calls.add(call); } });
        assertEquals(CallExpression.Resolution.UNRESOLVED, calls.getFirst().resolution());
        assertTrue(calls.getFirst().target().isEmpty());
    }
    @Test void identicalHeaderTemplatesMayRepeatAcrossTranslationUnits() throws Exception {
        Files.writeString(directory.resolve("api.h"), "template<class T, typename U> T identity(T value, U ignored) { return value; }");
        for (String name : List.of("main.cpp", "impl.cpp"))
            Files.writeString(directory.resolve(name), "#include \"api.h\"\n");
        var units = List.of("main.cpp", "impl.cpp").stream().map(name -> new Build.TranslationUnit(
                new Build.PhysicalSource(directory.resolve(name)), directory, List.of(), List.of(), "c++03")).toList();
        var build = new Build(units, new Build.Composition("headers", units.stream().map(Build.TranslationUnit::variantId).toList()));
        var p = Cpg.read(build, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
        assertFalse(hasOdrConflict(p));
        assertEquals(2, p.files().size());
        assertEquals(2, function(p, 0).templateParameters().size());
        assertTrue(hasOdrConflict(resolve("template<class T> T f(T x) { return x; } template<class T> T f(T x) { return x; }")));
        Files.writeString(directory.resolve("impl.cpp"), "template<class T, typename U> T identity(T value, U ignored) { T other; return other; }");
        assertTrue(hasOdrConflict(Cpg.read(build, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED))));
    }
    private boolean hasOdrConflict(Project p) {
        var conflicts = new ArrayList<Diagnostic>();
        p.walk(new NodeVisitor() { @Override public void visit(Node n) { n.diagnostics().stream()
                .filter(d -> d.code().equals("odr-conflict")).forEach(conflicts::add); } });
        return !conflicts.isEmpty();
    }
    @Test void alphaEquivalentTemplateDeclarationsShareTheirEntity() {
        var p = resolve("template<class T> T f(T count); template<typename U> U f(U n) { return n; }");
        assertSame(function(p, 1), function(p, 0).definition().orElseThrow());
        assertSame(function(p, 0), function(p, 1).previousDeclaration().orElseThrow());
        var nested = resolve("template<class T> const T* f(T& count); template<class U> const U* f(U& n) { return &n; }");
        assertSame(function(nested, 1), function(nested, 0).definition().orElseThrow());
    }
    @Test void templateArityAndDependentReturnPatternsDiscriminateEntities() {
        for (String source : List.of(
                "template<class T> int f(int a); template<class T, class U> int f(int b);",
                "template<class T, class U> T f(int a); template<class T, class U> U f(int b);",
                "template<class T> int f(int a); int f(int b);")) {
            var p = resolve(source);
            assertTrue(function(p, 1).previousDeclaration().isEmpty(), source);
        }
    }
    @Test void unsupportedFormsFailInsteadOfBecomingOrdinaryDeclarations() {
        for (String source : List.of("template<int N> int f();", "template<class T = int> T f();",
                "template<class T> class Box {};", "template<> int f();", "template<class T> T variable;",
                "template<class T, class T> T f();", "extern \"C\" { template<class T> T f(); }"))
            assertThrows(ParseException.class, () -> resolve(source), source);
    }
}
