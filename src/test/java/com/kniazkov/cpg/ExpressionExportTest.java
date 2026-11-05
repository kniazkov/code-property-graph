/* C++03 expression source, JSON and DOT export contracts. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import java.math.*;
import java.nio.file.*;
import org.junit.jupiter.api.io.TempDir;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ExpressionExportTest {
    @TempDir Path directory;
    private static NameExpression.Builder name(String value) { return NameExpression.create().setName(value); }
    private static IntegerLiteral.Builder integer(int value) { return IntegerLiteral.create().setValue(value); }
    private static Project project(NodeBuilder<? extends Expression> expression) {
        return Project.create().addFile(SourceFile.create().setPath("expression.cpp").setLanguage(Language.CPP)
                .addItem(FunctionDeclaration.create().setName("test").setReturnType(NamedType.create().setName("int"))
                        .setBody(BlockStatement.create().addItem(ReturnStatement.create().setValue(expression))))).build();
    }
    private static Expression reparse(Expression expression) {
        String source = "int test() { return " + Source.render(expression, Language.CPP) + "; }";
        SourceFile parsed = Parser.parse("expression.cpp", source, Language.CPP).build();
        assertFalse(parsed.hasErrors(), source + "\n" + parsed.parseProblems());
        var function = (FunctionDeclaration) parsed.items().getFirst();
        return ((ReturnStatement) function.body().orElseThrow().items().getFirst()).value().orElseThrow();
    }
    private static String shape(Node node) {
        return node.getClass().getSimpleName() + node.children().stream().map(ExpressionExportTest::shape).toList();
    }
    @Test void groupingPreservesOperatorOwnershipAndAssociativity() {
        List<Expression> expressions = List.of(
            BitwiseOrExpression.create().setLeft(BitwiseAndExpression.create().setLeft(name("a")).setRight(name("b")))
                    .setRight(BitwiseXorExpression.create().setLeft(name("c")).setRight(name("d"))).build(),
            LogicalAndExpression.create().setLeft(LogicalOrExpression.create().setLeft(name("a")).setRight(name("b")))
                    .setRight(name("c")).build(),
            ConditionalExpression.create().setCondition(name("a"))
                    .setThenExpression(CommaExpression.create().setLeft(name("b")).setRight(name("c")))
                    .setElseExpression(AssignmentExpression.create().setTarget(name("d")).setValue(name("e"))).build(),
            CommaExpression.create().setLeft(name("a"))
                    .setRight(CommaExpression.create().setLeft(name("b")).setRight(name("c"))).build(),
            CompoundAssignmentExpression.create().setTarget(name("a")).setOperator("<<=")
                    .setValue(CompoundAssignmentExpression.create().setTarget(name("b")).setOperator("+=").setValue(integer(1))).build(),
            IndexExpression.create().setLeft(name("a"))
                    .setRight(CommaExpression.create().setLeft(name("b")).setRight(name("c"))).build(),
            PostfixIncrementExpression.create().setOperand(IndexExpression.create().setLeft(name("a")).setRight(integer(0))).build(),
            PrefixDecrementExpression.create().setOperand(DereferenceExpression.create().setOperand(name("p"))).build(),
            DotMemberPointerExpression.create().setLeft(name("a")).setRight(name("p")).build(),
            ArrowMemberPointerExpression.create().setLeft(name("a")).setRight(name("p")).build());
        for (Expression expression : expressions) assertEquals(shape(expression), shape(reparse(expression)), Source.render(expression, Language.CPP));
    }
    @Test void arbitraryPrecisionAndLiteralSpellingsSurviveExports() {
        BigInteger maximum = new BigInteger("18446744073709551615");
        var literal = IntegerLiteral.create().setValue(maximum).setCppType("unsigned long").setSpelling("0xffffffffffffffffUL");
        var project = project(literal);
        String json = ProjectJson.encode(project).toText("");
        assertTrue(json.contains(maximum.toString()), json);
        assertTrue(json.contains("0xffffffffffffffffUL"), json);
        assertTrue(DotVisualizer.toDot(project, false).contains(maximum.toString()));
        assertEquals("0xffffffffffffffffUL", Source.render(literal.build(), Language.CPP));
        assertThrows(IllegalArgumentException.class, () -> Source.render(literal.build(), Language.JAVA));
        assertEquals(maximum, ((IntegerLiteral) reparse(literal.build())).bigValue());
        assertEquals("18446744073709551615UL", Source.render(IntegerLiteral.create().setValue(maximum).build(), Language.CPP));
        assertThrows(IllegalArgumentException.class, () -> Source.render(IntegerLiteral.create().setValue(maximum.add(BigInteger.ONE)).build(), Language.CPP));
        assertEquals("true", Source.render(BooleanLiteral.create().setValue(true).build(), Language.CPP));
        var floating = FloatingLiteral.create().setValue(new BigDecimal("1.25")).setCppType("long double").setSpelling("1.25L").build();
        assertEquals("1.25L", Source.render(floating, Language.CPP));
        assertEquals(floating.value(), ((FloatingLiteral) reparse(floating)).value());
        var character = CharacterLiteral.create().setValue(BigInteger.valueOf(65)).setWide(true).setCppType("wchar_t").setSpelling("L'A'").build();
        assertEquals(character.value(), ((CharacterLiteral) reparse(character)).value());
        var string = StringLiteral.create().setCodeUnits(List.of(BigInteger.valueOf(65), BigInteger.ZERO, BigInteger.valueOf(66)))
                .setSpelling("\"A\\0B\"").build();
        assertEquals(string.codeUnits(), ((StringLiteral) reparse(string)).codeUnits());
        assertThrows(IllegalArgumentException.class, () -> Source.render(string, Language.JAVA));
    }
    @Test void memberPointerAddressDoesNotParenthesizeTheQualifiedId() {
        var member = AddressOfExpression.create().setOperand(ScopeMemberExpression.create().setReceiver(name("S")).setMember("member")).build();
        assertEquals("(&S::member)", Source.render(member, Language.CPP));
        assertEquals(shape(member), shape(reparse(member)));
    }
    @Test void conversionsExposeMetadataWithoutInventingWrittenCasts() {
        var implicit = ConversionExpression.create().setOperand(name("x")).setTargetType(NamedType.create().setName("long"))
                .setSourceType("int").setKind(ConversionExpression.Kind.ARITHMETIC_CONVERSION).setExplicit(false);
        assertEquals("x", Source.render(implicit.build(), Language.CPP));
        var explicit = ConversionExpression.create().setOperand(name("x")).setTargetType(NamedType.create().setName("long"))
                .setKind(ConversionExpression.Kind.STATIC_CAST).setExplicit(true);
        assertEquals("static_cast<long>(x)", Source.render(explicit.build(), Language.CPP));
        assertEquals(shape(explicit.build()), shape(reparse(explicit.build())));
        String json = ProjectJson.encode(project(implicit)).toText("");
        assertTrue(json.contains("arithmetic_conversion"), json);
        assertTrue(json.contains("sourceType"), json);
        assertTrue(DotVisualizer.toDot(project(implicit), false).contains("implicit arithmetic_conversion to long"));
    }
    @Test void sourcePreservingValueEditKeepsMacrosCommentsAndImplicitConversions() throws Exception {
        String source = "#define VALUE 2\nlong take(long n);\nint main() { int x = VALUE; /*keep*/ return take(x + 3); }\n";
        Files.writeString(directory.resolve("main.cpp"), source);
        var parsed = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.GREEN));
        assertEquals(source, Cpg.sources(parsed, Language.CPP).get("main.cpp"));
        var literals = new ArrayList<IntegerLiteral>();
        parsed.walk(new NodeVisitor() { @Override public void visit(IntegerLiteral node) {
            if (node.bigValue().equals(BigInteger.valueOf(3))) literals.add(node);
        }});
        var editor = new ProjectEditor(parsed);
        editor.replace(literals.getFirst(), literals.getFirst().edit().setValue(4).build());
        String edited = Cpg.sources(editor.build(), Language.CPP).get("main.cpp");
        assertEquals(source.replace("x + 3", "x + 4"), edited);
        assertFalse(edited.contains("static_cast"));
    }

}
