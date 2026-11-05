/* Family-specific standard-container normalization. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.generation.Source;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.model.ContainerOperation.Kind.*;
class ContainerNormalizationTest {
    Project green(String code) { return ContainerSemantics.green(NameResolver.resolve(Project.create().addFile(Parser.parse("containers.cpp",code,Language.CPP)).build())); }
    ContainerOperation op(Project p,ContainerOperation.Kind k) { return p.containerSemantics().stream().filter(o->o.kind()==k).findFirst().orElseThrow(); }
    @Test void dequeEndsAreDistinctFromVectorAndListIterators() {
        var p=green("#include <deque>\n#include <list>\nvoid f(){std::deque<int> d; d.push_front(1);d.push_back(2);d[0];d.at(0);d.front();d.back();std::list<int> l;l.begin();}");
        assertEquals(ContainerType.IteratorCategory.RANDOM_ACCESS,op(p,PUSH_FRONT).type().iteratorCategory());
        assertEquals(ContainerType.IteratorCategory.BIDIRECTIONAL,op(p,BEGIN).type().iteratorCategory());
        assertTrue(op(p,AT).boundsChecked()); assertFalse(op(p,SUBSCRIPT).boundsChecked());
        assertTrue(p.dynamicArraySemantics().isEmpty());
        assertEquals(ContainerOperation.ResultKind.REFERENCE,op(p,FRONT).resultKind());
    }
    @Test void listSpliceTransfersStorageWithoutCopying() {
        var p=green("#include <list>\nvoid f(){std::list<int> a,b;a.push_back(7);std::list<int>::iterator it=a.begin();b.splice(b.end(),a,it);}");
        var begin=op(p,BEGIN); var splice=op(p,SPLICE);
        assertEquals(ContainerOperation.TransferKind.ONE,splice.transferKind()); assertEquals(2,splice.mutatedObjects().size());
        assertTrue(splice.preservesTransferredReferences()); assertTrue(splice.elementCopyConstructor().isEmpty()); assertTrue(splice.elementDestructor().isEmpty());
        var effect=splice.handleEffects().stream().filter(e->e.producer()==begin).findFirst().orElseThrow();
        assertEquals("b",assertInstanceOf(VariableDeclaration.class,effect.sequenceAfter().orElseThrow()).name());
        assertEquals(ContainerOperation.Handle.Validity.VALID,effect.validityAfter());
        assertTrue(Source.render(p.files().getFirst(),Language.CPP).contains("b.splice"));
    }
    @Test void allAndRangeSpliceRetainTheirWrittenBounds() {
        var p=green("#include <list>\nvoid f(){std::list<int> a(2,1),b;std::list<int>::iterator it=a.begin();b.splice(b.end(),a,a.begin(),a.end());b.splice(b.end(),a);}");
        var splices=p.containerSemantics().stream().filter(o->o.kind()==SPLICE).toList();
        assertEquals(ContainerOperation.TransferKind.RANGE,splices.getFirst().transferKind()); assertEquals(4,splices.getFirst().sources().size());
        assertEquals(ContainerOperation.TransferKind.ALL,splices.getLast().transferKind());
    }
    @Test void conditionalTransferDoesNotChooseAnOwner() {
        var p=green("#include <list>\nvoid f(bool flag){std::list<int> a(2,1),b;std::list<int>::iterator it=a.begin();if(flag)b.splice(b.end(),a,it);}");
        var first=op(p,BEGIN); var effect=op(p,SPLICE).handleEffects().stream().filter(e->e.producer()==first).findFirst().orElseThrow();
        assertTrue(effect.sequenceAfter().isEmpty());
    }
    @Test void sizeofAndLookalikesNeverPublishOperations() {
        var p=green("#include <deque>\nstruct Fake{void push_front(int);};void f(){Fake f;f.push_front(1);std::deque<int> d;sizeof(d[0]);}");
        assertFalse(p.containerSemantics().stream().anyMatch(o->o.kind()==PUSH_FRONT || o.kind()==SUBSCRIPT));
    }
    @Test void customComparatorHasSelectedCallAndNoInventedComparisonCount() {
        var p=green("#include <set>\nstruct Less{bool operator()(const int&,const int&)const;};void f(){std::set<int,Less> s;s.insert(1);s.find(1);}");
        assertTrue(op(p,INSERT).type().uniqueKeys()); assertFalse(op(p,FIND).mayInsert()); assertTrue(op(p,INSERT).mayInsert());
        assertEquals("operator()",op(p,FIND).comparator().orElseThrow().name());
        assertEquals(ContainerOperation.ResultKind.VALUE,op(p,INSERT).resultKind());
    }
    @Test void mapLookupAndSubscriptHaveDifferentEffects() {
        var p=green("#include <map>\nvoid f(){std::map<int,int> m;m.find(3);m[3]=9;}");
        assertFalse(op(p,FIND).mayInsert());assertTrue(op(p,SUBSCRIPT).mayInsert());
        assertTrue(op(p,FIND).mutatedObjects().isEmpty());assertEquals(1,op(p,SUBSCRIPT).mutatedObjects().size());
        assertTrue(op(p,SUBSCRIPT).type().mappedType().isPresent());
    }
    @Test void adapterOrdersAndPriorityMutabilityAreDistinct() {
        var p=green("#include <stack>\n#include <queue>\nvoid f(){std::stack<int> s;std::queue<int> q;std::priority_queue<int> p;s.push(1);q.push(1);p.push(1);s.top();q.front();p.top();}");
        assertEquals(Set.of(ContainerType.Order.LIFO,ContainerType.Order.FIFO,ContainerType.Order.PRIORITY),new HashSet<>(p.containerSemantics().stream().filter(o->o.kind()==PUSH).map(o->o.type().order()).toList()));
        assertFalse(p.containerSemantics().stream().filter(o->o.kind()==TOP && o.type().kind()==ContainerType.Kind.PRIORITY_QUEUE).findFirst().orElseThrow().writable());
    }
    @Test void dequePushPreservesReferencesButNotIteratorProofs() {
        var p=green("#include <deque>\nvoid f(){std::deque<int> d(2,1);int& r=d.front();std::deque<int>::iterator it=d.begin();d.push_front(3);}");
        var push=op(p,PUSH_FRONT); var iterator=op(p,BEGIN);
        assertEquals(ContainerOperation.Handle.Validity.UNKNOWN,push.handleEffects().stream().filter(e->e.producer()==iterator).findFirst().orElseThrow().validityAfter());
        var reference=op(p,FRONT);
        assertEquals(reference.handle().orElseThrow().validity(),push.handleEffects().stream().filter(e->e.producer()==reference).findFirst().orElseThrow().validityAfter());
    }
    @Test void reassignedIteratorDoesNotTransferItsOriginalElement() {
        var p=green("#include <list>\nvoid f(){std::list<int> a(2,1),b;std::list<int>::iterator it=a.begin();it=a.end();b.splice(b.end(),a,it);}");
        var first=op(p,BEGIN);
        assertTrue(op(p,SPLICE).handleEffects().stream().filter(e->e.producer()==first).findFirst().orElseThrow().sequenceAfter().isEmpty());
    }
    @Test void nestedUnknownCallInvalidatesPriorIteratorProof() {
        var p=green("#include <list>\nint change(std::list<int>&);void f(){std::list<int> a;std::list<int>::iterator it=a.begin();a.insert(a.begin(),change(a));}");
        var first=op(p,BEGIN);
        assertEquals(ContainerOperation.Handle.Validity.UNKNOWN,op(p,INSERT).handleEffects().stream().filter(e->e.producer()==first).findFirst().orElseThrow().validityAfter());
    }
    @Test void foldingRefreshesContainerFacts() {
        var p=green("#include <deque>\nvoid f(){std::deque<int> a;a.push_back(1+2);}");
        var folded=ConstantFolder.fold(p);
        assertEquals(PUSH_BACK,op(folded,PUSH_BACK).kind());
        assertTrue(Source.render(folded.files().getFirst(),Language.CPP).contains("push_back(3)"));
    }

    @Test void incrementedIteratorAndCopiedAliasDoNotReuseOriginalPosition() {
        for(String edit:List.of("++it;", "it=a.end();std::list<int>::iterator alias=it;")) {
            String selected=edit.contains("alias")?"alias":"it";
            var p=green("#include <list>\nvoid f(){std::list<int> a(2,1),b;std::list<int>::iterator it=a.begin();"+edit+"b.splice(b.end(),a,"+selected+");}");
            var first=op(p,BEGIN);
            assertTrue(op(p,SPLICE).handleEffects().stream().filter(e->e.producer()==first).findFirst().orElseThrow().sequenceAfter().isEmpty());
        }
    }
    @Test void recordSplicePreservesStorageWithoutCopyOrDestruction() {
        var p=green("#include <list>\nstruct R{R();R(const R&);~R();};void f(){R r;std::list<R> a,b;a.push_back(r);b.splice(b.end(),a);b.clear();}");
        assertTrue(op(p,PUSH_BACK).elementCopyConstructor().isPresent());
        assertTrue(op(p,CLEAR).elementDestructor().isPresent());
        assertTrue(op(p,SPLICE).elementCopyConstructor().isEmpty());assertTrue(op(p,SPLICE).elementDestructor().isEmpty());
    }
    @Test void userSpecializationIsNotAStandardContainer() {
        var p=green("#include <list>\nstruct Tag {}; namespace std {template<> class list<Tag>{public:void push_back(const Tag&);};}void f(){Tag t;std::list<Tag> a;a.push_back(t);}");
        assertTrue(p.containerTypes().isEmpty());assertTrue(p.containerSemantics().isEmpty());
    }

    @Test void nodeContainerClearPreservesEndBasedIteratorsOnly() {
        var p=green("#include <list>\nvoid f(){std::list<int> a(3,7);a.begin();a.end();a.rbegin();a.rend();a.clear();}");
        var clear=op(p,CLEAR);
        for(var kind:List.of(END,RBEGIN)) assertEquals(ContainerOperation.Handle.Validity.VALID,clear.handleEffects().stream().filter(e->e.producer().kind()==kind).findFirst().orElseThrow().validityAfter());
        for(var kind:List.of(BEGIN,REND)) assertEquals(ContainerOperation.Handle.Validity.INVALID,clear.handleEffects().stream().filter(e->e.producer().kind()==kind).findFirst().orElseThrow().validityAfter());
    }

    @Test void emptyBeginStaysWithItsSourceAfterAllSplice() {
        var p=green("#include <list>\nvoid f(){std::list<int> a,b;std::list<int>::iterator it=a.begin();b.splice(b.end(),a);}");
        var first=op(p,BEGIN);
        assertEquals("a",assertInstanceOf(VariableDeclaration.class,op(p,SPLICE).handleEffects().stream().filter(e->e.producer()==first).findFirst().orElseThrow().sequenceAfter().orElseThrow()).name());
    }

    @Test void inheritedComparatorResolvesItsAccessibleBaseOperator() {
        var p=green("#include <set>\nint comparisons;struct Base{bool operator()(int a,int b)const{++comparisons;return a<b;}};struct Less:Base{};void f(){std::set<int,Less> s;s.insert(1);s.find(1);}");
        assertEquals("operator()",op(p,FIND).comparator().orElseThrow().name());
        assertTrue(op(p,FIND).comparator().orElseThrow().body().isPresent());
    }
    @Test void mapScalarPairInsertProducesValueResult() {
        var p=green("#include <map>\nvoid f(){std::map<int,int> m;std::pair<const int,int> value(1,2);m.insert(value);}");
        assertEquals(ContainerOperation.ResultKind.VALUE,op(p,INSERT).resultKind());
    }

    @Test void constructorInputsPrecedeTheirContainerOperation() {
        var p=green("#include <list>\nvoid f(){std::list<int> a;std::list<int> b((int)a.size(),1);}");
        assertTrue(p.containerSemantics().indexOf(op(p,SIZE)) < p.containerSemantics().indexOf(op(p,FILL)));
    }

    @Test void eraseResultMayBeSourceEndAfterFullSplice() {
        for(String erase:List.of("a.erase(a.begin())", "a.erase(a.begin(),a.end())")) {
            var p=green("#include <list>\nvoid f(){std::list<int> a(1,7),b;std::list<int>::iterator it="+erase+";b.splice(b.end(),a);}");
            var result=p.containerSemantics().stream().filter(o->o.kind()==ERASE || o.kind()==ERASE_RANGE).findFirst().orElseThrow();
            var effect=op(p,SPLICE).handleEffects().stream().filter(e->e.producer()==result).findFirst().orElseThrow();
            assertTrue(effect.sequenceAfter().isEmpty());
            assertEquals(ContainerOperation.Handle.Validity.VALID,effect.validityAfter());
        }
    }
    @Test void possibleEndResultsAreNotDefinitelyInvalidatedByClear() {
        for(String lookup:List.of("find", "lower_bound", "upper_bound")) {
            var p=green("#include <set>\nvoid f(){std::set<int> a;a."+lookup+"(7);a.clear();}");
            assertEquals(ContainerOperation.Handle.Validity.UNKNOWN,op(p,CLEAR).handleEffects().getFirst().validityAfter());
        }
        var p=green("#include <list>\nvoid f(){std::list<int> a(1,7);a.erase(a.begin());a.clear();}");
        assertEquals(ContainerOperation.Handle.Validity.UNKNOWN,op(p,CLEAR).handleEffects().stream().filter(e->e.producer().kind()==ERASE).findFirst().orElseThrow().validityAfter());
    }
    @Test void emptyReverseEndKeepsItsEndBaseAcrossClearAndSplice() {
        var p=green("#include <list>\nvoid f(){std::list<int> a,b;a.rend();a.clear();b.splice(b.end(),a);}");
        var reverse=op(p,REND);
        assertEquals(ContainerOperation.Handle.Validity.VALID,op(p,CLEAR).handleEffects().stream().filter(e->e.producer()==reverse).findFirst().orElseThrow().validityAfter());
        assertEquals("a",assertInstanceOf(VariableDeclaration.class,op(p,SPLICE).handleEffects().stream().filter(e->e.producer()==reverse).findFirst().orElseThrow().sequenceAfter().orElseThrow()).name());
    }

}
