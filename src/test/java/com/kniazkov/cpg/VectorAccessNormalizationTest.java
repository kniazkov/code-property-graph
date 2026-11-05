/* Vector access and conservative temporal handle facts. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.generation.Source;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.model.DynamicArrayOperation.Kind.*;
import static com.kniazkov.cpg.model.DynamicArrayOperation.Handle.Validity.*;
class VectorAccessNormalizationTest {
    private Project green(String code) {
        return DynamicArraySemantics.green(NameResolver.resolve(Project.create()
                .addFile(Parser.parse("access.cpp", "#include <vector>\n" + code, Language.CPP)).build()));
    }
    private DynamicArrayOperation op(Project p, DynamicArrayOperation.Kind kind) {
        return p.dynamicArraySemantics().stream().filter(o -> o.kind() == kind).findFirst().orElseThrow(() -> new AssertionError("Missing " + kind + ": " + p.dynamicArraySemantics().stream().map(DynamicArrayOperation::kind).toList()));
    }
    @Test void accessQueriesAndBoundariesRetainWrittenTargets() {
        var p = green("void f(){std::vector<int> a(3,1); int& r=a[1]; a.front(); a.back(); a.at(2); a.size(); a.empty(); a.capacity(); a.max_size(); a.begin(); a.end(); a.rbegin(); a.rend(); const std::vector<int>& c=a; c.front();}");
        for (var kind : List.of(SUBSCRIPT, FRONT, BACK, AT)) assertEquals(DynamicArrayOperation.ResultKind.REFERENCE, op(p,kind).resultKind());
        assertTrue(op(p,SUBSCRIPT).writable()); assertTrue(op(p,AT).boundsChecked()); assertFalse(op(p,SUBSCRIPT).boundsChecked());
        for (var kind : List.of(SIZE, IS_EMPTY, CAPACITY, MAX_SIZE)) assertEquals(DynamicArrayOperation.ResultKind.VALUE, op(p,kind).resultKind());
        assertEquals(DynamicArrayOperation.Handle.Direction.REVERSE, op(p,RBEGIN).handle().orElseThrow().direction());
        assertEquals(DynamicArrayOperation.Handle.Position.END, op(p,END).handle().orElseThrow().positionKind());
        assertFalse(p.dynamicArraySemantics().stream().filter(o -> o.kind()==FRONT).toList().getLast().writable());
        assertTrue(Source.render(p.files().getFirst(), Language.CPP).contains("a.at(2)"));
        assertTrue(p.dynamicArraySemantics().stream().allMatch(o -> o.selectedTarget()!=null));
    }
    @Test void reservedInsertionPreservesPrefixAndSwapChangesOwner() {
        var p = green("void f(){std::vector<int> a; a.reserve(8); a.push_back(1); a.push_back(2); int& first=a.front(); a.insert(a.begin()+1,9); std::vector<int> b(1,4); a.swap(b);}");
        var producer=op(p,FRONT);
        assertEquals(VALID, op(p,INSERT).handleEffects().stream().filter(e->e.producer()==producer).findFirst().orElseThrow().validityAfter());
        var swap=op(p,SWAP); assertEquals(2,swap.mutatedObjects().size());
        var effect=swap.handleEffects().stream().filter(e->e.producer()==producer).findFirst().orElseThrow();
        assertEquals(VALID,effect.validityAfter());
        assertEquals("b",assertInstanceOf(VariableDeclaration.class,effect.sequenceAfter().orElseThrow()).name());
    }
    @Test void unknownCapacityAndConditionalReserveNeverPromiseValidity() {
        var p = green("void f(int n){std::vector<int> a(2,1); int& first=a.front(); a.reserve(n); a.insert(a.begin()+1,9);}");
        var first=op(p,FRONT);
        assertEquals(UNKNOWN,op(p,INSERT).handleEffects().stream().filter(e->e.producer()==first).findFirst().orElseThrow().validityAfter());
        var conditional=green("void f(bool yes){std::vector<int> a(2,1); if(yes) a.reserve(20); int& first=a.front(); a.insert(a.begin()+1,9);}");
        assertEquals(UNKNOWN,op(conditional,INSERT).handleEffects().stream().filter(e->e.producer()==op(conditional,FRONT)).findFirst().orElseThrow().validityAfter());
    }
    @Test void recordQueriesHaveNoCopiesAndDestructionRemainsLinked() {
        var p=green("int count; struct R{R();R(const R&);~R(){++count;}};void f(){std::vector<R> a; a.size(); a.front(); a.pop_back(); a.clear();}");
        assertTrue(op(p,SIZE).elementCopyConstructor().isEmpty()); assertTrue(op(p,FRONT).elementCopyConstructor().isEmpty());
        assertTrue(op(p,SIZE).elementDestructor().isEmpty()); assertTrue(op(p,POP_BACK).elementDestructor().isPresent()); assertTrue(op(p,CLEAR).elementDestructor().isPresent());
    }
    @Test void unknownCallDropsReservedCapacityProof() {
        var p=green("void mutate(std::vector<int>&); void f(){std::vector<int> a; a.reserve(20); a.push_back(1); a.push_back(2); int& first=a.front(); mutate(a); a.insert(a.begin()+1,9);}");
        assertEquals(UNKNOWN,op(p,INSERT).handleEffects().stream().filter(e->e.producer()==op(p,FRONT)).findFirst().orElseThrow().validityAfter());
    }
    @Test void scalarModifiersAndRangeInputsRemainDistinct() {
        var p=green("void f(){std::vector<int> a(3,1); int values[2]; a.reserve(12); a.resize(4,9); a.insert(a.begin(),8); a.insert(a.end(),2,7); a.insert(a.end(),values,values+2); a.erase(a.begin()); a.erase(a.begin(),a.end()); a.pop_back(); a.clear();}");
        for (var kind : List.of(RESERVE,RESIZE,INSERT,INSERT_FILL,INSERT_RANGE,ERASE,ERASE_RANGE,POP_BACK,CLEAR)) op(p,kind);
        assertEquals(3,op(p,INSERT_RANGE).sources().size());
        assertEquals(DynamicArrayOperation.ResultKind.NONE,op(p,INSERT_FILL).resultKind());
        assertEquals(DynamicArrayOperation.ResultKind.ITERATOR,op(p,ERASE).resultKind());
    }
    @Test void nontrivialAssignmentShiftsRemainRaw() {
        var p=green("struct R{R();R(const R&);R& operator=(const R&);};void f(){R r;std::vector<R> a; a.insert(a.begin(),r); a.erase(a.begin());}");
        assertFalse(p.dynamicArraySemantics().stream().anyMatch(o->o.kind()==INSERT || o.kind()==ERASE));
        assertTrue(Source.render(p.files().getFirst(),Language.CPP).contains("a.erase"));
    }
    @Test void reverseBeginUsesItsEndBaseForInvalidation() {
        var p=green("void f(){std::vector<int> a(2,1);a.reserve(8);std::vector<int>::reverse_iterator r=a.rbegin();a.push_back(3);}");
        assertEquals(INVALID,op(p,APPEND).handleEffects().stream().filter(e->e.producer()==op(p,RBEGIN)).findFirst().orElseThrow().validityAfter());
    }
    @Test void argumentEffectsPrecedeEnclosingMutation() {
        var p=green("int mutate(std::vector<int>&);void f(){std::vector<int> a(2,1);a.reserve(8);int& r=a.front();a.insert(a.begin()+1,mutate(a));}");
        assertEquals(UNKNOWN,op(p,INSERT).handleEffects().stream().filter(e->e.producer()==op(p,FRONT)).findFirst().orElseThrow().validityAfter());
        var q=green("void f(){std::vector<int> a(2,1);a.reserve(8);a.insert(a.begin(),1);}");
        assertEquals(INVALID,op(q,INSERT).handleEffects().stream().filter(e->e.producer()==op(q,BEGIN)).findFirst().orElseThrow().validityAfter());
    }
    @Test void unevaluatedSizeofDoesNotGrowSequence() {
        var p=green("void f(){std::vector<int> a(2,1);sizeof(a.insert(a.begin(),9));int& r=a[2];}");
        assertFalse(p.dynamicArraySemantics().stream().anyMatch(o->o.kind()==INSERT));
        assertEquals(UNKNOWN,op(p,SUBSCRIPT).handle().orElseThrow().validity());
    }
    @Test void assignmentDropsOldCapacityLowerBound() {
        var p=green("void f(){std::vector<int> a;std::vector<int> b(2,1);a.reserve(100);a=b;a.resize(2);int& r=a.front();a.push_back(3);}");
        assertEquals(UNKNOWN,op(p,APPEND).handleEffects().stream().filter(e->e.producer()==op(p,FRONT)).findFirst().orElseThrow().validityAfter());
    }
    @Test void conditionalSwapKeepsBothPossibleOwners() {
        for (String owner : List.of("a", "b")) {
            var p=green("void f(bool flag){std::vector<int> a(2,1),b(2,2);int& r=a.front();if(flag)a.swap(b);"+owner+".clear();}");
            var swap=op(p,SWAP).handleEffects().stream().filter(e->e.producer()==op(p,FRONT)).findFirst().orElseThrow();
            assertTrue(swap.sequenceAfter().isEmpty());
            assertEquals(UNKNOWN,op(p,CLEAR).handleEffects().stream().filter(e->e.producer()==op(p,FRONT)).findFirst().orElseThrow().validityAfter());
        }
    }
    @Test void unconditionalSwapAfterBranchStillTransfersSingletonOwner() {
        var p=green("void f(bool flag){std::vector<int> a(2,1),b(2,2);if(flag)a.size();int& r=a.front();a.swap(b);}");
        var effect=op(p,SWAP).handleEffects().stream().filter(e->e.producer()==op(p,FRONT)).findFirst().orElseThrow();
        assertEquals("b",assertInstanceOf(VariableDeclaration.class,effect.sequenceAfter().orElseThrow()).name());
    }
    @Test void boolRemainsRaw() {
        var p=green("void f(){std::vector<bool> b; b.front(); b.reserve(8); b.clear();}");
        assertTrue(p.dynamicArraySemantics().isEmpty());
    }
}
