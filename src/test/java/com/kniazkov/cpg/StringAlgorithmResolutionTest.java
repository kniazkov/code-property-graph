/* Trusted string aliases and iterator-aware function deduction. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class StringAlgorithmResolutionTest {
    @Test void stringAliasesDemandConcreteMembersAndIteratorTypes() {
        var p=LazyLibraryGraphTest.resolve("#include <string>\nvoid f(){std::string s(\"a\\0b\",3);s.size();s.find(\"b\");s.substr(1,2);s.append(\"x\",1);s[0];std::string::iterator i=s.begin();*i;std::wstring w(L\"x\",1);w.size();}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        for(var call:LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class))assertTrue(call.target().isPresent(),call.range().toString());
        var dereference=LazyLibraryGraphTest.nodes(p.files().getFirst(),DereferenceExpression.class).getFirst();
        assertEquals("char",TypeSemantics.expressionType(dereference,TypeSemantics.profile(p.files().getFirst())).orElseThrow().canonicalIdentity());
        assertTrue(p.classTemplateInstances().stream().anyMatch(i->i.origin().name().equals("basic_string")));
        var unused=LazyLibraryGraphTest.resolve("#include <string>\nint f(){return 1;}");
        assertTrue(unused.classTemplateInstances().isEmpty());
    }
    @Test void iteratorArgumentsAndUserFunctorsSurviveOwnedInstantiation() {
        var p=LazyLibraryGraphTest.resolve("#include <vector>\n#include <iterator>\n#include <algorithm>\n#include <numeric>\nstruct F{int operator()(int)const;};void f(){std::vector<int> v;std::vector<int>::iterator i=v.begin();std::advance(i,2);std::distance(i,v.end());std::copy(i,v.end(),std::back_inserter(v));F op;std::find_if(i,v.end(),op);std::transform(i,v.end(),i,op);std::sort(i,v.end());std::remove(i,v.end(),2);std::accumulate(i,v.end(),0L);}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        for(var call:LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class)) {
            assertTrue(call.target().isPresent(),call.range().toString());
            if(call.target().orElseThrow().name().equals("accumulate"))assertEquals("long",TypeSemantics.expressionType(call,TypeSemantics.profile(p.files().getFirst())).orElseThrow().canonicalIdentity());
        }
        assertDoesNotThrow(()->p.edit().build());
    }
    @Test void pointerAndReversePointerDistanceUseCatalogueDifferencePolicy() {
        var p=LazyLibraryGraphTest.resolve("#include <iterator>\nvoid f(){int a[3];std::distance(a,a+3);std::reverse_iterator<int*> r(a+3);std::distance(r,r);}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        for(var call:LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class))assertTrue(call.target().isPresent(),call.range().toString());
    }
    @Test void explicitCharacterTraitsUseTheSelectedCharacterSpecialization() {
        var p=LazyLibraryGraphTest.resolve("#include <string>\nvoid f(){char a[4];std::char_traits<char>::length(\"a\\0b\");std::char_traits<char>::compare(\"a\\0b\",\"a\\0c\",3);std::char_traits<char>::copy(a,\"a\\0b\",3);std::char_traits<char>::find(a,3,'b');}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        for(var call:LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class)) {
            assertTrue(call.target().isPresent());
            if(call.target().orElseThrow().name().equals("copy"))assertEquals("pointer(char)",TypeSemantics.expressionType(call,TypeSemantics.profile(p.files().getFirst())).orElseThrow().canonicalIdentity());
        }
    }
    @Test void namedAdaptersSelectOnlyDeducedConcreteConstructors() {
        var p=LazyLibraryGraphTest.resolve("#include <vector>\n#include <iterator>\nvoid f(){std::vector<int> a;std::reverse_iterator<std::vector<int>::iterator> r(a.end());std::insert_iterator<std::vector<int> > out(a,a.begin());}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        for(var variable:LazyLibraryGraphTest.nodes(p.files().getFirst(),VariableDeclaration.class))
            assertTrue(variable.constructorTarget().isPresent(),variable.name());
    }
    @Test void userStringSpecializationKeepsItsOwnMembers() {
        var p=LazyLibraryGraphTest.resolve("#include <string>\nstruct C{};namespace std{template<> class basic_string<C>{public:int size();};}void f(){std::basic_string<C> s;s.size();}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var call=LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class).getFirst();
        assertFalse(LibraryDeclarations.isLibrary(call.target().orElseThrow()));
    }
}
