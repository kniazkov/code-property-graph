/* C++03 structured template export and declaration-only catalogue boundaries. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TemplateExportTest {
    private static Project parse(String text) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("templates.cpp", text, Language.CPP)).build());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }});
        return result;
    }

    @Test void dependentSegmentsAndTemplateDefaultsRoundTrip() {
        String text = "template<class T, int N = 3, template<class U> class C = Holder> class Box { public: typename T::template rebind<int>::other value; };";
        // A written snapshot does not require declaration lookup to preserve its syntax.
        var project = Project.create().addFile(Parser.parse("templates.cpp", text, Language.CPP)).build();
        var copy = project.edit().build();
        assertEquals(ProjectJson.encode(project), ProjectJson.encode(copy));
        for (var p : List.of(project, copy)) {
            String json = ProjectJson.encode(p).toText("");
            assertTrue(json.contains("typenameKeyword"), json);
            assertTrue(json.contains("templateKeyword"), json);
            assertTrue(json.contains("defaultArgument"), json);
            assertTrue(json.contains("qualifier"), json);
            String emitted = Source.render(p.files().getFirst(), Language.CPP);
            assertTrue(emitted.contains("typename T::template rebind<int >::other"), emitted);
            assertFalse(emitted.contains(">>"), emitted);
            var reparsed = Parser.parse("again.cpp", emitted, Language.CPP).build();
            assertEquals(nodes(p, TemplateArgument.class).size(), nodes(reparsed, TemplateArgument.class).size());
        }
    }

    @Test void catalogueAndInstancesAreOwnedExportedAndNeverEmitted() {
        String text = "#include <vector>\n// keep this source\ntemplate<class T> unsigned long count(const T& x) { return x.size(); }\nint main() { std::vector<int> x(3); return count(x); }\n";
        var original = parse(text);
        var copy = original.edit().build();
        assertEquals(ProjectJson.encode(original), ProjectJson.encode(copy));
        for (var project : List.of(original, copy)) {
            assertTrue(project.librarySources().size() >= 1);
            assertEquals(1, project.templateInstances().size());
            var catalogue = project.librarySources().stream().filter(f->LibraryDeclarations.headerName(f).equals("vector")).findFirst().orElseThrow();
            assertTrue(LibraryDeclarations.visible(project.files().getFirst(), catalogue));
            assertTrue(nodes(catalogue, CallableDeclaration.class).stream().allMatch(c -> c.body().isEmpty()));
            assertTrue(nodes(catalogue, VariableDeclaration.class).isEmpty());
            String json = ProjectJson.encode(project).toText("");
            assertTrue(json.contains("libraryDeclaration"), json);
            assertTrue(json.contains("templateInstances"), json);
            assertTrue(json.contains("originId"), json);
            String dot = DotVisualizer.toDot(project, false);
            assertTrue(dot.contains("library declarations only"), dot);
            assertTrue(dot.contains("TemplateInstance"), dot);
            assertEquals(Map.of("templates.cpp", text), Source.files(project, Language.CPP));
            assertEquals(1, Source.physicalSources(project).size());
            var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
            project.walk(new NodeVisitor() { @Override public void visit(Node n) { assertTrue(owned.add(n)); }});
            project.walk(new NodeVisitor() { @Override public void visit(Node n) { for (Node ref : n.references()) assertTrue(owned.contains(ref)); }});
        }
    }

    @Test void catalogueProvenanceUsesStableVirtualPathsInsteadOfTheProcessDirectory() {
        var project = LibraryDeclarations.install(Project.create()
                .addFile(Parser.parse("physical.cpp", "#include <vector>\nint f();", Language.CPP)).build());
        var library = project.librarySources().getFirst();
        var unit = library.provenance().orElseThrow().unit();
        String expected = unit.configuration().source().path().toString();
        assertTrue(expected.startsWith("/__cpg_library__/"));
        assertEquals(expected, unit.translationUnit());
        assertEquals(java.nio.file.Path.of(expected), unit.configuration().source().path());
        assertEquals(unit.configuration().source().path().getParent(), unit.configuration().directory());
        assertEquals(java.time.Instant.EPOCH, unit.translationTime());
        assertTrue(unit.sources().isEmpty());
        assertTrue(unit.tokens().isEmpty());
        library.walk(new NodeVisitor() { @Override public void visit(Node node) {
            node.range().ifPresent(range -> assertEquals(expected, range.file()));
            node.provenance().ifPresent(provenance -> assertSame(unit, provenance.unit()));
        }});
        for (var token : unit.tokens()) {
            assertEquals(expected, token.spellingRange().file());
            assertEquals(expected, token.expansionRange().file());
        }
    }

    @Test void libraryRoleCannotBeSpoofedByPhysicalFilename() {
        var physical = Parser.parse("__cpg_library_vector_user.hpp", "struct Real { int value; };", Language.CPP).build();
        assertFalse(LibraryDeclarations.isLibrary(physical));
        assertFalse(LibraryDeclarations.isLibrary(physical.items().getFirst()));
    }

    @Test void libraryProvenanceRetainsTheFullTargetProfile() {
        var wide = com.kniazkov.cpg.build.PlatformProfile.defaults();
        var narrow = wide.toBuilder().name("narrow-long").sizeBits("long", 32).alignmentBits("long", 32)
                .sizeBits("unsigned long", 32).alignmentBits("unsigned long", 32).build();
        var project = Project.create();
        int index = 0;
        for (var profile : List.of(wide, narrow)) {
            var path = java.nio.file.Path.of("profile" + index++ + ".cpp").toAbsolutePath();
            var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(
                    new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03")
                    .withPlatform(profile);
            project.addFile(Parser.parse(unit, "#include <vector>\nint f();"));
        }
        var snapshot = LibraryDeclarations.install(project.build());
        assertEquals(2, snapshot.librarySources().size());
        for (int i = 0; i < 2; i++) {
            var source = snapshot.files().get(i);
            var library = snapshot.librarySources().get(i);
            assertEquals(source.provenance().orElseThrow().unit().configuration().platform().identityHash(),
                    library.provenance().orElseThrow().unit().configuration().platform().identityHash());
            assertTrue(LibraryDeclarations.visible(source, library));
            assertFalse(LibraryDeclarations.visible(source, snapshot.librarySources().get(1 - i)));
        }
    }

    @Test void vectorCatalogueIsSharedOnlyByIncludingTranslationUnits() {
        var project = LibraryDeclarations.install(Project.create()
                .addFile(Parser.parse("one.cpp", "#include <vector>\nint one();", Language.CPP))
                .addFile(Parser.parse("two.cpp", "#include <vector>\nint two();", Language.CPP))
                .addFile(Parser.parse("other.cpp", "int other();", Language.CPP)).build());
        assertEquals(1, project.librarySources().size());
        var library = project.librarySources().getFirst();
        assertTrue(LibraryDeclarations.visible(project.files().get(0), library));
        assertTrue(LibraryDeclarations.visible(project.files().get(1), library));
        assertFalse(LibraryDeclarations.visible(project.files().get(2), library));
        assertSame(project, LibraryDeclarations.install(project));
    }
}
