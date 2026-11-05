/* C++03 container-family iterator contracts, without layout assumptions. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.generation.Source;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class ContainerIteratorResolutionTest {
    @Test void sequencesSelectConcreteOperationsAndRespectIteratorCategories() {
        var p=LazyLibraryGraphTest.resolve("#include <deque>\n#include <list>\nvoid f(){std::deque<int> d;d.push_front(1);d.push_back(2);d.pop_front();d.pop_back();d[0];std::deque<int>::iterator i=d.begin();i+1;i[0];std::list<int> a,b;std::list<int>::iterator j=a.begin();a.splice(j,b);*j;++j;--j;j!=a.end();j+1;j[0];j-a.begin();j<a.end();j+=1;a.insert(j,2,3);}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var profile=TypeSemantics.profile(p.files().getFirst());
        for(var call:LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class))assertTrue(call.target().isPresent() || call.operatorTarget().isPresent(),call.range().toString());
        var adds=LazyLibraryGraphTest.nodes(p.files().getFirst(),AddExpression.class);
        assertTrue(TypeSemantics.expressionType(adds.getFirst(),profile).isPresent());
        assertTrue(TypeSemantics.expressionType(adds.getLast(),profile).isEmpty());
        var indexes=LazyLibraryGraphTest.nodes(p.files().getFirst(),IndexExpression.class);
        assertTrue(TypeSemantics.expressionType(indexes.getLast(),profile).isEmpty());
        assertTrue(TypeSemantics.expressionType(LazyLibraryGraphTest.nodes(p.files().getFirst(),SubtractExpression.class).getFirst(),profile).isEmpty());
        assertTrue(TypeSemantics.expressionType(LazyLibraryGraphTest.nodes(p.files().getFirst(),LessExpression.class).getFirst(),profile).isEmpty());
        assertTrue(TypeSemantics.expressionType(LazyLibraryGraphTest.nodes(p.files().getFirst(),CompoundAssignmentExpression.class).getFirst(),profile).isEmpty());
    }
    @Test void associativeIteratorsPreserveConstKeysAndMappedReferences() {
        var p=LazyLibraryGraphTest.resolve("#include <map>\n#include <set>\nvoid f(){std::map<int,int> m;m[1]=2;std::map<int,int>::iterator i=m.find(1);i->second=3;*i;++i;i!=m.end();std::set<int> s;s.insert(1);std::set<int>::iterator j=s.find(1);*j;std::map<int,int>::reverse_iterator r=m.rbegin();r->second;*r;}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var profile=TypeSemantics.profile(p.files().getFirst());
        var dereferences=LazyLibraryGraphTest.nodes(p.files().getFirst(),DereferenceExpression.class);
        assertTrue(TypeSemantics.expressionType(dereferences.getFirst(),profile).isPresent());
        assertEquals("const(int)",TypeSemantics.expressionType(dereferences.get(1),profile).orElseThrow().canonicalIdentity());
        for(var member:LazyLibraryGraphTest.nodes(p.files().getFirst(),PointerMemberExpression.class))assertEquals("int",TypeSemantics.expressionType(member,profile).orElseThrow().canonicalIdentity());
        assertFalse(LazyLibraryGraphTest.resolve(Source.render(p.files().getFirst(),Language.CPP)).hasErrors());
    }
    @Test void associativeConstIteratorsCannotBindWritableReferences() {
        var p=LazyLibraryGraphTest.resolve("#include <map>\n#include <set>\nvoid write(int& x);void f(){std::map<int,int> m;std::map<int,int>::const_iterator i=m.begin();i->first;i->second;write(i->second);std::set<int> s;std::set<int>::const_iterator j=s.begin();write(*j);}");
        var profile=TypeSemantics.profile(p.files().getFirst());
        for(var member:LazyLibraryGraphTest.nodes(p.files().getFirst(),PointerMemberExpression.class))
            assertTrue(TypeSemantics.expressionType(member,profile).orElseThrow().canonicalIdentity().contains("const(int)"));
        for(var call:LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class))
            if(call.callee() instanceof NameExpression name && name.name().equals("write"))assertTrue(call.target().isEmpty());
    }
    @Test void constClassElementsResolveOriginalFieldsAndRespectMutable() {
        var p=LazyLibraryGraphTest.resolve("#include <list>\nstruct X{int count;mutable int cache;int& reference;};void read(const int&);void write(int&);void f(){std::list<X> l;std::list<X>::const_iterator i=l.begin();i->count;i->cache;i->reference;read(i->count);write(i->cache);write(i->reference);}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var profile=TypeSemantics.profile(p.files().getFirst());
        for(var field:LazyLibraryGraphTest.nodes(p.files().getFirst(),PointerMemberExpression.class)) {
            assertTrue(field.target().isPresent());
            assertEquals(field.member().equals("count")?"const(int)":"int",TypeSemantics.expressionType(field,profile).orElseThrow().canonicalIdentity());
        }
        var read=LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class).stream()
                .filter(c->c.callee() instanceof NameExpression name && name.name().equals("read")).findFirst().orElseThrow();
        assertTrue(read.target().isPresent());
        assertTrue(LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class).stream()
                .filter(c->c.callee() instanceof NameExpression name && name.name().equals("write")).allMatch(c->c.target().isPresent()));
    }
    @Test void userSpecializationWithDefaultAllocatorRemainsUntrusted() {
        var p=LazyLibraryGraphTest.resolve("#include <list>\nstruct Tag{};namespace std{template<> class list<Tag>{public:void push_back(Tag);};}void f(){std::list<Tag> a;Tag t;a.push_back(t);}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var instance=p.classTemplateInstances().stream().filter(i->i.origin().name().equals("list")).findFirst().orElseThrow();
        assertEquals(TemplateKind.FULL_SPECIALIZATION,instance.selected().templateKind());
        assertFalse(LibraryDeclarations.isLibrary(instance.selected()));
        var call=LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class).getFirst();
        assertTrue(call.target().isPresent());assertFalse(LibraryDeclarations.isLibrary(call.target().orElseThrow()));
    }
    @Test void directFindResultsBindArrowMembersBeforeReferenceUse() {
        var p=LazyLibraryGraphTest.resolve("#include <map>\nvoid write(int&);void read(const int&);int f(){std::map<int,int> m;m[7]=70;int& mapped=m.find(7)->second;mapped=72;write(m.find(7)->second);const std::map<int,int>& c=m;read(c.find(7)->second);return m[7];}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var profile=TypeSemantics.profile(p.files().getFirst());
        var members=LazyLibraryGraphTest.nodes(p.files().getFirst(),PointerMemberExpression.class);
        assertEquals(3,members.size());
        for(int i=0;i<members.size();i++) {
            assertTrue(members.get(i).target().isPresent());
            assertEquals(i==2?"const(int)":"int",TypeSemantics.expressionType(members.get(i),profile).orElseThrow().canonicalIdentity());
        }
        for(var call:LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class))assertTrue(call.target().isPresent());
        assertFalse(LazyLibraryGraphTest.resolve(Source.render(p.files().getFirst(),Language.CPP)).hasErrors());
    }
    @Test void adaptersAndCustomComparatorsRetainSelectedTargets() {
        var p=LazyLibraryGraphTest.resolve("#include <stack>\n#include <queue>\n#include <set>\nstruct Compare{bool operator()(const int& a,const int& b)const{return a>b;}};void f(){std::stack<int> s;s.push(1);s.top();s.pop();std::queue<int> q;q.push(2);q.front();q.pop();std::priority_queue<int,std::vector<int>,Compare> p;p.push(3);p.top();p.pop();std::set<int,Compare> keys;keys.insert(1);Compare c=keys.key_comp();c(1,2);std::multiset<int> ms;ms.insert(1);std::priority_queue<int> defaults;defaults.push(4);defaults.top();}");
        assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        for(var call:LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class))assertTrue(call.target().isPresent() || call.operatorTarget().isPresent(),call.range().toString());
        var queues=p.classTemplateInstances().stream().filter(i->i.origin().name().equals("priority_queue")).toList();
        assertTrue(queues.stream().anyMatch(i->i.arguments().size()==3 && i.arguments().get(2).type().orElseThrow().canonicalIdentity().contains("Compare")));
        assertTrue(queues.stream().anyMatch(i->i.arguments().size()==3 && i.arguments().get(2).type().orElseThrow().canonicalIdentity().contains("less")));
        var comparatorCall=LazyLibraryGraphTest.nodes(p.files().getFirst(),CallExpression.class).stream()
                .filter(c->c.callee() instanceof NameExpression name && name.name().equals("c")).findFirst().orElseThrow();
        assertEquals("operator()",comparatorCall.operatorTarget().orElseThrow().name());
        assertFalse(LibraryDeclarations.isLibrary(comparatorCall.operatorTarget().orElseThrow()));
        assertFalse(LazyLibraryGraphTest.resolve(Source.render(p.files().getFirst(),Language.CPP)).hasErrors());
    }
}
