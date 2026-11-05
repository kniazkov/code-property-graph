/* Runtime implementations are non-owning and separate from the statically selected method. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class VirtualMethodsModelTest {
    private Project project() {
        var base = MethodDeclaration.create().setName("base").addModifier("virtual").setPureVirtual(true);
        var derived = MethodDeclaration.create().setName("derived");
        var call = CallExpression.create().setCallee(NameExpression.create().setName("run")).setTarget(base).setVirtualTargets(List.of(derived), false);
        return Project.create().addFile(SourceFile.create().setPath("virtual.cpp").setLanguage(Language.CPP).addItem(base).addItem(derived)
                .addItem(VariableDeclaration.create().setName("result").setInitializer(call))).build();
    }
    private CallExpression call(Project p) { return (CallExpression)((VariableDeclaration)p.files().getFirst().items().get(2)).initializer().orElseThrow(); }
    @Test void oneDeclarationMayBeAmbiguousAcrossDistinctBaseSubobjects() {
        var method = MethodDeclaration.create().setName("f");
        var ambiguous = CallExpression.create().setCallee(NameExpression.create().setName("f"))
                .setCandidates(List.of(method), CallExpression.Resolution.AMBIGUOUS).build();
        assertEquals(CallExpression.Resolution.AMBIGUOUS, ambiguous.resolution());
        assertEquals(1, ambiguous.candidates().size()); assertTrue(ambiguous.target().isEmpty());
        assertEquals(CallExpression.Resolution.AMBIGUOUS, ambiguous.edit().build().resolution());
        assertThrows(IllegalArgumentException.class, () -> CallExpression.create().setCandidates(List.of(), CallExpression.Resolution.AMBIGUOUS));
        assertThrows(IllegalArgumentException.class, () -> CallExpression.create().setCandidates(List.of(method), CallExpression.Resolution.ODR_CONFLICT));
    }
    @Test void declarationSyntaxEditsInvalidateDerivedDispatchAcrossSnapshot() {
        var p = project(); var target = (MethodDeclaration) p.files().getFirst().items().get(1);
        for (var replacement : List.of(target.edit().setName("renamed").build(), target.edit().setPureVirtual(true).build())) {
            var editor = new ProjectEditor(p); editor.replace(target, replacement);
            var edited = call(editor.build());
            assertFalse(edited.isVirtualDispatch()); assertTrue(edited.virtualTargets().isEmpty());
            assertFalse(edited.virtualTargetsComplete());
        }
        assertTrue(call(p.edit().build()).isVirtualDispatch(), "Unchanged snapshot copies retain facts");
    }
    @Test void referencesRemainInSnapshotAndDoNotBecomeChildren() {
        var original = project();
        for (var p : List.of(original, original.edit().build(), new ProjectEditor(original).build())) {
            var call = call(p); var items = p.files().getFirst().items();
            assertSame(items.get(0), call.target().orElseThrow());
            assertSame(items.get(1), call.virtualTargets().getFirst());
            assertTrue(call.isVirtualDispatch()); assertFalse(call.virtualTargetsComplete());
            assertTrue(call.references().contains(items.get(1))); assertFalse(call.children().contains(items.get(1)));
            assertThrows(UnsupportedOperationException.class, () -> call.virtualTargets().clear());
        }
    }
    @Test void syntaxAndStaticSelectionEditsInvalidateDispatch() {
        var p = project(); var c = call(p);
        for (var changed : List.of(c.edit().addArgument(IntegerLiteral.create().setValue(1)).build(), c.edit().setArguments(List.of()).build(),
                c.edit().setCallee(NameExpression.create().setName("other")).build(), c.edit().clearTarget().build(),
                c.edit().setCandidates(List.of(), CallExpression.Resolution.UNRESOLVED).build())) {
            assertFalse(changed.isVirtualDispatch()); assertTrue(changed.virtualTargets().isEmpty()); assertFalse(changed.virtualTargetsComplete());
        }
        var editor = new ProjectEditor(p); editor.replace(c.callee(), NameExpression.create().setName("other").build());
        assertFalse(call(editor.build()).isVirtualDispatch());
    }
    @Test void editorMapsImplementationEdgesAndCanRepresentUnknownTargets() {
        var p = project(); var editor = new ProjectEditor(p);
        editor.virtualTargets(call(p), List.of((CallableDeclaration)p.files().getFirst().items().get(1)), true);
        var edited = editor.build(); assertTrue(call(edited).virtualTargetsComplete());
        assertSame(edited.files().getFirst().items().get(1), call(edited).virtualTargets().getFirst());
        editor = new ProjectEditor(p); editor.virtualTargets(call(p), List.of(), false);
        var unknown = call(editor.build()); assertTrue(unknown.isVirtualDispatch()); assertTrue(unknown.virtualTargets().isEmpty());
    }
}
