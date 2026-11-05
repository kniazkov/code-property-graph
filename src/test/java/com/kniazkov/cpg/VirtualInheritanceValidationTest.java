/* Final overriders belong to base subobjects, not merely class names. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static com.kniazkov.cpg.ClassInitializationSemanticsTest.*;

class VirtualInheritanceValidationTest {
    private static final String DIAMOND = "struct V { virtual void f() {} }; "
            + "struct A : virtual V { void f() {} }; struct B : virtual V { void f() {} }; ";

    private static boolean conflict(Project project) {
        return nodes(project, ClassDeclaration.class).stream()
                .anyMatch(cls -> !VirtualInheritanceValidation.conflictingMethods(project, cls).isEmpty());
    }
    @Test void sharedVirtualBaseRequiresUniqueFinalOverriderWithoutAnyCall() {
        var project = parse(DIAMOND + "struct D : A, B {};");
        assertTrue(conflict(project));
        var derived = nodes(project, ClassDeclaration.class).getLast();
        assertTrue(derived.diagnostics().stream().anyMatch(d -> d.code().equals("multiple-final-overriders")
                && d.severity() == Diagnostic.Severity.ERROR));
        assertThrows(RuntimeException.class, () -> Cpg.sources(project, Language.CPP));
    }
    @Test void mostDerivedOverrideResolvesBothBranches() {
        assertFalse(conflict(parse(DIAMOND + "struct D : A, B { void f() {} };")));
    }
    @Test void pureOverridesStillCompeteAndCanResolveConflict() {
        assertTrue(conflict(parse("struct V { virtual void f() = 0; }; struct A : virtual V { void f() = 0; }; "
                + "struct B : virtual V { void f() {} }; struct D : A, B {};")));
        assertFalse(conflict(parse(DIAMOND + "struct D : A, B { void f() = 0; };")));
    }
    @Test void nonvirtualDiamondHasIndependentSlots() {
        assertFalse(conflict(parse(DIAMOND.replace("virtual V", "V") + "struct D : A, B {};")));
    }
    @Test void onlyOneBranchOverridesSharedSlot() {
        assertFalse(conflict(parse("struct V { virtual void f() {} }; struct A : virtual V { void f() {} }; "
                + "struct B : virtual V {}; struct D : A, B {};")));
    }
    @Test void repeatedOverriderClassInstancesAreDistinct() {
        assertTrue(conflict(parse("struct V { virtual void f() {} }; struct A : virtual V { void f() {} }; "
                + "struct L : A {}; struct R : A {}; struct D : L, R {};")));
    }
    @Test void virtualAncestorDoesNotMergeItsOrdinaryBaseWithOtherInstances() {
        assertFalse(conflict(parse("struct V { virtual void f() {} }; struct A : V { void f() {} }; "
                + "struct B : V { void f() {} }; struct D : virtual A, B {};")));
    }
    @Test void sharedVirtualAncestorAlsoSharesItsOrdinaryBase() {
        assertTrue(conflict(parse("struct V { virtual void f() {} }; struct W : V {}; "
                + "struct A : virtual W { void f() {} }; struct B : virtual W { void f() {} }; struct D : A, B {};")));
    }
    @Test void unknownIncompleteAndCyclicHierarchiesRemainConservative() {
        for (String suffix : new String[] { "struct D : A, B, Missing {};",
                "struct Missing; struct D : A, B, Missing {};", "struct D : A, B, D {};" }) {
            var project = parse(DIAMOND + suffix);
            assertTrue(VirtualInheritanceValidation.conflictingMethods(project,
                    nodes(project, ClassDeclaration.class).getLast()).isEmpty(), suffix);
        }
    }
    @Test void differentCvAndParameterSignaturesDoNotOverride() {
        assertFalse(conflict(parse("struct V { virtual void f() {} }; struct A : virtual V { void f() {} }; "
                + "struct B : virtual V { void f() const {} void f(int) {} }; struct D : A, B {};")));
    }
    @Test void editedHierarchyGetsItsOwnValidationIndex() {
        var original=parse(DIAMOND+"struct D : A, B { void f() {} };");
        assertFalse(conflict(original));
        ClassDeclaration derived=nodes(original,ClassDeclaration.class).getLast();
        MethodDeclaration method=(MethodDeclaration)derived.members().stream().filter(CallableDeclaration.class::isInstance).findFirst().orElseThrow();
        var context=new EditContext();
        var root=context.edit(original,Project.Builder.class);
        context.edit(method,MethodDeclaration.Builder.class).setName("g");
        var edited=NameResolver.resolve(root.build());
        assertTrue(conflict(edited));
        assertTrue(edited.semanticDiagnostics().stream().anyMatch(d->d.code().equals("multiple-final-overriders")));
        assertFalse(conflict(original));
        assertFalse(conflict(original.edit().build()));
    }

}
