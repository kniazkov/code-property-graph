/* Behavioral and snapshot-integrity tests for CPG. MIT License. */
package com.kniazkov.cpg;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import com.kniazkov.cpg.Graph.*;

class CpgTest {
    @TempDir Path temp;
    private Graph read(String body, Cpg.Stage stage) throws Exception {
        Files.writeString(temp.resolve("Program.java"), "public class Program { public static void main(String[] args) { " + body + " } }");
        return Cpg.read(temp, new Cpg.Options(Language.JAVA, stage));
    }
    private List<Node> all(Node n) {
        List<Node> list = new ArrayList<>(); list.add(n);
        for (Node child : n.children()) list.addAll(all(child)); return list;
    }
    @Test void stagesAndSnapshots() throws Exception {
        Graph parsed = read("int a = 2; System.out.println(a + (2 + 3) * 4);", Cpg.Stage.PARSED);
        String before = Cpg.sources(parsed, Language.JAVA).get("Program.java");
        Graph resolved = Passes.resolve(parsed), green = Passes.green(resolved), folded = Passes.fold(green);
        assertTrue(all(parsed.root()).stream().allMatch(n -> n.reference().isEmpty()));
        Node use = all(resolved.root()).stream().filter(n -> n.kind() == Kind.NAME && n.text().equals("a")).findFirst().orElseThrow();
        assertEquals(Kind.DECLARE, use.reference().orElseThrow().kind());
        assertTrue(all(green.root()).stream().anyMatch(n -> n.kind() == Kind.PRINT));
        assertTrue(Cpg.sources(folded, Language.JAVA).get("Program.java").contains("(a + 20)"));
        assertEquals(before, Cpg.sources(parsed, Language.JAVA).get("Program.java"));
        Set<Node> members = Collections.newSetFromMap(new IdentityHashMap<>()); members.addAll(all(folded.root()));
        for (Node n : members) n.reference().ifPresent(ref -> assertTrue(members.contains(ref)));
        Node print = all(folded.root()).stream().filter(n -> n.kind() == Kind.PRINT).findFirst().orElseThrow();
        assertTrue(print.source(Language.CPP).contains("std::cout"));
    }
    @Test void unsafeArithmeticRemains() throws Exception {
        Graph graph = read("int a = 2147483647 + 1; int b = 10 / 0; int c = -2147483648 / -1; int d = -7 / 2;", Cpg.Stage.FOLDED);
        String code = Cpg.sources(graph, Language.JAVA).get("Program.java");
        assertTrue(code.contains("(2147483647 + 1)")); assertTrue(code.contains("(10 / 0)"));
        assertTrue(code.contains("(-2147483648 / -1)")); assertTrue(code.contains("int d = -3;"));
    }
    @Test void replacementRedirectsAndDeletionIsGuarded() throws Exception {
        Graph original = read("int a = 2; System.out.println(a);", Cpg.Stage.RESOLVED);
        Builder b = original.mutable();
        Draft declaration = b.nodes().stream().filter(n -> n.kind() == Kind.DECLARE).findFirst().orElseThrow();
        assertThrows(IllegalStateException.class, () -> b.remove(declaration));
        Draft replacement = new Draft(Kind.DECLARE, "a", new Draft(Kind.INTEGER, "5"));
        b.replace(declaration, replacement);
        assertTrue(b.nodes().stream().anyMatch(n -> n.reference() == replacement));
        Graph updated = b.build(); replacement.text("changedLater");
        assertTrue(Cpg.sources(updated, Language.JAVA).get("Program.java").contains("int a = 5"));
        assertTrue(Cpg.sources(original, Language.JAVA).get("Program.java").contains("int a = 2"));
    }
    @Test void invalidStateCannotBePublished() {
        Draft bad = new Draft(Kind.DECLARE, "x");
        Builder builder = new Builder(new Draft(Kind.PROJECT, "p", new Draft(Kind.FILE, "a.java", new Draft(Kind.CLASS, "C", new Draft(Kind.METHOD, "static void main()", new Draft(Kind.BLOCK, "", bad))))));
        assertThrows(IllegalStateException.class, builder::build);
        bad.add(new Draft(Kind.INTEGER, "1")); builder.build();
        bad.reference(new Draft(Kind.DECLARE, "foreign", new Draft(Kind.INTEGER, "2")));
        assertThrows(IllegalStateException.class, builder::build);
    }
    @Test void folderFilterAndNoRecursion() throws Exception {
        Files.createDirectory(temp.resolve("nested")); Files.writeString(temp.resolve("nested/bad.java"), "bad");
        Files.writeString(temp.resolve("ignored.cpp"), "bad"); Files.writeString(temp.resolve("notes.txt"), "bad");
        Graph g = read("int a = 1;", Cpg.Stage.PARSED); assertEquals(1, g.root().children().size());
    }
    @Test void diagnosticsRejectWholeProject() throws Exception {
        Files.writeString(temp.resolve("bad.java"), "class Bad {\n public static void main(String[] args) { while (1) {} } }");
        ParseException e = assertThrows(ParseException.class, () -> read("int a = 1;", Cpg.Stage.PARSED));
        assertEquals("bad.java", e.file()); assertEquals(2, e.line()); assertTrue(e.column() > 0);
    }
    @Test void commentsAndUnresolvedNames() throws Exception {
        Graph g = read("/* comment */ int a = external(2); // line\nSystem.out.println(a);", Cpg.Stage.RESOLVED);
        assertTrue(all(g.root()).stream().anyMatch(n -> n.kind() == Kind.NAME && n.text().equals("external") && n.reference().isEmpty()));
        assertFalse(Cpg.sources(g, Language.JAVA).get("Program.java").contains("comment"));
    }
    @Test void cppAndCrossFileReferences() throws Exception {
        Files.writeString(temp.resolve("a.cpp"), "#include <iostream>\nclass Program { public: static void main() { int a = 2 + 3; std::cout << a << std::endl; } };");
        Files.writeString(temp.resolve("b.cpp"), "int main() { Program::main(); return 0; }");
        Graph g = Cpg.read(temp, new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED));
        assertEquals(2, g.root().children().size());
        assertTrue(all(g.root()).stream().anyMatch(n -> n.kind() == Kind.CALL && n.reference().isPresent()));
        assertTrue(Cpg.sources(g, Language.CPP).get("a.cpp").contains("int a = 5;"));
        assertThrows(IllegalArgumentException.class, () -> g.root().children().get(0).source(Language.JAVA));
    }
}
