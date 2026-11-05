/* Dynamic-array result handles and temporal effects. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicArrayHandleModelTest {
    private Project source() {
        var project = Project.create().addFile(Parser.parse("handles.cpp",
                "int a; int b; int f(){return 1;}", Language.CPP)).build();
        var variables = new ArrayList<VariableDeclaration>();
        var functions = new ArrayList<CallableDeclaration>();
        var types = new ArrayList<TypeNode>();
        var literals = new ArrayList<IntegerLiteral>();
        project.walk(new NodeVisitor() {
            @Override public void visit(VariableDeclaration n) { variables.add(n); }
            @Override public void visit(CallableDeclaration n) { functions.add(n); }
            @Override public void visit(TypeNode n) { types.add(n); }
            @Override public void visit(IntegerLiteral n) { literals.add(n); }
        });
        var c = new EditContext(); var root = c.edit(project, Project.Builder.class);
        var type = DynamicArrayType.create().setElementType(c.copy(types.getFirst()))
                .setWrittenTypes(List.of(c.copy(types.getFirst())));
        var operations = new ArrayList<NodeBuilder<? extends DynamicArrayOperation>>();
        for (var variable : variables) operations.add(DynamicArrayOperation.create()
                .setKind(DynamicArrayOperation.Kind.EMPTY).setSyntax(c.copy(variable))
                .setDestination(c.copy(variable)).setType(type).setSelectedTarget(c.copy(functions.getFirst())));
        operations.add(DynamicArrayOperation.create().setKind(DynamicArrayOperation.Kind.APPEND)
                .setSyntax(c.copy(literals.getFirst())).setDestination(c.copy(variables.getFirst()))
                .setType(type).setSelectedTarget(c.copy(functions.getFirst()))
                .setSources(List.of(c.copy(literals.getFirst()))));
        return root.setDynamicArrayTypes(List.of(type)).setDynamicArraySemantics(operations).build();
    }
    private DynamicArrayOperation.Builder operation(EditContext c, DynamicArrayOperation basis,
            DynamicArrayOperation.Kind kind) {
        return DynamicArrayOperation.create().setKind(kind).setSyntax(c.copy(basis.syntax()))
                .setDestination(c.copy(basis.destination())).setType(c.copy(basis.type()))
                .setSelectedTarget(c.copy(basis.selectedTarget()));
    }
    private Project handles() {
        var source = source(); var c = new EditContext(); var root = c.edit(source, Project.Builder.class);
        var first = source.dynamicArraySemantics().getFirst();
        var second = source.dynamicArraySemantics().get(1);
        var operand = source.dynamicArraySemantics().getLast().sources().getFirst();
        var access = operation(c, first, DynamicArrayOperation.Kind.AT)
                .setResultKind(DynamicArrayOperation.ResultKind.REFERENCE).setWritable(true)
                .setIndex(c.copy(operand)).setHandle(DynamicArrayOperation.Handle.create()
                        .setKind(DynamicArrayOperation.Handle.Kind.REFERENCE)
                        .setSequence(c.copy(first.destination())).setPosition(c.copy(operand))
                        .setPositionKind(DynamicArrayOperation.Handle.Position.INDEX)
                        .setWritable(true).setValidity(DynamicArrayOperation.Handle.Validity.VALID));
        var swap = operation(c, first, DynamicArrayOperation.Kind.SWAP)
                .setMutatedObjects(List.of(c.copy(first.destination()), c.copy(second.destination())))
                .setSources(List.of(c.copy(second.destination())))
                .setHandleEffects(List.of(DynamicArrayOperation.HandleEffect.create().setProducer(access)
                        .setValidityAfter(DynamicArrayOperation.Handle.Validity.VALID)
                        .setSequenceAfter(c.copy(second.destination())).setPositionAfter(c.copy(operand))))
                .setElementDestructor(c.copy(first.selectedTarget()))
                .setElementAssignment(c.copy(first.selectedTarget()));
        return root.setDynamicArraySemantics(List.of(access, swap)).build();
    }
    @Test void copiedHandlesAndTemporalEffectsRebindWithoutOwningSyntax() {
        var original = handles(); var copy = original.edit().build();
        var access = copy.dynamicArraySemantics().getFirst(); var swap = copy.dynamicArraySemantics().getLast();
        assertEquals(DynamicArrayOperation.ResultKind.REFERENCE, access.resultKind());
        assertTrue(access.boundsChecked()); assertTrue(access.writable());
        assertTrue(access.children().isEmpty()); assertTrue(access.mutatedObjects().isEmpty());
        assertSame(access.index().orElseThrow(), access.handle().orElseThrow().position().orElseThrow());
        assertSame(access.destination(), access.handle().orElseThrow().sequence());
        assertSame(access, swap.handleEffects().getFirst().producer());
        assertSame(swap.mutatedObjects().getLast(), swap.handleEffects().getFirst().sequenceAfter().orElseThrow());
        assertSame(access.index().orElseThrow(), swap.handleEffects().getFirst().positionAfter().orElseThrow());
        assertNotSame(original.dynamicArraySemantics().getFirst(), access);
        assertNotSame(original.dynamicArraySemantics().getFirst().handle().orElseThrow().sequence(),
                access.handle().orElseThrow().sequence());
        assertSame(swap.selectedTarget(), swap.elementDestructor().orElseThrow());
        assertSame(swap.selectedTarget(), swap.elementAssignment().orElseThrow());
        assertEquals(DynamicArrayOperation.Handle.Validity.VALID, swap.handleEffects().getFirst().validityAfter());
        assertThrows(UnsupportedOperationException.class, () -> swap.mutatedObjects().clear());
        assertThrows(UnsupportedOperationException.class, () -> swap.handleEffects().clear());
        assertEquals(2, original.dynamicArraySemantics().size());
    }
    @Test void foreignHandleEdgesAreRejected() {
        for (int field = 0; field < 8; field++) {
            var source = source(); var c = new EditContext(); var root = c.edit(source, Project.Builder.class);
            var basis = source.dynamicArraySemantics().getFirst();
            var foreign = IntegerLiteral.create().setValue(java.math.BigInteger.valueOf(42));
            var operation = operation(c, basis, DynamicArrayOperation.Kind.FRONT);
            if (field == 0) operation.setIndex(foreign);
            if (field == 1) operation.setMutatedObjects(List.of(foreign));
            if (field == 2) operation.setHandle(DynamicArrayOperation.Handle.create()
                    .setKind(DynamicArrayOperation.Handle.Kind.REFERENCE).setSequence(foreign));
            if (field == 3) operation.setHandleEffects(List.of(DynamicArrayOperation.HandleEffect.create()
                    .setProducer(operation(c, basis, DynamicArrayOperation.Kind.FRONT))));
            if (field == 4) operation.setHandle(DynamicArrayOperation.Handle.create()
                    .setKind(DynamicArrayOperation.Handle.Kind.ITERATOR).setSequence(c.copy(basis.destination()))
                    .setPosition(foreign));
            if (field == 5) operation.setHandleEffects(List.of(DynamicArrayOperation.HandleEffect.create()
                    .setProducer(operation).setSequenceAfter(foreign)));
            if (field == 6) operation.setHandleEffects(List.of(DynamicArrayOperation.HandleEffect.create()
                    .setProducer(operation).setPositionAfter(foreign)));
            if (field == 7) operation.setElementDestructor(FunctionDeclaration.create().setName("foreign"));
            root.setDynamicArraySemantics(List.of(operation));
            assertThrows(IllegalStateException.class, root::build, "foreign field " + field);
        }
    }
    @Test void swapRequiresBothMutationOperands() {
        var source = source(); var c = new EditContext();
        var root = c.edit(source, Project.Builder.class).setDynamicArraySemantics(List.of(
                operation(c, source.dynamicArraySemantics().getFirst(), DynamicArrayOperation.Kind.SWAP)));
        assertThrows(IllegalStateException.class, root::build);
    }
    @Test void indexPositionRequiresOperand() {
        var source = source(); var c = new EditContext(); var basis = source.dynamicArraySemantics().getFirst();
        var access = operation(c, basis, DynamicArrayOperation.Kind.SUBSCRIPT)
                .setHandle(DynamicArrayOperation.Handle.create().setKind(DynamicArrayOperation.Handle.Kind.REFERENCE)
                        .setSequence(c.copy(basis.destination())).setPositionKind(DynamicArrayOperation.Handle.Position.INDEX));
        var root = c.edit(source, Project.Builder.class).setDynamicArraySemantics(List.of(access));
        assertThrows(IllegalStateException.class, root::build);
    }
    @Test void syntaxEditsDropHandlesAndEffectsTogether() {
        var source = handles(); var c = new EditContext(); var root = c.edit(source, Project.Builder.class);
        var index = (IntegerLiteral) source.dynamicArraySemantics().getFirst().index().orElseThrow();
        c.edit(index, IntegerLiteral.Builder.class).setValue(java.math.BigInteger.TWO);
        assertTrue(root.build().dynamicArraySemantics().isEmpty());
        assertEquals(2, source.dynamicArraySemantics().size());
    }
}
