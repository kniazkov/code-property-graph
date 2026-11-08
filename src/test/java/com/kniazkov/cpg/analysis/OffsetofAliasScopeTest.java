/* Instantiated aliases retain their caller's binding until source edits rebuild them. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OffsetofAliasScopeTest {
    private static final PlatformProfile PROFILE = PlatformProfile.builder()
            .offsetof("gcc-clang-builtin", "x86_64-sysv-lp64-v1", "64").build();
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }
        }); return result;
    }
    private static void check(Project project, int inner) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
        var constants = new IntegralConstants(project, PROFILE);
        for (VariableDeclaration variable : nodes(project.files().getFirst(), VariableDeclaration.class)) {
            if (!variable.name().startsWith("result")) continue;
            int expected = variable.name().equals("resultOuter") ? 4 : inner;
            assertEquals(BigInteger.valueOf(expected), constants.evaluate(variable.initializer().orElseThrow()).orElseThrow().number());
        }
    }
    @Test void shadowedArgumentAliasesKeepMeaningAcrossCopiesReanalysisAndEdits() {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(Path.of("/tmp/aliases.cpp")), Path.of("/tmp"),
                List.of(), List.of(), "c++03").withPlatform(PROFILE);
        var project = NameResolver.resolve(Project.create().addFile(Parser.parse(unit,
                "typedef int Shape; template<class T> struct Box {char c; T field;}; Box<Shape> first,second;"
                + "unsigned long resultOuter=__builtin_offsetof(Box<Shape>,field);"
                + "void f(){typedef long Shape; Box<Shape> local; unsigned long resultInner=__builtin_offsetof(Box<Shape>,field);}")).build());
        check(project, 8); check(project.edit().build(), 8);
        var resolved = NameResolver.resolve(project.edit().build()); check(resolved, 8);
        var folded = NameResolver.resolve(ConstantFolder.fold(resolved)); check(folded, 8);
        var alias = nodes(folded.files().getFirst(), TypedefDeclaration.class).stream()
                .filter(value -> value.name().equals("Shape") && value.type() instanceof NamedType type && type.name().equals("long"))
                .findFirst().orElseThrow();
        var editor = new ProjectEditor(folded); editor.replace(alias.type(), NamedType.create().setName("short").build());
        var edited = NameResolver.resolve(editor.build()); check(edited, 2); check(folded, 8);
        check(NameResolver.resolve(edited.edit().build()), 2);
    }
    @Test void unresolvedMemberAliasArgumentsAreNotCertifiedAsConcreteLayouts() {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(Path.of("/tmp/member-aliases.cpp")), Path.of("/tmp"),
                List.of(), List.of(), "c++03").withPlatform(PROFILE);
        var p = NameResolver.resolve(Project.create().addFile(Parser.parse(unit,
                "template<class T> struct Container {typedef T Value;}; template<class T> struct Box {char c; T field;};"
                + "unsigned long resultOuter=__builtin_offsetof(Box<Container<int>::Value>,field);"
                + "unsigned long resultInner=__builtin_offsetof(Box<Container<long>::Value>,field);")).build());
        // The generic instantiator currently coalesces these requests before
        // Container<T>::Value resolves. A stored Value -> T is not a concrete proof.
        for (Project snapshot : List.of(p, NameResolver.resolve(p.edit().build()))) {
            assertTrue(snapshot.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("unsupported-offsetof")), snapshot.semanticDiagnostics().toString());
            var constants = new IntegralConstants(snapshot, PROFILE);
            for (VariableDeclaration variable : nodes(snapshot.files().getFirst(), VariableDeclaration.class))
                if (variable.name().startsWith("result")) assertTrue(constants.evaluate(variable.initializer().orElseThrow()).isEmpty());
        }
        var copied = p.edit().build();
        var copiedConstants = new IntegralConstants(copied, PROFILE);
        for (VariableDeclaration variable : nodes(copied.files().getFirst(), VariableDeclaration.class))
            if (variable.name().startsWith("result")) assertTrue(copiedConstants.evaluate(variable.initializer().orElseThrow()).isEmpty());
    }

    @Test void fullyBoundNamespaceQualifiedArgumentsRemainSupported() {
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(Path.of("/tmp/qualified-aliases.cpp")), Path.of("/tmp"),
                List.of(), List.of(), "c++03").withPlatform(PROFILE);
        var p = NameResolver.resolve(Project.create().addFile(Parser.parse(unit,
                "namespace small {typedef int Shape;} namespace large {typedef long Shape;}"
                + "template<class T> struct Box {char c; T field;};"
                + "unsigned long resultOuter=__builtin_offsetof(Box<small::Shape>,field);"
                + "unsigned long resultInner=__builtin_offsetof(Box<large::Shape>,field);")).build());
        check(p, 8); check(NameResolver.resolve(p.edit().build()), 8);
    }

}
