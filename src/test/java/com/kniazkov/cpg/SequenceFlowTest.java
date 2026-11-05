/* C++03 character/range effects and lifetime boundaries. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SequenceFlowTest {
    private Project analyze(String code) {
        return UnreadWrites.analyze(SequenceSemantics.green(ContainerSemantics.green(DynamicArraySemantics.green(NameResolver.resolve(Project.create()
            .addFile(Parser.parse("sequences.cpp", "#include <string>\n#include <vector>\n#include <iterator>\n#include <algorithm>\n#include <numeric>\n" + code, Language.CPP)).build())))));
    }
    private FunctionFlow flow(Project p) { return p.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow(); }
    private boolean reaches(Project p, Node write, String name) {
        return flow(p).data().stream().anyMatch(e -> e.from()==write && e.kind()==FunctionFlow.Dependency.DEFINITION
                && e.to() instanceof NameExpression n && n.name().equals(name));
    }
    private SequenceOperation operation(Project p, SequenceOperation.Kind kind) {
        return p.sequenceSemantics().stream().filter(o -> o.kind()==kind).findFirst().orElseThrow();
    }
    private void complete(Project p) { assertFalse(p.hasErrors(),p.semanticDiagnostics().toString()); assertTrue(flow(p).complete()); }
    @Test void stringReferencesWriteBackingButCopiesDoNot() {
        var p=analyze("int f(){std::string s(3,'a'); char& reference=s[0]; char copy=s[1]; reference='x'; copy='y'; return s[0]+copy;}");
        complete(p);
        var writes=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class);
        assertTrue(reaches(p,writes.getFirst(),"s")); assertFalse(reaches(p,writes.getLast(),"s"));
        assertTrue(writes.getFirst().diagnostics().stream().noneMatch(d->d.code().equals(UnreadWrites.CODE)));
    }
    @Test void appendDefinesStringButFindAndSizeOnlyRead() {
        var p=analyze("int f(){std::string s(3,'a'); s.append(2,'b'); s.find('b'); return s.size();}");
        complete(p);
        var append=operation(p,SequenceOperation.Kind.STRING_APPEND);
        assertTrue(reaches(p,append,"s"));
        assertFalse(reaches(p,operation(p,SequenceOperation.Kind.STRING_FIND),"s"));
        assertTrue(flow(p).data().stream().anyMatch(e->e.from()==append && e.to()==operation(p,SequenceOperation.Kind.STRING_SIZE) && e.kind()==FunctionFlow.Dependency.DEFINITION));
    }
    @Test void copyOutputAdapterWritesDestinationNotInput() {
        var p=analyze("int f(){std::vector<int> a(3,7); std::vector<int> b; std::copy(a.begin(),a.end(),std::back_inserter(b)); return a.size()+b.size();}");
        complete(p);var copy=operation(p,SequenceOperation.Kind.COPY);
        assertTrue(reaches(p,copy,"b"));assertFalse(reaches(p,copy,"a"));
        assertFalse(reaches(p,operation(p,SequenceOperation.Kind.BACK_INSERTER),"b"));
    }
    @Test void advanceWritesIteratorNotSequenceAndDistanceDoesNotWrite() {
        var p=analyze("int f(){std::vector<int> a(3,7); std::vector<int>::iterator i=a.begin(); std::advance(i,1); int d=std::distance(i,a.end()); *i=9; return a[1]+d;}");
        complete(p);var advance=operation(p,SequenceOperation.Kind.ADVANCE);
        assertTrue(reaches(p,advance,"i"));assertFalse(reaches(p,advance,"a"));
        assertFalse(reaches(p,operation(p,SequenceOperation.Kind.DISTANCE),"a"));
        assertTrue(reaches(p,LazyLibraryGraphTest.nodes(p,AssignmentExpression.class).getFirst(),"a"));
    }
    @Test void removeAndSortMutateRangeWithoutSizeChange() {
        var p=analyze("int f(){std::vector<int> a(3,7); std::sort(a.begin(),a.end()); std::remove(a.begin(),a.end(),7); return a.size();}");
        complete(p);
        for(var kind:List.of(SequenceOperation.Kind.SORT,SequenceOperation.Kind.REMOVE)) {
            var op=operation(p,kind);assertTrue(reaches(p,op,"a"));assertEquals(SequenceOperation.SizeEffect.UNCHANGED,op.sizeEffect());
        }
    }
    @Test void callbackEffectsReachGlobalAndUnlinkedCallbackRemainsConservative() {
        var p=analyze("int count; struct Pred { bool operator()(int v) const {++count; return v==7;} }; int f(){std::vector<int> a(3,7); Pred predicate; count=2; std::find_if(a.begin(),a.end(),predicate); return count;}");
        complete(p);var find=operation(p,SequenceOperation.Kind.FIND_IF);
        assertTrue(find.callableTarget().isPresent());assertTrue(reaches(p,find,"count"));
        var context=new EditContext();var builder=context.edit(p,Project.Builder.class);
        var ops=new ArrayList<NodeBuilder<? extends SequenceOperation>>();
        for(var op:p.sequenceSemantics())ops.add(context.edit(op,SequenceOperation.Builder.class).setCallableTarget(null));
        var fallback=DataFlow.analyze(builder.setSequenceSemantics(ops).build());
        assertTrue(reaches(fallback,operation(fallback,SequenceOperation.Kind.FIND_IF),"count"));
    }
    @Test void appendResultAliasesWholeStringRatherThanCharacterStorage() {
        var p=analyze("int f(){std::string s(3,'a'); s.append(1,'x').at(0)='z'; std::string& r=s.append(1,'x'); r[1]='y'; return s[0]+s[1];}");
        complete(p);var writes=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class);
        assertEquals(2,writes.size());
        for(var write:writes)assertTrue(reaches(p,write,"s"));
    }
    @Test void rawPointerRangesAndSavedOutputAdaptersRetainStorage() {
        var p=analyze("int f(){int a[3]={1,2,3}; int b[3]={0,0,0}; int* first=a; int* output=b; std::copy(first,first+3,output); return a[0]+b[0];}");
        complete(p);var copy=operation(p,SequenceOperation.Kind.COPY);
        assertTrue(reaches(p,copy,"b"));assertFalse(reaches(p,copy,"a"));
        var adapter=analyze("int f(){std::vector<int> a(3,7); std::vector<int> b; std::back_insert_iterator<std::vector<int> > output=std::back_inserter(b); std::copy(a.begin(),a.end(),output); return b.size();}");
        complete(adapter);assertTrue(reaches(adapter,operation(adapter,SequenceOperation.Kind.COPY),"b"));
    }
    @Test void directInsertAdapterAndReassignedAdaptersTrackDestination() {
        var p=analyze("int f(){int a[2]={1,2}; std::vector<int> b; std::insert_iterator<std::vector<int> > out(b,b.begin()); std::copy(a,a+2,out); return b.size();}");
        complete(p);assertTrue(reaches(p,operation(p,SequenceOperation.Kind.COPY),"b"));
        var rebound=analyze("int f(){int a[2]={1,2}; std::vector<int> b; std::vector<int> c; std::back_insert_iterator<std::vector<int> > out=std::back_inserter(b); out=std::back_inserter(c); std::copy(a,a+2,out); return b.size()+c.size();}");
        complete(rebound);var copy=operation(rebound,SequenceOperation.Kind.COPY);
        assertTrue(reaches(rebound,copy,"c"));assertFalse(reaches(rebound,copy,"b"));
    }
    @Test void unknownAdapterRebindingRetainsPossibleDestinationWrites() {
        var p=analyze("void change(std::back_insert_iterator<std::vector<int> >& out); int f(){int a[2]={1,2}; std::vector<int> b; std::vector<int> c; std::back_insert_iterator<std::vector<int> > out=std::back_inserter(b); change(out); std::copy(a,a+2,out); return b.size()+c.size();}");
        complete(p);var copy=operation(p,SequenceOperation.Kind.COPY);
        assertTrue(reaches(p,copy,"b"));assertTrue(reaches(p,copy,"c"));
        var alias=analyze("void change(std::back_insert_iterator<std::vector<int> >& out); int f(){int a[2]={1,2}; std::vector<int> b; std::vector<int> c; std::back_insert_iterator<std::vector<int> > out=std::back_inserter(b); std::back_insert_iterator<std::vector<int> >& ref=out; change(ref); std::copy(a,a+2,out); return b.size()+c.size();}");
        complete(alias);assertTrue(reaches(alias,operation(alias,SequenceOperation.Kind.COPY),"c"));
    }
    @Test void adapterReferenceBindingReadsSuppressOnlyUsedReferenceWarnings() {
        for(String use:List.of("alias=std::inserter(c,c.begin());", "rebind(alias);")) {
            var p=analyze("void rebind(std::insert_iterator<std::vector<int> >& out); int f(){int a[2]={1,2}; std::vector<int> b; std::vector<int> c; std::insert_iterator<std::vector<int> > out(b,b.begin()); std::insert_iterator<std::vector<int> >& alias=out; "+use+" std::copy(a,a+2,out); return b.size()+c.size();}");
            complete(p);
            var reference=LazyLibraryGraphTest.nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals("alias")).findFirst().orElseThrow();
            assertTrue(reference.diagnostics().stream().noneMatch(d->d.code().equals(UnreadWrites.CODE)));
            assertTrue(flow(p).data().stream().anyMatch(e->e.from()==reference && e.kind()==FunctionFlow.Dependency.DEFINITION));
        }
        var unused=analyze("int f(){std::vector<int> b; std::insert_iterator<std::vector<int> > out(b,b.begin()); std::insert_iterator<std::vector<int> >& unused=out; return b.size();}");
        complete(unused);
        assertTrue(LazyLibraryGraphTest.nodes(unused,VariableDeclaration.class).stream().filter(v->v.name().equals("unused"))
                .anyMatch(v->v.diagnostics().stream().anyMatch(d->d.code().equals(UnreadWrites.CODE))));
    }
    @Test void reverseIteratorConstructionRetainsRangeStorage() {
        var p=analyze("int f(){std::vector<int> a(3,7); std::reverse_iterator<std::vector<int>::iterator> r(a.end()); *r=9; return a[2];}");
        complete(p);assertTrue(reaches(p,LazyLibraryGraphTest.nodes(p,AssignmentExpression.class).getFirst(),"a"));
        var begin=analyze("int f(){std::vector<int> a(3,7); std::vector<int>::reverse_iterator r=a.rbegin(); *r=9; return a[2];}");
        complete(begin);assertTrue(reaches(begin,LazyLibraryGraphTest.nodes(begin,AssignmentExpression.class).getFirst(),"a"));
    }
    @Test void materializationDoesNotAdmitNestedOrDiscardedStringTemporary() {
        var p=analyze("void consume(std::string value); int f(){std::string s; consume(s.substr(0,1)); return 0;}");
        assertFalse(flow(p).complete());
        var custom=analyze("struct Traits:std::char_traits<char> {}; int f(){std::basic_string<char,Traits> s; std::basic_string<char,Traits> part=s.substr(0,1); return 0;}");
        assertFalse(flow(custom).complete());
    }
    @Test void directDefaultSubstringMaterializationHasIndependentStorage() {
        var p=analyze("int f(){std::string original(3,'a'); std::string part=original.substr(1,2); part[0]='x'; return original[0]+part[0];}");
        complete(p);var write=LazyLibraryGraphTest.nodes(p,AssignmentExpression.class).getFirst();
        assertTrue(reaches(p,write,"part"));assertFalse(reaches(p,write,"original"));
        var discard=analyze("int f(){std::string s; s.substr(0,1); return 0;}");assertFalse(flow(discard).complete());
        var user=analyze("struct R {R(); ~R();}; R make(); int f(){R r=make(); return 0;}");assertFalse(flow(user).complete());
    }
}
