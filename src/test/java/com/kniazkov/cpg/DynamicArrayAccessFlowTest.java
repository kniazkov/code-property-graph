/* C++03 sequence queries, handles and mutations in the DFG. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DynamicArrayAccessFlowTest {
    private Project analyze(String body) {
        return UnreadWrites.analyze(DynamicArraySemantics.green(NameResolver.resolve(Project.create()
                .addFile(Parser.parse("access.cpp", "#include <vector>\n" + body, Language.CPP)).build())));
    }
    private List<FunctionFlow.DataEdge> edges(Project p) {
        return p.flows().stream().flatMap(f -> f.data().stream()).toList();
    }
    private boolean reaches(Project p, Node from, String name) {
        return edges(p).stream().anyMatch(e -> e.kind() == FunctionFlow.Dependency.DEFINITION && e.from() == from
                && e.to() instanceof NameExpression n && n.name().equals(name));
    }
    @Test void queriesReadWithoutDefiningSequenceOrRunningElementCode() {
        var p = analyze("int count; struct R { R(){} R(const R& r){++count;} ~R(){++count;} }; int f(){std::vector<R> a(2); count=7; int n=a.size(); int c=a.capacity(); return count+n+c;}");
        var queries = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.SIZE || o.kind() == DynamicArrayOperation.Kind.CAPACITY).toList();
        assertEquals(2, queries.size());
        for (var query : queries) {
            assertTrue(edges(p).stream().anyMatch(e -> e.to() == query && e.kind() == FunctionFlow.Dependency.DEFINITION));
            assertFalse(reaches(p, query, "a"));
            assertFalse(reaches(p, query, "count"));
        }
    }
    @Test void elementReferencesAndDirectSubscriptWritesReachSequenceReads() {
        var p = analyze("int f(){std::vector<int> a(2,7); int& r=a.front(); r=41; a[1]=42; return a[0]+a[1];}");
        var writes = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class);
        assertEquals(2, writes.size());
        for (var write : writes) assertTrue(reaches(p, write, "a"));
    }
    @Test void swapTransfersElementReferenceButNotContainerAlias() {
        var p = analyze("int f(){std::vector<int> a(2,7); std::vector<int> b(3,8); int& element=a.front(); std::vector<int>& container=a; a.swap(b); element=41; return b.front()+container.front();}");
        var write = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class).getFirst();
        assertTrue(reaches(p, write, "b"));
        assertFalse(reaches(p, write, "container"));
        assertFalse(reaches(p, write, "a"));
        var swap = p.dynamicArraySemantics().stream().filter(o -> o.kind() == DynamicArrayOperation.Kind.SWAP).findFirst().orElseThrow();
        assertTrue(reaches(p, swap, "b")); assertTrue(reaches(p, swap, "container"));
    }
    @Test void iteratorDereferenceUpdatesSequenceWithoutPointerAliases() {
        var p = analyze("int f(){std::vector<int> a(2,7); std::vector<int>::iterator it=a.begin(); *it=9; int value=*it; value=4; return a.front()+value;}");
        var writes = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class);
        assertEquals(2, writes.size());
        assertTrue(reaches(p, writes.getFirst(), "a"));
        assertFalse(reaches(p, writes.getLast(), "a"), "Dereferenced scalar copy is not a sequence alias");
    }
    @Test void reservedInsertionPreservesPrefixReferenceDependency() {
        var p = analyze("int f(){std::vector<int> a; a.reserve(20); a.push_back(1); a.push_back(2); int& prefix=a.front(); a.insert(a.begin()+1,8); prefix=41; return a.front();}");
        var write = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class).getFirst();
        assertTrue(reaches(p, write, "a"));
    }
    @Test void conditionalSwapJoinsPossibleElementStorage() {
        var p = analyze("int f(bool flag){std::vector<int> a(2,7); std::vector<int> b(2,8); int& element=a.front(); if(flag) a.swap(b); element=41; return a.front()+b.front();}");
        var write = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class).getFirst();
        assertTrue(reaches(p, write, "a")); assertTrue(reaches(p, write, "b"));
    }
    @Test void referenceWriteInArgumentRetainsBindingSensitiveEffect() {
        var p = analyze("void take(int); int f(){std::vector<int> a(2,7); int& element=a.front(); take(element=41); return a.front();}");
        var write = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class).getFirst();
        assertTrue(reaches(p, write, "a"));
    }
    @Test void queryInEvaluatedDefaultArgumentHasOwnedDataEndpoints() {
        var p = analyze("std::vector<int> a(2,7); int g(int n=a.size()){return n;} int f(){return g();}");
        assertTrue(p.flows().stream().filter(f -> f.entry().name().equals("f")).flatMap(f -> f.data().stream())
                .anyMatch(e -> e.from() instanceof DynamicArrayOperation o && o.kind() == DynamicArrayOperation.Kind.SIZE));
    }
    @Test void iteratorSubscriptAndDelayedInitializationUpdateStorage() {
        for (String access : List.of("std::vector<int>::iterator i=a.begin(); i[0]=7;", "std::vector<int>::iterator i; i=a.begin(); *i=7;")) {
            var p = analyze("int f(){std::vector<int> a(1,0); " + access + " return a[0];}");
            var write = LazyLibraryGraphTest.nodes(p, AssignmentExpression.class).getLast();
            assertTrue(reaches(p, write, "a"), access);
        }
    }
    @Test void boundsCheckedAccessKeepsExceptionalControlPath() {
        var p = analyze("int f(){std::vector<int> a(2,7); try { return a.at(9); } catch (...) { return 3; }}");
        assertTrue(p.dynamicArraySemantics().stream().anyMatch(o -> o.kind() == DynamicArrayOperation.Kind.AT));
        assertTrue(p.flows().stream().flatMap(f -> f.control().stream()).anyMatch(e -> e.kind() == FunctionFlow.Branch.EXCEPTION));
    }
}
