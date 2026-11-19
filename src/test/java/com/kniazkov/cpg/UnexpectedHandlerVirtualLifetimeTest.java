/* Complete-object versus base-subobject virtual lifetime summaries. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import org.junit.jupiter.api.Test;
import static com.kniazkov.cpg.model.AuxiliaryFlowVertex.Kind.*;
import static org.junit.jupiter.api.Assertions.*;

class UnexpectedHandlerVirtualLifetimeTest {
    private final UnexpectedHandlerLifetimeTest fixture = new UnexpectedHandlerLifetimeTest();
    void check(String code, AuxiliaryFlowVertex.Kind... expected) { fixture.check(code, expected); }
    @Test void implicitDiamondPreservesReplacement() {
        check("struct V{};struct L:virtual V{};struct R:virtual V{};struct D:L,R{};void h(){D d;throw 1;}",EXCEPTION_REPLACEMENT);
    }
    @Test void sharedVirtualDestructorRunsOnlyOnce() {
        check("struct V{~V(){throw 1;}};struct L:virtual V{};struct R:virtual V{};struct D:L,R{};void h(){{D d;}throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void aliasedVirtualBaseIdentityIsShared() {
        check("struct V{~V(){throw 1;}};typedef V Alias;struct L:virtual Alias{};struct R:virtual V{};struct D:L,R{};void h(){{D d;}throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void virtualAndNonvirtualSubobjectsOfSameTypeRemainDistinct() {
        check("struct V{~V(){throw 1;}};struct L:virtual V{};struct R:V{};struct D:L,R{};void h(){{D d;}throw true;}",TERMINATE_EXIT);
    }
    @Test void virtualDestructorDuringUnwindingTerminates() {
        check("struct V{~V(){throw 1;}};struct L:virtual V{};struct R:virtual V{};struct D:L,R{};void h(){D d;throw 1;}",TERMINATE_EXIT);
    }
    @Test void baseConstructorSpecificationDoesNotCoverMostDerivedVirtualConstruction() {
        String types="struct V{V(){throw 1;}};struct L:virtual V{L()throw(){}};struct D:L{};";
        check(types+"void h(){D d;throw true;}",EXCEPTION_REPLACEMENT);
        check(types+"void h(){L l;throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void baseDestructorSpecificationDoesNotCoverMostDerivedVirtualCleanup() {
        String types="struct V{~V(){throw 1;}};struct L:virtual V{~L()throw(){}};struct D:L{};";
        check(types+"void h(){{D d;}throw true;}",EXCEPTION_REPLACEMENT);
        check(types+"void h(){{L l;}throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void mostDerivedSpecificationStillCoversVirtualBase() {
        check("struct V{V(){throw 1;}};struct D:virtual V{D()throw(){}};void h(){D d;throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
        check("struct V{~V(){throw 1;}};struct D:virtual V{~D()throw(){}};void h(){{D d;}throw true;}",EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void fieldsAreCompleteObjectsWithTheirOwnVirtualBases() {
        check("struct V{~V(){throw 1;}};struct L:virtual V{};struct D:virtual V{L member;};void h(){{D d;}throw true;}",TERMINATE_EXIT);
        check("struct V{~V(){throw 1;}};struct L:virtual V{};struct D{L members[2];};void h(){{D d;}throw true;}",TERMINATE_EXIT);
    }
    @Test void constructorFailureCleansEarlierVirtualBasesOnly() {
        check("struct A{~A(){throw 2;}};struct B{B(){throw 1;}};struct D:virtual A,virtual B{};void h(){D d;throw true;}",TERMINATE_EXIT);
        check("struct A{A(){throw 1;}~A(){std::terminate();}};struct D:virtual A{~D(){std::terminate();}};void h(){D d;throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void constructorBodyFailureCleansVirtualBase() {
        check("struct V{~V(){throw 2;}};struct D:virtual V{D(){throw 1;}};void h(){D d;throw true;}",TERMINATE_EXIT);
    }
    @Test void nestedVirtualBaseCleanupIsNotDuplicated() {
        check("struct A{~A(){throw 1;}};struct B:virtual A{};struct L:virtual B{};struct R:virtual B{};struct D:L,R{};void h(){{D d;}throw true;}",EXCEPTION_REPLACEMENT);
    }
    @Test void unsupportedSpecialMemberBodiesStayUnknown() {
        for(String code:java.util.List.of(
            "struct V{};struct D:virtual V{D(){while(true){}}};void h(){D d;throw 1;}",
            "struct V{};struct D:virtual V{D():V(){}};void h(){D d;throw 1;}",
            "struct V{};struct D:virtual V{~D();};void h(){D d;throw 1;}",
            "struct V{};struct D:virtual V{D()try{}catch(...){}};void h(){D d;throw 1;}"))
            check(code,EXCEPTION_REPLACEMENT,BAD_EXCEPTION_REPLACEMENT,TERMINATE_EXIT);
    }
    @Test void contextualSummaryDoesNotPublishUnconditionalSpecialMemberCfg() {
        var p=fixture.read("struct V{};struct D:virtual V{D(){}~D(){}};void h(){D d;throw 1;}void f()throw(int){std::set_unexpected(h);throw true;}");
        assertEquals(java.util.Set.of(EXCEPTION_REPLACEMENT),fixture.outcomes(p));
        assertTrue(p.flows().stream().filter(f->f.entry().kind()==CallableDeclaration.Kind.CONSTRUCTOR||f.entry().kind()==CallableDeclaration.Kind.DESTRUCTOR).allMatch(f->!f.complete()));
        var copy=p.withDiagnostics("probe",java.util.Map.of(p,new Diagnostic("probe",Diagnostic.Severity.INFO,"copy")));
        assertEquals(fixture.outcomes(p),fixture.outcomes(copy));
        assertDoesNotThrow(()->ProjectJson.encode(copy).toText(""));
    }
    @Test void virtualDestructorEditRecomputesOutcome() {
        var p=fixture.read("struct V{~V(){}};struct D:virtual V{};void h(){D d;throw 1;}void f()throw(int){std::set_unexpected(h);throw true;}");
        var target=p.flows().stream().filter(f->f.entry().kind()==CallableDeclaration.Kind.DESTRUCTOR).findFirst().orElseThrow().entry();
        var replacement=fixture.read("void h(){throw 2;}void f()throw(int){std::set_unexpected(h);throw true;}");
        var editor=new ProjectEditor(p);editor.replace(target.body().orElseThrow(),fixture.flow(replacement,"h").entry().body().orElseThrow());
        assertEquals(java.util.Set.of(TERMINATE_EXIT),fixture.outcomes(DataFlow.analyze(ControlFlow.analyze(editor.build()))));
    }
}
