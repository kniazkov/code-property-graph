/* Basic dependent-template semantics. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TemplateInstanceTest {
    @org.junit.jupiter.api.io.TempDir java.nio.file.Path directory;
    @Test void foldingProcessesConcreteInstanceWithoutRewritingDependentOrigin() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("fold.cpp"), "template<class T> unsigned long width(T x) { return sizeof(T); } int main() { return width(1); }");
        var p = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.FOLDED, "c++03"));
        var instance = p.templateInstances().getFirst();
        assertEquals(1, nodes(instance.origin(), SizeofExpression.class).size());
        assertTrue(nodes(instance.declaration(), SizeofExpression.class).isEmpty());
        assertTrue(nodes(instance.declaration(), IntegerLiteral.class).stream().anyMatch(literal -> literal.bigValue().intValue() == 4));
        assertTrue(instance.declaration().returnType().orElseThrow().semanticType().isPresent());
    }
    @Test void concreteInstanceFlowUsesItsInvocationTargetProfile() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("profile.cpp"), "template<class T> int choose(T value) { int x=1; if(sizeof(long)==4) x=2; return x; } int main() { return choose(1); }");
        for (boolean narrow : List.of(false, true)) {
            var profile = com.kniazkov.cpg.build.PlatformProfile.defaults();
            if (narrow) profile = profile.toBuilder().name("narrow").sizeBits("long", 32).alignmentBits("long", 32).build();
            var p = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03").withPlatform(profile));
            var instance = p.templateInstances().getFirst();
            var flow = p.flows().stream().filter(value -> value.entry() == instance.declaration()).findFirst().orElseThrow();
            assertTrue(flow.complete());
            var branches = flow.control().stream().map(FunctionFlow.ControlEdge::kind).toList();
            assertTrue(branches.contains(narrow ? FunctionFlow.Branch.TRUE : FunctionFlow.Branch.FALSE));
            assertFalse(branches.contains(narrow ? FunctionFlow.Branch.FALSE : FunctionFlow.Branch.TRUE));
            assertFalse(flow.data().isEmpty());
        }
    }
    @Test void actualPipelineBuildsIndependentInstanceFlowsAtEveryStage() throws Exception {
        java.nio.file.Files.writeString(directory.resolve("templates.cpp"), "#include <vector>\ntemplate<class T> unsigned long count(const T& x) { return x.size(); } int main() { std::vector<int> xs; return count(xs); }");
        for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
            var p = Cpg.read(directory, new Cpg.Options(Language.CPP, stage, "c++03"));
            var instance = p.templateInstances().getFirst();
            var flow = p.flows().stream().filter(value -> value.entry() == instance.declaration()).findFirst().orElseThrow();
            assertTrue(flow.complete(), stage.toString());
            assertFalse(flow.control().isEmpty());
            assertFalse(flow.data().isEmpty());
            assertNotSame(instance.origin(), flow.entry());
            assertTrue(p.flows().stream().anyMatch(value -> value.entry() == instance.origin()));
            var specialized = nodes(instance.declaration(), CallExpression.class).getFirst();
            assertTrue(specialized.semanticInfo().isPresent());
            assertFalse(specialized.dependent());
            assertEquals("size", specialized.target().orElseThrow().name());
        }
    }

    static Project resolve(String text) { return NameResolver.resolve(Project.create().addFile(Parser.parse("templates.cpp", text, Language.CPP, "c++03")).build()); }
    static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>(); root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (type.isInstance(n)) result.add(type.cast(n)); }}); return result;
    }
    @Test void uninstantiatedBodyRemainsOwnedAndDependent() {
        var p = resolve("int size(); template<class T> unsigned long count(const T& x) { return x.size(); }");
        var f = nodes(p, FunctionDeclaration.class).getLast();
        assertEquals(1, nodes(f, ReturnStatement.class).size());
        var call = nodes(f, CallExpression.class).getFirst();
        assertTrue(call.dependent()); assertTrue(call.target().isEmpty());
        var member = (MemberExpression) call.callee(); assertTrue(member.dependent());
        var receiver = (NameExpression) member.receiver(); assertTrue(receiver.dependent());
        assertSame(f.parameters().getFirst(), receiver.target().orElseThrow());
    }
    @Test void basicInstanceWithConcreteClass() {
        var p = resolve("struct Box { unsigned long size() const; }; template<class T> unsigned long count(const T& x) { return x.size(); } int main() { Box xs; return count(xs); }");
        assertEquals(1, p.templateInstances().size());
        var instance = p.templateInstances().getFirst();
        assertEquals("size", nodes(instance.declaration(), CallExpression.class).getFirst().target().orElseThrow().name());
    }
    @Test void editsInvalidateAndReanalysisRebuildsInstances() {
        var p = resolve("struct Box { int size() const; }; template<class T> int count(const T& x) { return x.size(); } int main() { Box xs; return count(xs); }");
        var original = p.templateInstances().getFirst();
        var edit = new ProjectEditor(p);
        var genericCall = nodes(original.origin(), CallExpression.class).getFirst();
        edit.replace(genericCall, IntegerLiteral.create().setValue(7).build());
        var changed = edit.build();
        assertTrue(changed.templateInstances().isEmpty());
        var rerun = NameResolver.resolve(changed);
        assertEquals(1, rerun.templateInstances().size());
        assertTrue(nodes(rerun.templateInstances().getFirst().declaration(), CallExpression.class).isEmpty());
        assertEquals(1, p.templateInstances().size());
        var removal = new ProjectEditor(p);
        removal.remove(nodes(p.files().getFirst(), FunctionDeclaration.class).getLast());
        assertTrue(removal.build().templateInstances().isEmpty());
        assertTrue(p.edit().setFiles(List.of(Parser.parse("templates.cpp", p.files().getFirst().source(Language.CPP), Language.CPP))).build().templateInstances().isEmpty());
        assertTrue(p.edit().setFiles(List.of()).build().templateInstances().isEmpty());
        var emptied = p.edit();
        ((SourceFile.Builder) emptied.files().getFirst()).setItems(List.of());
        assertTrue(emptied.build().templateInstances().isEmpty());
        var root = p.edit();
        var file = (SourceFile.Builder) root.files().getFirst();
        var function = (FunctionDeclaration.Builder) file.items().get(1);
        function.setBody(BlockStatement.create().addItem(ReturnStatement.create().setValue(IntegerLiteral.create().setValue(8))));
        assertTrue(root.build().templateInstances().isEmpty());
    }
    @Test void unsupportedOrNonviableDeductionNeverBinds() {
        var p = resolve("template<class T> int f(T x, int y) { return y; } int g() { return f(1, \"bad\"); }");
        assertTrue(p.templateInstances().isEmpty());
        var invalidKind = resolve("template<int N> int f() { return N; } int g() { return f<int>(); }");
        assertTrue(invalidKind.templateInstances().isEmpty());
        assertTrue(nodes(p, CallExpression.class).getFirst().target().isEmpty());
        var explicit = resolve("template<class T> int f(T x) { return 1; } int g() { return f<int>(1.5); }");
        assertEquals(1, explicit.templateInstances().size());
        assertTrue(explicit.templateInstances().getFirst().cause().target().isPresent());
    }
    @Test void vectorCallOwnsSubstitutedBodyWithoutChangingWrittenTemplate() {
        var p = resolve("#include <vector>\ntemplate<class T> unsigned long count(const T& x) { return x.size(); } int main() { std::vector<int> xs; return count(xs); }");
        assertEquals(1, p.templateInstances().size());
        for (var snapshot : List.of(p, p.edit().build(), NameResolver.resolve(p))) {
            var instance = snapshot.templateInstances().getFirst();
            assertTrue(instance.declaration().templateParameters().isEmpty());
            assertNotSame(instance.origin().body().orElseThrow(), instance.declaration().body().orElseThrow());
            var generic = nodes(instance.origin(), CallExpression.class).getFirst();
            assertTrue(generic.dependent()); assertTrue(generic.target().isEmpty());
            var specialized = nodes(instance.declaration(), CallExpression.class).getFirst();
            assertFalse(specialized.dependent());
            assertEquals("size", specialized.target().orElseThrow().name());
            assertSame(instance.declaration(), instance.cause().target().orElseThrow());
            assertSame(instance.declaration().parameters().getFirst(), ((NameExpression)((MemberExpression)specialized.callee()).receiver()).target().orElseThrow());
            assertEquals(1, snapshot.files().size());
            assertFalse(snapshot.files().getFirst().source(Language.CPP).contains("cpg-library"));
        }
    }
}
