/* Operator syntax and derived non-owning bindings are independent. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class OperatorBindingModelTest {
    static Project parse(String code) { return Project.create().addFile(Parser.parse("operators.cpp", code, Language.CPP)).build(); }
    static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); }});
        return result;
    }
    @Test void parsesOperatorNamesAndConversionFunctionsWithoutInventedReturnTypes() {
        var project = parse("struct Price { explicit Price(int); operator int() const; operator const int*() const; int operator+(int) const; int operator()(); int operator[](int); int operator++(int); int operator->* (int); }; Price::operator int() const { return 1; }");
        var methods = nodes(project, MethodDeclaration.class);
        assertTrue(methods.getFirst().modifiers().contains("explicit"));
        assertEquals(List.of("Price", "operator int", "operator int const *", "operator+", "operator()", "operator[]", "operator++", "operator->*", "Price::operator int"), methods.stream().map(CallableDeclaration::name).toList());
        for (var method : methods.stream().filter(CallableDeclaration::isConversionFunction).toList()) {
            assertTrue(method.returnType().isEmpty()); assertTrue(method.conversionType().isPresent()); assertTrue(method.isConst());
        }
        var rebuilt = project.edit().build();
        assertEquals(3, nodes(rebuilt, MethodDeclaration.class).stream().filter(CallableDeclaration::isConversionFunction).count());
    }
    @Test void bindingCopiesRemapsAndInvalidatesWithoutRewritingSyntax() {
        var original = parse("int operator+(int a, int b); int main() { return 1 + 2; }");
        var operation = nodes(original, AddExpression.class).getFirst();
        var declaration = nodes(original, CallableDeclaration.class).getFirst();
        var editor = new ProjectEditor(original); editor.bindOperator(operation, declaration);
        var bound = editor.build(); var add = nodes(bound, AddExpression.class).getFirst();
        assertEquals("(1 + 2)", add.source(Language.CPP));
        assertSame(nodes(bound, CallableDeclaration.class).getFirst(), add.operatorTarget().orElseThrow());
        assertEquals(List.of(add.operatorTarget().orElseThrow()), add.references());
        var copy = bound.edit().build(); var copied = nodes(copy, AddExpression.class).getFirst();
        assertSame(nodes(copy, CallableDeclaration.class).getFirst(), copied.operatorTarget().orElseThrow());
        assertNotSame(add.operatorTarget().orElseThrow(), copied.operatorTarget().orElseThrow());
        var changed = new ProjectEditor(bound); changed.replace(add.left(), IntegerLiteral.create().setValue(3).build());
        assertTrue(nodes(changed.build(), AddExpression.class).getFirst().operatorTarget().isEmpty());
        var declarationEdit = new ProjectEditor(bound);
        var method = nodes(bound, FunctionDeclaration.class).getFirst(); declarationEdit.replace(method, method.edit().setName("operator-").build());
        assertTrue(nodes(declarationEdit.build(), AddExpression.class).getFirst().operatorTarget().isEmpty());
        assertTrue(add.edit().setRight(IntegerLiteral.create().setValue(4)).build().operatorTarget().isEmpty());
        var nested = add.edit(); ((IntegerLiteral.Builder) nested.left().orElseThrow()).setValue(5);
        assertTrue(nested.build().operatorTarget().isEmpty());
    }
    @Test void implicitConversionsKeepOperatorBindingsAndOwnedTypeOccurrences() {
        var original = parse("int operator+(int a, int b); int main() { return 1 + 2; }");
        var declaration = nodes(original, CallableDeclaration.class).getFirst();
        var editor = new ProjectEditor(original); editor.bindOperator(nodes(original, AddExpression.class).getFirst(), declaration);
        var bound = editor.build(); var operation = nodes(bound, AddExpression.class).getFirst();
        var convert = new ProjectEditor(bound);
        convert.convert(operation.left(), List.of(new ProjectEditor.ConversionStep("int", nodes(bound, CallableDeclaration.class).getFirst().returnType().orElseThrow(), ConversionExpression.Kind.ARITHMETIC_CONVERSION)));
        var converted = convert.build();
        var convertedAdd = nodes(converted, AddExpression.class).getFirst();
        assertTrue(convertedAdd.operatorTarget().isPresent());
        assertInstanceOf(ConversionExpression.class, convertedAdd.left());
        assertNotSame(nodes(converted, CallableDeclaration.class).getFirst().returnType().orElseThrow(), ((ConversionExpression) convertedAdd.left()).targetType());
    }
    @Test void explicitOperatorCallsKeepTheirWrittenCallees() {
        var project = parse("struct C { int operator+(int); operator int() const; }; int main() { C c; return c.operator+(2) + c.operator int(); }");
        var calls = nodes(project, CallExpression.class);
        assertEquals(2, calls.size());
        assertEquals(List.of("operator+", "operator int"), calls.stream().map(call -> ((MemberExpression)call.callee()).member()).toList());
    }
    @Test void unresolvedOperatorsRetainKnownCandidatesThroughCopies() {
        var project = com.kniazkov.cpg.analysis.NameResolver.resolve(parse(
                "struct A {}; int operator+(A, int); int main() { A a; a + missing; }"));
        var operation = nodes(project, AddExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.UNRESOLVED, operation.operatorResolution());
        assertTrue(operation.operatorTarget().isEmpty());
        assertEquals(1, operation.operatorCandidates().size());
        assertTrue(com.kniazkov.cpg.analysis.OperatorSemantics.mayMutate(operation));
        assertTrue(com.kniazkov.cpg.analysis.OperatorSemantics.mayThrow(operation));
        var copy = project.edit().build();
        var copied = nodes(copy, AddExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.UNRESOLVED, copied.operatorResolution());
        assertTrue(copied.operatorTarget().isEmpty());
        var target = nodes(copy, CallableDeclaration.class).stream().filter(c -> c.name().equals("operator+")).findFirst().orElseThrow();
        assertEquals(List.of(target), copied.operatorCandidates());
        assertEquals(List.of(target), copied.references());
        assertNotSame(operation.operatorCandidates().getFirst(), target);
    }
    @Test void builtinOnlyAmbiguityNeedsNoSyntheticDeclaration() {
        var project = parse("int main() { return 1 + 2; }");
        var editor = new ProjectEditor(project);
        editor.resolveOperator(nodes(project, AddExpression.class).getFirst(), List.of(), CallExpression.Resolution.AMBIGUOUS);
        var copied = editor.build().edit().build();
        var operation = nodes(copied, AddExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.AMBIGUOUS, operation.operatorResolution());
        assertTrue(operation.operatorCandidates().isEmpty());
        assertTrue(operation.operatorTarget().isEmpty());
        assertTrue(com.kniazkov.cpg.analysis.OperatorSemantics.mayInvokeUserCode(operation));
    }
    @Test void bindingCannotEscapeProjectOwnership() {
        var original = parse("int main() { return 1 + 2; }");
        var editor = new ProjectEditor(original);
        assertThrows(IllegalArgumentException.class, () -> editor.bindOperator(nodes(original, AddExpression.class).getFirst(), FunctionDeclaration.create().setName("operator+").build()));
    }
}
