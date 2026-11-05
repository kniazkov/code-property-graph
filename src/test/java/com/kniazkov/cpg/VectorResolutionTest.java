/* C++03 vector constructor/member-template prerequisites. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VectorResolutionTest {
    @Test void selectedRangesSurvivePublicationAndCopyConstruction() {
        var p=LazyLibraryGraphTest.resolve("#include <vector>\nint main(){std::vector<int> a; std::vector<int> b(3,7); int xs[2]={1,2}; std::vector<int> c(xs,xs+2); std::vector<int> d=b; a=b; a.assign(2,8); a.assign(xs,xs+2); a.push_back(4);}");
        var variables=LazyLibraryGraphTest.nodes(p,VariableDeclaration.class);
        for(String name:List.of("a","b","c","d"))assertTrue(variables.stream().filter(v->v.name().equals(name)).findFirst().orElseThrow().constructorTarget().isPresent(),name);
        var calls=LazyLibraryGraphTest.nodes(p,CallExpression.class).stream().filter(c->c.callee() instanceof MemberExpression m && m.member().equals("assign")).toList();
        assertEquals(2,calls.size());
        assertEquals(CallExpression.Resolution.RESOLVED,calls.get(0).resolution());
        assertEquals(CallExpression.Resolution.RESOLVED,calls.get(1).resolution());
        assertFalse(calls.get(0).target().orElseThrow().parameters().getFirst().type().orElseThrow() instanceof PointerType);
        assertInstanceOf(PointerType.class,calls.get(1).target().orElseThrow().parameters().getFirst().type().orElseThrow());
        assertEquals(2,p.templateInstances().size());
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        p.walk(new NodeVisitor(){@Override public void visit(Node n){owned.add(n);}});
        p.walk(new NodeVisitor(){@Override public void visit(Node n){assertTrue(owned.containsAll(n.references()),n.getClass().getSimpleName());}});
        assertEquals(2,p.edit().build().templateInstances().size());
        var edited=p.edit().setFiles(List.of(com.kniazkov.cpg.parser.Parser.parse("replacement.cpp","int main(){}",Language.CPP))).build();
        assertTrue(edited.templateInstances().isEmpty());
    }
    @Test void aliasesKeepVectorTargetsAndConstRangeAssignStaysUnresolved() {
        var p=LazyLibraryGraphTest.resolve("#include <vector>\nusing std::vector; typedef vector<int> Values; void f(){Values a; a.push_back(1); const Values b; int *p; b.assign(p,p);}");
        var calls=LazyLibraryGraphTest.nodes(p,CallExpression.class);
        assertTrue(calls.stream().filter(c->c.callee() instanceof MemberExpression m && m.member().equals("push_back")).allMatch(c->c.target().isPresent()));
        assertTrue(calls.stream().filter(c->c.callee() instanceof MemberExpression m && m.member().equals("assign")).allMatch(c->c.target().isEmpty()));
    }
    @Test void elementCopySelectionPreservesConstSourceAndUserDeclaration() {
        var p=LazyLibraryGraphTest.resolve("struct Record { Record(); Record(const Record& r); }; void f(){ Record r; }");
        var r=LazyLibraryGraphTest.nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals("r")).findFirst().orElseThrow();
        var selected=ClassSemantics.selectedElementCopyConstructor(r.type().orElseThrow(),null,com.kniazkov.cpg.build.PlatformProfile.defaults()).orElseThrow();
        assertEquals(CallableDeclaration.Kind.CONSTRUCTOR,selected.kind());
        assertEquals(1,selected.parameters().size());
    }
}
