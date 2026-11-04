/* Code Property Graph: typed pipeline and snapshot integrity. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.parser.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the public folder pipeline, individual analysis passes and snapshot integrity. */
class CpgTest {
    @TempDir Path temp;

    private Project read(String body, Cpg.Stage stage) throws Exception {
        Files.writeString(temp.resolve("Program.java"),
                "public class Program { public static void main(String[] args) { " + body + " } }");
        return Cpg.read(temp, new Cpg.Options(Language.JAVA, stage));
    }

    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        List<T> matches = new ArrayList<>();
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                if (type.isInstance(node)) matches.add(type.cast(node));
            }
        });
        return matches;
    }

    @Test void stagesAndIndependentSnapshots() throws Exception {
        Project parsed = read("int a = 2; System.out.println(a + (2 + 3) * 4);", Cpg.Stage.PARSED);
        Project resolved = NameResolver.resolve(parsed);
        Project green = ConsoleNormalizer.green(resolved);
        Project folded = ConstantFolder.fold(green);
        assertTrue(nodes(parsed, NameExpression.class).stream().allMatch(n -> n.target().isEmpty()));
        var use = nodes(resolved, NameExpression.class).stream().filter(n -> n.name().equals("a")).findFirst().orElseThrow();
        assertSame(nodes(resolved, VariableDeclaration.class).getFirst(), use.target().orElseThrow());
        assertEquals(1, nodes(green, PrintStatement.class).size());
        assertTrue(Cpg.sources(folded, Language.JAVA).get("Program.java").contains("(a + 20)"));
        assertEquals(0, nodes(parsed, PrintStatement.class).size());
        Set<Node> members = Collections.newSetFromMap(new IdentityHashMap<>());
        members.addAll(nodes(folded, Node.class));
        for (Node n : members) assertTrue(members.containsAll(n.references()));
    }

    @Test void replacementRedirectsAndDeletionIsGuarded() throws Exception {
        Project original = read("int a = 2; System.out.println(a);", Cpg.Stage.RESOLVED);
        var declaration = nodes(original, VariableDeclaration.class).getFirst();
        var editor = new ProjectEditor(original);
        assertThrows(IllegalStateException.class, () -> editor.remove(declaration));
        editor.replace(declaration, declaration.edit().setInitializer(IntegerLiteral.create().setValue(5)).build());
        var changed = editor.build();
        var newDecl = nodes(changed, VariableDeclaration.class).getFirst();
        var use = nodes(changed, NameExpression.class).stream().filter(n -> n.name().equals("a")).findFirst().orElseThrow();
        assertSame(newDecl, use.target().orElseThrow());
        assertEquals(5, ((IntegerLiteral)newDecl.initializer().orElseThrow()).value());
        assertEquals(2, ((IntegerLiteral)declaration.initializer().orElseThrow()).value());
    }

    @Test void invalidStateCannotBePublished() {
        assertThrows(IllegalStateException.class, () -> VariableDeclaration.create().build());
        var outside = VariableDeclaration.create().setName("outside");
        var name = NameExpression.create().setName("outside").setTarget(outside);
        var variable = VariableDeclaration.create().setName("v").setInitializer(name);
        var project = Project.create().addFile(SourceFile.create().setPath("a.cpp").setLanguage(Language.CPP).addItem(variable));
        assertThrows(IllegalStateException.class, project::build);
    }

    @Test void unsafeArithmeticRemains() throws Exception {
        var project = read("int a = 2147483647 + 1; int b = 10 / 0; int c = -2147483648 / -1; int d = -7 / 2;", Cpg.Stage.FOLDED);
        var values = nodes(project, VariableDeclaration.class);
        assertInstanceOf(AddExpression.class, values.get(0).initializer().orElseThrow());
        assertInstanceOf(DivideExpression.class, values.get(1).initializer().orElseThrow());
        assertInstanceOf(DivideExpression.class, values.get(2).initializer().orElseThrow());
        assertEquals(-3, ((IntegerLiteral)values.get(3).initializer().orElseThrow()).value());
    }

    @Test void assignmentIsRightAssociativeAndOwnedByStatement() throws Exception {
        var p = read("int a = 0; int b = 0; a = b = 7;", Cpg.Stage.RESOLVED);
        var statement = nodes(p, ExpressionStatement.class).getFirst();
        var outer = assertInstanceOf(AssignmentExpression.class, statement.expression());
        assertInstanceOf(AssignmentExpression.class, outer.value());
        assertEquals(2, nodes(outer, NameExpression.class).stream().filter(n -> n.target().isPresent()).count());
    }

    @Test void shadowedConsoleAndUnresolvedNamesSurvive() throws Exception {
        var p = read("/* comment */ int System = external(2); System.out.println(System);", Cpg.Stage.GREEN);
        assertTrue(nodes(p, PrintStatement.class).isEmpty());
        var external = nodes(p, NameExpression.class).stream().filter(n -> n.name().equals("external")).findFirst().orElseThrow();
        assertTrue(external.target().isEmpty());
        assertFalse(Cpg.sources(p, Language.JAVA).get("Program.java").contains("comment"));
    }

    @Test void folderFilterAndDiagnostics() throws Exception {
        Files.createDirectory(temp.resolve("nested"));
        Files.writeString(temp.resolve("nested/bad.java"), "bad");
        Files.writeString(temp.resolve("ignored.cpp"), "bad");
        assertEquals(1, read("int x = 1;", Cpg.Stage.PARSED).files().size());
        Files.writeString(temp.resolve("bad.java"), "class Bad {\npublic static void main(String[] a) { while (1) {} } }");
        var e = assertThrows(ParseException.class, () -> read("int x = 1;", Cpg.Stage.PARSED));
        assertEquals("bad.java", e.file());
        assertEquals(2, e.line());
        assertTrue(e.column() > 0);
    }

    @Test void cppCrossFileReferences() throws Exception {
        Files.writeString(temp.resolve("a.cpp"), "#include <iostream>\nclass Program { public: static void main() { int a = 2 + 3; std::cout << a << std::endl; } };");
        Files.writeString(temp.resolve("b.cpp"), "int main() { Program::main(); return 0; }");
        var project = Cpg.read(temp, new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED));
        var call = nodes(project, CallExpression.class).getFirst();
        assertSame(nodes(project, MethodDeclaration.class).getFirst(), call.target().orElseThrow());
        assertTrue(Cpg.sources(project, Language.CPP).get("a.cpp").contains("int a = 5;"));
    }
}
