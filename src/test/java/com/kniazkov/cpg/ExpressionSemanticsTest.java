/* C++03 expression types, categories and conversion evidence. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ExpressionSemanticsTest {
    @TempDir Path temp;
    private static final PlatformProfile PROFILE = PlatformProfile.defaults();
    private Project read(String text) throws Exception {
        Files.writeString(temp.resolve("expressions.cpp"), text);
        return Cpg.read(temp, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED));
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    private static Expression initializer(Project project, String name) {
        return nodes(project, VariableDeclaration.class).stream().filter(variable -> variable.name().equals(name))
                .findFirst().orElseThrow().initializer().orElseThrow();
    }
    private static Expression underlying(Expression expression) {
        while (expression instanceof ConversionExpression conversion) expression = conversion.operand();
        return expression;
    }
    @Test void uniqueCallRetainsImplicitVersusWrittenCastAndSnapshotLinks() throws Exception {
        String text = "void f(long n); int main(){ int x=7; f(x); f(static_cast<long>(x)); }";
        var project = read(text);
        var calls = nodes(project, CallExpression.class);
        var implicit = assertInstanceOf(ConversionExpression.class, calls.get(0).arguments().getFirst());
        assertFalse(implicit.isExplicit());
        assertEquals("int", implicit.sourceType().orElseThrow());
        assertEquals("long", implicit.targetType().canonicalIdentity());
        assertEquals(ValueCategory.RVALUE, implicit.semanticInfo().orElseThrow().category());
        var load = assertInstanceOf(ConversionExpression.class, implicit.operand());
        assertEquals(ConversionExpression.Kind.LVALUE_TO_RVALUE, load.kind());
        var written = assertInstanceOf(ConversionExpression.class, calls.get(1).arguments().getFirst());
        assertTrue(written.isExplicit());
        var range = written.range().orElseThrow();
        assertEquals("static_cast<long>(x)", text.substring(range.start().offset(), range.end().offset()));
        assertEquals("int", written.sourceType().orElseThrow());
        assertTrue(written.source(Language.CPP).contains("static_cast<long>"));
        assertEquals("x", implicit.source(Language.CPP));
        for (var snapshot : List.of(project, project.edit().build(), NameResolver.resolve(project))) {
            assertEquals(3, nodes(snapshot, ConversionExpression.class).size());
            var owned = new HashSet<>(nodes(snapshot, Node.class));
            for (var node : owned) assertTrue(owned.containsAll(node.references()));
        }
    }
    @Test void literalValuesAndExpressionTypesAreIndependent() throws Exception {
        var project = read("int main(){ bool b=true; char c='a'; float f=1.25f; const char* s=\"ab\"; }");
        var floating = nodes(project, FloatingLiteral.class).getFirst();
        assertEquals("float", floating.semanticInfo().orElseThrow().resultType().orElseThrow());
        assertEquals(new java.math.BigDecimal("1.25"), floating.value());
        var string = nodes(project, StringLiteral.class).getFirst();
        assertEquals("array(3,const(char))", string.semanticInfo().orElseThrow().resultType().orElseThrow());
        assertEquals(ValueCategory.LVALUE, string.semanticInfo().orElseThrow().category());
        assertEquals(ConversionExpression.Kind.ARRAY_TO_POINTER,
                assertInstanceOf(ConversionExpression.class, initializer(project, "s")).kind());
    }
    @Test void promotionsUseTargetRanksRatherThanEqualWidths() throws Exception {
        var project = read("int main(){ short s=1; unsigned int u=1U; long l=1L; float f=1.0f; s+s; u+l; f+s; }");
        var additions = nodes(project, AddExpression.class);
        assertEquals(List.of("int", "long", "float"), additions.stream()
                .map(value -> value.semanticInfo().orElseThrow().resultType().orElseThrow()).toList());
        assertEquals(ConversionExpression.Kind.INTEGRAL_PROMOTION,
                assertInstanceOf(ConversionExpression.class, additions.get(0).left()).kind());
        var long32 = PROFILE.toBuilder().sizeBits("long", 32).alignmentBits("long", 32).build();
        assertEquals("unsigned long", TypeSemantics.commonArithmeticType(NamedType.create().setName("unsigned int").build(),
                NamedType.create().setName("long").build(), long32).orElseThrow().canonicalIdentity());
    }
    @Test void arraysFunctionsNullQualificationAndReferenceBinding() throws Exception {
        var project = read("int f(int n); int main(){ int x=1; int a[3]; int *p=a; int (*fp)(int)=f; int *z=0; const int *q=p; int &r=x; const int &t=9; }");
        assertEquals(ConversionExpression.Kind.ARRAY_TO_POINTER, assertInstanceOf(ConversionExpression.class, initializer(project,"p")).kind());
        assertEquals(ConversionExpression.Kind.FUNCTION_TO_POINTER, assertInstanceOf(ConversionExpression.class, initializer(project,"fp")).kind());
        assertEquals(ConversionExpression.Kind.NULL_POINTER, assertInstanceOf(ConversionExpression.class, initializer(project,"z")).kind());
        assertEquals(ConversionExpression.Kind.QUALIFICATION, assertInstanceOf(ConversionExpression.class, initializer(project,"q")).kind());
        for (String name : List.of("r", "t")) {
            var binding = assertInstanceOf(ConversionExpression.class, initializer(project,name));
            assertEquals(ConversionExpression.Kind.REFERENCE_BINDING, binding.kind());
            assertEquals(ValueCategory.LVALUE, binding.semanticInfo().orElseThrow().category());
        }
    }
    @Test void noInventedReferenceBindingOrUnsafeNestedQualification() throws Exception {
        var project = read("int main(){ int *p=0; int **pp=&p; const int **bad=pp; int &r=1; const int c=2; int &s=c; }");
        for (String name : List.of("bad", "r", "s")) assertFalse(initializer(project,name) instanceof ConversionExpression, name);
    }
    @Test void lvalueOperationsAndUnevaluatedArrayExtentKeepTheirCategories() throws Exception {
        var project = read("int &get(); int main(){ int x=1; int a[3]; (x=2); ++x; x++; a[0]; *a; (x,x); (true?x:x); get(); sizeof(a); }");
        for (Class<? extends Expression> type : List.of(AssignmentExpression.class, PrefixIncrementExpression.class,
                IndexExpression.class, DereferenceExpression.class, CommaExpression.class, ConditionalExpression.class, CallExpression.class))
            for (var expression : nodes(project,type)) assertEquals(ValueCategory.LVALUE, expression.semanticInfo().orElseThrow().category(), type.getSimpleName());
        assertEquals(ValueCategory.RVALUE, nodes(project, PostfixIncrementExpression.class).getFirst().semanticInfo().orElseThrow().category());
        var sizeof = nodes(project,SizeofExpression.class).getFirst();
        assertInstanceOf(NameExpression.class,sizeof.operand().orElseThrow());
        assertEquals(java.math.BigInteger.valueOf(12),TypeSemantics.sizeof(sizeof,PROFILE).orElseThrow());
    }
    @Test void ambiguousOverloadsRemainUnrankedAndUnknownExpressionsUntyped() throws Exception {
        var project = read("void f(long); void f(double); int main(){ int x=1; f(x); missing+1; }");
        var call = nodes(project,CallExpression.class).getFirst();
        assertEquals(CallExpression.Resolution.AMBIGUOUS,call.resolution());
        assertInstanceOf(NameExpression.class,call.arguments().getFirst());
        assertTrue(nodes(project,AddExpression.class).getFirst().semanticInfo().orElseThrow().resultType().isEmpty());
    }
    @Test void foldingDoesNotEraseSideEffectsOrTruncateWideLiterals() throws Exception {
        var project = read("int main(){ int x=0; false && ++x; true ? ++x : ++x; (x++,3); 4294967295U + 1U; }");
        var folded = ConstantFolder.fold(project);
        assertEquals(3,nodes(folded,PrefixIncrementExpression.class).size());
        assertEquals(1,nodes(folded,PostfixIncrementExpression.class).size());
        assertEquals(1,nodes(folded,LogicalAndExpression.class).size());
        assertEquals(1,nodes(folded,ConditionalExpression.class).size());
        assertEquals(1,nodes(folded,CommaExpression.class).size());
        assertTrue(nodes(folded,IntegerLiteral.class).stream().anyMatch(value -> value.bigValue().signum()==0
                && value.cppType().equals(Optional.of("unsigned int"))));
    }
    @Test void semanticEditsInvalidateFactsAndEnclosingExpressions() throws Exception {
        var project = read("int main(){ 1.0f + 2.0f; }");
        var sum = nodes(project, AddExpression.class).getFirst();
        var literal = nodes(project,FloatingLiteral.class).getFirst();
        assertEquals(Optional.of("float"),literal.edit().build().semanticInfo().orElseThrow().resultType());
        assertTrue(literal.edit().setCppType("double").build().semanticInfo().isEmpty());
        var builder = sum.edit();
        ((FloatingLiteral.Builder)builder.left().orElseThrow()).setCppType("double");
        assertTrue(builder.build().semanticInfo().isEmpty());
        var editor = new ProjectEditor(project);
        editor.replace(literal,literal.edit().setCppType("double").build());
        var changed = editor.build();
        assertTrue(nodes(changed,AddExpression.class).getFirst().semanticInfo().isEmpty());
        assertEquals(Optional.of("double"),nodes(NameResolver.resolve(changed),AddExpression.class)
                .getFirst().semanticInfo().orElseThrow().resultType());
    }
    @Test void reanalysisRebuildsImplicitConversionsButKeepsWrittenCasts() throws Exception {
        var project = read("void f(long n); int main(){ int x=1; f(x); f(static_cast<long>(x)); }");
        var parameter = nodes(project,ParameterDeclaration.class).getFirst();
        var editor = new ProjectEditor(project);
        editor.replace(parameter,parameter.edit().setType(NamedType.create().setName("double")).build());
        var result = NameResolver.resolve(editor.build());
        var calls = nodes(result,CallExpression.class);
        var implicit = assertInstanceOf(ConversionExpression.class,calls.get(0).arguments().getFirst());
        assertEquals("double",implicit.targetType().canonicalIdentity());
        assertEquals(Optional.of("int"),implicit.sourceType());
        assertFalse(nodes(implicit,ConversionExpression.class).stream().anyMatch(c -> c.targetType().canonicalIdentity().equals("long")));
        var explicitArgument = assertInstanceOf(ConversionExpression.class,calls.get(1).arguments().getFirst());
        assertEquals(Optional.of("long"),explicitArgument.sourceType());
        assertTrue(assertInstanceOf(ConversionExpression.class,explicitArgument.operand()).isExplicit());
    }
    @Test void foldingNarrowConversionsAndMemberAddressRemainConservative() throws Exception {
        var project = read("struct S { int x; }; int main(){ bool b=1; short s=2; int S::*p=&S::x; return b+s; }");
        assertDoesNotThrow(() -> ConstantFolder.fold(project));
        assertTrue(nodes(project,AddressOfExpression.class).getFirst().semanticInfo().orElseThrow().resultType().isEmpty());
        var folded = ConstantFolder.fold(project);
        assertInstanceOf(ConversionExpression.class,initializer(folded,"b"));
        assertInstanceOf(ConversionExpression.class,initializer(folded,"s"));
    }

}
