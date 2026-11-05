/* C++03 concrete vector access and abstract iterator resolution. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.generation.Source;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class VectorIteratorResolutionTest {
    @Test void qualifiedIteratorAndModifiersUseConcreteCatalogueTargets() {
        var p=LazyLibraryGraphTest.resolve("#include <vector>\nvoid f(){std::vector<int> a; const std::vector<int> b; std::vector<int>::iterator i=a.begin(); *i; ++i; i++; i+1; 1+i; i-a.begin(); i!=a.end(); a.insert(i,2,4); a.erase(i); b.at(0); a[0]; b[0];}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var profile=TypeSemantics.profile(p.files().getFirst());
        for(var call:LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class))
            assertTrue(call.target().filter(LibraryDeclarations::isLibrary).isPresent(),call.range().toString());
        for(var index:LazyLibraryGraphTest.nodes(p.files().getFirst(),IndexExpression.class)) {
            assertTrue(index.operatorTarget().isPresent());
            assertEquals(ValueCategory.LVALUE,TypeSemantics.valueCategory(index,profile));
        }
        var indexes=LazyLibraryGraphTest.nodes(p.files().getFirst(),IndexExpression.class);
        assertEquals("int",TypeSemantics.expressionType(indexes.getFirst(),profile).orElseThrow().canonicalIdentity());
        assertEquals("const(int)",TypeSemantics.expressionType(indexes.getLast(),profile).orElseThrow().canonicalIdentity());
        var dereference=LazyLibraryGraphTest.nodes(p.files().getFirst(),DereferenceExpression.class).getFirst();
        assertEquals("int",TypeSemantics.expressionType(dereference,profile).orElseThrow().canonicalIdentity());
        var difference=LazyLibraryGraphTest.nodes(p.files().getFirst(),SubtractExpression.class).getFirst();
        assertEquals(OpaqueLibraryType.Category.INTEGER,assertInstanceOf(OpaqueLibraryType.class,TypeSemantics.expressionType(difference,profile).orElseThrow()).category());
        for(var add:LazyLibraryGraphTest.nodes(p.files().getFirst(),AddExpression.class)) {
            var type=TypeSemantics.expressionType(add,profile).orElseThrow();
            assertTrue(LibraryIteratorSemantics.info(type).isPresent());
            assertFalse(type.canonicalType() instanceof PointerType);
            assertTrue(TypeSemantics.sizeof(type,profile).isEmpty());
        }
        assertEquals("bool",TypeSemantics.expressionType(LazyLibraryGraphTest.nodes(p.files().getFirst(),NotEqualExpression.class).getFirst(),profile).orElseThrow().canonicalIdentity());
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        p.walk(new NodeVisitor(){@Override public void visit(Node n){assertTrue(owned.add(n));}});
        p.walk(new NodeVisitor(){@Override public void visit(Node n){assertTrue(owned.containsAll(n.references()),n.getClass().getSimpleName());}});
        assertFalse(NameResolver.resolve(p).hasErrors());
        assertFalse(LazyLibraryGraphTest.resolve(Source.render(p.files().getFirst(),Language.CPP)).hasErrors());
    }
    @Test void constIteratorKeepsConstElementsAndUnrelatedContainersDoNotCompare() {
        var p=LazyLibraryGraphTest.resolve("#include <vector>\nvoid f(){const std::vector<int> a;std::vector<int>::const_iterator i=a.begin();std::vector<double> b;*i;i==a.end();i==b.end();}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var profile=TypeSemantics.profile(p.files().getFirst());
        var dereference=LazyLibraryGraphTest.nodes(p.files().getFirst(),DereferenceExpression.class).getFirst();
        assertEquals("const(int)",TypeSemantics.expressionType(dereference,profile).orElseThrow().canonicalIdentity());
        var comparisons=LazyLibraryGraphTest.nodes(p.files().getFirst(),EqualExpression.class);
        assertTrue(TypeSemantics.expressionType(comparisons.getFirst(),profile).isPresent());
        assertTrue(TypeSemantics.expressionType(comparisons.getLast(),profile).isEmpty());
    }
    @Test void nestedRangeDefaultsAndReverseIteratorKeepPortableTypes() {
        var p=LazyLibraryGraphTest.resolve("#include <vector>\nvoid f(){std::vector<int> a;int values[2];a.at(0);a.resize(3);a.insert(a.begin()+1,values,values+2);std::vector<int>::reverse_iterator i=a.rbegin();*i;++i;i!=a.rend();i[0];i+1;i-a.rbegin();const std::vector<int>& c=a;std::vector<int>::const_reverse_iterator j=c.rbegin();*j;std::vector<int>::const_iterator k=a.begin();k==a.end();a.size()<3;}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var profile=TypeSemantics.profile(p.files().getFirst());
        var calls=LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class);
        assertTrue(calls.stream().allMatch(c->c.target().isPresent()));
        var resize=calls.stream().filter(c->c.callee() instanceof MemberExpression m && m.member().equals("resize")).findFirst().orElseThrow();
        assertEquals(1,resize.defaultArguments().size());
        var insert=calls.stream().filter(c->c.callee() instanceof MemberExpression m && m.member().equals("insert")).findFirst().orElseThrow();
        assertTrue(insert.target().orElseThrow().templateOrigin().isPresent());
        var dereferences=LazyLibraryGraphTest.nodes(p.files().getFirst(),DereferenceExpression.class);
        assertEquals("int",TypeSemantics.expressionType(dereferences.getFirst(),profile).orElseThrow().canonicalIdentity());
        assertEquals("const(int)",TypeSemantics.expressionType(dereferences.getLast(),profile).orElseThrow().canonicalIdentity());
        var comparison=LazyLibraryGraphTest.nodes(p.files().getFirst(),LessExpression.class).getFirst();
        assertEquals("bool",TypeSemantics.expressionType(comparison,profile).orElseThrow().canonicalIdentity());
        assertFalse(LazyLibraryGraphTest.resolve(Source.render(p.files().getFirst(),Language.CPP)).hasErrors());
    }
    @Test void boolProxyIteratorsNeverAcquireOrdinaryElementReferences() {
        var p=LazyLibraryGraphTest.resolve("#include <vector>\nvoid f(){std::vector<bool> bits;std::vector<bool>::iterator i=bits.begin();*i;}");
        var dereference=LazyLibraryGraphTest.nodes(p.files().getFirst(),DereferenceExpression.class).getFirst();
        var profile=TypeSemantics.profile(p.files().getFirst());
        assertTrue(TypeSemantics.expressionType(dereference,profile).isEmpty());
        assertTrue(TypeSemantics.expressionType(dereference.operand(),profile).flatMap(LibraryIteratorSemantics::info).isEmpty());
    }
    @Test void iteratorNamesInUserClassesDoNotAcquireLibraryFacts() {
        var p=LazyLibraryGraphTest.resolve("struct User { typedef int iterator; iterator begin(); };void f(){User a;User::iterator i=a.begin();*i;}");
        var dereference=LazyLibraryGraphTest.nodes(p.files().getFirst(),DereferenceExpression.class).getFirst();
        assertTrue(TypeSemantics.expressionType(dereference,TypeSemantics.profile(p.files().getFirst())).isEmpty());
    }
}
