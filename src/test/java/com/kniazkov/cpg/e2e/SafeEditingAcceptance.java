/* Safe immutable C++03 editing and native round trips. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.utilities.DeclarationRename;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

final class SafeEditingAcceptance {
    private SafeEditingAcceptance() { }
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03");
    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        var original = Cpg.read(cli.input, OPTIONS);
        assertFalse(original.hasErrors());
        var originalJson = ProjectJson.encode(original);
        var output = new ArrayList<Path>(); output.add(cli.input);
        var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--svg", "snapshot.svg").expectStatus(0);
        output.add(run.work().resolve("generated"));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        var alias = nodes(original, TypedefDeclaration.class).stream().filter(d -> d.name().equals("OrderId")).findFirst().orElseThrow();
        var renamed = DeclarationRename.rename(original, alias, "PurchaseId");
        assertFalse(renamed.hasErrors());
        var base = nodes(renamed, ClassDeclaration.class).stream().filter(c -> c.name().equals("Base")).findFirst().orElseThrow();
        var method = nodes(base, MethodDeclaration.class).stream().filter(m -> m.name().equals("value")).findFirst().orElseThrow();
        assertEquals(2, DeclarationRename.affectedDeclarations(renamed, method).size());
        var virtual = DeclarationRename.rename(renamed, method, "readValue");
        assertFalse(virtual.hasErrors());
        assertEquals(2, nodes(virtual, MethodDeclaration.class).stream().filter(m -> m.name().equals("readValue")).count());
        output.add(emit(virtual, work.resolve("renamed")));
        assertTrue(DotVisualizer.toDot(virtual).contains("readValue"));
        var shop = nodes(virtual, NamespaceDeclaration.class).stream().filter(n -> n.name().equals("shop")).findFirst().orElseThrow();
        var commerce = DeclarationRename.rename(virtual, shop, "commerce");
        var namespaceAlias = nodes(commerce, NamespaceAliasDeclaration.class).getFirst();
        var chosen = DeclarationRename.rename(commerce, namespaceAlias, "chosen");
        var derived = nodes(chosen, ClassDeclaration.class).stream().filter(c -> c.name().equals("Derived")).findFirst().orElseThrow();
        var specialized = DeclarationRename.rename(chosen, derived, "Specialized");
        assertFalse(specialized.hasErrors());
        Path renamedScopes = emit(specialized, work.resolve("renamed-scopes"));
        String scopedText = Files.readString(renamedScopes.resolve("main.cpp"));
        assertTrue(scopedText.contains("Specialized::Specialized()"));
        assertTrue(scopedText.contains("Specialized::~Specialized()"));
        assertTrue(scopedText.contains("chosen::PurchaseId"));
        assertTrue(scopedText.contains("namespace commerce"));
        output.add(renamedScopes);
        assertEquals(originalJson, ProjectJson.encode(original), "Original snapshot is immutable");

        var file = original.files().getFirst();
        var items = new ArrayList<NodeBuilder<? extends Node>>();
        for (Node item : file.items()) {
            items.add(item.edit());
            if (item instanceof UsingDirective) items.add(UsingDirective.create().setNamespaceName("archive"));
        }
        var editor = new ProjectEditor(original); editor.replace(file, file.edit().setItems(items).build());
        var ambiguous = editor.build();
        Path qualified = emit(ambiguous, work.resolve("qualified"));
        assertTrue(Files.readString(qualified.resolve("main.cpp")).contains("::shop::OrderId"));
        var reparsed = Cpg.read(qualified, OPTIONS);
        var order = nodes(reparsed, VariableDeclaration.class).stream().filter(v -> v.name().equals("order")).findFirst().orElseThrow();
        var type = nodes(order, NamedType.class).getFirst();
        assertEquals("OrderId", ((TypedefDeclaration) type.declaration().orElseThrow()).name());
        assertTrue(nodes(reparsed, NamespaceDeclaration.class).stream().filter(n -> n.name().equals("shop"))
                .anyMatch(n -> nodes(n, TypedefDeclaration.class).contains(type.declaration().orElseThrow())));
        output.add(qualified);

        var invalid = new CliSandbox(Files.createDirectory(work.resolve("ambiguous-cli"))).source("main.cpp",
                "namespace shop { typedef int OrderId; } namespace archive { typedef long OrderId; }\nusing namespace shop; using namespace archive; OrderId order; int main(){return 0;}\n");
        var rejected = invalid.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(1);
        rejected.diagnosticSnapshot();
        var conflictCli = new CliSandbox(Files.createDirectory(work.resolve("conflict-cli"))).source("main.cpp",
                "struct Device { void restart(int); void restart(int); }; int main(){return 0;}\n");
        conflictCli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(1).diagnosticSnapshot();
        var deviceCli = new CliSandbox(Files.createDirectory(work.resolve("device"))).source("main.cpp",
                "struct Device { void reset(int); void restart(int); }; int main(){return 0;}\n");
        var device = Cpg.read(deviceCli.input, OPTIONS);
        var reset = nodes(device, MethodDeclaration.class).stream().filter(m -> m.name().equals("reset")).findFirst().orElseThrow();
        var conflict = DeclarationRename.rename(device, reset, "restart");
        assertTrue(conflict.hasErrors()); assertFalse(device.hasErrors());
        assertEquals(2, nodes(conflict, MethodDeclaration.class).stream().filter(m -> m.name().equals("restart")).count());
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(conflict, Language.CPP));

        var repairCli = new CliSandbox(Files.createDirectory(work.resolve("repair"))).source("main.cpp", "int x = ; int main(){return x;}\n");
        var broken = Cpg.readRecovering(repairCli.input, OPTIONS);
        broken = broken.withDiagnostics("independent-warning", Map.of(broken.files().getFirst(),
                new Diagnostic("independent-warning", Diagnostic.Severity.WARNING, "Keep this independent warning")));
        var brokenJson = ProjectJson.encode(broken);
        var repair = new ProjectEditor(broken); repair.replace(nodes(broken, ErrorExpression.class).getFirst(), IntegerLiteral.create().setValue(0).build());
        var fixed = repair.build();
        assertFalse(fixed.hasErrors()); assertTrue(broken.hasErrors());
        assertTrue(fixed.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("independent-warning")));
        assertEquals(brokenJson, ProjectJson.encode(broken));
        output.add(emit(fixed, work.resolve("repaired")));
        var twoCli = new CliSandbox(Files.createDirectory(work.resolve("two-errors"))).source("main.cpp", "int x = ; int y = ; int main(){return 0;}\n");
        var two = Cpg.readRecovering(twoCli.input, OPTIONS);
        var oneRepair = new ProjectEditor(two);
        oneRepair.replace(nodes(two, ErrorExpression.class).getFirst(), IntegerLiteral.create().setValue(0).build());
        var oneLeft = oneRepair.build();
        assertTrue(oneLeft.hasErrors()); assertEquals(1, nodes(oneLeft, ErrorExpression.class).size());
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(oneLeft, Language.CPP));
        assertEquals(2, nodes(two, ErrorExpression.class).size());
        assertEquals(originalJson, ProjectJson.encode(original));
        assertEquals(Files.readString(fixture.resolve("sources/main.cpp")), Files.readString(cli.input.resolve("main.cpp")));
        return List.copyOf(output);
    }
    private static Path emit(Project project, Path output) throws Exception {
        var files = Cpg.sources(project, Language.CPP); Files.createDirectories(output);
        for (var entry : files.entrySet()) { Path path = output.resolve(entry.getKey()); Files.createDirectories(path.getParent()); Files.writeString(path, entry.getValue()); }
        assertFalse(Cpg.read(output, OPTIONS).hasErrors()); return output;
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } }); return result;
    }
}
