/* Character-sequence and algorithm normalization. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.generation.Source;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.model.SequenceOperation.Kind.*;
class SequenceNormalizationTest {
    Project green(String code) {return SequenceSemantics.green(NameResolver.resolve(Project.create().addFile(Parser.parse("sequence.cpp",code,Language.CPP)).build()));}
    SequenceOperation op(Project p,SequenceOperation.Kind kind){return p.sequenceSemantics().stream().filter(o->o.kind()==kind).findFirst().orElseThrow(()->new AssertionError("missing "+kind+" from "+p.sequenceSemantics().stream().map(SequenceOperation::kind).toList()));}
    @Test void countedStringsRetainNulAndIndependentSubstring() {
        var p=green("#include <string>\nvoid f(){std::string s(\"a\\0b\",3);s.append(\"c\\0d\",3);s.find(\"\\0\",0,1);std::string part=s.substr(1,2);s.size();s[1];s.at(1);}");
        assertEquals(SequenceOperation.LengthKind.EXPLICIT_COUNT,op(p,STRING_BUFFER).lengthKind());
        assertEquals(SequenceOperation.LengthKind.EXPLICIT_COUNT,op(p,STRING_APPEND).lengthKind());
        assertEquals(SequenceOperation.LengthKind.EXPLICIT_COUNT,op(p,STRING_FIND).lengthKind());
        assertEquals(SequenceOperation.ResultKind.SEQUENCE,op(p,STRING_SUBSTR).resultKind());
        assertEquals(SequenceOperation.ResultKind.REFERENCE,op(p,STRING_SUBSCRIPT).resultKind());
        assertTrue(Source.render(p.files().getFirst(),Language.CPP).contains("\\0"));
        assertFalse(p.characterSequenceTypes().isEmpty());
    }
    @Test void nullTerminatedAndFillAreDifferentLengths() {
        var p=green("#include <string>\nvoid f(){std::string s(\"a\\0b\");std::string t(3,'x');s.append(3,'z');}");
        assertEquals(SequenceOperation.LengthKind.NULL_TERMINATED,op(p,STRING_C_STRING).lengthKind());
        assertEquals(SequenceOperation.LengthKind.EXPLICIT_COUNT,op(p,STRING_FILL).lengthKind());
        assertEquals("3",Source.render(op(p,STRING_APPEND).count().orElseThrow(),Language.CPP));
    }
    @Test void algorithmsRetainRangesOutputsCallbacksAndAccumulator() {
        var p=green("#include <vector>\n#include <algorithm>\n#include <numeric>\n#include <iterator>\nstruct F{int operator()(int)const;};void f(){int a[3];std::vector<int> v;std::copy(a,a+3,std::back_inserter(v));F f;std::find_if(v.begin(),v.end(),f);std::transform(v.begin(),v.end(),v.begin(),f);std::sort(v.begin(),v.end());std::remove(v.begin(),v.end(),7);std::accumulate(v.begin(),v.end(),0L);std::distance(v.begin(),v.end());std::vector<int>::iterator i=v.begin();std::advance(i,2);}");
        assertEquals("v",assertInstanceOf(NameExpression.class,op(p,COPY).outputDestination().orElseThrow()).name());
        assertEquals("operator()",op(p,FIND_IF).callableTarget().orElseThrow().name());
        assertEquals(SequenceOperation.SizeEffect.UNCHANGED,op(p,REMOVE).sizeEffect());
        assertEquals(SequenceOperation.ResultKind.ITERATOR,op(p,REMOVE).resultKind());
        assertEquals("long",op(p,ACCUMULATE).accumulatorType().orElseThrow().canonicalIdentity());
        assertTrue(op(p,ADVANCE).count().isPresent());assertTrue(op(p,DISTANCE).first().isPresent());
    }
    @Test void invalidCategoriesAndUnevaluatedCallsStayRaw() {
        var p=green("#include <list>\n#include <algorithm>\n#include <string>\nvoid f(){std::list<int> a;std::sort(a.begin(),a.end());std::string s;sizeof(s.at(0));}");
        assertTrue(p.sequenceSemantics().stream().noneMatch(o->o.kind()==SORT || o.kind()==STRING_AT));
    }
    @Test void foldingRefreshesCountReferences() {
        var p=ConstantFolder.fold(green("#include <string>\nvoid f(){std::string s(\"abc\",1+2);}"));
        assertEquals("3",Source.render(op(p,STRING_BUFFER).count().orElseThrow(),Language.CPP));
    }
    @Test void reverseStringTraversalAndConstAccessStayDistinct() {
        var p=green("#include <string>\nvoid f(){std::string s;const std::string c;s.rbegin();s.rend();c.at(0);}");
        assertEquals(SequenceOperation.Direction.REVERSE,op(p,STRING_RBEGIN).direction());
        assertEquals(SequenceOperation.Direction.REVERSE,op(p,STRING_REND).direction());
        assertFalse(op(p,STRING_AT).writable());
    }
    @Test void lookalikeMethodsNeverAcquireLibrarySemantics() {
        var p=green("struct S{int size();void append(int,int);};void f(){S s;s.size();s.append(1,2);}");
        assertTrue(p.sequenceSemantics().isEmpty());
    }

    @Test void substringCopyOverloadsNeverBecomeBufferCopies() {
        var p=green("#include <string>\nvoid f(){std::string a;std::string b(a,1);std::string c(a,1,2);a.append(b,1,2);a.assign(b,1,2);}");
        assertTrue(p.sequenceSemantics().stream().noneMatch(o->o.kind()==STRING_BUFFER || o.kind()==STRING_APPEND || o.kind()==STRING_ASSIGN));
    }

    @Test void namespaceQualifiedPointerAlgorithmsAreNotStringMembers() {
        var p=green("#include <algorithm>\n#include <numeric>\n#include <iterator>\nbool pred(int);int twice(int);void f(){int a[3],b[3];std::copy(a,a+3,b);std::find_if(a,a+3,pred);std::transform(a,a+3,b,twice);std::sort(a,a+3);std::remove(a,a+3,1);std::accumulate(a,a+3,0L);std::distance(a,a+3);int* i=a;std::advance(i,1);}");
        for(var kind:List.of(COPY,FIND_IF,TRANSFORM,SORT,REMOVE,ACCUMULATE,DISTANCE,ADVANCE))assertNotNull(op(p,kind));
        assertEquals("long",op(p,ACCUMULATE).accumulatorType().orElseThrow().canonicalIdentity());
    }

    @Test void readOnlyRangesAndOutputsNeverAcquireMutationFacts() {
        var p=green("#include <algorithm>\nint twice(int);void f(){const int a[3]={1,2,3};int b[3];std::sort(a,a+3);std::remove(a,a+3,1);std::copy(b,b+3,a);std::transform(b,b+3,a,twice);}");
        assertTrue(p.sequenceSemantics().stream().noneMatch(o->Set.of(SORT,REMOVE,COPY,TRANSFORM).contains(o.kind())));
    }

    @Test void appendAndAssignReturnTheSequenceObjectReference() {
        var p=green("#include <string>\nvoid f(){std::string s;std::string& r=s.append(\"x\");s.assign(\"y\");r[0]='z';}");
        assertEquals(SequenceOperation.ResultKind.SEQUENCE_REFERENCE,op(p,STRING_APPEND).resultKind());
        assertEquals(SequenceOperation.ResultKind.SEQUENCE_REFERENCE,op(p,STRING_ASSIGN).resultKind());
        assertInstanceOf(ReferenceType.class,op(p,STRING_APPEND).resultType().orElseThrow());
    }

    @Test void savedOutputAdaptersKeepDestinationAndSizeChange() {
        for(String adapter:List.of("back_insert_iterator", "front_insert_iterator", "insert_iterator")) {
            String factory=adapter.equals("back_insert_iterator")?"back_inserter":adapter.equals("front_insert_iterator")?"front_inserter":"inserter";
            String inputs=adapter.equals("insert_iterator")?"v,v.begin()":"v";
            var p=green("#include <list>\n#include <iterator>\n#include <algorithm>\nvoid f(){int a[3];std::list<int> v;std::"+adapter+"<std::list<int> > out=std::"+factory+"("+inputs+");std::copy(a,a+3,out);}");
            assertEquals(SequenceOperation.SizeEffect.MAY_CHANGE,op(p,COPY).sizeEffect());
            assertEquals("v",assertInstanceOf(NameExpression.class,op(p,COPY).outputDestination().orElseThrow()).name());
        }
    }

    @Test void namedReverseAdapterRetainsItsBaseIterator() {
        var p=green("#include <vector>\n#include <iterator>\nvoid f(){std::vector<int> a(3,7);std::reverse_iterator<std::vector<int>::iterator> r(a.end());}");
        assertEquals(SequenceOperation.Direction.REVERSE,op(p,REVERSE_ITERATOR).direction());
        assertTrue(op(p,REVERSE_ITERATOR).first().isPresent());
    }

    @Test void directInsertAdapterKeepsDestinationAndPosition() {
        var p=green("#include <vector>\n#include <iterator>\n#include <algorithm>\nvoid f(){int a[2];std::vector<int> b;std::insert_iterator<std::vector<int> > out(b,b.begin());std::copy(a,a+2,out);}");
        assertEquals(SequenceOperation.SizeEffect.MAY_CHANGE,op(p,COPY).sizeEffect());
        assertEquals("b",assertInstanceOf(NameExpression.class,op(p,COPY).outputDestination().orElseThrow()).name());
    }

    @Test void reassignedOutputAdapterDoesNotClaimItsOriginalDestination() {
        var p=green("#include <vector>\n#include <iterator>\n#include <algorithm>\nvoid f(){int a[2];std::vector<int> v,w;std::back_insert_iterator<std::vector<int> > out=std::back_inserter(v);out=std::back_inserter(w);std::copy(a,a+2,out);}");
        assertEquals("out",assertInstanceOf(NameExpression.class,op(p,COPY).outputDestination().orElseThrow()).name());
        assertEquals(SequenceOperation.SizeEffect.MAY_CHANGE,op(p,COPY).sizeEffect());
    }

}
