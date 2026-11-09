/* Snapshot integral template arguments under the original C++03 contract. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.math.BigInteger;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ConstantTemplateArgumentsTest {
    @TempDir Path temp;
    private static final PlatformProfile PROFILE=PlatformProfile.builder()
            .offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build();
    private Project resolve(String source){return resolve(PROFILE,source);}
    private Project resolve(PlatformProfile profile,String source){
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(profile);
        return NameResolver.resolve(Project.create().addFile(Parser.parse(unit,source)).build());
    }
    private static <T extends Node> List<T> nodes(Node root,Class<T> type){
        var list=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node node){if(type.isInstance(node))list.add(type.cast(node));}});return list;
    }
    private static VariableDeclaration variable(Project p,String name){return nodes(p.files().getFirst(),VariableDeclaration.class).stream().filter(v->v.name().equals(name)).findFirst().orElseThrow();}
    private static void healthy(Project p){assertFalse(p.hasErrors(),()->p.parseProblems()+" / "+p.semanticDiagnostics());}
    private static void value(Project p,String name,long expected){assertEquals(BigInteger.valueOf(expected),new IntegralConstants(p,PROFILE).evaluate(variable(p,name).initializer().orElseThrow()).orElseThrow().number());}
    private static final String BOX="template<int N> struct Box {char data[N];char tail;};template<> struct Box<1> {long data;char tail;};";

    @Test void namedArgumentsResolveThroughTheirActualVisibleDeclarations(){
        var p=resolve("const int n=1;namespace api {const int n=3;enum {first=1,next};}"
                +BOX+"typedef Box<n> Outer;typedef Box<api::n> Inner;typedef Box<api::next> Enum;"
                +"unsigned long outer=__builtin_offsetof(Outer,tail);unsigned long inner=__builtin_offsetof(Inner,tail);unsigned long enumerated=__builtin_offsetof(Enum,tail);"
                +"int f(){const int n=4;typedef Box<n> Local;return __builtin_offsetof(Local,tail);}");
        healthy(p);value(p,"outer",8);value(p,"inner",3);value(p,"enumerated",2);
        assertTrue(p.classTemplateInstances().stream().anyMatch(i->i.arguments().stream().anyMatch(a->a.expression().flatMap(e->ConstantEvaluator.evaluate(e,PROFILE)).map(v->v.number().equals(BigInteger.valueOf(4))).orElse(false))));
    }

    @Test void fullSpecializationNamesUseTheSameSnapshotFacts(){
        var p=resolve("const int pattern=2;const unsigned int requested=2;template<int N> struct Box {char data;char tail;};"
                +"template<> struct Box<pattern> {long data;char tail;};typedef Box<requested> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
        healthy(p);value(p,"result",8);
    }

    @Test void typedNormalizationPreservesParameterSizeAndBooleanConversion(){
        for(String[] row:List.of(new String[]{"unsigned long","1","8"},new String[]{"unsigned char","2","1"},new String[]{"short","2","2"},new String[]{"bool","7","1"})){
            var p=resolve("const int n="+row[1]+";template<"+row[0]+" N> struct Box {char data[sizeof(N)];char tail;};typedef Box<n> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
            healthy(p);value(p,"result",Long.parseLong(row[2]));
        }
    }

    @Test void editsRetainNamedInitializerChainsAndReselectSpecializations(){
        String source="const int seed=1;const int count=seed;"+BOX+"typedef Box<count> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
        Project original=resolve(source);healthy(original);value(original,"result",8);
        for(Project p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"result",8);assertTrue(Cpg.sources(p,Language.CPP).get("main.cpp").contains("count"));
            var editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(3).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",3);value(p,"result",8);
            healthy(resolve(Cpg.sources(changed,Language.CPP).get("main.cpp")));
            assertTrue(ProjectJson.encode(changed).toText("  ").contains("OffsetofExpression"));
        }
    }

    @Test void unresolvedForwardNonconstantAndFloatingArgumentsAreNotInvented(){
        for(String declaration:List.of("int n=1;","const double n=1.0;","int f();const int n=f();","extern const int n;","const volatile int n=1;")){
            var p=resolve(declaration+BOX+"typedef Box<n> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
            assertTrue(p.hasErrors(),declaration);assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),declaration);
        }
        for(String source:List.of(BOX+"typedef Box<n> Selected;const int n=1;unsigned long result=__builtin_offsetof(Selected,tail);",
                "const int n=n;"+BOX+"typedef Box<n> Selected;unsigned long result=__builtin_offsetof(Selected,tail);",
                "struct Object {const int n;};Object object;"+BOX+"typedef Box<object.n> Selected;unsigned long result=__builtin_offsetof(Selected,tail);")){
            var p=resolve(source);assertTrue(p.hasErrors(),source);assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),source);
        }
    }

    @Test void nonrepresentableConversionsRemainExplicitlyUnsupported(){
        for(String[] row:List.of(new String[]{"int","4294967295UL"},new String[]{"unsigned char","256"},new String[]{"unsigned int","-1"})){
            var p=resolve("const long n="+row[1]+";template<"+row[0]+" N> struct Box {char tail;};typedef Box<n> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        }
    }

    @Test void unsupportedSpecializationCannotSilentlySelectThePrimary(){
        String source="const int outside=256;template<unsigned char N> struct Box {char data;char tail;};"
                +"template<> struct Box<outside> {long data;char tail;};typedef Box<0> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
        var p=resolve(source);
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        assertTrue(nodes(p.files().getFirst(),NameExpression.class).stream().anyMatch(n->n.name().equals("outside")));
        assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP),
                "Unsupported selection must not emit a misleading primary instantiation");
    }

    @Test void enumArgumentsRetainOriginalNativeCompatibilityChecks(){
        var selected=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,"gcc-clang-x86_64-default-enum-v1").build();
        String source="enum E {n=1};"+BOX+"typedef Box<n> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
        var good=resolve(selected,source);healthy(good);
        assertEquals(BigInteger.valueOf(8),new IntegralConstants(good,selected).evaluate(variable(good,"result").initializer().orElseThrow()).orElseThrow().number());
        for(String policy:List.of("unsigned-first","unknown")){
            var profile=selected.toBuilder().enumUnderlyingType(policy).build();
            var p=resolve(profile,source);assertTrue(p.hasErrors(),policy);
            assertTrue(new IntegralConstants(p,profile).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),policy);
        }
        var bare=EnumDeclaration.create().setName("Foreign").addEnumerator(EnumeratorDeclaration.create().setName("n").setValue(IntegerLiteral.create().setValue(1))).build();
        assertTrue(new IntegralConstants(good,selected).apply(bare.enumerators().getFirst()).isEmpty());
    }

    @Test void laterDefinitionDoesNotRetroactivelyCertifyEarlierExternUse(){
        var p=resolve("extern const int n;"+BOX+"typedef Box<n> Early;const int n=1;unsigned long result=__builtin_offsetof(Early,tail);");
        assertTrue(p.hasErrors());
        assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
    }

    @Test void aliasedNarrowParametersPreserveTypeAndFullSelectionAfterCopiesAndEdits(){
        String source="typedef unsigned short Count;const int count=2;template<Count N> struct Box {char data[sizeof(N)];char tail;};"
                +"template<> struct Box<2> {long data;char tail;};typedef Box<count> Named;typedef Box<2> Literal;"
                +"unsigned long result=__builtin_offsetof(Named,tail);unsigned long literal=__builtin_offsetof(Literal,tail);";
        var original=resolve(source);healthy(original);value(original,"result",8);value(original,"literal",8);
        for(Project p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"result",8);
            var editor=new ProjectEditor(p);editor.replace(variable(p,"count").initializer().orElseThrow(),IntegerLiteral.create().setValue(3).build());
            var edited=NameResolver.resolve(editor.build());healthy(edited);value(edited,"result",2);value(edited,"literal",8);value(p,"result",8);
        }
    }

    @Test void namedAndLiteralFullSpecializationRedeclarationsHaveOneIdentity(){
        var p=resolve("const int value=2;template<unsigned short N> struct Box {char data;char tail;};"
                +"template<> struct Box<value>;template<> struct Box<2> {long data;char tail;};"
                +"typedef Box<value> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
        healthy(p);value(p,"result",8);
    }

    @Test void existingLiteralAndExpressionLocalPartialPatternsRemainSupported(){
        for(String[] row:List.of(new String[]{"2","2"},new String[]{"sizeof(object)","4"})){
            var p=resolve("int object;const int selected="+row[1]+";template<class T,int N> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<T,"+row[0]+"> {long data;char tail;};"
                    +"typedef Box<int,selected> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
            healthy(p);value(p,"result",8);
        }
    }

    @Test void namedPartialPatternsCannotFallThroughToTheWrongPrimary(){
        var p=resolve("const int fixed=1;template<int N,int M> struct Box {char data;char tail;};"
                +"template<int N> struct Box<N,fixed> {long data;char tail;};typedef Box<2,fixed> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
    }

    @Test void convertedPartialPatternValuesCannotFallThroughToTheWrongPrimary(){
        for(String[] row:List.of(new String[]{"bool","2","2"},new String[]{"bool","-1","true"},new String[]{"unsigned char","256","0"})){
            var p=resolve("template<class T,"+row[0]+" N> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<T,"+row[1]+"> {long data;char tail;};"
                    +"typedef Box<int,"+row[2]+"> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
        }
    }

    @Test void referenceAndPointerArgumentsKeepObjectIdentityNotInitializerValues(){
        var original=resolve("extern const int first=1;extern const int second=1;"
                +"template<const int& R> struct Ref {int value;};template<const int* P> struct Ptr {int value;};"
                +"Ref<first> a;Ref<second> b;Ptr<&first> c;");
        for(Project p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()))){
            healthy(p);assertEquals(3,p.classTemplateInstances().size());
            var arguments=p.classTemplateInstances().stream().flatMap(instance->instance.arguments().stream()).map(argument->argument.expression().orElseThrow()).toList();
            assertEquals(2,arguments.stream().filter(NameExpression.class::isInstance).count());
            assertEquals(1,arguments.stream().filter(AddressOfExpression.class::isInstance).count());
            assertTrue(arguments.stream().noneMatch(IntegerLiteral.class::isInstance));
        }
    }

    @Test void existingReferenceAndPointerPartialPatternsKeepIdentityMatching(){
        var p=resolve("extern const int n=1;template<class T,const int& R> struct Ref {int value;};"
                +"template<class T> struct Ref<T,n> {long value;};Ref<int,n> a;"
                +"template<class T,const int* P> struct Ptr {int value;};template<class T> struct Ptr<T,&n> {long value;};Ptr<int,&n> b;");
        healthy(p);assertEquals(2,p.classTemplateInstances().size());
        assertTrue(p.classTemplateInstances().stream().allMatch(instance->instance.selected().templateKind()==TemplateKind.PARTIAL_SPECIALIZATION));
    }

    @Test void precedingTypeArgumentsStillDetermineIntegralParameterType(){
        var p=resolve("template<class T,T N> struct Box {char data[sizeof(N)];char tail;};"
                +"typedef Box<unsigned short,2> Narrow;typedef Box<unsigned long,2> Wide;"
                +"unsigned long narrow=__builtin_offsetof(Narrow,tail);unsigned long wide=__builtin_offsetof(Wide,tail);");
        healthy(p);value(p,"narrow",2);value(p,"wide",8);
    }

    @Test void foldedNamedSizeofDependenciesStillEnforceTheQueryCeiling(){
        var bounded=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        var p=resolve(bounded,"struct Giant {char bytes[16];};const int count=sizeof(Giant)-15;"+BOX+"typedef Box<count> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
        healthy(p);var folded=NameResolver.resolve(ConstantFolder.fold(p));healthy(folded);
        var editor=new ProjectEditor(folded);editor.replace(((ArrayType)variable(folded,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
        var changed=NameResolver.resolve(editor.build());assertTrue(changed.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),changed.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(changed,bounded).evaluate(variable(changed,"result").initializer().orElseThrow()).isEmpty());
    }
}
