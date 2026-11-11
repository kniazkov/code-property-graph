/* Identity-directed rename regression tests. MIT License. */
package com.kniazkov.cpg.utilities;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DeclarationRenameTest {
    private static final String ROOT = Path.of("/virtual/rename").toAbsolutePath().normalize() + java.io.File.separator;
    private static Project parse(Map<String, String> sources, String... files) {
        var root = Project.create();
        for (String file : files) {
            var unit = new Build.TranslationUnit(new Build.PhysicalSource(Path.of(ROOT + file)), Path.of(ROOT), List.of(), List.of(), "c++03");
            root.addFile(Parser.parse(unit, sources.get(ROOT + file), sources));
        }
        return NameResolver.resolve(root.build());
    }
    private static Project parse(String source) { return parse(Map.of(ROOT + "main.cpp", source), "main.cpp"); }
    private static <T extends Node> List<T> nodes(Project project, Class<T> type) {
        var result = new ArrayList<T>();
        project.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    @Test void typedefAndUsingRenameAcrossTranslationUnitsPreservesIndependentNames() {
        String header = "namespace shop { typedef int OrderId; }\n";
        String a = "#include \"api.h\"\nusing shop::OrderId;\nOrderId get(OrderId n) { return n; }\n";
        String b = "#include \"api.h\"\nnamespace other { typedef long OrderId; OrderId x; }\nshop::OrderId x;\n";
        var project = parse(Map.of(ROOT + "api.h", header, ROOT + "a.cpp", a, ROOT + "b.cpp", b), "a.cpp", "b.cpp");
        assertFalse(project.hasErrors());
        String before = ProjectJson.encode(project).toText("");
        var selected = nodes(project, TypedefDeclaration.class).getFirst();
        var renamed = DeclarationRename.rename(project, selected, "CustomerId");
        assertFalse(renamed.hasErrors());
        var sources = Source.physicalSources(renamed);
        assertEquals(header.replace("OrderId", "CustomerId"), sources.get(ROOT + "api.h"));
        assertEquals(a.replace("OrderId", "CustomerId"), sources.get(ROOT + "a.cpp"));
        assertEquals(b.replace("shop::OrderId", "shop::CustomerId"), sources.get(ROOT + "b.cpp"));
        assertEquals(before, ProjectJson.encode(project).toText(""));
    }
    @Test void multipleInheritancePreviewsAndRenamesWholeConnectedFamilyOnly() {
        String source = "struct A { virtual int run(int n) { return n; } };\n"
                + "struct B { virtual int run(int n) { return n + 1; } };\n"
                + "struct C : A, B { int run(int n); };\nint C::run(int n) { return n + 2; }\n"
                + "struct Independent { int run(int n) { return n; } };\n"
                + "int use(A& a, B& b, C& c, Independent& i) { return a.run(1) + b.run(2) + c.run(3) + i.run(4); }\n";
        var project = parse(source); assertFalse(project.hasErrors());
        var selected = nodes(project, CallableDeclaration.class).stream().filter(c -> c.name().equals("run")).findFirst().orElseThrow();
        var family = DeclarationRename.affectedDeclarations(project, selected);
        assertEquals(4, family.size());
        var renamed = DeclarationRename.rename(project, selected, "execute");
        assertFalse(renamed.hasErrors());
        String output = Source.physicalSources(renamed).get(ROOT + "main.cpp");
        assertTrue(output.contains("C::execute")); assertTrue(output.contains("a.execute(1) + b.execute(2) + c.execute(3) + i.run(4)"));
        assertTrue(output.contains("struct Independent { int run(int n)"));
        assertEquals(source, Source.physicalSources(project).get(ROOT + "main.cpp"));
    }
    @Test void macroMemberInVirtualFamilyIsRefusedWithoutPartialEdits() {
        String source = "#define METHOD run\nstruct A { virtual void run() {} };\nstruct B : A { void METHOD() {} };\n";
        var project = parse(source); assertFalse(project.hasErrors());
        var selected = nodes(project, CallableDeclaration.class).getFirst();
        assertEquals(2, DeclarationRename.affectedDeclarations(project, selected).size());
        String before = ProjectJson.encode(project).toText("");
        assertThrows(IllegalArgumentException.class, () -> DeclarationRename.rename(project, selected, "execute"));
        assertEquals(before, ProjectJson.encode(project).toText(""));
    }
    @Test void declarationDefinitionAndRecursiveUseRenameTogether() {
        var project = parse("int count(int n);\nint count(int n) { return n ? count(n - 1) : 0; }\n");
        var selected = nodes(project, CallableDeclaration.class).getFirst();
        assertEquals(2, DeclarationRename.affectedDeclarations(project, selected).size());
        var renamed = DeclarationRename.rename(project, selected, "tally");
        assertEquals("int tally(int n);\nint tally(int n) { return n ? tally(n - 1) : 0; }\n", Source.physicalSources(renamed).get(ROOT + "main.cpp"));
    }
    @Test void classRenameIncludesConstructorDestructorAndQualifiedDefinitions() {
        String source = "struct Base { virtual ~Base() {} };\n"
                + "struct Item : Base { Item(); ~Item(); int value(); };\n"
                + "Item::Item() {} Item::~Item() {} int Item::value() { return 1; }\n"
                + "int use() { Item item; return item.value(); }\n";
        var project = parse(source); assertFalse(project.hasErrors());
        var selected = nodes(project, ClassDeclaration.class).stream().filter(c -> c.name().equals("Item")).findFirst().orElseThrow();
        var renamed = DeclarationRename.rename(project, selected, "Record");
        assertFalse(renamed.hasErrors());
        assertEquals(source.replace("Item", "Record"), Source.physicalSources(renamed).get(ROOT + "main.cpp"));
        assertEquals(source, Source.physicalSources(project).get(ROOT + "main.cpp"));
    }
    @Test void namespaceAndAliasRenameRespectReopeningsAndIndependentScopes() {
        String source = "namespace shop { typedef int Id; } namespace shop { Id value; }\n"
                + "namespace selected = shop; using namespace shop; using shop::Id;\n"
                + "shop::Id one; selected::Id two; namespace other { namespace shop { int value; } shop::value; }\n";
        // Keep the independent namespace use in a declaration rather than an expression statement.
        source = source.replace("shop::value; }", "int get() { return shop::value; } }");
        var project = parse(source); assertFalse(project.hasErrors());
        var selected = nodes(project, NamespaceDeclaration.class).getFirst();
        var renamed = DeclarationRename.rename(project, selected, "commerce");
        assertFalse(renamed.hasErrors());
        String expected = source.replace("namespace shop { typedef", "namespace commerce { typedef")
                .replace("namespace shop { Id", "namespace commerce { Id")
                .replace("= shop;", "= commerce;").replace("namespace shop;", "namespace commerce;")
                .replace("shop::Id", "commerce::Id");
        assertEquals(expected, Source.physicalSources(renamed).get(ROOT + "main.cpp"));
        var alias = nodes(renamed, NamespaceAliasDeclaration.class).getFirst();
        var again = DeclarationRename.rename(renamed, alias, "chosen");
        assertFalse(again.hasErrors());
        assertEquals(expected.replace("selected", "chosen"), Source.physicalSources(again).get(ROOT + "main.cpp"));
    }
    @Test void overloadSelectionLeavesIndependentOverloadsAndUsesUntouched() {
        String source = "int run(int n) { return n; }\nint run(double n) { return 2; }\nint use() { return run(1) + run(1.0); }\n";
        var project = parse(source); assertFalse(project.hasErrors());
        var selected = nodes(project, CallableDeclaration.class).getFirst();
        var renamed = DeclarationRename.rename(project, selected, "execute");
        assertEquals(source.replace("run(int", "execute(int").replace("run(1)", "execute(1)"),
                Source.physicalSources(renamed).get(ROOT + "main.cpp"));
    }
    @Test void collisionReturnsBothDeclarationsAndDiagnosticsWithoutChangingOldSnapshot() {
        var project = parse("struct Device { void reset(int); void restart(int); };\n");
        String original = ProjectJson.encode(project).toText("");
        var reset = nodes(project, CallableDeclaration.class).getFirst();
        var renamed = DeclarationRename.rename(project, reset, "restart");
        assertTrue(renamed.hasErrors());
        assertEquals(2, nodes(renamed, CallableDeclaration.class).stream().filter(c -> c.name().equals("restart")).count());
        assertThrows(IllegalArgumentException.class, () -> Source.physicalSources(renamed));
        assertEquals(original, ProjectJson.encode(project).toText(""));
    }

    @Test void classTypedefUsesRenameTheWrittenAliasNotItsUnderlyingClass() {
        String source = "struct Item {}; typedef Item Alias; Alias item; Item original;\n";
        var project = parse(source);
        var renamed = DeclarationRename.rename(project, nodes(project, TypedefDeclaration.class).getFirst(), "Record");
        assertEquals(source.replace("Alias", "Record"), Source.physicalSources(renamed).get(ROOT + "main.cpp"));
    }

    @Test void captureProducesAnErrorGraphRatherThanSilentlyChangingBindings() {
        String source = "int use() { int x = 1; { int y = 2; return x + y; } }\n";
        var project = parse(source);
        var selected = nodes(project, VariableDeclaration.class).stream().filter(v -> v.name().equals("x")).findFirst().orElseThrow();
        var renamed = DeclarationRename.rename(project, selected, "y");
        assertTrue(renamed.hasErrors());
        assertTrue(nodes(renamed, Node.class).stream().flatMap(n -> n.diagnostics().stream()).anyMatch(d -> d.code().equals("rename-binding-conflict")));
        assertThrows(IllegalArgumentException.class, () -> Source.physicalSources(renamed));
        assertEquals(source, Source.physicalSources(project).get(ROOT + "main.cpp"));
    }

    @Test void uncapturedExternalFamilyIsPreviewedButCannotBePartiallyEdited() {
        var base = ClassDeclaration.create().setName("External").addMember(MethodDeclaration.create()
                .setName("run").addModifier("virtual").setReturnType(NamedType.create().setName("void")));
        var derived = ClassDeclaration.create().setName("Derived")
                .addBase(BaseSpecifier.create().setType(NamedType.create().setName("External").setDeclaration(base)))
                .addMember(MethodDeclaration.create().setName("run").setReturnType(NamedType.create().setName("void")));
        var project = Project.create().addFile(SourceFile.create().setPath("external-model.cpp")
                .setLanguage(Language.CPP).addItem(base).addItem(derived)).build();
        var selected = nodes(project, CallableDeclaration.class).getLast();
        assertEquals(2, DeclarationRename.affectedDeclarations(project, selected).size());
        String original = ProjectJson.encode(project).toText("");
        assertThrows(IllegalArgumentException.class, () -> DeclarationRename.rename(project, selected, "execute"));
        assertEquals(original, ProjectJson.encode(project).toText(""));
    }
    @Test void overloadedUsingCannotSilentlyLoseAnUnselectedOverload() {
        var project = parse("namespace api { int run(int n) { return n; } int run(double n) { return 2; } }\n"
                + "using api::run; int use() { return run(1) + run(1.0); }\n");
        assertFalse(project.hasErrors());
        var selected = nodes(project, CallableDeclaration.class).getFirst();
        var renamed = DeclarationRename.rename(project, selected, "execute");
        // A single using declaration cannot import the two newly different names. The resulting
        // source is retained for inspection but must never silently pass generation validation.
        assertTrue(renamed.hasErrors());
        assertThrows(IllegalArgumentException.class, () -> Source.physicalSources(renamed));
    }

    @Test void uncalledOverloadImportStillRequiresExplicitSplit() {
        var project = parse("namespace api { int run(int); int run(double); } using api::run;\n");
        var renamed = DeclarationRename.rename(project, nodes(project, CallableDeclaration.class).getFirst(), "execute");
        assertTrue(renamed.hasErrors());
        assertTrue(renamed.diagnostics().stream().anyMatch(d -> d.code().equals("rename-overload-import-conflict")));
        assertThrows(IllegalArgumentException.class, () -> Source.physicalSources(renamed));
    }

    @Test void qualifierCaptureIsDiagnosedAfterNamespaceRename() {
        String source = "namespace left { int x; } namespace right { int x; }\n"
                + "namespace scope { namespace chosen = right; int use() { return left::x; } }\n";
        var project = parse(source); assertFalse(project.hasErrors());
        var renamed = DeclarationRename.rename(project, nodes(project, NamespaceDeclaration.class).getFirst(), "chosen");
        assertTrue(renamed.hasErrors());
        assertThrows(IllegalArgumentException.class, () -> Source.physicalSources(renamed));
        assertEquals(source, Source.physicalSources(project).get(ROOT + "main.cpp"));
    }
    @Test void aliasChainsAndUsingNamespaceFollowTheSelectedAliasIdentity() {
        String source = "namespace shop { int x; } namespace selected = shop; namespace second = selected;\n"
                + "using namespace selected; int use() { return second::x + selected::x; }\n";
        var project = parse(source); assertFalse(project.hasErrors());
        var renamed = DeclarationRename.rename(project, nodes(project, NamespaceAliasDeclaration.class).getFirst(), "chosen");
        assertFalse(renamed.hasErrors());
        assertEquals(source.replace("selected", "chosen"), Source.physicalSources(renamed).get(ROOT + "main.cpp"));
    }

    @Test void independentNamespacesCannotBecomeReopeningsAfterRename() {
        assertIdentityConflict("namespace n { int x; } namespace Changed { int y; } using namespace n;\n", NamespaceDeclaration.class);
    }
    @Test void independentForwardClassCannotMergeWithExistingDefinition() {
        assertIdentityConflict("struct X; struct Changed {}; Changed* b; X* a;\n", ClassDeclaration.class);
    }
    @Test void independentFunctionPrototypesCannotBecomeRedeclarations() {
        assertIdentityConflict("int f(int); int Changed(int);\n", CallableDeclaration.class);
    }
    private static <T extends Declaration> void assertIdentityConflict(String source, Class<T> type) {
        var project = parse(source); assertFalse(project.hasErrors());
        String original = ProjectJson.encode(project).toText("");
        var renamed = DeclarationRename.rename(project, nodes(project, type).getFirst(), "Changed");
        assertTrue(renamed.hasErrors());
        assertTrue(nodes(renamed, Node.class).stream().flatMap(n -> n.diagnostics().stream())
                .anyMatch(d -> d.code().equals("rename-identity-conflict")));
        assertThrows(IllegalArgumentException.class, () -> Source.physicalSources(renamed));
        assertEquals(original, ProjectJson.encode(project).toText(""));
    }

}
