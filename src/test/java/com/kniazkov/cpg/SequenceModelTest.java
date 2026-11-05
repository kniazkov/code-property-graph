package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SequenceModelTest {
    private Project annotated() {
        var project = Project.create().addFile(Parser.parse("sequence.cpp",
                "char text; int count; int target();", Language.CPP)).build();
        var variables = new ArrayList<VariableDeclaration>();
        var functions = new ArrayList<CallableDeclaration>();
        project.walk(new NodeVisitor() {
            @Override public void visit(VariableDeclaration n) { variables.add(n); }
            @Override public void visit(CallableDeclaration n) { functions.add(n); }
        });
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        var text = context.copy(variables.getFirst());
        var count = context.copy(variables.getLast());
        var target = context.copy(functions.getFirst());
        var type = CharacterSequenceType.create()
                .setCharacterType(context.copy(variables.getFirst().type().orElseThrow()));
        var buffer = SequenceOperation.create().setKind(SequenceOperation.Kind.STRING_BUFFER)
                .setSyntax(text).setSelectedTarget(target).setStringType(type)
                .setDestination(text).setSources(List.of(text, count)).setPosition(count).setCount(count)
                .setLengthKind(SequenceOperation.LengthKind.EXPLICIT_COUNT)
                .setSizeEffect(SequenceOperation.SizeEffect.MAY_CHANGE)
                .setResultKind(SequenceOperation.ResultKind.SEQUENCE);
        var algorithm = SequenceOperation.create().setKind(SequenceOperation.Kind.ACCUMULATE)
                .setSyntax(count).setSelectedTarget(target).setFirst(text).setLast(count)
                .setInputSequence(text).setInitialValue(count).setCallable(text).setCallableTarget(target)
                .setCallableEffect(SequenceOperation.CallableEffect.MAY_WRITE).setCallableMayThrow(true)
                .setCallableEffects(List.of(text)).setMutatedObjects(List.of(text, count))
                .setAccumulatorType(context.copy(variables.getLast().type().orElseThrow()))
                .setResultType(context.copy(variables.getLast().type().orElseThrow()))
                .setDirection(SequenceOperation.Direction.FORWARD)
                .setOutputDestination(buffer).setSizeEffect(SequenceOperation.SizeEffect.UNCHANGED);
        return root.setCharacterSequenceTypes(List.of(type)).setSequenceSemantics(List.of(buffer, algorithm)).build();
    }
    @Test void ownedOperationsAndAllReferencesRebindOnCopy() {
        var project = annotated(); var copy = project.edit().build();
        assertEquals(2, copy.sequenceSemantics().size());
        var first = copy.sequenceSemantics().getFirst(); var last = copy.sequenceSemantics().getLast();
        assertNotSame(project.sequenceSemantics().getFirst(), first);
        assertSame(copy.characterSequenceTypes().getFirst(), first.stringType().orElseThrow());
        assertSame(first, last.outputDestination().orElseThrow());
        assertSame(first.count().orElseThrow(), first.position().orElseThrow());
        assertSame(last.resultType().orElseThrow(), last.accumulatorType().orElseThrow());
        assertEquals(SequenceOperation.Direction.FORWARD, last.direction());
        assertEquals(SequenceOperation.CallableEffect.MAY_WRITE, last.callableEffect());
        assertTrue(last.callableMayThrow());
        assertSame(first.destination().orElseThrow(), last.callableEffects().getFirst());
        assertTrue(first.children().isEmpty());
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        copy.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } });
        for (var operation : copy.sequenceSemantics()) assertTrue(operation.references().stream().allMatch(owned::contains));
    }
    @Test void retainedIdentitySyntaxEditInvalidatesOperationsAndTypes() {
        var project = annotated(); var type = (NamedType) project.characterSequenceTypes().getFirst().characterType();
        var editor = new ProjectEditor(project); editor.replace(type, type.edit().setName("wchar_t").build());
        var changed = editor.build();
        assertTrue(changed.sequenceSemantics().isEmpty()); assertTrue(changed.characterSequenceTypes().isEmpty());
        assertEquals(2, project.sequenceSemantics().size());
    }
    @Test void targetOnlyRebindingInvalidatesCopiedSemantics() {
        var project = Project.create().addFile(Parser.parse("binding.cpp",
                "int a(); int b(); int main(){ return a(); }", Language.CPP)).build();
        project = com.kniazkov.cpg.analysis.NameResolver.resolve(project);
        var calls = new ArrayList<CallExpression>(); var targets = new ArrayList<CallableDeclaration>();
        project.walk(new NodeVisitor() {
            @Override public void visit(CallExpression n) { calls.add(n); }
            @Override public void visit(CallableDeclaration n) { targets.add(n); }
        });
        var context = new EditContext(); var root = context.edit(project, Project.Builder.class);
        root.setSequenceSemantics(List.of(SequenceOperation.create().setKind(SequenceOperation.Kind.DISTANCE)
                .setSyntax(context.copy(calls.getFirst())).setSelectedTarget(context.copy(calls.getFirst().target().orElseThrow()))));
        project = root.build(); calls.clear(); targets.clear();
        project.walk(new NodeVisitor() {
            @Override public void visit(CallExpression n) { calls.add(n); }
            @Override public void visit(CallableDeclaration n) { targets.add(n); }
        });
        var edit = new EditContext(); var changed = edit.edit(project, Project.Builder.class);
        edit.edit(calls.getFirst(), CallExpression.Builder.class).setTarget(edit.copy(targets.stream()
                .filter(t -> t.name().equals("b")).findFirst().orElseThrow()));
        assertTrue(changed.build().sequenceSemantics().isEmpty());
    }
    @Test void discardedStringMethodsClearDefaultArgumentReferences() {
        var project = com.kniazkov.cpg.analysis.NameResolver.resolve(Project.create().addFile(Parser.parse(
                "defaults.cpp", "#include <string>\nvoid f(){std::string s; s.find('a'); int n=1+2;}", Language.CPP)).build());
        var calls = new ArrayList<CallExpression>(); var literals = new ArrayList<IntegerLiteral>();
        project.files().getFirst().walk(new NodeVisitor() {
            @Override public void visit(CallExpression n) { calls.add(n); }
            @Override public void visit(IntegerLiteral n) { literals.add(n); }
        });
        assertTrue(calls.stream().anyMatch(c -> !c.defaultArguments().isEmpty()));
        var editor = new ProjectEditor(project);
        editor.replace(literals.getFirst(), IntegerLiteral.create().setValue(7).build());
        var changed = assertDoesNotThrow(editor::build);
        changed.files().getFirst().walk(new NodeVisitor() {
            @Override public void visit(CallExpression n) { assertTrue(n.defaultArguments().isEmpty()); }
        });
    }
    @Test void libraryAliasReferencesAreClearedWhenInstancesAreDiscarded() {
        var project = com.kniazkov.cpg.analysis.NameResolver.resolve(Project.create().addFile(Parser.parse(
                "alias.cpp", "#include <string>\nvoid f(){std::string s(\"abc\",1);}", Language.CPP)).build());
        assertFalse(project.classTemplateInstances().isEmpty());
        var counts = new ArrayList<IntegerLiteral>();
        project.files().getFirst().walk(new NodeVisitor() {
            @Override public void visit(IntegerLiteral n) { counts.add(n); }
        });
        var editor = new ProjectEditor(project);
        editor.replace(counts.getFirst(), IntegerLiteral.create().setValue(2).build());
        var changed = assertDoesNotThrow(editor::build);
        assertTrue(changed.classTemplateInstances().isEmpty());
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        changed.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } });
        for (var node : owned) assertTrue(node.references().stream().allMatch(owned::contains));
    }
    @Test void profileFactsInvalidateCopiedSemantics() {
        var project = annotated(); var context = new EditContext(); var root = context.edit(project, Project.Builder.class);
        var type = project.characterSequenceTypes().getFirst().characterType();
        context.copy(type).setTypeRepresentation(new TypeRepresentation(type.canonicalIdentity(), "different",
                Optional.empty(), Optional.empty(), OptionalInt.empty(), Optional.empty()));
        assertTrue(root.build().sequenceSemantics().isEmpty());
    }
    @Test void foreignReferenceIsRejected() {
        var project = annotated(); var context = new EditContext(); var root = context.edit(project, Project.Builder.class);
        var old = project.sequenceSemantics().getFirst();
        root.setSequenceSemantics(List.of(SequenceOperation.create().setKind(SequenceOperation.Kind.STRING_SIZE)
                .setSyntax(context.copy(old.syntax())).setSelectedTarget(context.copy(old.selectedTarget()))
                .setOutputDestination(IntegerLiteral.create().setValue(4))));
        assertThrows(IllegalStateException.class, root::build);
    }
    private SequenceOperation.Builder standalone() {
        return SequenceOperation.create().setKind(SequenceOperation.Kind.STRING_BUFFER)
                .setSyntax(IntegerLiteral.create().setValue(1))
                .setSelectedTarget(FunctionDeclaration.create().setName("target").setReturnType(NamedType.create().setName("int")));
    }
    @Test void explicitLengthAndRangeCannotSilentlyDropOperands() {
        assertThrows(IllegalStateException.class, () -> standalone().setLengthKind(SequenceOperation.LengthKind.EXPLICIT_COUNT).build());
        assertThrows(IllegalStateException.class, () -> standalone().setLengthKind(SequenceOperation.LengthKind.RANGE)
                .setFirst(IntegerLiteral.create().setValue(0)).build());
        assertThrows(NullPointerException.class, () -> SequenceOperation.create().setKind(SequenceOperation.Kind.COPY).build());
    }
    @Test void sequenceReferenceIsDistinctFromCharacterReference() {
        var append = standalone().setKind(SequenceOperation.Kind.STRING_APPEND)
                .setResultKind(SequenceOperation.ResultKind.SEQUENCE_REFERENCE).build();
        var access = standalone().setKind(SequenceOperation.Kind.STRING_AT)
                .setResultKind(SequenceOperation.ResultKind.REFERENCE).build();
        assertNotEquals(access.resultKind(), append.resultKind());
        assertEquals(SequenceOperation.ResultKind.SEQUENCE_REFERENCE, append.edit().build().resultKind());
    }
    @Test void removeLogicalEndIsNotPhysicalErase() {
        var operation = standalone().setKind(SequenceOperation.Kind.REMOVE)
                .setResultKind(SequenceOperation.ResultKind.ITERATOR)
                .setSizeEffect(SequenceOperation.SizeEffect.UNCHANGED).build();
        assertEquals(SequenceOperation.ResultKind.ITERATOR, operation.resultKind());
        assertEquals(SequenceOperation.SizeEffect.UNCHANGED, operation.sizeEffect());
    }
}
