/* Opt-in C++03 relational operators. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class RelOpsLibraryTest {
    static final String VALUE="struct Value { int n; bool operator==(const Value& x) const { return n==x.n; } bool operator<(const Value& x) const { return n<x.n; } };\n";
    static Project resolve(String code) { return LazyLibraryGraphTest.resolve("#include <utility>\n"+VALUE+code); }
    @Test void qualifiedAndImportedOperators() {
        var p=resolve("bool f(Value a,Value b){return std::rel_ops::operator!=(a,b) && std::rel_ops::operator>(a,b) && std::rel_ops::operator<=(a,b) && std::rel_ops::operator>=(a,b); } bool g(Value a,Value b){using namespace std::rel_ops; return a!=b && a>b && a<=b && a>=b;}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var calls=LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class);
        assertEquals(4,calls.size());
        for(var c:calls) assertTrue(c.target().isPresent(),c.toString());
        var ops=LazyLibraryGraphTest.nodes(p.files().getFirst(),Expression.class).stream().filter(e->e.operatorTarget().isPresent()&&LibraryDeclarations.isLibrary(e.operatorTarget().orElseThrow())).toList();
        assertEquals(4,ops.size());
    }

    private static String shape(Node n) {
        String head=n.getClass().getSimpleName();
        if(n instanceof TypeNode t) head+=":"+t.canonicalIdentity();
        if(n instanceof CallableDeclaration c) head+=":"+c.kind()+":"+c.name()+":"+c.modifiers();
        if(n instanceof NamespaceDeclaration ns) head+=":"+ns.name();
        return head+"["+n.children().stream().map(RelOpsLibraryTest::shape).reduce("",(a,b)->a+"|"+b)+"]";
    }
    @Test void independentInventoryMatchesExactNestedSignatures() throws Exception {
        var path=java.nio.file.Path.of("/__no_host_stl__/utility.cpp");
        var unit=new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03");
        var reference=com.kniazkov.cpg.parser.Parser.parseLibrary(unit,java.nio.file.Files.readString(java.nio.file.Path.of("src/test/resources/library-catalogue/rel-ops-20-2-inventory.hpp"))).build();
        var actual=com.kniazkov.cpg.parser.Parser.parseLibrary(unit,StandardLibraryCatalogue.declarations("utility",com.kniazkov.cpg.build.PlatformProfile.defaults())).build();
        var namespaces=LazyLibraryGraphTest.nodes(actual,NamespaceDeclaration.class).stream().filter(n->n.name().equals("rel_ops")).toList();
        assertEquals(1,namespaces.size());
        assertEquals(shape(LazyLibraryGraphTest.nodes(reference,NamespaceDeclaration.class).getLast()),shape(namespaces.getFirst()));
        assertEquals(4,namespaces.getFirst().members().size());
    }

    static void owned(Project p) {
        var all=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        p.walk(new NodeVisitor(){@Override public void visit(Node n){assertTrue(all.add(n));}});
        p.walk(new NodeVisitor(){@Override public void visit(Node n){assertTrue(all.containsAll(n.references()),n.toString());}});
    }
    @Test void exactDeclarationsAndCopyRemainNestedAndDemandOnly() {
        assertTrue(resolve("int f();").librarySources().getFirst().items().isEmpty());
        var p=resolve("bool f(Value a,Value b){return std::rel_ops::operator!=(a,b);}");
        for(var graph:List.of(p,p.edit().build(),NameResolver.resolve(p))) {
            assertFalse(graph.hasErrors(),graph.semanticDiagnostics().toString()); owned(graph);
            var header=graph.librarySources().getFirst();
            var std=(NamespaceDeclaration)header.items().getFirst(); assertEquals("std",std.name());
            assertEquals(1,std.members().size());
            var rel=(NamespaceDeclaration)std.members().getFirst(); assertEquals("rel_ops",rel.name());
            assertEquals(1,rel.members().size());
            var origin=(CallableDeclaration)rel.members().getFirst();
            assertEquals("operator!=",origin.name()); assertEquals(1,origin.templateParameters().size());
            assertEquals(2,origin.parameters().size()); assertTrue(origin.body().isEmpty());
            var target=LazyLibraryGraphTest.nodes(graph.files().getFirst(),CallExpression.class).getFirst().target().orElseThrow();
            assertSame(origin,graph.templateInstances().getFirst().origin()); assertSame(target,graph.templateInstances().getFirst().declaration()); assertTrue(target.body().isEmpty());
            assertEquals(1,graph.templateInstances().size());
        }
        var edit=new ProjectEditor(p);
        edit.remove(p.files().getFirst().items().stream().filter(FunctionDeclaration.class::isInstance).findFirst().orElseThrow());
        var changed=NameResolver.resolve(edit.build());
        assertFalse(changed.hasErrors(),changed.semanticDiagnostics().toString());owned(changed);
        assertTrue(changed.templateInstances().isEmpty());
        assertTrue(changed.librarySources().getFirst().items().isEmpty());
        assertEquals(1,p.templateInstances().size());
    }
    @Test void explicitUsingAndExplicitTemplateArgumentsResolve() {
        for(String code:List.of("using ::std::rel_ops::operator!=; bool f(Value a,Value b){return a!=b;}","using std::rel_ops::operator!=; bool f(Value a,Value b){return a!=b;}",
                "bool f(Value a,Value b){return std::rel_ops::operator!=<Value>(a,b);}",
                "namespace relations=std::rel_ops; bool f(Value a,Value b){return relations::operator!=(a,b);}")) {
            var p=resolve(code);assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
            assertEquals(1,p.templateInstances().size());
        }
    }
    @Test void unimportedNamespaceDoesNotPolluteStdOrGlobalLookup() {
        for(String prefix:List.of("","using namespace std;")) {
            var p=resolve(prefix+"bool f(Value a,Value b){return a!=b;}");
            assertTrue(LazyLibraryGraphTest.nodes(p.files().getFirst(),NotEqualExpression.class).getFirst().operatorTarget().isEmpty());assertTrue(p.templateInstances().isEmpty());
        }
        var p=resolve("bool f(Value a,Value b){return std::operator!=(a,b);}");
        assertTrue(p.hasErrors()); assertTrue(p.templateInstances().isEmpty());
    }
    @Test void mismatchedDeductionIsRejectedButLibraryPreconditionsAreNotDiagnosed() {
        for(String call:List.of("std::rel_ops::operator!=(a,1)","std::rel_ops::operator!=<int>(a,a)","std::rel_ops::operator!=(1,2)")) {
            var p=resolve("bool f(Value a){return "+call+";}");assertTrue(p.hasErrors());
            assertTrue(LazyLibraryGraphTest.nodes(p,CallExpression.class).getFirst().target().isEmpty());
        }
        var p=resolve("struct NoEquality {}; bool f(NoEquality a,NoEquality b){return std::rel_ops::operator!=(a,b);}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        assertTrue(LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class).getFirst().target().isPresent());
    }
    @Test void userNonTemplateWinsAndEquivalentTemplateIsAmbiguous() {
        var p=resolve("bool operator!=(const Value&,const Value&); using namespace std::rel_ops; bool f(Value a,Value b){return a!=b;}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var op=LazyLibraryGraphTest.nodes(p.files().getFirst(),NotEqualExpression.class).getFirst();
        assertFalse(LibraryDeclarations.isLibrary(op.operatorTarget().orElseThrow())); assertTrue(p.templateInstances().isEmpty());
        p=resolve("template<class T> bool operator!=(const T&,const T&); using namespace std::rel_ops; bool f(Value a,Value b){return a!=b;}");
        assertTrue(p.hasErrors());
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().contains("ambiguous")),p.semanticDiagnostics().toString());
    }
    @Test void localImportDoesNotEscapeAndBuiltinComparisonsStayBuiltin() {
        var p=resolve("bool f(Value a,Value b){using namespace std::rel_ops;return a!=b;} bool g(Value a,Value b){return a!=b;}");
        var ops=LazyLibraryGraphTest.nodes(p.files().getFirst(),NotEqualExpression.class);
        assertTrue(ops.getFirst().operatorTarget().isPresent());assertTrue(ops.getLast().operatorTarget().isEmpty());
        p=resolve("using namespace std::rel_ops; bool f(int a,int b){return a!=b && a>b && a<=b && a>=b;}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());assertTrue(p.templateInstances().isEmpty());
        p=resolve("enum E { first, second }; bool f(E a,E b){return std::rel_ops::operator!=(a,b);}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());assertEquals(1,p.templateInstances().size());
    }
    @Test void unknownEffectsAreNotReplacedByPureComparisons() {
        var p=UnreadWrites.analyze(resolve("int global; int f(Value a,Value b){global=1;std::rel_ops::operator!=(a,b);return global;}"));
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var f=LazyLibraryGraphTest.nodes(p.files().getFirst(),FunctionDeclaration.class).stream().filter(x->x.name().equals("f")).findFirst().orElseThrow();
        var call=LazyLibraryGraphTest.nodes(f,CallExpression.class).getFirst();
        var use=LazyLibraryGraphTest.nodes(LazyLibraryGraphTest.nodes(f,ReturnStatement.class).getFirst(),NameExpression.class).getFirst();
        var flow=p.flows().stream().filter(x->x.entry()==f).findFirst().orElseThrow();
        assertTrue(flow.data().stream().anyMatch(e->e.from()==call&&e.to()==use&&e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
}
