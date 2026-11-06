/* Printed C++03 switch conversion/profile diagnostics. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.analysis.ConstantFolder;
import com.kniazkov.cpg.analysis.TypeSemantics;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SwitchConversionCompletenessTest {
    private static final PlatformProfile DEFAULT = PlatformProfile.defaults();
    private static final PlatformProfile WRAP = DEFAULT.toBuilder().signedIntegerConversion("twos-complement").build();
    private Project resolve(String source, PlatformProfile profile) {
        Path path = Path.of("complete-switch.cpp").toAbsolutePath();
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03").withPlatform(profile);
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit, source)).build());
    }
    private Project resolve(String source) { return resolve(source, DEFAULT); }
    private long duplicates(Project project) { return project.semanticDiagnostics().stream().filter(d -> d.code().equals("duplicate-case")).count(); }
    private String select(String type, String condition, String a, String b) {
        return "void f(" + type + " x){switch(" + condition + "){case " + a + ":break;case " + b + ":break;}}";
    }
    private void check(long expected, String source, PlatformProfile profile) {
        Project project = resolve(source, profile);
        assertEquals(expected, duplicates(project), () -> source + "\n" + project.semanticDiagnostics());
    }
    private void check(long expected, String source) { check(expected, source, DEFAULT); }
    @Test void enumUnderlyingChoicePrecedesFullRangePromotion() {
        var unsigned = DEFAULT.toBuilder().enumUnderlyingType("unsigned-first").build();
        String source = "enum E{zero};" + select("E", "x", "-1", "4294967295U");
        check(0, source); check(1, source, unsigned); check(0, source, DEFAULT.toBuilder().enumUnderlyingType("unknown").build());
        check(1, "enum E{big=4294967295U};" + select("E", "x", "-1", "4294967295U"));
        check(1, "enum E{big=18446744073709551615UL};" + select("E", "x", "-1", "18446744073709551615UL"));
        check(0, "enum E{big=4294967296UL};" + select("E", "x", "-1", "4294967295U"));
        check(1, "enum E{};" + select("E", "x", "-1", "4294967295U"), unsigned);
        check(1, "enum E{zero};typedef const E CE;" + select("CE&", "x", "-1", "4294967295U"), unsigned);
        check(1, "enum E{zero};void f(){switch(E x=zero){case -1:break;case 4294967295U:break;}}", unsigned);
        check(1, "enum E{zero};struct S{E bits:1;};" + select("S", "x.bits", "-1", "4294967295U"), unsigned);
    }
    @Test void enumAndCertifiedConstCasesAreEvaluatedContextually() {
        check(1, "enum E{a=0UL,b,c=b-2};" + select("E", "x", "-1", "18446744073709551615UL"));
        check(1, "enum E{zero,one,two=one+1};" + select("int", "x", "two", "2"));
        check(1, "enum E{minus=-1};" + select("unsigned int", "x", "minus", "4294967295U"));
        check(1, "const unsigned int top=4294967295U;" + select("unsigned int", "x", "-1", "top"));
        check(1, "const int a=1;const int b=a+1;" + select("int", "x", "b", "2"));
        check(1, "struct S{static const unsigned int top=4294967295U;};" + select("unsigned int", "x", "-1", "S::top"));
        check(1, "const unsigned int bound=4294967295U;enum E{top=bound};" + select("E", "x", "-1", "4294967295U"));
    }
    @Test void unknownSignedConversionAndOptInWrapAreDistinctFromOverflow() {
        for (String type : List.of("int", "long")) {
            String max = type.equals("int") ? "4294967295U" : "18446744073709551615UL";
            check(0, select(type, "x", "-1", max)); check(1, select(type, "x", "-1", max), WRAP);
        }
        check(1, select("int", "x", "-2147483647-1", "2147483648U"), WRAP);
        check(1, select("int", "x", "0", "4294967296UL"), WRAP);
        check(0, select("int", "x", "-1", "4294967294U"), WRAP);
        check(1, select("int", "x", "static_cast<int>(4294967295U)", "-1"), WRAP);
        check(0, select("int", "x", "2147483647+1", "-2147483647-1"), WRAP);
        check(1, select("int", "x", "4294967295U", "4294967295U"));
        var narrow = WRAP.toBuilder().sizeBits("int", 16).alignmentBits("int", 16)
                .wcharType("long").sizeBits("wchar_t", 64).alignmentBits("wchar_t", 64).build();
        check(1, select("int", "x", "-1", "65535U"), narrow);
        check(0, select("int", "x", "-1", "65535U"), WRAP);
    }
    private String bits(String type, int width, String condition) {
        return "struct S{" + type + " bits:" + width + ";};" + select("S", condition, "-1", "4294967295U");
    }
    @Test void bitfieldPromotionsUseValueWidthNotStorageWidth() {
        check(0, bits("unsigned int", 3, "x.bits"));
        check(1, bits("unsigned int", 32, "x.bits"));
        check(1, bits("unsigned int", 100, "(x.bits)"));
        check(0, bits("unsigned long", 3, "x.bits"));
        check(1, bits("unsigned long", 32, "x.bits"));
        check(0, bits("unsigned long", 33, "x.bits"));
        check(0, bits("signed long", 33, "x.bits"));
        check(0, bits("bool", 100, "x.bits"));
        check(1, "struct S{unsigned int bits:32;void f(){switch(bits){case -1:break;case 4294967295U:break;}}};");
        check(1, "const int width=32;struct S{unsigned int bits:width;};" + select("S", "x.bits", "-1", "4294967295U"));
    }
    @Test void plainBitfieldSignednessIsAnIndependentTargetChoice() {
        var unsigned = DEFAULT.toBuilder().bitFieldSignedness("unsigned").build();
        check(0, bits("int", 32, "x.bits")); check(1, bits("int", 32, "x.bits"), unsigned);
        check(0, bits("signed int", 32, "x.bits"), unsigned);
        check(1, bits("unsigned int", 32, "x.bits"), DEFAULT.toBuilder().bitFieldSignedness("unknown").build());
        check(0, bits("int", 32, "x.bits"), DEFAULT.toBuilder().bitFieldSignedness("unknown").build());
    }
    @Test void bitfieldDescendantsDoNotTaintUnambiguousResultTypes() {
        check(0, bits("unsigned int", 3, "+x.bits"));
        check(1, bits("unsigned int", 3, "x.bits+0U"));
        check(0, bits("unsigned int", 3, "x.bits+0L"));
        check(1, bits("unsigned int", 3, "static_cast<unsigned int>(x.bits)"));
        check(1, bits("unsigned int", 3, "(x.bits, 0U)"));
        check(1, bits("unsigned int", 3, "x.bits++"));
        check(0, bits("unsigned int", 3, "x.bits==0"));
        check(1, bits("unsigned int", 32, "+x.bits"));
    }
    @Test void preCwg324ResultProvenanceStaysUnknown() {
        for (String condition : List.of("++x.bits", "x.bits=0", "x.bits+=1", "(0, x.bits)", "true?x.bits:x.bits", "+(x.bits=0)"))
            check(0, bits("unsigned int", 32, condition), WRAP);
        check(1, "struct S{unsigned int bits:32;};" + select("S", "++x.bits", "1", "1"));
    }
    @Test void uniqueClassConversionsIncludeReferencesDeclarationsAndInheritance() {
        for (String members : List.of("operator unsigned int();", "operator unsigned int() const;", "operator unsigned int&();", "operator unsigned int();operator double();operator void*();"))
            check(1, "struct C{" + members + "};" + select("C", "x", "-1", "4294967295U"));
        check(1, "struct B{operator unsigned int();};struct C:B{};" + select("C", "x", "-1", "4294967295U"));
        check(1, "struct B{operator int();};struct C:B{operator unsigned int();};" + select("C", "x", "1", "1"));
        check(1, "struct B{operator unsigned int();};struct C:B{operator unsigned int();};" + select("C", "x", "-1", "4294967295U"));
        check(1, "struct B{operator unsigned int();};struct L:virtual B{};struct R:virtual B{};struct C:L,R{};" + select("C", "x", "-1", "4294967295U"));
        check(1, "struct C{operator unsigned int();};void f(){switch(C x=C()){case -1:break;case 4294967295U:break;}}");
        check(1, "enum E{top=4294967295U};struct C{operator E();};" + select("C", "x", "-1", "4294967295U"));
    }
    @Test void ambiguousInaccessibleAndUncallableClassConversionsAreNotGuessed() {
        for (String members : List.of("operator int();operator unsigned int();", "operator unsigned int();operator unsigned int() const;",
                "operator unsigned int();operator unsigned int&();", "operator int();private:operator unsigned int();"))
            check(0, "struct C{" + members + "};" + select("C", "x", "-1", "4294967295U"));
        check(0, "struct C{operator unsigned int();};" + select("const C&", "x", "-1", "4294967295U"));
        check(0, "class C{operator unsigned int();};" + select("C", "x", "-1", "4294967295U"));
        check(0, "struct B{operator unsigned int();};struct L:B{};struct R:B{};struct C:L,R{};" + select("C", "x", "-1", "4294967295U"));
        check(0, "struct B{operator unsigned int();};class C:B{};" + select("C", "x", "-1", "4294967295U"));
        check(0, "struct B{operator unsigned int();};struct C{operator B();};" + select("C", "x", "-1", "4294967295U"));
    }
    @Test void foldableButForbiddenCaseSyntaxAndNonconstantsStayUnevaluated() {
        for (String value : List.of("true?1:side()", "false?side():1", "(0,1)", "true?1:++x", "true?1:(x=2)"))
            check(0, "int side();" + select("int", "x", value, "1"));
        check(0, "volatile const int v=1;" + select("int", "x", "v", "1"));
        check(0, "const int n=1;const int& r=n;" + select("int", "x", "r", "1"));
        check(0, "struct S{static const int n=1;};S get();" + select("int", "x", "get().n", "1"));
        check(0, "struct S{const int n;};" + select("S", "x.n", "x.n", "1"));
        check(0, "const int a=a;" + select("int", "x", "a", "1"));
        check(0, "extern const int a;const int b=a;const int a=b;" + select("int", "x", "a", "1"));
    }
    @Test void enumPromotionsShareTheOverloadPolicy() {
        String source = "enum E{zero};int pick(int);int pick(unsigned int);int f(E e){return pick(e);}";
        for (String policy : List.of("int-first", "unsigned-first")) {
            var project = resolve(source, DEFAULT.toBuilder().enumUnderlyingType(policy).build());
            var calls = new ArrayList<CallExpression>(); project.walk(new NodeVisitor(){@Override public void visit(CallExpression call){calls.add(call);}});
            assertEquals(policy.equals("int-first") ? "int" : "unsigned int",
                    calls.getFirst().target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
        }
    }
    @Test void integralEnumCastsUseOnlyProvenValuesAndFloatingPolicyStaysUnknown() {
        check(1, "enum E{one=1};" + select("int", "x", "static_cast<E>(1)", "1"));
        check(1, "enum E{lo=0,hi=3};" + select("int", "x", "E(2)", "2"));
        check(0, "enum E{one=1};" + select("int", "x", "static_cast<E>(4294967295U)", "-1"), WRAP);
        check(1, select("int", "x", "static_cast<int>(0.0)", "0"));
        check(1, select("int", "x", "int(0.0)", "0"));
        // No floating representation or rounding policy has been declared.
        check(0, select("int", "x", "static_cast<int>(1.0)", "1"));
        check(0, select("unsigned int", "x", "static_cast<unsigned int>(-1.0)", "4294967295U"), WRAP);
    }
    @Test void diagnosticEnumValuesDoNotEraseNominalTypesDuringFolding() {
        var original = resolve("enum E{one=1};int pick(E);int pick(int);int f(){return pick(static_cast<E>(1));}");
        var folded = ConstantFolder.fold(original);
        var conversions = new ArrayList<ConversionExpression>();
        folded.walk(new NodeVisitor(){@Override public void visit(ConversionExpression conversion){if(conversion.isExplicit())conversions.add(conversion);}});
        assertEquals(1, conversions.size());
        assertInstanceOf(EnumDeclaration.class, assertInstanceOf(NamedType.class, conversions.getFirst().targetType()).declaration().orElseThrow());
        var rebuilt = NameResolver.resolve(folded);
        var calls = new ArrayList<CallExpression>(); rebuilt.walk(new NodeVisitor(){@Override public void visit(CallExpression call){calls.add(call);}});
        assertEquals("named(E)", calls.getFirst().target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
    }
    @Test void contextualConstantsReachNestedPromotionExpressions() {
        check(1, "const int n=32;struct S{unsigned int bits:n;};" + select("S", "+x.bits", "-1", "4294967295U"));
        check(1, "const unsigned int n=4294967295U;enum E{top=n};" + select("E", "+x", "-1", "4294967295U"));
        check(1, "enum A{a=4294967295U};enum E{top=a};" + select("E", "+x", "-1", "4294967295U"));
        for (String condition : List.of("x|0", "true?x:0U", "x<<0"))
            check(1, "enum E{top=4294967295U};" + select("E", condition, "-1", "4294967295U"));
        check(0, "enum E{top=4294967295U};" + select("E", "x==x", "0", "2"));
        check(1, "enum E{top=4294967295U};" + select("int", "+top", "-1", "4294967295U"));
        check(1, "enum W{n=32};struct S{unsigned int bits:n;};" + select("S", "+x.bits", "-1", "4294967295U"));
        check(0, "volatile const int n=32;struct S{unsigned int bits:n;};" + select("S", "+x.bits", "-1", "4294967295U"));
    }
    @Test void referenceConditionsAndProtectedReceiversUseActualObjectTypes() {
        check(1, "struct C{operator unsigned int() const;};void f(C c){switch(const C& x=c){case -1:break;case 4294967295U:break;}}");
        String invalid = "struct B{protected:operator unsigned int();};struct D:B{void f(B b){switch(b){case -1:break;case 4294967295U:break;}}};";
        Project project = resolve(invalid);
        assertEquals(0, duplicates(project));
        assertTrue(project.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("inaccessible-overload")));
        check(1, "struct B{protected:operator unsigned int();};struct D:B{void f(D d){switch(d){case -1:break;case 4294967295U:break;}}};");
    }
    @Test void lvalueWrappersCannotEraseUnknownHistoricalFieldProvenance() {
        for (String condition : List.of("++x.bits", "x.bits=0", "(0,x.bits)", "true?x.bits:x.bits")) {
            var project = resolve(bits("unsigned int", 32, condition));
            var selections = new ArrayList<SwitchStatement>(); project.walk(new NodeVisitor(){@Override public void visit(SwitchStatement selection){selections.add(selection);}});
            Expression wrapped = ConversionExpression.create().setOperand(new EditContext().copy(selections.getFirst().condition()))
                    .setTargetType(NamedType.create().setName("unsigned int")).setKind(ConversionExpression.Kind.LVALUE_TO_RVALUE).setExplicit(false).build();
            assertTrue(TypeSemantics.promoted(wrapped, DEFAULT).isEmpty(), condition);
        }
    }
    @Test void ownershipRatherThanAccessMetadataCertifiesStaticConstants() {
        var project = resolve("struct S{const int n;void f(int x){switch(x){case n:break;case 1:break;}}};");
        var fields = new ArrayList<VariableDeclaration>(); project.walk(new NodeVisitor(){@Override public void visit(VariableDeclaration field){if(field.name().equals("n"))fields.add(field);}});
        VariableDeclaration field = fields.getFirst();
        var replacement = field.edit().setInitializer(IntegerLiteral.create().setValue(1)); replacement.clearAccess();
        var editor = new ProjectEditor(project); editor.replace(field, replacement.build());
        assertEquals(0, duplicates(NameResolver.resolve(editor.build())), "A nonstatic member is not an ICE even without access metadata");
    }
    @Test void repeatedCrossEnumDependenciesAreMemoizedAndCyclesRemainUnknown() {
        StringBuilder source = new StringBuilder("enum E0{e0=0};");
        for (int i=1;i<25;i++) source.append("enum E"+i+"{e"+i+"=e"+(i-1)+"+1};");
        source.append(select("unsigned int", "x", "e24", "24"));
        assertTimeoutPreemptively(java.time.Duration.ofSeconds(10), () -> check(1, source.toString()));
    }
    @Test void nestedLabelsCopyJsonRangesAndEditedRebuildRemainExact() {
        String source = "struct C{operator unsigned int();};\nvoid f(C x){switch(x){\ncase -1: switch(0L){case -1:break;case 4294967295U:break;}break;\ncase 4294967295U:break;default:break;default:break;}}";
        Project project = resolve(source);
        assertEquals(1, duplicates(project));
        assertEquals(1, project.semanticDiagnostics().stream().filter(d -> d.code().equals("duplicate-default")).count());
        var cases = new ArrayList<CaseStatement>(); project.walk(new NodeVisitor(){@Override public void visit(CaseStatement label){cases.add(label);}});
        CaseStatement conflicting = cases.get(3);
        Diagnostic diagnostic = conflicting.diagnostics().stream().filter(d -> d.code().equals("duplicate-case")).findFirst().orElseThrow();
        assertEquals(conflicting.range().orElseThrow(), diagnostic.primaryRange()); assertEquals(4, diagnostic.primaryRange().start().line());
        String json = ProjectJson.encode(project).toText("");
        assertTrue(json.contains("duplicate-case")); assertTrue(json.contains("enumUnderlyingType"));
        Project copy = project.edit().build();
        assertEquals(0, duplicates(copy), "The ordinary editable snapshot clears derived diagnostics");
        assertEquals(project.files().getFirst().provenance().orElseThrow().unit().configuration(),
                copy.files().getFirst().provenance().orElseThrow().unit().configuration());
        Project rebuilt = NameResolver.resolve(copy);
        assertEquals(project.semanticDiagnostics(), rebuilt.semanticDiagnostics());
        assertEquals(1, duplicates(rebuilt));
        assertTrue(ProjectJson.encode(rebuilt).toText("").contains("duplicate-case"));
        var editor = new ProjectEditor(project);
        editor.replace(conflicting.value().orElseThrow(), IntegerLiteral.create().setValue(2).build());
        assertEquals(0, duplicates(NameResolver.resolve(editor.build())));
        var methods = new ArrayList<CallableDeclaration>(); project.walk(new NodeVisitor(){@Override public void visit(CallableDeclaration callable){if(callable.conversionType().isPresent())methods.add(callable);}});
        var changed = new ProjectEditor(project);
        CallableDeclaration conversion = methods.getFirst();
        changed.replace(conversion, ((MethodDeclaration)conversion).edit().setName("operator long").setConversionType(NamedType.create().setName("long")).build());
        assertEquals(0, duplicates(NameResolver.resolve(changed.build())));
    }
}
