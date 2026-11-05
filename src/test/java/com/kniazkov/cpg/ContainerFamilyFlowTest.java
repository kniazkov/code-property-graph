/* C++03 stable-node and adapter storage in the DFG. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContainerFamilyFlowTest {
    private Project analyze(String body) {
        return UnreadWrites.analyze(ContainerSemantics.green(NameResolver.resolve(Project.create()
                .addFile(Parser.parse("families.cpp", "#include <list>\n#include <deque>\n#include <map>\n#include <set>\n#include <stack>\n#include <queue>\n" + body, Language.CPP)).build())));
    }
    private List<FunctionFlow.DataEdge> edges(Project p) {
        return p.flows().stream().flatMap(f -> f.data().stream()).toList();
    }
    private boolean reaches(Project p, Node from, String name) {
        return edges(p).stream().anyMatch(e -> e.kind() == FunctionFlow.Dependency.DEFINITION && e.from() == from
                && e.to() instanceof NameExpression n && n.name().equals(name));
    }
    @Test void queriesDoNotDefineButMapSubscriptDoes() {
        var p = analyze("int f(){std::map<int,int> m; m[1]=7; m.find(1); m.count(1); int n=m.size(); return m[1]+n;}");
        var queries = p.containerSemantics().stream().filter(o -> Set.of(ContainerOperation.Kind.FIND, ContainerOperation.Kind.COUNT_KEY, ContainerOperation.Kind.SIZE).contains(o.kind())).toList();
        assertEquals(3, queries.size());
        for (var query : queries) {
            assertTrue(edges(p).stream().anyMatch(e -> e.to() == query && e.kind() == FunctionFlow.Dependency.DEFINITION));
            assertFalse(reaches(p, query, "m"));
        }
        assertTrue(p.containerSemantics().stream().filter(o -> o.kind() == ContainerOperation.Kind.SUBSCRIPT).anyMatch(o -> reaches(p,o,"m")));
        assertTrue(reaches(p, LazyLibraryGraphTest.nodes(p, AssignmentExpression.class).getFirst(), "m"));
    }
    @Test void wholeSpliceMovesElementButNotObjectReference() {
        var p = analyze("int f(){std::list<int> a(2,7); std::list<int> b(2,8); int& element=a.front(); std::list<int>& object=a; b.splice(b.end(),a); element=41; return b.front()+object.size();}");
        var write = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class).getFirst();
        assertTrue(reaches(p, write, "b"));
        assertFalse(reaches(p, write, "object"));
        assertFalse(reaches(p, write, "a"));
        var splice = p.containerSemantics().stream().filter(o -> o.kind() == ContainerOperation.Kind.SPLICE).findFirst().orElseThrow();
        assertTrue(reaches(p,splice,"b")); assertTrue(reaches(p,splice,"object"));
    }
    @Test void selectedSpliceTransfersSavedReference() {
        var p = analyze("int f(){std::list<int> a(2,7); std::list<int> b; std::list<int>::iterator moved=a.begin(); int& saved=*moved; b.splice(b.end(),a,moved); saved=17; return b.back()+a.size();}");
        var write = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class).getFirst();
        assertTrue(reaches(p,write,"b"));
        assertFalse(reaches(p,write,"a"));
    }
    @Test void unknownPartialSpliceKeepsBothPossibleOwners() {
        var p = analyze("int f(std::list<int>::iterator position){std::list<int> a(2,7); std::list<int> b; int& saved=a.front(); b.splice(b.end(),a,position); saved=17; return b.size()+a.size();}");
        var write = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class).getFirst();
        assertTrue(reaches(p,write,"b"));
        assertTrue(reaches(p,write,"a"));
    }
    @Test void adapterReferencesDifferFromCopiesAndPopWrites() {
        var p = analyze("int f(){std::stack<int> s; s.push(2); int& r=s.top(); int copy=s.top(); r=7; copy=9; s.pop(); return s.size()+copy;}");
        var writes = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class);
        assertTrue(reaches(p,writes.getFirst(),"s"));
        assertFalse(reaches(p,writes.getLast(),"s"));
        var pop = p.containerSemantics().stream().filter(o -> o.kind()==ContainerOperation.Kind.POP).findFirst().orElseThrow();
        assertTrue(reaches(p,pop,"s"));
    }
    @Test void directFindArrowReferenceWriteIsReadThroughContainer() {
        var p=analyze("int f(){std::map<int,int> dictionary; dictionary[7]=70; int& mapped_reference=dictionary.find(7)->second; mapped_reference=72; return dictionary[7];}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var write=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class).getLast();
        var reference=LazyLibraryGraphTest.nodes(p,VariableDeclaration.class).stream().filter(v -> v.name().equals("mapped_reference")).findFirst().orElseThrow();
        assertTrue(reaches(p,write,"dictionary"));
        assertTrue(edges(p).stream().anyMatch(e -> e.from()==reference && e.to()==write && e.kind()==FunctionFlow.Dependency.DEFINITION));
        assertTrue(reference.diagnostics().stream().noneMatch(d -> d.code().equals(UnreadWrites.CODE)));
        assertTrue(write.diagnostics().stream().noneMatch(d -> d.code().equals(UnreadWrites.CODE)));
    }
    @Test void writingElementReferenceConsumesBindingNotOldElementValue() {
        var p=analyze("int f(){std::map<int,int> m; m[1]=2; std::map<int,int>::iterator i=m.find(1); int& reference=i->second; reference=7; return m.size();}");
        var reference=LazyLibraryGraphTest.nodes(p,VariableDeclaration.class).stream().filter(v -> v.name().equals("reference")).findFirst().orElseThrow();
        assertTrue(reference.diagnostics().stream().noneMatch(d -> d.code().equals(UnreadWrites.CODE)));
        var unused=analyze("int f(){std::map<int,int> m; m[1]=2; std::map<int,int>::iterator i=m.find(1); int& unused=i->second; return m.size();}");
        assertTrue(LazyLibraryGraphTest.nodes(unused,VariableDeclaration.class).stream().filter(v -> v.name().equals("unused"))
                .anyMatch(v -> v.diagnostics().stream().anyMatch(d -> d.code().equals(UnreadWrites.CODE))));
    }
    @Test void iteratorArrowWritesAndReadsMappedStorage() {
        var p = analyze("int f(){std::map<int,int> m; m[1]=2; std::map<int,int>::iterator i=m.find(1); i->second=7; int read=i->second; return m[1]+read;}");
        var write=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class).getLast();
        assertTrue(reaches(p,write,"m"));
        assertTrue(edges(p).stream().anyMatch(e -> e.from()==write && e.kind()==FunctionFlow.Dependency.DEFINITION
                && e.to() instanceof PointerMemberExpression));
    }
    @Test void iteratorArrowReferenceAliasesButValueCopyDoesNot() {
        var p=analyze("int f(){std::map<int,int> m; m[1]=2; std::map<int,int>::iterator i=m.find(1); int& reference=i->second; int copy=i->second; reference=7; copy=8; return m[1]+copy;}");
        var writes=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class);
        assertTrue(reaches(p,writes.get(1),"m"));
        assertFalse(reaches(p,writes.get(2),"m"));
    }
    @Test void constIteratorArrowReadsRetainDefinition() {
        var p=analyze("int f(){std::map<int,int> m; m[1]=2; const std::map<int,int>& view=m; std::map<int,int>::const_iterator i=view.find(1); return i->second;}");
        var write=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class).getFirst();
        assertTrue(edges(p).stream().anyMatch(e -> e.from()==write && e.kind()==FunctionFlow.Dependency.DEFINITION
                && e.to() instanceof PointerMemberExpression));
        assertTrue(p.containerSemantics().stream().filter(o -> o.kind()==ContainerOperation.Kind.FIND).allMatch(o -> !o.writable()));
    }
    @Test void constIteratorMutableAndReferenceMembersRemainWritable() {
        var p=analyze("struct R { mutable int mutable_value; int& reference; R(int& r):mutable_value(0),reference(r){} }; int f(){int value=1; R record(value); std::list<R> values; values.push_back(record); const std::list<R>& view=values; std::list<R>::const_iterator i=view.begin(); i->mutable_value=7; i->reference=8; return values.front().mutable_value+value;}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var writes=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class);
        assertEquals(2,writes.size());
        assertTrue(reaches(p,writes.getFirst(),"values"));
        assertTrue(reaches(p,writes.getLast(),"value"));
    }
    @Test void iteratorStaticMemberWritesDeclarationNotContainer() {
        var p=analyze("struct R { static int shared; }; int f(){std::list<R> values; std::list<R>::const_iterator i=values.begin(); i->shared=7; return R::shared+values.size();}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var write=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class).getFirst();
        assertFalse(reaches(p,write,"values"));
        assertTrue(edges(p).stream().anyMatch(e -> e.from()==write && e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void uniqueInsertPairResultsHaveCompleteFlow() {
        var p = analyze("int f(){std::set<int> s; std::map<int,int> m; s.insert(1); m.insert(std::pair<const int,int>(1,2)); return s.size()+m.size();}");
        // The explicit pair temporary remains unsupported even though insert's
        // own iterator/bool result is safe; isolate a set insertion as positive.
        assertFalse(p.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow().complete());
        var set = analyze("int f(){std::set<int> s; s.insert(1); return s.size();}");
        assertTrue(set.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow().complete());
        var map = analyze("int f(){std::map<int,int> m; std::pair<const int,int> value(1,2); m.insert(value); return m.size();}");
        assertTrue(map.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow().complete());
    }
    @Test void recordReferenceResultsDoNotCreateTemporaryLifetimeBarriers() {
        var p=analyze("struct Item { mutable int observed; Item():observed(0){} }; int f(){Item item; std::list<Item> values; values.push_back(item); const std::list<Item>& readonly=values; std::list<Item>::const_iterator view=readonly.begin(); view->observed=9; return readonly.front().observed;}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        assertTrue(p.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow().complete());
        var write=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class).getFirst();
        assertTrue(reaches(p,write,"readonly"));
        var user=analyze("struct R { int value; }; R& get(); int f(){return get().value;}");
        assertTrue(user.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow().complete());
    }
    @Test void userRecordResultStaysConservative() {
        var p = analyze("struct R { R(); ~R(); }; R make(); int f(){make(); return 1;}");
        assertFalse(p.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow().complete());
    }
    @Test void unlinkedComparatorStillHasPossibleEffects() {
        var parsed=NameResolver.resolve(Project.create().addFile(Parser.parse("compare.cpp",
                "#include <set>\nint global; struct Base { bool operator()(int a,int b) const {++global; return a<b;} }; struct Less:Base {}; int f(){std::set<int,Less> s; s.insert(1); global=7; s.find(2); return global;}",Language.CPP)).build());
        var normalized=ContainerSemantics.green(parsed);
        // Deliberately remove the optional target: unsupported comparator lookup
        // must preserve conservative effects independently of resolution support.
        var context=new EditContext();
        var builder=context.edit(normalized,Project.Builder.class);
        var operations=new ArrayList<NodeBuilder<? extends ContainerOperation>>();
        for(var operation:normalized.containerSemantics())
            operations.add(context.edit(operation,ContainerOperation.Builder.class).setComparator(null));
        var p=DataFlow.analyze(builder.setContainerSemantics(operations).build());
        var find=p.containerSemantics().stream().filter(o -> o.kind()==ContainerOperation.Kind.FIND).findFirst().orElseThrow();
        assertTrue(find.comparator().isEmpty());
        assertTrue(reaches(p,find,"global"));
    }
    @Test void customComparatorPossibleGlobalEffectsRemain() {
        var p = analyze("int comparisons; struct Less { bool operator()(int a,int b) const {++comparisons; return a<b;} }; int f(){std::set<int,Less> s; comparisons=7; s.insert(1); s.find(2); return comparisons;}");
        var calls=p.containerSemantics().stream().filter(o -> o.comparator().isPresent()).toList();
        assertFalse(calls.isEmpty());
        for(var call:calls) assertTrue(reaches(p,call,"comparisons"), call.kind()+" "+call.comparator().orElseThrow().name());
    }
}
