/* Concrete catalogue classes demand their inherited class-template specializations. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class StrstreamInheritanceTest {
    private static Project resolve(String code) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("main.cpp", code, Language.CPP, "c++03")).build());
    }
    @Test void returnedBuffersDemandInstantiatedBaseMembersOnBothProfilesAndRebuilds() {
        for (var profile : List.of(PlatformProfile.defaults(), PlatformProfile.builder().sizeType("unsigned int").build())) {
            Path path = Path.of("/__no_host_stl__/returned-buffer.cpp");
            var unit = new Build.TranslationUnit(new Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03").withPlatform(profile);
            var project = NameResolver.resolve(Project.create().addFile(Parser.parse(unit, """
                    #include <strstream>
                    std::ostrstream& identity(std::ostrstream& stream) { return stream; }
                    void f(std::ostrstream& out, std::istrstream& in, std::strstream& both) {
                        out.rdbuf()->sputc('a');
                        in.rdbuf()->sgetc();
                        both.rdbuf()->pubseekoff(0, std::ios_base::beg);
                        identity(out).rdbuf()->sputc('b');
                    }
                    """)).build());
            for (var snapshot : List.of(project, NameResolver.resolve(project.edit().build()))) {
                assertFalse(snapshot.hasErrors(), () -> snapshot.semanticDiagnostics().toString());
                var base = snapshot.classTemplateInstances().stream().filter(i -> i.origin().name().equals("basic_streambuf")).findFirst().orElseThrow();
                var targets = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
                base.declaration().walk(new NodeVisitor() { @Override public void visit(Node node) { targets.add(node); } });
                var calls = new ArrayList<CallExpression>();
                snapshot.files().getFirst().walk(new NodeVisitor() { @Override public void visit(CallExpression call) {
                    assertTrue(call.target().isPresent(), () -> call.range().toString());
                    if (call.target().map(c -> Set.of("sputc", "sgetc", "pubseekoff").contains(c.name())).orElse(false)) calls.add(call);
                } });
                assertEquals(4, calls.size());
                for (var call : calls) assertTrue(targets.contains(call.target().orElseThrow()));
            }
        }
    }
    @Test void concreteAndTemplateStreamWrappersSelectInstantiatedIntegerOperators() {
        for (String declaration : List.of("std::strstream stream;", "std::stringstream stream;", "Wrapper stream;")) {
            var project = resolve("#include <strstream>\n#include <sstream>\nstruct Wrapper : std::strstream {};\nint main() { " + declaration
                    + " stream << 17 << 18; int value = 0; stream >> value >> value; return value; }\n");
            assertFalse(project.hasErrors(), () -> project.semanticDiagnostics().toString());
            var operators = new ArrayList<Expression>();
            project.files().getFirst().walk(new NodeVisitor() { @Override public void visit(Expression expression) {
                if (expression instanceof ShiftLeftExpression || expression instanceof ShiftRightExpression) operators.add(expression);
            } });
            assertEquals(4, operators.size());
            for (var operator : operators) {
                var target = operator.operatorTarget().orElseThrow();
                assertTrue(target instanceof MethodDeclaration);
                assertTrue(LibraryDeclarations.isLibrary(target));
                assertEquals(operator instanceof ShiftLeftExpression ? "int" : "reference(int)", target.parameters().getFirst().type().orElseThrow().canonicalIdentity());
            }
            for (String name : List.of("basic_iostream", "basic_ostream", "basic_istream"))
                assertTrue(project.classTemplateInstances().stream().anyMatch(i -> i.origin().name().equals(name)));
        }
    }
    @Test void substitutedReceiversDemandBasesOnlyInUsedTemplateBodies() {
        for (String code : List.of(
                "template<class T> void write(T& s){s.rdbuf()->sputc('a');} void f(std::ostrstream& s){write(s);}",
                "template<class T> struct Writer { void write(T& s){s.rdbuf()->sputc('a');} }; void f(std::ostrstream& s){Writer<std::ostrstream> w;w.write(s);}")) {
            var project = resolve("#include <strstream>\n" + code);
            for (var snapshot : List.of(project, NameResolver.resolve(project.edit().build()))) {
                assertFalse(snapshot.hasErrors(), () -> snapshot.semanticDiagnostics().toString());
                var base = snapshot.classTemplateInstances().stream().filter(i -> i.origin().name().equals("basic_streambuf")).findFirst().orElseThrow();
                var methods = new HashSet<Node>(base.declaration().members());
                var calls = new ArrayList<CallExpression>();
                var visitor = new NodeVisitor() { @Override public void visit(CallExpression call) {
                    if (call.callee() instanceof MemberExpression member && member.member().equals("sputc")) calls.add(call);
                } };
                snapshot.templateInstances().forEach(i -> i.declaration().walk(visitor));
                snapshot.classTemplateInstances().forEach(i -> i.declaration().walk(visitor));
                assertEquals(1, calls.size());
                assertTrue(methods.contains(calls.getFirst().target().orElseThrow()));
            }
        }
        var unused = resolve("#include <strstream>\ntemplate<class T> struct Reader { void read(T& s){s.rdbuf()->sgetc();} }; Reader<std::ostrstream> r;");
        assertFalse(unused.hasErrors(), () -> unused.semanticDiagnostics().toString());
        assertTrue(unused.classTemplateInstances().stream().noneMatch(i -> i.origin().name().equals("basic_streambuf")));
    }
    @Test void includeOnlyDoesNotInstantiateUnusedStreamBases() {
        var project = resolve("#include <strstream>\nint main() { return 0; }\n");
        assertFalse(project.hasErrors());
        assertTrue(project.classTemplateInstances().isEmpty());
        assertEquals(1, project.librarySources().size());
        assertTrue(project.librarySources().getFirst().items().isEmpty());
    }
}
