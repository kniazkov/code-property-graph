/* C++03 declarations, resolution paths and safe source export. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ScopedExportTest {
    @TempDir Path directory;
    private static NamedType.Builder type(String name) { return NamedType.create().setName(name); }
    private static VariableDeclaration.Builder variable(String name, NamedType.Builder type) {
        return VariableDeclaration.create().setName(name).setType(type);
    }
    private static SourceFile.Builder file() { return SourceFile.create().setPath("types.cpp").setLanguage(Language.CPP); }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (kind.isInstance(node)) result.add(kind.cast(node));
        }});
        return result;
    }
    private static Map<Node, Integer> ids(Node root) {
        var result = new IdentityHashMap<Node, Integer>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { result.put(node, result.size()); }});
        return result;
    }
    private static String json(Project project) { return ProjectJson.encode(project).toText("").replaceAll("\\s+", ""); }
    private static Project parse(String text) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("types.cpp", text, Language.CPP)).build());
    }

    @Test void sharedDirectiveAndPerUseResolutionStepsExportOwnedIdsAfterCopying() {
        var order = TypedefDeclaration.create().setName("OrderId").setType(type("int"));
        var receipt = TypedefDeclaration.create().setName("ReceiptId").setType(type("long"));
        var shop = NamespaceDeclaration.create().setName("shop").addMember(order).addMember(receipt);
        var using = UsingDirective.create().setNamespaceName("shop").setTarget(shop);
        var first = type("OrderId").setAlias(order).setDeclaration(order)
                .addResolutionStep("using-directive", using, order).addResolutionStep("typedef", order, order);
        var second = type("ReceiptId").setAlias(receipt).setDeclaration(receipt)
                .addResolutionStep("using-directive", using, receipt).addResolutionStep("typedef", receipt, receipt);
        var qualified = type("shop::OrderId").setAlias(order).setDeclaration(order)
                .addResolutionStep("typedef", order, order);
        var project = Project.create().addFile(file().addItem(shop).addItem(using)
                .addItem(variable("id", first)).addItem(variable("receipt", second)).addItem(variable("qualified", qualified))).build();
        var copied = project.edit().build();
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(copied));
        var numbers = ids(copied);
        var directive = nodes(copied, UsingDirective.class).getFirst();
        var uses = nodes(copied, NamedType.class).stream().filter(t -> t.alias().isPresent()).toList();
        assertEquals(3, uses.size());
        String text = json(copied);
        assertEquals(1, nodes(copied, UsingDirective.class).size());
        for (var use : uses) {
            assertTrue(numbers.keySet().containsAll(use.references()));
            for (var step : use.resolutionSteps())
                assertTrue(text.contains("{\"kind\":\"" + step.kind() + "\",\"viaId\":" + numbers.get(step.via())
                        + ",\"targetId\":" + numbers.get(step.target()) + "}"), text);
        }
        assertSame(directive, uses.get(0).resolutionSteps().getFirst().via());
        assertSame(directive, uses.get(1).resolutionSteps().getFirst().via());
        assertTrue(uses.get(2).resolutionSteps().stream().noneMatch(step -> step.kind().startsWith("using")));
        String source = Cpg.sources(copied, Language.CPP).get("types.cpp");
        assertTrue(source.contains("OrderId id;"), source);
        assertTrue(source.contains("shop::OrderId qualified;"), source);
        String dot = DotVisualizer.toDot(copied, false);
        assertTrue(dot.contains("resolve 0 using-directive via"), dot);
        assertTrue(dot.contains("resolve 0 using-directive target"), dot);
    }

    @Test void forwardDefinitionEdgesAndCompletenessAreNotConfusedWithOwnership() {
        var forward = ClassDeclaration.create().setName("Order");
        forward.setDeclarationDefinition(false);
        var definition = ClassDeclaration.create().setName("Order");
        forward.setDefinition(definition);
        definition.setPreviousDeclaration(forward);
        var use = type("Order").setDeclaration(forward).setCompleteAtUse(false)
                .addResolutionStep("declaration", forward, forward);
        var pointer = VariableDeclaration.create().setName("current").setType(PointerType.create().setPointee(use));
        var project = Project.create().addFile(file().addItem(forward).addItem(pointer).addItem(definition)).build();
        var copy = project.edit().build();
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(copy));
        var declarations = nodes(copy, ClassDeclaration.class);
        assertSame(declarations.getLast(), declarations.getFirst().definition().orElseThrow());
        assertSame(declarations.getFirst(), declarations.getLast().previousDeclaration().orElseThrow());
        var numbers = ids(copy);
        String text = json(copy);
        assertTrue(text.contains("\"isDefinition\":false"), text);
        assertTrue(text.contains("\"completeAtUse\":false"), text);
        assertTrue(text.contains("\"definitionId\":" + numbers.get(declarations.getLast())), text);
        assertTrue(text.contains("\"previousDeclarationId\":" + numbers.get(declarations.getFirst())), text);
        assertTrue(Cpg.sources(copy, Language.CPP).get("types.cpp").startsWith("class Order;\n"));
        String dot = DotVisualizer.toDot(copy, false);
        assertTrue(dot.contains("forward declaration"), dot);
        assertTrue(dot.contains("incomplete at use"), dot);
    }

    @Test void scopedDeclarationsAndStorageClassesRoundTripWithoutChangingTypeSpellings() {
        String source = """
            namespace shop { typedef int Key; typedef Key OrderId; enum Status { pending = 1, paid }; }
            namespace shop { typedef OrderId ReceiptId; }
            namespace orders = shop;
            using namespace shop;
            using shop::Status;
            OrderId id;
            shop::OrderId qualified;
            orders::ReceiptId receipt;
            namespace { static int private_value = 2; }
            extern int counter;
            int counter = 3;
            class Order;
            Order *current;
            class Order { public: typedef int Item; Item value; };
            Order::Item item;
            int run(register int input) { auto int a = 1; register int b = 2; static int calls = 3; return input + a + b + calls; }
            """;
        var project = parse(source);
        String generated = Cpg.sources(project, Language.CPP).get("types.cpp");
        for (String spelling : List.of("using namespace shop;", "using shop::Status;", "namespace orders = shop;",
                "OrderId id;", "shop::OrderId qualified;", "orders::ReceiptId receipt;", "Order::Item item;",
                "static int private_value = 2;", "extern int counter;", "class Order;", "auto int a = 1;", "register int b = 2;", "register int input"))
            assertTrue(generated.contains(spelling), generated);
        assertEquals(generated, Cpg.sources(parse(generated), Language.CPP).get("types.cpp"));
        assertEquals(2, nodes(project, NamespaceDeclaration.class).stream().filter(n -> n.name().equals("shop")).count());
        assertTrue(json(project).contains("\"previousDeclarationId\""));
        assertTrue(json(project).contains("\"kind\":\"EnumeratorDeclaration\""));
    }

    @Test void cLanguageLinkagePreservesVariableDeclarationsAndDefinitions() {
        String source = "extern \"C\" int declared; extern \"C\" { int defined; extern int also_declared; int get() { return defined; } }";
        var project = parse(source);
        String generated = Cpg.sources(project, Language.CPP).get("types.cpp");
        assertTrue(generated.contains("extern \"C\" {"), generated);
        assertFalse(generated.contains("extern \"C\" extern"), generated);
        var original = nodes(project, VariableDeclaration.class);
        var reparsed = nodes(parse(generated), VariableDeclaration.class);
        assertEquals(original.stream().map(VariableDeclaration::isDefinition).toList(), reparsed.stream().map(VariableDeclaration::isDefinition).toList());
        assertEquals(List.of(false, true, false), original.stream().map(VariableDeclaration::isDefinition).toList());
        assertTrue(reparsed.stream().allMatch(v -> v.languageLinkage().equals("C")));
        assertTrue(json(project).contains("\"languageLinkage\":\"C\""));
    }

    @Test void cppOnlyDeclarationsNeverSilentlyDisappearDuringJavaTranslation() {
        for (String extra : List.of("namespace shop {}", "namespace shop {} namespace alias = shop;",
                "namespace shop {} using namespace shop;", "namespace shop { typedef int T; } using shop::T;",
                "enum Status { ready };", "class Forward;", "extern \"C\" int get();")) {
            var project = parse(extra + " class Demo { public: static void main() {} }; int main() { Demo::main(); return 0; }");
            assertFalse(project.languages().contains(Language.JAVA), extra);
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA), extra);
        }
        for (String declaration : List.of("enum Status { ready };", "class Forward;", "static int value = 0;", "register int value = 0;")) {
            var project = parse("class Demo { public: static void main() { " + declaration + " } }; int main() { Demo::main(); return 0; }");
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA), declaration);
        }
        assertFalse(type("shop::OrderId").build().languages().contains(Language.JAVA));
    }

    @Test void provenSemanticErrorsBlockSourceButRemainVisibleInJsonAndDot() {
        var project = Project.create().addFile(file().addItem(variable("value", type("int")))).build();
        var declaration = nodes(project, VariableDeclaration.class).getFirst();
        var broken = project.withDiagnostics("incomplete-type", Map.of(declaration,
                new Diagnostic("incomplete-type", Diagnostic.Severity.ERROR, "Incomplete type at this source point")));
        assertTrue(broken.languages().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(broken, Language.CPP));
        assertTrue(json(broken).contains("incomplete-type"));
        assertTrue(DotVisualizer.toDot(broken).contains("incomplete-type"));
    }

    @Test void sharedNamespaceEditsStillReconcileUnchangedHeaderOccurrences() throws Exception {
        String header = "namespace shop { enum Status { ready = 1 }; typedef int OrderId; }\n";
        Files.writeString(directory.resolve("types.h"), header);
        for (String name : List.of("a.cpp", "b.cpp"))
            Files.writeString(directory.resolve(name), "#include \"types.h\"\nshop::OrderId " + name.charAt(0) + ";\n");
        var project = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        var editor = new ProjectEditor(project);
        editor.replace(nodes(project.files().getFirst(), IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(2).build());
        var error = assertThrows(IllegalArgumentException.class, () -> Cpg.sources(editor.build(), Language.CPP));
        assertTrue(error.getMessage().contains("conflicting physical requirements"), error.getMessage());
        assertEquals(header, Files.readString(directory.resolve("types.h")));
        editor.replace(nodes(project.files().getLast(), IntegerLiteral.class).getFirst(), IntegerLiteral.create().setValue(2).build());
        var output = Cpg.sources(editor.build(), Language.CPP);
        assertEquals(header.replace("1", "2"), output.get("types.h"));
        for (String name : List.of("a.cpp", "b.cpp")) assertEquals(Files.readString(directory.resolve(name)), output.get(name));
    }
    @Test void structuralNamespaceEditsCannotFlattenConditionalHeaderDeclarations() throws Exception {
        String header = """
            namespace shop {
            #ifdef WIDE
            typedef long Key;
            #else
            typedef int Key;
            #endif
            typedef Key OrderId;
            }
            """;
        String source = "#include \"types.h\"\nint main() { return 0; }\n";
        Files.writeString(directory.resolve("types.h"), header);
        Files.writeString(directory.resolve("types.cpp"), source);
        var project = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        var namespace = nodes(project, NamespaceDeclaration.class).getFirst();
        var editor = new ProjectEditor(project);
        editor.replace(namespace, namespace.edit().addMember(variable("counter", type("int"))).build());
        var error = assertThrows(IllegalArgumentException.class, () -> Cpg.sources(editor.build(), Language.CPP));
        assertTrue(error.getMessage().contains("crosses preprocessing directive #ifdef"), error.getMessage());
        assertEquals(header, Files.readString(directory.resolve("types.h")));
        assertEquals(source, Files.readString(directory.resolve("types.cpp")));
    }

    @Test void functionTypedefDeclarationsKeepWrittenAliasInSourceAndJson() {
        String source = "typedef int F(double); typedef F G; F first; G second; int run() { F local; return 0; }";
        var project = parse(source);
        String generated = Cpg.sources(project, Language.CPP).get("types.cpp");
        for (String written : List.of("F first;", "G second;", "F local;")) assertTrue(generated.contains(written), generated);
        var aliases = nodes(project, FunctionDeclaration.class).stream().filter(fn -> fn.writtenType().isPresent()).toList();
        assertEquals(3, aliases.size());
        assertTrue(aliases.stream().allMatch(fn -> fn.parameters().size() == 1 && !fn.isDefinition()));
        assertEquals(generated, Cpg.sources(parse(generated), Language.CPP).get("types.cpp"));
        assertTrue(json(project).contains("\"writtenType\""));
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(project.edit().build()));
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.JAVA));
    }

    @Test void elaboratedTagSpellingSurvivesOrdinaryNameShadowing() {
        var project = parse("struct Tag {}; int Tag; struct Tag *current; int read() { return Tag; }");
        String generated = Cpg.sources(project, Language.CPP).get("types.cpp");
        assertTrue(generated.contains("struct Tag *current;"), generated);
        var tagged = nodes(project, NamedType.class).stream().filter(t -> t.elaboratedKeyword().isPresent()).findFirst().orElseThrow();
        assertInstanceOf(ClassDeclaration.class, tagged.declaration().orElseThrow());
        assertTrue(json(project).contains("\"elaboratedKeyword\":\"struct\""));
        assertTrue(json(project).contains("\"typeIdentity\""));
        assertEquals(generated, Cpg.sources(parse(generated), Language.CPP).get("types.cpp"));
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(project.edit().build()));
        assertFalse(tagged.languages().contains(Language.JAVA));
    }

    @Test void inconsistentEditedDeclarationShapesNeverSilentlyDiscardOwnedSyntax() {
        var forward = ClassDeclaration.create().setName("Forward").addMember(variable("member", type("int")));
        forward.setDeclarationDefinition(false);
        var callable = FunctionDeclaration.create().setName("operation").setReturnType(type("int"))
                .setWrittenType(type("FunctionAlias")).setBody(BlockStatement.create());
        for (Declaration declaration : List.of(forward.build(), callable.build())) {
            assertTrue(declaration.languages().isEmpty());
            assertThrows(IllegalArgumentException.class, () -> declaration.source(Language.CPP));
            var project = Project.create().addFile(file().addItem(declaration.edit())).build();
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP));
        }
    }

    @Test void implicitTagIntroductionsKeepOnlyTheirWrittenSpellingAndRejectMismatchedEdits() {
        var project = parse("struct Introduced *current; int inspect(struct Argument *argument); struct Introduced { int value; };");
        String generated = Cpg.sources(project, Language.CPP).get("types.cpp");
        assertTrue(generated.contains("struct Introduced *current;"), generated);
        assertTrue(generated.contains("int inspect(struct Argument *argument);"), generated);
        assertFalse(generated.contains("struct Introduced;"), generated);
        assertFalse(generated.contains("struct Argument;"), generated);
        assertTrue(json(project).contains("\"implicit\":true"));
        assertEquals(generated, Cpg.sources(parse(generated), Language.CPP).get("types.cpp"));
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(project.edit().build()));
        var original = nodes(project, NamedType.class).stream().filter(type -> type.name().equals("Introduced")).findFirst().orElseThrow();
        var editor = new ProjectEditor(project);
        editor.replace(original, type("Different").setElaboratedKeyword("struct").build());
        var changed = editor.build();
        assertTrue(changed.languages().isEmpty());
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(changed, Language.CPP));
    }

    @Test void qualifiedValuesExportTargetsAndLeadingGlobalScopeSpelling() {
        var project = parse("namespace shop { int value = 2; enum State { active = 3 }; } int global = 1; int read() { return shop::value + shop::active + ::global; }");
        var members = nodes(project, ScopeMemberExpression.class);
        assertEquals(2, members.size());
        assertInstanceOf(VariableDeclaration.class, members.getFirst().target().orElseThrow());
        assertInstanceOf(EnumeratorDeclaration.class, members.getLast().target().orElseThrow());
        var numbers = ids(project);
        String text = json(project);
        for (var member : members) assertTrue(text.contains("\"targetId\":" + numbers.get(member.target().orElseThrow())), text);
        String generated = Cpg.sources(project, Language.CPP).get("types.cpp");
        assertTrue(generated.contains("::global"), generated);
        assertEquals(generated, Cpg.sources(parse(generated), Language.CPP).get("types.cpp"));
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(project.edit().build()));
        assertFalse(NameExpression.create().setName("::global").build().languages().contains(Language.JAVA));
    }

    @Test void inheritedCLanguageLinkageNeverCreatesIllegalBlockScopeLinkageSpecifications() {
        var project = parse("extern \"C\" int helper() { return 2; } int main() { int helper(); return helper(); }");
        String generated = Cpg.sources(project, Language.CPP).get("types.cpp");
        assertTrue(generated.contains("extern \"C\""), generated);
        assertFalse(generated.substring(generated.indexOf("int main")).contains("extern \"C\""), generated);
        assertEquals(generated, Cpg.sources(parse(generated), Language.CPP).get("types.cpp"));

        var preserved = parse("#define RET 0\nextern \"C\" int helper(); int main() { int helper(); return RET; }");
        var local = nodes(preserved, FunctionDeclaration.class).stream().filter(fn -> fn.name().equals("helper")).toList().getLast();
        var replacement = FunctionDeclaration.create().setName("helper").setReturnType(type("int")).addModifier("extern");
        replacement.setDeclarationDefinition(false);
        var editor = new ProjectEditor(preserved);
        editor.replace(local, replacement.build());
        String patched = Cpg.sources(editor.build(), Language.CPP).get("types.cpp");
        assertTrue(patched.contains("int main() { extern int helper();"), patched);
        assertFalse(patched.substring(patched.indexOf("int main")).contains("extern \"C\""), patched);
    }

}
