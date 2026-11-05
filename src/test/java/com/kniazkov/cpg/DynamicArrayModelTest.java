package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class DynamicArrayModelTest {
    private Project annotated() {
        var project = Project.create().addFile(Parser.parse("model.cpp", "int value;", Language.CPP)).build();
        var types = new ArrayList<TypeNode>();
        project.walk(new NodeVisitor() { @Override public void visit(TypeNode n) { types.add(n); } });
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        root.setDynamicArrayTypes(List.of(DynamicArrayType.create().setElementType(context.copy(types.getFirst()))
                .setWrittenTypes(List.of(context.copy(types.getFirst())))));
        return root.build();
    }
    @Test void abstractCommonTypeExportsWithoutConcreteClass() {
        var project = annotated();
        String json = com.kniazkov.cpg.serialization.ProjectJson.encode(project).toText("  ");
        String dot = com.kniazkov.cpg.visualization.DotVisualizer.toDot(project);
        assertTrue(json.contains("dynamicArrayTypes"));
        assertTrue(dot.contains("DynamicArrayType"));
        assertFalse(json.contains("concreteClassId"));
        assertFalse(dot.contains("DYNAMIC_ARRAY_CLASS"));
    }
    @Test void typesAreOwnedAndReferencesReboundOnCopy() {
        var project = annotated();
        var copy = project.edit().build();
        assertEquals(1, copy.dynamicArrayTypes().size());
        var type = copy.dynamicArrayTypes().getFirst();
        assertNotSame(project.dynamicArrayTypes().getFirst(), type);
        assertSame(type.elementType(), type.writtenTypes().getFirst());
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        copy.walk(new NodeVisitor() { @Override public void visit(Node n) { owned.add(n); } });
        assertTrue(owned.contains(type.elementType()));
        assertTrue(type.children().isEmpty());
    }
    @Test void retainedIdentitySyntaxEditsClearDerivedTypes() {
        var project = annotated();
        var type = (NamedType) project.dynamicArrayTypes().getFirst().elementType();
        var editor = new ProjectEditor(project);
        editor.replace(type, type.edit().setName("long").build());
        assertTrue(editor.build().dynamicArrayTypes().isEmpty());
        assertEquals(1, project.dynamicArrayTypes().size());
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
        root.setDynamicArrayTypes(List.of(DynamicArrayType.create().setElementType(context.copy(types.getFirst()))
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
        assertTrue(changed.build().dynamicArrayTypes().isEmpty());
    }
    @Test void profileFactOnlyChangeInvalidates() {
        var project = annotated(); var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        var type = project.dynamicArrayTypes().getFirst().elementType();
        context.copy(type).setTypeRepresentation(new TypeRepresentation(type.canonicalIdentity(), "another-profile",
                Optional.empty(), Optional.empty(), OptionalInt.empty(), Optional.empty()));
        assertTrue(root.build().dynamicArrayTypes().isEmpty());
    }
    @Test void ownedLibraryTargetEditsInvalidateDerivedOperations() {
        var project = com.kniazkov.cpg.analysis.DynamicArraySemantics.green(
                com.kniazkov.cpg.analysis.NameResolver.resolve(Project.create().addFile(Parser.parse("vector.cpp",
                "#include <vector>\nvoid f(){std::vector<int> a; a.push_back(1);}", Language.CPP)).build()));
        assertFalse(project.dynamicArraySemantics().isEmpty());
        var target = project.dynamicArraySemantics().getLast().selectedTarget();
        for (int change = 0; change < 3; change++) {
            var context = new EditContext(); var root = context.edit(project, Project.Builder.class);
            var edited = context.edit(target, CallableDeclaration.Builder.class);
            if (change == 0) edited.setName("resize");
            if (change == 1) edited.setParameters(List.of());
            if (change == 2) edited.setProvenance(project.files().getFirst().provenance().orElseThrow());
            assertTrue(root.build().dynamicArraySemantics().isEmpty(), "change " + change);
        }
    }
    @Test void foreignTypeReferenceIsRejected() {
        var project = annotated();
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        root.setDynamicArraySemantics(List.of());
        root.setDynamicArrayTypes(List.of(DynamicArrayType.create().setElementType(NamedType.create().setName("foreign"))
                .setWrittenTypes(List.of())));
        assertThrows(IllegalStateException.class, root::build);
    }
    @Test void missingRequiredOperandFails() {
        assertThrows(NullPointerException.class, () -> DynamicArrayOperation.create()
                .setKind(DynamicArrayOperation.Kind.APPEND).build());
    }
}
