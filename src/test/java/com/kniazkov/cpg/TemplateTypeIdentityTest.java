package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.analysis.NameResolver;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TemplateTypeIdentityTest {
    @Test void differentArgumentsNeverCollapse() {
        var file = Parser.parse("identity.cpp", "template<class T> struct A {}; template<int N> struct B {}; A<int> x; A<double> y; B<1> a; B<2> b; typename A<int>::nested c; typename A<double>::nested d;", Language.CPP).build();
        var identities = new ArrayList<String>();
        file.walk(new NodeVisitor() { @Override public void visit(VariableDeclaration variable) { identities.add(TypeIdentity.canonicalIdentity(variable.type().orElseThrow())); } });
        assertEquals(6, new HashSet<>(identities).size());
    }
    @Test void resolvedOverloadsDistinguishArgumentsAndNormalizeDependentPositions() {
        var project = NameResolver.resolve(Project.create().addFile(Parser.parse("identity.cpp", "template<class T> struct A {}; template<int N> struct B {}; void f(A<int>); void f(A<double>); template<int N> void g(B<N>); template<int M> void g(B<M>);", Language.CPP)).build());
        var functions = new ArrayList<FunctionDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(FunctionDeclaration function) { functions.add(function); } });
        assertTrue(functions.get(1).previousDeclaration().isEmpty());
        assertSame(functions.get(2), functions.get(3).previousDeclaration().orElseThrow());
    }
    @Test void dependentValueNamesUseDeclarationPositions() {
        var left = TemplateParameterDeclaration.create().setName("N").setKind(TemplateParameterDeclaration.Kind.VALUE).setType(NamedType.create().setName("int"));
        var right = TemplateParameterDeclaration.create().setName("M").setKind(TemplateParameterDeclaration.Kind.VALUE).setType(NamedType.create().setName("int"));
        var a = NamedType.create().setName("Box").setTemplateArguments(List.of(TemplateArgument.create().setExpression(AddExpression.create().setLeft(NameExpression.create().setName("N").setTarget(left)).setRight(IntegerLiteral.create().setValue(1))))).build();
        var b = NamedType.create().setName("Box").setTemplateArguments(List.of(TemplateArgument.create().setExpression(AddExpression.create().setLeft(NameExpression.create().setName("M").setTarget(right)).setRight(IntegerLiteral.create().setValue(1))))).build();
        var at = ((NameExpression)((AddExpression)a.templateArguments().getFirst().expression().orElseThrow()).left()).target().orElseThrow();
        var bt = ((NameExpression)((AddExpression)b.templateArguments().getFirst().expression().orElseThrow()).left()).target().orElseThrow();
        assertEquals(TypeIdentity.canonicalIdentity(a, Map.of(at, "parameter#0")), TypeIdentity.canonicalIdentity(b, Map.of(bt, "parameter#0")));
        assertNotEquals(TypeIdentity.canonicalIdentity(a, Map.of(at, "parameter#0")), TypeIdentity.canonicalIdentity(b, Map.of(bt, "parameter#1")));
    }
    @Test void contextualExpressionIdentitiesAreExactAndDoNotChangeSyntax() {
        var type = NamedType.create().setName("Box").setTemplateArguments(List.of(
                TemplateArgument.create().setExpression(NameExpression.create().setName("fixed")),
                TemplateArgument.create().setExpression(NameExpression.create().setName("fixed")))).build();
        var first = type.templateArguments().getFirst().expression().orElseThrow();
        var second = type.templateArguments().getLast().expression().orElseThrow();
        String ordinary = TypeIdentity.canonicalIdentity(type);
        var values = new IdentityHashMap<Expression, String>(); values.put(first, "certified:2");
        String contextual = TypeIdentity.canonicalIdentity(type, Map.of(), values);
        assertTrue(contextual.contains("VALUE(certified:2),VALUE(name(fixed))"), contextual);
        assertEquals(ordinary, TypeIdentity.canonicalIdentity(type));
        assertEquals(ordinary, TypeIdentity.canonicalIdentity(type, Map.of(), Map.of()));
        assertSame(first, type.templateArguments().getFirst().expression().orElseThrow());
        assertSame(second, type.templateArguments().getLast().expression().orElseThrow());
        values.put(second, "certified:3");
        assertNotEquals(contextual, TypeIdentity.canonicalIdentity(type, Map.of(), values));
    }
}
