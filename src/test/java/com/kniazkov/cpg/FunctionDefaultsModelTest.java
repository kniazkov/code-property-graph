/* Default arguments are shared references, never duplicate syntax owners. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.model.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FunctionDefaultsModelTest {
    private CallExpression call(Node node) { return (CallExpression) ((VariableDeclaration) node).initializer().orElseThrow(); }
    private Project project() {
        var parameter = ParameterDeclaration.create().setName("x").setType(NamedType.create().setName("int"))
                .setDefaultValue(IntegerLiteral.create().setValue(7));
        var function = FunctionDeclaration.create().setName("f").setReturnType(NamedType.create().setName("int")).addParameter(parameter);
        var file = SourceFile.create().setPath("defaults.cpp").setLanguage(Language.CPP).addItem(function);
        for (int index = 0; index < 2; index++) file.addItem(VariableDeclaration.create().setName("result" + index).setInitializer(CallExpression.create().setCallee(NameExpression.create().setName("f"))
                .setTarget(function).setDefaultArguments(List.of(new CallExpression.DefaultArgumentBuilder(0, function, parameter)))));
        return Project.create().addFile(file).build();
    }
    @Test void callsShareOneOwnedDefaultThroughEverySnapshot() {
        var project = project();
        for (var snapshot : List.of(project, project.edit().build())) {
            var items = snapshot.files().getFirst().items();
            var function = (FunctionDeclaration) items.get(0);
            var first = call(items.get(1));
            var second = call(items.get(2));
            var value = function.parameters().getFirst().defaultValue().orElseThrow();
            assertSame(value, first.defaultArguments().getFirst().expression());
            assertSame(value, second.defaultArguments().getFirst().expression());
            assertTrue(first.arguments().isEmpty());
            assertFalse(first.children().contains(value));
            assertTrue(first.references().contains(value));
            assertEquals(1, first.children().size());
            assertThrows(UnsupportedOperationException.class, () -> first.defaultArguments().clear());
            var visits = new ArrayList<IntegerLiteral>();
            snapshot.walk(new NodeVisitor() { @Override public void visit(IntegerLiteral n) { visits.add(n); } });
            assertEquals(1, visits.size());
        }
    }
    @Test void changedDefaultRedirectsReferencesAndClearedDefaultDropsDerivedLinks() {
        var source = project();
        var parameter = ((FunctionDeclaration) source.files().getFirst().items().getFirst()).parameters().getFirst();
        var editor = new ProjectEditor(source);
        editor.replace(parameter.defaultValue().orElseThrow(), IntegerLiteral.create().setValue(9).build());
        var result = editor.build();
        var call = call(result.files().getFirst().items().get(1));
        assertEquals(9, ((IntegerLiteral) call.defaultArguments().getFirst().expression()).value());
        editor = new ProjectEditor(source);
        editor.replace(parameter, parameter.edit().clearDefaultValue().build());
        assertTrue(call(editor.build().files().getFirst().items().get(1)).defaultArguments().isEmpty());
        assertEquals(7, ((IntegerLiteral) parameter.defaultValue().orElseThrow()).value());
    }
    @Test void changingWrittenArgumentsInvalidatesOmittedArgumentLinks() {
        var call = call(project().files().getFirst().items().get(1));
        assertTrue(call.edit().addArgument(IntegerLiteral.create().setValue(3)).build().defaultArguments().isEmpty());
        assertTrue(call.edit().clearTarget().build().defaultArguments().isEmpty());
    }
}
