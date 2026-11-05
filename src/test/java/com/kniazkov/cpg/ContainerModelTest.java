package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ContainerModelTest {
    private Project annotated() {
        var project = Project.create().addFile(Parser.parse("model.cpp", "int value;", Language.CPP)).build();
        var types = new ArrayList<TypeNode>();
        project.walk(new NodeVisitor() { @Override public void visit(TypeNode n) { types.add(n); } });
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        root.setContainerTypes(List.of(ContainerType.create().setKind(ContainerType.Kind.LIST).setElementType(context.copy(types.getFirst()))
                .setWrittenTypes(List.of(context.copy(types.getFirst())))));
        return root.build();
    }
    @Test void typesAreOwnedAndReferencesReboundOnCopy() {
        var project = annotated();
        var copy = project.edit().build();
        assertEquals(1, copy.containerTypes().size());
        var type = copy.containerTypes().getFirst();
        assertNotSame(project.containerTypes().getFirst(), type);
        assertSame(type.elementType(), type.writtenTypes().getFirst());
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        copy.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } });
        assertTrue(owned.contains(type.elementType()));
        assertTrue(type.children().isEmpty());
    }
    @Test void retainedIdentitySyntaxEditsClearDerivedTypes() {
        var project = annotated();
        var type = (NamedType) project.containerTypes().getFirst().elementType();
        var editor = new ProjectEditor(project);
        editor.replace(type, type.edit().setName("long").build());
        assertTrue(editor.build().containerTypes().isEmpty());
        assertEquals(1, project.containerTypes().size());
    }
    @Test void targetOnlyRebindingInvalidatesWithoutChangingWrittenSource() {
        var project = Project.create().addFile(Parser.parse("links.cpp", "void a(); void b(); int main(){ a(); }", Language.CPP)).build();
        project = com.kniazkov.cpg.analysis.NameResolver.resolve(project);
        var calls = new ArrayList<CallExpression>(); var functions = new ArrayList<CallableDeclaration>(); var types = new ArrayList<TypeNode>();
        project.walk(new NodeVisitor() {
            @Override public void visit(CallExpression n) { calls.add(n); }
            @Override public void visit(CallableDeclaration n) { functions.add(n); }
            @Override public void visit(TypeNode n) { types.add(n); }
        });
        var context = new EditContext(); var root = context.edit(project, Project.Builder.class);
        root.setContainerTypes(List.of(ContainerType.create().setKind(ContainerType.Kind.LIST).setElementType(context.copy(types.getFirst()))
                .setWrittenTypes(List.of(context.copy(types.getFirst())))));
        project = root.build();
        calls.clear(); functions.clear();
        project.walk(new NodeVisitor() {
            @Override public void visit(CallExpression n) { calls.add(n); }
            @Override public void visit(CallableDeclaration n) { functions.add(n); }
        });
        var edited = new EditContext(); var changed = edited.edit(project, Project.Builder.class);
        var target = functions.stream().filter(f -> f.name().equals("b")).findFirst().orElseThrow();
        edited.edit(calls.getFirst(), CallExpression.Builder.class).setTarget(edited.copy(target));
        assertTrue(changed.build().containerTypes().isEmpty());
    }
    @Test void profileFactOnlyChangeInvalidates() {
        var project = annotated(); var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        var type = project.containerTypes().getFirst().elementType();
        context.copy(type).setTypeRepresentation(new TypeRepresentation(type.canonicalIdentity(), "another-profile",
                Optional.empty(), Optional.empty(), OptionalInt.empty(), Optional.empty()));
        assertTrue(root.build().containerTypes().isEmpty());
    }
    @Test void foreignTypeReferenceIsRejected() {
        var project = annotated();
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        root.setContainerSemantics(List.of());
        root.setContainerTypes(List.of(ContainerType.create().setKind(ContainerType.Kind.LIST).setElementType(NamedType.create().setName("foreign"))
                .setWrittenTypes(List.of())));
        assertThrows(IllegalStateException.class, root::build);
    }
    @Test void missingRequiredOperandFails() {
        assertThrows(NullPointerException.class, () -> ContainerOperation.create()
                .setKind(ContainerOperation.Kind.PUSH_BACK).build());
    }
    @Test void familiesHaveDistinctGuarantees() {
        for (var kind : ContainerType.Kind.values()) {
            var type = ContainerType.create().setKind(kind).setElementType(NamedType.create().setName("int")).build();
            if (kind == ContainerType.Kind.LIST) {
                assertEquals(ContainerType.IteratorCategory.BIDIRECTIONAL, type.iteratorCategory());
                assertEquals(ContainerType.ReferenceStability.NODE_STABLE, type.referenceStability());
            }
            if (kind == ContainerType.Kind.DEQUE) {
                assertEquals(ContainerType.IteratorCategory.RANDOM_ACCESS, type.iteratorCategory());
                assertEquals(ContainerType.ReferenceStability.DEQUE_RULES, type.referenceStability());
            }
            if (kind == ContainerType.Kind.STACK) assertEquals(ContainerType.Order.LIFO, type.order());
            if (kind == ContainerType.Kind.QUEUE) assertEquals(ContainerType.Order.FIFO, type.order());
            if (kind == ContainerType.Kind.PRIORITY_QUEUE) assertEquals(ContainerType.Order.PRIORITY, type.order());
            assertEquals(kind == ContainerType.Kind.SET || kind == ContainerType.Kind.MAP, type.uniqueKeys());
        }
    }
    private ContainerOperation operation(ContainerType.Kind family, ContainerOperation.Kind kind) {
        var value = IntegerLiteral.create().setValue(1);
        return ContainerOperation.create().setType(ContainerType.create().setKind(family)
                .setElementType(NamedType.create().setName("int")))
                .setSyntax(value).setDestination(value).setSourceContainer(IntegerLiteral.create().setValue(2))
                .setSelectedTarget(FunctionDeclaration.create().setName("operation").setReturnType(NamedType.create().setName("void")))
                .setKind(kind).build();
    }
    @Test void lookupSubscriptAndSpliceDoNotConflateEffects() {
        var find = operation(ContainerType.Kind.MAP, ContainerOperation.Kind.FIND);
        assertFalse(find.mayInsert()); assertTrue(find.mutatedObjects().isEmpty());
        var subscript = operation(ContainerType.Kind.MAP, ContainerOperation.Kind.SUBSCRIPT);
        assertTrue(subscript.mayInsert()); assertEquals(List.of(subscript.destination()), subscript.mutatedObjects());
        var list = operation(ContainerType.Kind.LIST, ContainerOperation.Kind.SPLICE);
        assertTrue(list.preservesTransferredReferences());
        assertEquals(List.of(list.destination(), list.sourceContainer().orElseThrow()), list.mutatedObjects());
        assertFalse(list.mayInsert());
        assertThrows(IllegalStateException.class, () -> operation(ContainerType.Kind.DEQUE, ContainerOperation.Kind.SPLICE));
    }
    @Test void operationAndTemporalReferencesAreOwnedOnceAndRebound() {
        var original = Project.create().addFile(Parser.parse("owned.cpp", "int a; int b; void target();", Language.CPP)).build();
        var declarations = new ArrayList<VariableDeclaration>();
        var targets = new ArrayList<CallableDeclaration>();
        original.walk(new NodeVisitor() {
            @Override public void visit(VariableDeclaration node) { declarations.add(node); }
            @Override public void visit(CallableDeclaration node) { targets.add(node); }
        });
        var context = new EditContext(); var root = context.edit(original, Project.Builder.class);
        var first = context.copy(declarations.get(0)); var second = context.copy(declarations.get(1));
        var target = context.copy(targets.getFirst());
        var type = ContainerType.create().setKind(ContainerType.Kind.LIST)
                .setElementType(context.copy(declarations.getFirst().type().orElseThrow()));
        var front = ContainerOperation.create().setKind(ContainerOperation.Kind.FRONT).setType(type)
                .setSyntax(first).setDestination(first).setSelectedTarget(target)
                .setResultKind(ContainerOperation.ResultKind.REFERENCE)
                .setHandle(ContainerOperation.Handle.create().setKind(ContainerOperation.Handle.Kind.REFERENCE)
                        .setSequence(first));
        var splice = ContainerOperation.create().setKind(ContainerOperation.Kind.SPLICE).setType(type)
                .setSyntax(second).setDestination(second).setSourceContainer(first).setSelectedTarget(target)
                .setTransferKind(ContainerOperation.TransferKind.ONE)
                .setHandleEffects(List.of(ContainerOperation.HandleEffect.create().setProducer(front)
                        .setValidityAfter(ContainerOperation.Handle.Validity.VALID).setSequenceAfter(second)));
        var project = root.setContainerTypes(List.of(type)).setContainerSemantics(List.of(front, splice)).build();
        var copy = project.edit().build();
        var operations = copy.containerSemantics();
        assertSame(copy.containerTypes().getFirst(), operations.getFirst().type());
        assertSame(operations.getFirst(), operations.getLast().handleEffects().getFirst().producer());
        assertSame(operations.getLast().destination(), operations.getLast().handleEffects().getFirst().sequenceAfter().orElseThrow());
        assertSame(operations.getFirst().destination(), operations.getFirst().handle().orElseThrow().sequence());
        assertTrue(operations.getLast().children().isEmpty());
        assertNotSame(project.containerSemantics().getFirst(), operations.getFirst());
        assertEquals(ContainerOperation.TransferKind.ONE, operations.getLast().transferKind());
    }
}
