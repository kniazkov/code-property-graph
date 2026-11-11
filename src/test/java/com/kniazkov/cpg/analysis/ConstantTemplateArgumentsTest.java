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
        for(String[] row:List.of(new String[]{"int","4294967295UL"},new String[]{"signed char","128"},new String[]{"short","65535"})){
            var p=resolve("const long n="+row[1]+";template<"+row[0]+" N> struct Box {char tail;};typedef Box<n> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        }
    }

    @Test void unsupportedSpecializationCannotSilentlySelectThePrimary(){
        String source="const int outside=128;template<signed char N> struct Box {char data;char tail;};"
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

    @Test void namedPartialPatternsSelectTheSpecialization(){
        var p=resolve("const int fixed=1;template<int N,int M> struct Box {char data;char tail;};"
                +"template<int N> struct Box<N,fixed> {long data;char tail;};typedef Box<2,fixed> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
        healthy(p);value(p,"result",8);
    }

    @Test void namedPartialValuesUseSnapshotOrderingAndRedeclarationIdentity(){
        var p=resolve("namespace api {const int first=2;enum {second=2};}"
                +"template<class T,int N> struct Box {char data;char tail;};"
                +"template<class T> struct Box<T,api::first>;"
                +"template<class U> struct Box<U,api::second> {short data;char tail;};"
                +"template<class T> struct Box<T*,api::first> {long data;char tail;};"
                +"typedef Box<int,2> A;typedef Box<int*,2> B;typedef Box<int,3> C;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
        healthy(p);value(p,"a",2);value(p,"b",8);value(p,"c",1);
    }

    @Test void editsToPartialHeadersReselectAfterFoldingWithoutErasingNames(){
        var original=resolve("const int seed=2;const int fixed=seed;"
                +"template<class T,int N> struct Box {char data;char tail;};"
                +"template<class T> struct Box<T,fixed> {long data;char tail;};"
                +"typedef Box<int,2> A;unsigned long result=__builtin_offsetof(A,tail);");
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"result",8);
            String before=Cpg.sources(p,Language.CPP).get("main.cpp");assertTrue(before.contains("fixed"));
            assertTrue(nodes(variable(p,"fixed").initializer().orElseThrow(),NameExpression.class).stream().anyMatch(n->n.name().equals("seed")), before);
            var editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(3).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",1);value(p,"result",8);
            healthy(resolve(Cpg.sources(changed,Language.CPP).get("main.cpp")));
            assertTrue(ProjectJson.encode(changed).toText("  ").contains("OffsetofExpression"));
        }
    }

    @Test void competingNamedPartialPatternsRemainAmbiguous(){
        var p=resolve("const int first=2;enum {second=2};template<class T,class U,int N> struct Box {char tail;};"
                +"template<class T> struct Box<T,int,first> {char tail;};"
                +"template<class U> struct Box<int,U,second> {char tail;};Box<int,int,2> object;");
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("ambiguous-class-specialization")),p.semanticDiagnostics().toString());
    }

    @Test void uncertifiedNamedPartialValuesNeverFallThrough(){
        for(String declaration:List.of("int fixed=2;","extern const int fixed;","const double fixed=2.0;","const int fixed=fixed;","const volatile int fixed=2;")){
            var p=resolve(declaration+"template<class T,int N> struct Box {char tail;};"
                    +"template<class T> struct Box<T,fixed> {long data;char tail;};Box<int,2> object;");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
        }
        var p=resolve("const int fixed=2;template<class T,T N> struct Box {char tail;};"
                +"template<class T> struct Box<T,fixed> {char tail;};Box<int,2> object;");
        assertTrue(p.hasErrors(),"Dependent parameter type is not certified by this package");
    }

    @Test void partialHeaderEditsCanActivatePreviouslyUnselectedCandidates(){
        var original=resolve("enum {fixed=3};template<class T,int N> struct Box {char data;char tail;};"
                +"template<class T> struct Box<T,fixed> {long data;char tail;};"
                +"typedef Box<int,2> A;unsigned long result=__builtin_offsetof(A,tail);");
        for(var p:List.of(original,NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"result",1);
            var fixed=nodes(p,EnumeratorDeclaration.class).stream().filter(e->e.name().equals("fixed")).findFirst().orElseThrow();
            var editor=new ProjectEditor(p);editor.replace(fixed.value().orElseThrow(),IntegerLiteral.create().setValue(2).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",8);value(p,"result",1);
        }
    }

    @Test void namedNestedPatternsSelectAndOversizedHeaderDependenciesRemainGuarded(){
        var nested=resolve("const int fixed=2;template<class T,int N> struct Key {};template<class T> struct Box {char tail;};"
                +"template<class T> struct Box<Key<T,fixed> > {long data;char tail;};Box<Key<int,2> > object;");
        healthy(nested);
        var limited=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        var p=resolve(limited,"struct Large {char data[32];};const int fixed=sizeof(Large);"
                +"template<class T,int N> struct Box {char tail;};template<class T> struct Box<T,fixed> {char tail;};"
                +"typedef Box<int,1> A;unsigned long result=__builtin_offsetof(A,tail);");
        assertTrue(p.hasErrors());assertTrue(p.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),p.semanticDiagnostics().toString());
    }

    @Test void actualNamedArrayBoundsRemainOutsidePatternNormalization(){
        var p=resolve("const int count=2;template<class T> struct Box {char data;char tail;};"
                +"typedef Box<int[count]> A;unsigned long result=__builtin_offsetof(A,tail);");
        healthy(p);value(p,"result",1);
    }

    @Test void nestedNamedValuesMatchAliasesQualifiedChainsAndPrimaryFallback(){
        String prefix="const int seed=2;namespace api {const int fixed=seed;enum {same=2};}"
                +"template<class X,int M> struct Key;template<class T,int N> struct Key {};template<class T> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Key<T,api::fixed> > {long data;char tail;};";
        var p=resolve(prefix+"typedef Key<int,api::same> Alias;typedef Box<Alias> A;"
                +"typedef Box<Key<int,seed> > B;typedef Box<Key<int,3> > C;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
        healthy(p);value(p,"a",8);value(p,"b",8);value(p,"c",1);
        for(var copy:List.of(p.edit().build(),NameResolver.resolve(p.edit().build()),NameResolver.resolve(ConstantFolder.fold(p)),resolve(Cpg.sources(p,Language.CPP).get("main.cpp")))){
            healthy(copy);value(copy,"a",8);value(copy,"b",8);value(copy,"c",1);
        }
    }

    @Test void nestedValuesOrderAndDeduplicateBoundRenamedParameters(){
        var p=resolve("const int fixed=2;enum {same=2};typedef int Int;"
                +"template<class T,int N> struct Key {};template<class T,class U> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Key<T,fixed>,Int>;"
                +"template<class Renamed> struct Box<Key<Renamed,2>,int> {short data;char tail;};"
                +"template<class U> struct Box<Key<U*,same>,Int> {long data;char tail;};"
                +"template<> struct Box<Key<char*,fixed>,int> {char data[3];char tail;};"
                +"typedef Box<Key<int,2>,int> A;typedef Box<Key<int*,2>,int> B;typedef Box<Key<char*,2>,int> C;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
        healthy(p);value(p,"a",2);value(p,"b",8);value(p,"c",3);
    }

    @Test void nestedValuesRetainNamespaceHeadIdentityAndDeeperWrappers(){
        var p=resolve("const int fixed=2;template<class T> struct Wrap {};namespace left {template<class T,int N> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Wrap<Key<T,fixed> >*> {long data;char tail;};"
                +"typedef Box<Wrap<Key<int,2> >*> A;unsigned long a=__builtin_offsetof(A,tail);}"
                +"namespace right {template<class T,int N> struct Key {};"
                +"typedef left::Box<Wrap<Key<int,2> >*> B;unsigned long b=__builtin_offsetof(B,tail);}");
        healthy(p);value(p,"a",8);value(p,"b",1);
    }

    @Test void repeatedNestedTypeDeductionsCompareOriginalNormalizedBindings(){
        String prefix="const int fixed=2;template<class T,int N> struct Key {};";
        for(String source:List.of(
                "template<class A,class B> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T,fixed>,Key<T,fixed> > {long data;char tail;};"
                    +"typedef Box<Key<Key<int,fixed>,2>,Key<Key<int,2>,2> > A;"
                    +"typedef Box<Key<Key<int,fixed>,2>,Key<Key<int,3>,2> > B;",
                "template<class A,class B,class C> struct Box {char data;char tail;};"
                    +"template<class T,class U> struct Box<T,T,Key<U,fixed> > {long data;char tail;};"
                    +"typedef Box<Key<int,fixed>,Key<int,2>,Key<int,2> > A;"
                    +"typedef Box<Key<int,fixed>,Key<int,3>,Key<int,2> > B;")){
            var original=resolve(prefix+source+"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);");
            for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),NameResolver.resolve(ConstantFolder.fold(original)),resolve(Cpg.sources(original,Language.CPP).get("main.cpp")))){
                healthy(p);value(p,"a",8);value(p,"b",1);
            }
        }
    }

    @Test void repeatedNestedTypesRetainCanonicalCvAndFunctionParameterIdentities(){
        for(String[] row:List.of(new String[]{"typedef const int CI;", "const CI", "const int"},
                new String[]{"typedef void Plain(int);typedef void Qualified(const int);", "Plain", "Qualified"})){
            var p=resolve("const int fixed=2;"+row[0]+"template<class T,int N> struct Key {};template<class A,class B> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T,fixed>,Key<T,fixed> > {long data;char tail;};"
                    +"typedef Box<Key<"+row[1]+",2>,Key<"+row[2]+",2> > A;unsigned long result=__builtin_offsetof(A,tail);");
            healthy(p);value(p,"result",8);
        }
    }

    @Test void nestedValuesUseBoolModuloAndOwnConcretePrecedingTypes(){
        for(String[] row:List.of(new String[]{"bool","-7","true"},new String[]{"unsigned char","258","2"},new String[]{"unsigned short","-1","65535"})){
            var p=resolve("const int fixed="+row[1]+";typedef "+row[0]+" Value;"
                    +"template<class T,class V,V N> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T,Value,fixed> >;"
                    +"template<class U> struct Box<Key<U,"+row[0]+","+row[2]+"> > {long data;char tail;};"
                    +"typedef Box<Key<int,Value,"+row[2]+"> > A;unsigned long result=__builtin_offsetof(A,tail);");
            healthy(p);value(p,"result",8);
        }
    }

    @Test void nestedHeaderEditsActivateAndDeactivateUnselectedPatterns(){
        String source="const int seed=3;const int fixed=seed;template<class T,int N> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T,fixed> > {long data;char tail;};"
                +"typedef Box<Key<int,2> > A;unsigned long result=__builtin_offsetof(A,tail);";
        var original=resolve(source);
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"result",1);
            assertTrue(nodes(variable(p,"fixed").initializer().orElseThrow(),NameExpression.class).stream().anyMatch(n->n.name().equals("seed")));
            var editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(2).build());
            var selected=NameResolver.resolve(editor.build());healthy(selected);value(selected,"result",8);value(p,"result",1);
            editor=new ProjectEditor(selected);editor.replace(variable(selected,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(4).build());
            var deselected=NameResolver.resolve(editor.build());healthy(deselected);value(deselected,"result",1);value(selected,"result",8);
            healthy(resolve(Cpg.sources(selected,Language.CPP).get("main.cpp")));
            assertTrue(ProjectJson.encode(selected).toText("  ").contains("OffsetofExpression"));
        }
    }

    @Test void nestedAmbiguityRemainsDiagnosed(){
        var p=resolve("const int fixed=2;template<class T,int N> struct Key {};template<class T,class U> struct Box {char tail;};"
                +"template<class T> struct Box<Key<T,fixed>,int> {char tail;};"
                +"template<class U> struct Box<Key<int,2>,U> {char tail;};Box<Key<int,2>,int> object;");
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("ambiguous-class-specialization")),p.semanticDiagnostics().toString());
    }

    @Test void uncertifiedNestedValuesNeverSelectPrimary(){
        for(String declaration:List.of("int fixed=2;","extern const int fixed;","const int fixed=fixed;","const volatile int fixed=2;","int f();const int fixed=f();")){
            var p=resolve(declaration+"template<class T,int N> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T,fixed> > {long data;char tail;};"
                    +"typedef Box<Key<int,3> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        }
        for(String key:List.of("template<class T,T N> struct Key {};","template<class T,signed char N> struct Key {};")){
            var p=resolve("const int fixed=128;"+key+"template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T,fixed> > {long data;char tail;};"
                    +"typedef Box<Key<int,0> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors());assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        }
    }

    @Test void omittedNestedActualDefaultsSelectThePartialAndShareExplicitIdentity(){
        var original=resolve("const int fixed=2;template<class T,int N=2> struct Key {};template<class T> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Key<T,fixed> > {long data;char tail;};"
                +"typedef Box<Key<int> > A;typedef Box<Key<int,2> > B;typedef Box<Key<int,3> > C;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"a",8);value(p,"b",8);value(p,"c",1);
            var outer=p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).toList();
            assertEquals(2,outer.size(),"Omitted and explicit defaults must not create duplicate outer instances");
            assertEquals(1,outer.stream().filter(i->i.selected().templateKind()==TemplateKind.PARTIAL_SPECIALIZATION).count());
        }
        var reparsed=resolve(Cpg.sources(original,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"a",8);value(reparsed,"b",8);value(reparsed,"c",1);
    }

    @Test void nestedActualDefaultsUseLiteralNamedEnumAndConvertedValues(){
        for(String[] row:List.of(new String[]{"int","2","2"},new String[]{"int","fixed","2"},
                new String[]{"int","enumerated","2"},new String[]{"bool","-7","true"},
                new String[]{"unsigned char","258","2"},new String[]{"signed char","127","127"})){
            var p=resolve("const int seed=2;const int fixed=seed;enum {enumerated=2};"
                    +"template<class T,"+row[0]+" N="+row[1]+"> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T,"+row[2]+"> > {long data;char tail;};"
                    +"typedef Box<Key<int> > A;unsigned long result=__builtin_offsetof(A,tail);");
            healthy(p);value(p,"result",8);
        }
        var p=resolve("const int fixed=258;template<class T,class V,V N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Key<T,unsigned char,2> > {long data;char tail;};"
                +"typedef Box<Key<int,unsigned char> > A;unsigned long result=__builtin_offsetof(A,tail);");
        healthy(p);value(p,"result",8);
    }

    @Test void nestedActualDefaultEditsReselectWithoutMutatingSnapshotsOrWrittenSyntax(){
        var original=resolve("const int seed=2;const int fixed=seed;template<class T,int N=fixed> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T,2> > {long data;char tail;};"
                +"typedef Box<Key<int> > A;unsigned long result=__builtin_offsetof(A,tail);");
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))))){
            healthy(p);value(p,"result",8);
            String before=Cpg.sources(p,Language.CPP).get("main.cpp"),json=ProjectJson.encode(p).toText("  ");
            assertTrue(before.contains("fixed"));
            assertTrue(nodes(variable(p,"fixed").initializer().orElseThrow(),NameExpression.class).stream().anyMatch(n->n.name().equals("seed")));
            var editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(3).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",1);
            editor=new ProjectEditor(changed);editor.replace(variable(changed,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(2).build());
            var restored=NameResolver.resolve(editor.build());healthy(restored);value(restored,"result",8);
            value(p,"result",8);value(changed,"result",1);
            assertEquals(before,Cpg.sources(p,Language.CPP).get("main.cpp"));assertEquals(json,ProjectJson.encode(p).toText("  "));
            var reparsed=resolve(Cpg.sources(changed,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",1);
        }
    }

    @Test void nestedActualDefaultsShareRepeatedDeductionButNotUnequalValuesOrNamespaceHeads(){
        String prefix="template<class T,int N=2> struct Key {};namespace other {template<class T,int N=2> struct Key {};}"
                +"template<class A,class B,class C> struct Box {char data;char tail;};"
                +"template<class T,class U> struct Box<T,T,Key<U,2> > {long data;char tail;};";
        var original=resolve(prefix+"typedef Box<Key<int>,Key<int,2>,Key<int> > A;"
                +"typedef Box<Key<int>,Key<int,3>,Key<int> > B;typedef Box<Key<int>,other::Key<int>,Key<int> > C;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"a",8);value(p,"b",1);value(p,"c",1);
        }
    }

    @Test void uncertifiedNestedActualDefaultsNeverFallThroughToPrimaryLayout(){
        for(String declaration:List.of("int fixed=2;","extern const int fixed;","const int fixed=fixed;","const volatile int fixed=2;","int f();const int fixed=f();","const double fixed=2.0;")){
            var p=resolve(declaration+"template<class T,int N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T,2> > {long data;char tail;};typedef Box<Key<int> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors(),declaration);
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),declaration);
        }
        for(String key:List.of("template<class T,signed char N=128> struct Key {};",
                "template<class T,int N=sizeof(T)> struct Key {};")){
            String pattern=key.contains("class U")?"Key<T,int>":"Key<T,2>";
            var p=resolve(key+"template<class T> struct Box {char data;char tail;};template<class T> struct Box<"+pattern+" > {long data;char tail;};"
                    +"typedef Box<Key<int> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),key);
        }
    }

    @Test void nestedActualDefaultsDoNotBorrowLaterOrMergedHeaders(){
        String box="template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T,2> > {long data;char tail;};";
        for(String source:List.of(
                "template<class T,int N> struct Key;"+box+"typedef Box<Key<int> > A;template<class T,int N=2> struct Key {};",
                "template<class T,int N> struct Key {};"+box+"typedef Box<Key<int> > A;template<class T,int N=2> struct Key;",
                "template<class T,int N=2> struct Key;template<class T,int N> struct Key {};"+box+"typedef Box<Key<int> > A;",
                "template<class T,int N=fixed> struct Key {};const int fixed=2;"+box+"typedef Box<Key<int> > A;")){
            var p=resolve(source+"unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors(),source);
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),source);
        }
    }

    @Test void unspecializedFamiliesRetainLegacyNestedTypeDefaults(){
        String key="template<class T,class U=int> struct Key {};";
        var plain=resolve(key+"template<class T> struct Box {char data;char tail;};"
                +"typedef Box<Key<int> > A;unsigned long result=__builtin_offsetof(A,tail);");
        healthy(plain);value(plain,"result",1);
        for(String specialization:List.of("template<class T> struct Box<Key<T,int> > {long data;char tail;};",
                "template<> struct Box<Key<int,int> > {long data;char tail;};")){
            var p=resolve(key+"template<class T> struct Box {char data;char tail;};"+specialization
                    +"typedef Box<Key<int> > A;unsigned long result=__builtin_offsetof(A,tail);");
            healthy(p);value(p,"result",8);
        }
    }

    @Test void fullSpecializationNestedDefaultsRetainSelectedAndUnselectedHeaderDependencies(){
        var bounded=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        for(String actual:List.of("true","false")){
            var original=resolve(bounded,"struct Giant {char bytes[16];};const int seed=sizeof(Giant);const int fixed=seed;"
                    +"template<class T,bool N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<> struct Box<Key<int> > {long data;char tail;};typedef Box<Key<int,"+actual+"> > A;unsigned long result=__builtin_offsetof(A,tail);");
            healthy(original);
            for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))))){
                assertEquals(BigInteger.valueOf(actual.equals("true")?8:1),new IntegralConstants(p,bounded).evaluate(variable(p,"result").initializer().orElseThrow()).orElseThrow().number());
                String before=Cpg.sources(p,Language.CPP).get("main.cpp"),json=ProjectJson.encode(p).toText("  ");
                var editor=new ProjectEditor(p);editor.replace(((ArrayType)variable(p,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
                var changed=NameResolver.resolve(editor.build());
                assertTrue(changed.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),changed.semanticDiagnostics().toString());
                assertTrue(new IntegralConstants(changed,bounded).evaluate(variable(changed,"result").initializer().orElseThrow()).isEmpty());
                assertThrows(IllegalArgumentException.class,()->Cpg.sources(changed,Language.CPP));
                assertEquals(before,Cpg.sources(p,Language.CPP).get("main.cpp"));assertEquals(json,ProjectJson.encode(p).toText("  "));
            }
        }
    }

    @Test void multipleNestedDefaultsKeepPerUseConversionsIndependentOfRequestOrder(){
        String prefix="const int fixed=258;template<class T,class V,V N=fixed,int M=2> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Key<T,unsigned char,2,2> > {long data;char tail;};"
                +"template<class T> struct Box<Key<T,int,258,2> > {short data;char tail;};";
        String omitted="typedef Box<Key<int,unsigned char> > A;typedef Box<Key<int,int> > B;";
        String explicit="typedef Box<Key<int,unsigned char,2,2> > C;typedef Box<Key<int,int,258,2> > D;";
        for(String declarations:List.of(omitted+explicit,explicit+omitted)){
            var original=resolve(prefix+declarations+"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);"
                    +"unsigned long c=__builtin_offsetof(C,tail);unsigned long d=__builtin_offsetof(D,tail);");
            for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)))){
                healthy(p);value(p,"a",8);value(p,"b",2);value(p,"c",8);value(p,"d",2);
                assertEquals(2,p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).count());
            }
            var reparsed=resolve(Cpg.sources(original,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"a",8);value(reparsed,"b",2);value(reparsed,"c",8);value(reparsed,"d",2);
        }
    }

    @Test void builtinTypeDefaultsShareActualAndPatternIdentityWithoutRewritingSyntax(){
        for(String pattern:List.of("Key<T>","Key<T,unsigned char>","Key<T,unsigned char,2>")){
            var original=resolve("template<class T,class V=unsigned char,V N=258> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<"+pattern+" > {long data;char tail;};"
                    +"typedef Box<Key<int> > A;typedef Box<Key<int,unsigned char> > B;typedef Box<Key<int,unsigned char,2> > C;"
                    +"typedef Box<Key<int,int,2> > D;typedef Box<Key<int,unsigned char,3> > E;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);"
                    +"unsigned long d=__builtin_offsetof(D,tail);unsigned long e=__builtin_offsetof(E,tail);");
            String source=Cpg.sources(original,Language.CPP).get("main.cpp"),json=ProjectJson.encode(original).toText("  ");
            for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))),resolve(source))){
                healthy(p);value(p,"a",8);value(p,"b",8);value(p,"c",8);value(p,"d",1);value(p,"e",1);
                assertEquals(3,p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).count());
                var partial=nodes(p.files().getFirst(),ClassDeclaration.class).stream().filter(c->c.templateKind()==TemplateKind.PARTIAL_SPECIALIZATION).findFirst().orElseThrow();
                var key=nodes(partial.templateArguments().getFirst(),NamedType.class).stream().filter(n->n.name().equals("Key")).findFirst().orElseThrow();
                assertEquals(pattern.split(",").length,key.templateArguments().size());
            }
            assertEquals(source,Cpg.sources(original,Language.CPP).get("main.cpp"));assertEquals(json,ProjectJson.encode(original).toText("  "));
        }
    }

    @Test void builtinTypeDefaultsPreserveCvAndAllIntegralKinds(){
        for(String type:List.of("bool","char","signed char","unsigned char","wchar_t","short","unsigned short","int","unsigned int","long","unsigned long","const int","volatile int","const volatile int")){
            var p=resolve("template<class T,class V="+type+"> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};"
                    +"typedef Box<Key<char> > A;typedef Box<Key<char,"+type+"> > B;typedef Box<Key<char,"+(type.contains("const")||type.contains("volatile")?"int":"double")+"> > C;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
            healthy(p);value(p,"a",8);value(p,"b",8);value(p,"c",1);
            assertEquals(2,p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).count());

        }
    }

    @Test void builtinTypeDefaultsConvertValuesAndRespectTargetWidths(){
        for(String[] row:List.of(new String[]{"bool","-7","true"},new String[]{"unsigned char","258","2"},
                new String[]{"signed char","127","127"},new String[]{"const int","-2","-2"})){
            var p=resolve("template<class T,class V="+row[0]+",V N="+row[1]+"> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};typedef Box<Key<int,"+row[0]+","+row[2]+"> > A;unsigned long result=__builtin_offsetof(A,tail);");
            healthy(p);value(p,"result",8);
        }
        var narrow=PlatformProfile.builder().sizeBits("long",32).alignmentBits("long",32).build();
        String source="template<class T,class V=unsigned long,V N=4294967295UL+2UL> struct Key {};template<class T> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Key<T> > {short data;char tail;};typedef Box<Key<int,unsigned long,1> > A;";
        // A symbolic 32-bit-long target is not an LP64 layout/native oracle.
        for(var profile:List.of(PROFILE,narrow)){
            var p=resolve(profile,source);healthy(p);
            var outer=p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).findFirst().orElseThrow();
            assertEquals(profile==narrow?TemplateKind.PARTIAL_SPECIALIZATION:TemplateKind.PRIMARY,outer.selected().templateKind());
        }
    }

    @Test void builtinTypeAndValueDefaultsKeepSharedNodesIndependentInOneUse(){
        for(boolean reversed:List.of(false,true)){
            String left=reversed?"int":"unsigned char",right=reversed?"unsigned char":"int";
            String lv=reversed?"258":"2",rv=reversed?"2":"258";
            String omittedLeft=reversed?",int":"",omittedRight=reversed?"":",int";
            var original=resolve("const int fixed=258;template<class T,class V=unsigned char,V N=fixed,bool B=7,class W=int,int M=2> struct Key {};"
                    +"template<class A,class B> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T"+omittedLeft+">,Key<T"+omittedRight+"> > {long data;char tail;};"
                    +"typedef Box<Key<int,"+left+","+lv+",true,int,2>,Key<int,"+right+","+rv+",true,int,2> > A;"
                    +"typedef Box<Key<int"+omittedLeft+">,Key<int"+omittedRight+"> > B;"
                    +"typedef Box<Key<int,"+left+",3>,Key<int"+omittedRight+"> > C;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
            for(var p:List.of(original,NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(original)),resolve(Cpg.sources(original,Language.CPP).get("main.cpp")))){
                healthy(p);value(p,"a",8);value(p,"b",8);value(p,"c",1);
                assertEquals(2,p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).count());
            }
        }
    }

    @Test void builtinTypeDefaultEditsAndConstChainEditsReselectWithoutMutatingSnapshots(){
        var original=resolve("const int seed=258;const int fixed=seed;template<class T,class V=unsigned char,V N=fixed> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};"
                +"typedef Box<Key<int,unsigned char,2> > A;unsigned long result=__builtin_offsetof(A,tail);");
        for(var p:List.of(original,NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))))){
            healthy(p);value(p,"result",8);
            String source=Cpg.sources(p,Language.CPP).get("main.cpp"),json=ProjectJson.encode(p).toText("  ");
            var v=nodes(p.files().getFirst(),TemplateParameterDeclaration.class).stream().filter(t->t.name().equals("V")).findFirst().orElseThrow();
            var editor=new ProjectEditor(p);editor.replace(v.defaultArgument().orElseThrow().type().orElseThrow(),NamedType.create().setName("int").build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",1);
            editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(259).build());
            var changedValue=NameResolver.resolve(editor.build());healthy(changedValue);value(changedValue,"result",1);
            for(var edited:List.of(changed,changedValue)){var reparsed=resolve(Cpg.sources(edited,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",1);}
            value(p,"result",8);assertEquals(source,Cpg.sources(p,Language.CPP).get("main.cpp"));assertEquals(json,ProjectJson.encode(p).toText("  "));
        }
    }

    @Test void unsupportedTypeDefaultsRemainGuardedInActualAndPartialHeads(){
        for(String type:List.of("T","Alias","E","Object","int*","int&","double"))for(boolean omittedPattern:List.of(false,true)){
            String explicit=type.equals("T")?"int":type;
            var p=resolve("typedef int Alias;enum E {one=1};struct Object {};template<class T,class V="+type+"> struct Key {};"
                    +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T"+(omittedPattern?"":","+explicit)+"> > {long data;char tail;};"
                    +"typedef Box<Key<int"+(omittedPattern?","+explicit:"")+"> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors(),type+" "+omittedPattern);
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),type);
        }
        for(String parameters:List.of("class V=T,V N=2","class V=int,V N=sizeof(T)","class V=int,V N=sizeof(V)","int M=2,class V=int,V N=M","class V=signed char,V N=128")){
            var p=resolve("template<class T,"+parameters+"> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};typedef Box<Key<int> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors(),parameters);assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),parameters);
        }
    }

    @Test void builtinTypeDefaultsShareOrderingRedeclarationsAndAmbiguity(){
        String prefix="template<class T,class V=unsigned char,V N=258> struct Key {};template<class A,class B> struct Box {char data;char tail;};";
        var p=resolve(prefix+"template<class T,class U> struct Box<Key<T>,U> {short data;char tail;};"
                +"template<class X,class Y> struct Box<Key<X,unsigned char,2>,Y>;"
                +"template<class T,class U> struct Box<Key<T*>,U> {long data;char tail;};"
                +"typedef Box<Key<int*>,char> A;typedef Box<Key<int,unsigned char,2>,char> B;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);");
        healthy(p);value(p,"a",8);value(p,"b",2);
        var ambiguous=resolve(prefix+"template<class T,class U> struct Box<Key<T>,U> {short data;char tail;};"
                +"template<class T,class U> struct Box<T,Key<U> > {long data;char tail;};"
                +"typedef Box<Key<int>,Key<char> > A;unsigned long result=__builtin_offsetof(A,tail);");
        assertTrue(ambiguous.semanticDiagnostics().stream().anyMatch(d->d.code().contains("ambiguous")),ambiguous.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(ambiguous,PROFILE).evaluate(variable(ambiguous,"result").initializer().orElseThrow()).isEmpty());
    }

    @Test void builtinTypeDefaultsRespectNamespaceHeadsAndRepeatedDeductions(){
        var p=resolve("namespace other {template<class T,class V=int> struct Key {};}namespace api {template<class T,class V=int> struct Key {};"
                +"template<class A,class B,class C> struct Box {char data;char tail;};"
                +"template<class T,class U> struct Box<T,T,Key<U> > {long data;char tail;};}"
                +"typedef api::Box<api::Key<int>,api::Key<int,int>,api::Key<char> > A;"
                +"typedef api::Box<api::Key<int>,api::Key<int,const int>,api::Key<char> > B;"
                +"typedef api::Box<api::Key<int>,other::Key<int>,api::Key<char> > C;"
                +"typedef api::Box<api::Key<int>,api::Key<int>,other::Key<char> > D;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);unsigned long d=__builtin_offsetof(D,tail);");
        for(var copy:List.of(p,NameResolver.resolve(p.edit().build()),TypeNormalizer.green(p),NameResolver.resolve(ConstantFolder.fold(p)),resolve(Cpg.sources(p,Language.CPP).get("main.cpp")))){
            healthy(copy);value(copy,"a",8);value(copy,"b",1);value(copy,"c",1);value(copy,"d",1);
        }
    }

    @Test void builtinTypeDefaultsKeepOwnHeaderVisibility(){
        String box="template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};";
        for(String source:List.of("template<class T,class V> struct Key {};"+box+"template<class T,class V=int> struct Key;",
                "template<class T,class V> struct Key;"+box+"template<class T,class V=int> struct Key {};",
                "template<class T,class V=int> struct Key;template<class T,class V> struct Key {};"+box)){
            var p=resolve(source+"typedef Box<Key<int,int> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors(),source);assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        }
    }

    @Test void builtinTypeDefaultsRetainSelectedAndUnselectedHiddenLayoutDependencies(){
        var bounded=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        for(String type:List.of("bool","unsigned char"))for(boolean selected:List.of(false,true)){
            String actual=type.equals("bool")?(selected?"true":"false"):(selected?"0":"1");
            var original=resolve(bounded,"struct Giant {char bytes[16];};const int seed=sizeof(Giant)*16;const int fixed=seed;"
                    +"template<class T,class V="+type+",V N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};typedef Box<Key<int,"+type+","+actual+"> > A;unsigned long result=__builtin_offsetof(A,tail);");
            for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))))){
                healthy(p);assertEquals(BigInteger.valueOf(selected?8:1),new IntegralConstants(p,bounded).evaluate(variable(p,"result").initializer().orElseThrow()).orElseThrow().number());
                String before=Cpg.sources(p,Language.CPP).get("main.cpp"),json=ProjectJson.encode(p).toText("  ");
                var editor=new ProjectEditor(p);editor.replace(((ArrayType)variable(p,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
                var changed=NameResolver.resolve(editor.build());
                assertTrue(changed.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),changed.semanticDiagnostics().toString());
                assertTrue(new IntegralConstants(changed,bounded).evaluate(variable(changed,"result").initializer().orElseThrow()).isEmpty());
                assertThrows(IllegalArgumentException.class,()->Cpg.sources(changed,Language.CPP));
                assertEquals(before,Cpg.sources(p,Language.CPP).get("main.cpp"));assertEquals(json,ProjectJson.encode(p).toText("  "));
            }
        }
    }

    @Test void builtinTypeDefaultsRetainSelectedAndUnselectedEnumPolicyGuards(){
        for(String actual:List.of("1","2")){
            String source="enum E {enumerated=1};const int seed=+enumerated+256;const int fixed=seed;"
                    +"template<class T,class V=unsigned char,V N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};typedef Box<Key<int,unsigned char,"+actual+"> > A;unsigned long result=__builtin_offsetof(A,tail);";
            var good=resolve(EnumStorageLayoutTest.P,source);healthy(good);
            assertEquals(BigInteger.valueOf(actual.equals("1")?8:1),new IntegralConstants(good,EnumStorageLayoutTest.P).evaluate(variable(good,"result").initializer().orElseThrow()).orElseThrow().number());
            var incompatible=EnumStorageLayoutTest.P.toBuilder().enumUnderlyingType("unsigned-first").build();
            for(String text:List.of(source,Cpg.sources(NameResolver.resolve(ConstantFolder.fold(good)),Language.CPP).get("main.cpp"))){
                var bad=resolve(incompatible,text);
                assertTrue(bad.semanticDiagnostics().toString().contains("UNSUPPORTED_ENUM_SEMANTICS"),bad.semanticDiagnostics().toString());
                assertTrue(new IntegralConstants(bad,incompatible).evaluate(variable(bad,"result").initializer().orElseThrow()).isEmpty());
                assertThrows(IllegalArgumentException.class,()->Cpg.sources(bad,Language.CPP));
            }
        }
    }

    @Test void nestedDefaultsCompleteOmittedPartialPatternsWithoutChangingWrittenSyntax(){
        for(String pattern:List.of("Key<T>","Key<T,2>")){
            var original=resolve("template<class T,int N=2> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<"+pattern+" > {long data;char tail;};"
                    +"typedef Box<Key<int> > A;typedef Box<Key<int,2> > B;typedef Box<Key<int,3> > C;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
            String source=Cpg.sources(original,Language.CPP).get("main.cpp"),json=ProjectJson.encode(original).toText("  ");
            for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))),resolve(source))){
                healthy(p);value(p,"a",8);value(p,"b",8);value(p,"c",1);
                assertEquals(2,p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).count());
                var partial=nodes(p.files().getFirst(),ClassDeclaration.class).stream().filter(c->c.templateKind()==TemplateKind.PARTIAL_SPECIALIZATION).findFirst().orElseThrow();
                var key=nodes(partial.templateArguments().getFirst(),NamedType.class).stream().filter(n->n.name().equals("Key")).findFirst().orElseThrow();
                assertEquals(pattern.equals("Key<T>")?1:2,key.templateArguments().size());
            }
            assertEquals(source,Cpg.sources(original,Language.CPP).get("main.cpp"));assertEquals(json,ProjectJson.encode(original).toText("  "));
        }
    }

    @Test void omittedPartialPatternDefaultsConvertOriginalLiteralNamedAndEnumFacts(){
        for(String[] row:List.of(new String[]{"int","2","2","3"},new String[]{"int","fixed","2","3"},
                new String[]{"int","enumerated","2","3"},new String[]{"bool","-7","true","false"},
                new String[]{"unsigned char","258","2","3"},new String[]{"signed char","127","127","126"})){
            var p=resolve("const int seed=2;const int fixed=seed;enum {enumerated=2};"
                    +"template<class T,"+row[0]+" N="+row[1]+"> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};"
                    +"typedef Box<Key<int,"+row[2]+"> > A;typedef Box<Key<int,"+row[3]+"> > B;typedef Box<Key<int> > C;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
            healthy(p);value(p,"a",8);value(p,"b",1);value(p,"c",8);
        }
        var p=resolve("template<class T,int N,int M=0> struct Key {};template<class T> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Key<T,128> > {long data;char tail;};typedef Box<Key<int,128,0> > A;unsigned long result=__builtin_offsetof(A,tail);");
        healthy(p);value(p,"result",8);
    }

    @Test void omittedPartialPatternsShareOrderingAndAlphaEquivalentRedeclarations(){
        for(boolean reversed:List.of(false,true)){
            String forward=reversed?"Key<T,2>":"Key<T>",definition=reversed?"Key<Renamed>":"Key<Renamed,2>";
            var p=resolve("template<class T,int N=2> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<"+forward+" >;template<class Renamed> struct Box<"+definition+" > {short data;char tail;};"
                    +"template<class U> struct Box<Key<U*> > {long data;char tail;};"
                    +"typedef Box<Key<int> > A;typedef Box<Key<int*,2> > B;typedef Box<Key<int,3> > C;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
            healthy(p);value(p,"a",2);value(p,"b",8);value(p,"c",1);
            healthy(NameResolver.resolve(p.edit().build()));healthy(resolve(Cpg.sources(p,Language.CPP).get("main.cpp")));
        }
        var ambiguous=resolve("template<class T,int N=2> struct Key {};template<class T,class U> struct Box {};"
                +"template<class T> struct Box<Key<T>,int> {};template<class U> struct Box<Key<int,2>,U> {};Box<Key<int>,int> object;");
        assertTrue(ambiguous.semanticDiagnostics().stream().anyMatch(d->d.code().equals("ambiguous-class-specialization")),ambiguous.semanticDiagnostics().toString());
    }

    @Test void omittedPartialPatternsKeepNamespaceDeepNestingAndRepeatedDeduction(){
        var p=resolve("namespace api {template<class T,int N=2> struct Key {};"
                +"template<class T,int N=3> struct Wrap {};template<class A,class B,class C> struct Box {char data;char tail;};"
                +"template<class T,class U> struct Box<T,T,Wrap<Key<U> > > {long data;char tail;};}namespace other {template<class T,int N=2> struct Key {};}"
                +"typedef api::Box<api::Key<int>,api::Key<int,2>,api::Wrap<api::Key<char,2>,3> > A;"
                +"typedef api::Box<api::Key<int>,api::Key<int,3>,api::Wrap<api::Key<char> > > B;"
                +"typedef api::Box<api::Key<int>,other::Key<int>,api::Wrap<api::Key<char> > > C;"
                +"typedef api::Box<api::Key<int>,api::Key<int>,api::Wrap<other::Key<char> > > D;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);unsigned long d=__builtin_offsetof(D,tail);");
        for(var copy:List.of(p,p.edit().build(),NameResolver.resolve(p.edit().build()),NameResolver.resolve(ConstantFolder.fold(p)),resolve(Cpg.sources(p,Language.CPP).get("main.cpp")))){
            healthy(copy);value(copy,"a",8);value(copy,"b",1);value(copy,"c",1);value(copy,"d",1);
        }
    }

    @Test void omittedPartialDefaultsUseOwnExplicitTypesWithoutCrossOccurrenceContamination(){
        for(boolean reversed:List.of(false,true)){
            String left=reversed?"int":"unsigned char",right=reversed?"unsigned char":"int";
            String lv=reversed?"258":"2",rv=reversed?"2":"258";
            var p=resolve("const int fixed=258;template<class T,class V,V N=fixed,bool B=7,int M=2> struct Key {};"
                    +"template<class A,class B> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T,"+left+">,Key<T,"+right+"> > {long data;char tail;};"
                    +"typedef Box<Key<int,"+left+","+lv+",true,2>,Key<int,"+right+","+rv+",true,2> > A;"
                    +"typedef Box<Key<int,"+left+">,Key<int,"+right+"> > B;"
                    +"typedef Box<Key<int,"+left+",3,true,2>,Key<int,"+right+","+rv+",true,2> > C;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
            for(var copy:List.of(p,p.edit().build(),NameResolver.resolve(p.edit().build()),NameResolver.resolve(ConstantFolder.fold(p)),resolve(Cpg.sources(p,Language.CPP).get("main.cpp")))){
                healthy(copy);value(copy,"a",8);value(copy,"b",8);value(copy,"c",1);
                assertEquals(2,copy.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).count());
            }
        }
    }

    @Test void omittedPartialDefaultsUsePatternDeclarationVisibilityNotLaterActualUse(){
        String box="template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};";
        for(String source:List.of(
                "template<class T,int N> struct Key {};"+box+"template<class T,int N=2> struct Key;",
                "template<class T,int N> struct Key;"+box+"template<class T,int N=2> struct Key {};",
                "template<class T,int N=2> struct Key;template<class T,int N> struct Key {};"+box,
                "template<class T,int N=fixed> struct Key {};"+box+"const int fixed=2;")){
            var p=resolve(source+"typedef Box<Key<int,2> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors(),source);assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),source);
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
        }
    }

    @Test void unsupportedOmittedPartialDefaultsCannotSilentlySelectPrimary(){
        for(String key:List.of("template<class T,T N=2> struct Key {};","template<class T,int N=sizeof(T)> struct Key {};",
                "template<class T,signed char N=128> struct Key {};",
                "template<class U> struct Other {};template<class T,template<class> class C=Other> struct Key {};")){
            String actual=key.contains("class V")?"Key<int,int>":key.contains("class C=Other")?"Key<int,Other>":"Key<int,2>";
            var p=resolve(key+"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};"
                    +"typedef Box<"+actual+" > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),key);
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
        }
        for(String declaration:List.of("int fixed=2;","extern const int fixed;","const volatile int fixed=2;","const int fixed=fixed;","const double fixed=2.0;")){
            var p=resolve(declaration+"template<class T,int N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};typedef Box<Key<int,3> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors(),declaration);assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),declaration);
        }
        var invalidParameter=assertThrows(com.kniazkov.cpg.parser.ParseException.class,()->resolve("template<class T,int N=2> struct Key {};template<class T> struct Box {};template<class T=int> struct Box<Key<T> > {};Box<Key<int> > object;"));
        assertTrue(invalidParameter.getMessage().contains("Specialization template parameters cannot have defaults"));
    }

    @Test void omittedTemplateTemplatePatternDefaultsRemainGuarded(){
        var p=resolve("template<class T,int N=2> struct Key {};template<class T> struct Box {char data;char tail;};"
                +"template<template<class,int=2> class C,class T> struct Box<C<T> > {long data;char tail;};"
                +"typedef Box<Key<int,2> > A;unsigned long result=__builtin_offsetof(A,tail);");
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
    }

    @Test void symbolicTemplateHeadsCannotHideOmittedNestedPatternDefaults(){
        String prefix="template<class T,int N=2> struct Key {};template<class T> struct Wrap {};"
                +"template<class T> struct Box {char data;char tail;};";
        for(String wrapper:List.of("","*")){
            String use="typedef Box<Wrap<Key<int,2>"+wrapper+" > > A;unsigned long result=__builtin_offsetof(A,tail);";
            var unsupported=resolve(prefix+"template<template<class> class H,class T> struct Box<H<Key<T>"+wrapper+" > > {long data;char tail;};"+use);
            assertTrue(unsupported.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),unsupported.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(unsupported,PROFILE).evaluate(variable(unsupported,"result").initializer().orElseThrow()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(unsupported,Language.CPP));
        }
        var complete=resolve(prefix+"template<template<class> class H,class T> struct Box<H<T> > {long data;char tail;};"
                +"typedef Box<Wrap<int> > A;unsigned long result=__builtin_offsetof(A,tail);");
        healthy(complete);value(complete,"result",8);
        healthy(NameResolver.resolve(complete.edit().build()));
    }

    @Test void omittedPartialDefaultEditsReselectAndPreserveImmutableSnapshots(){
        var original=resolve("const int seed=2;const int fixed=seed;template<class T,int N=fixed> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};"
                +"typedef Box<Key<int,2> > A;unsigned long result=__builtin_offsetof(A,tail);");
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))))){
            healthy(p);value(p,"result",8);
            String before=Cpg.sources(p,Language.CPP).get("main.cpp"),json=ProjectJson.encode(p).toText("  ");
            assertTrue(nodes(variable(p,"fixed").initializer().orElseThrow(),NameExpression.class).stream().anyMatch(n->n.name().equals("seed")));
            var editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(3).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",1);
            editor=new ProjectEditor(changed);editor.replace(variable(changed,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(2).build());
            var restored=NameResolver.resolve(editor.build());healthy(restored);value(restored,"result",8);value(changed,"result",1);value(p,"result",8);
            assertEquals(before,Cpg.sources(p,Language.CPP).get("main.cpp"));assertEquals(json,ProjectJson.encode(p).toText("  "));
            var reparsed=resolve(Cpg.sources(changed,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",1);
        }
    }

    @Test void omittedPartialDefaultsRetainSelectedAndUnselectedHiddenLayoutDependencies(){
        var bounded=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        for(String type:List.of("bool","unsigned char"))for(boolean selected:List.of(false,true)){
            String actual=type.equals("bool")?(selected?"true":"false"):(selected?"0":"1");
            var original=resolve(bounded,"struct Giant {char bytes[16];};const int seed=sizeof(Giant)*16;const int fixed=seed;"
                    +"template<class T,"+type+" N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};typedef Box<Key<int,"+actual+"> > A;unsigned long result=__builtin_offsetof(A,tail);");
            for(var p:List.of(original,original.edit().build(),NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))))){
                healthy(p);assertEquals(BigInteger.valueOf(selected?8:1),new IntegralConstants(p,bounded).evaluate(variable(p,"result").initializer().orElseThrow()).orElseThrow().number());
                String before=Cpg.sources(p,Language.CPP).get("main.cpp"),json=ProjectJson.encode(p).toText("  ");
                var editor=new ProjectEditor(p);editor.replace(((ArrayType)variable(p,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
                var changed=NameResolver.resolve(editor.build());
                assertTrue(changed.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),changed.semanticDiagnostics().toString());
                assertTrue(new IntegralConstants(changed,bounded).evaluate(variable(changed,"result").initializer().orElseThrow()).isEmpty());
                assertThrows(IllegalArgumentException.class,()->Cpg.sources(changed,Language.CPP));
                assertEquals(before,Cpg.sources(p,Language.CPP).get("main.cpp"));assertEquals(json,ProjectJson.encode(p).toText("  "));
            }
        }
    }

    @Test void omittedPartialDefaultsRetainSelectedAndUnselectedEnumPolicyGuards(){
        for(String actual:List.of("1","2")){
            String source="enum E {enumerated=1};const int seed=+enumerated+256;const int fixed=seed;"
                    +"template<class T,unsigned char N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};typedef Box<Key<int,"+actual+"> > A;unsigned long result=__builtin_offsetof(A,tail);";
            var good=resolve(EnumStorageLayoutTest.P,source);healthy(good);
            assertEquals(BigInteger.valueOf(actual.equals("1")?8:1),new IntegralConstants(good,EnumStorageLayoutTest.P).evaluate(variable(good,"result").initializer().orElseThrow()).orElseThrow().number());
            var incompatible=EnumStorageLayoutTest.P.toBuilder().enumUnderlyingType("unsigned-first").build();
            for(String text:List.of(source,Cpg.sources(NameResolver.resolve(ConstantFolder.fold(good)),Language.CPP).get("main.cpp"))){
                var bad=resolve(incompatible,text);
                assertTrue(bad.semanticDiagnostics().toString().contains("UNSUPPORTED_ENUM_SEMANTICS"),bad.semanticDiagnostics().toString());
                assertTrue(new IntegralConstants(bad,incompatible).evaluate(variable(bad,"result").initializer().orElseThrow()).isEmpty());
                assertThrows(IllegalArgumentException.class,()->Cpg.sources(bad,Language.CPP));
            }
        }
    }

    @Test void nestedDefaultLayoutDependenciesSurviveSmallConvertedValuesAndEdits(){
        var bounded=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        String source="struct Giant {char bytes[16];};const int seed=sizeof(Giant);const int fixed=seed;"
                +"template<class T,bool N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Key<T,true> > {long data;char tail;};typedef Box<Key<int> > A;unsigned long result=__builtin_offsetof(A,tail);";
        var original=resolve(bounded,source);healthy(original);
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))))){
            assertEquals(BigInteger.valueOf(8),new IntegralConstants(p,bounded).evaluate(variable(p,"result").initializer().orElseThrow()).orElseThrow().number());
            var editor=new ProjectEditor(p);editor.replace(((ArrayType)variable(p,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
            var changed=NameResolver.resolve(editor.build());
            assertTrue(changed.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),changed.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(changed,bounded).evaluate(variable(changed,"result").initializer().orElseThrow()).isEmpty());
        }
        source=source.replace("struct Giant {char bytes[16];};const int seed=sizeof(Giant);","enum E{enumerated=2};const int seed=+enumerated;");
        var good=resolve(EnumStorageLayoutTest.P,source);healthy(good);
        var incompatible=EnumStorageLayoutTest.P.toBuilder().enumUnderlyingType("unsigned-first").build();
        var bad=resolve(incompatible,source);
        assertTrue(bad.semanticDiagnostics().toString().contains("UNSUPPORTED_ENUM_SEMANTICS"),bad.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(bad,incompatible).evaluate(variable(bad,"result").initializer().orElseThrow()).isEmpty());
    }

    @Test void nestedDeducedValuesRetainTheirDeclaredParameterType(){
        for(String[] row:List.of(new String[]{"int","4"},new String[]{"unsigned short","2"},new String[]{"bool","1"})){
            var p=resolve("const int fixed=2;template<class T,"+row[0]+" N,int M> struct Key {};"
                    +"template<class T> struct Box {char data;char tail;};"
                    +"template<"+row[0]+" N> struct Box<Key<int,N,fixed> > {char data[sizeof(N)];char tail;};"
                    +"typedef Box<Key<int,1,2> > A;unsigned long result=__builtin_offsetof(A,tail);");
            healthy(p);value(p,"result",Long.parseLong(row[1]));
        }
    }

    @Test void nestedPointerAndReferenceValuesKeepObjectIdentity(){
        for(String[] row:List.of(new String[]{"int*","&first","&second"},new String[]{"int&","first","second"})){
            var p=resolve("int first=2;int second=2;template<class T,"+row[0]+" P> struct Key {};"
                    +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T,"+row[1]+"> > {long data;char tail;};"
                    +"typedef Box<Key<int,"+row[1]+"> > A;typedef Box<Key<int,"+row[2]+"> > B;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);");
            healthy(p);value(p,"a",8);value(p,"b",1);
        }
    }

    @Test void nestedTemplateTemplateHeadsRemainOutsideNamedValueSubset(){
        var p=resolve("const int fixed=2;template<class T,int N> struct Key {};template<class T> struct Box {char data;char tail;};"
                +"template<class T,template<class,int> class TT> struct Box<TT<T,fixed> > {long data;char tail;};"
                +"typedef Box<Key<int,2> > A;unsigned long result=__builtin_offsetof(A,tail);");
        assertTrue(p.hasErrors());assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
    }

    @Test void nestedValuesPreserveOversizedAndEnumWrittenDependencies(){
        var bounded=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        String source="struct Giant {char data[32];};const int fixed=sizeof(Giant);template<class T,bool N> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T,fixed> > {long data;char tail;};"
                +"typedef Box<Key<int,false> > A;unsigned long result=__builtin_offsetof(A,tail);";
        var p=resolve(bounded,source);assertTrue(p.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),p.semanticDiagnostics().toString());
        var incompatible=EnumStorageLayoutTest.P.toBuilder().enumUnderlyingType("unsigned-first").build();
        p=resolve(incompatible,source.replace("struct Giant {char data[32];};const int fixed=sizeof(Giant);","enum E{EValue=2};const int fixed=+EValue;"));
        assertTrue(p.semanticDiagnostics().toString().contains("UNSUPPORTED_ENUM_SEMANTICS"),p.semanticDiagnostics().toString());
    }

    @Test void booleanPartialConversionsShareMatchingOrderingAndRedeclarationIdentity(){
        for(String value:List.of("2","-7","0")){
            String converted=value.equals("0")?"false":"true";
            var p=resolve("const int seed="+value+";const int fixed=seed;enum {same="+value+"};"
                    +"template<class T,bool B> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<T,fixed>;"
                    +"template<class U> struct Box<U,same> {short data;char tail;};"
                    +"template<class T> struct Box<T*,fixed> {long data;char tail;};"
                    +"typedef Box<int,"+converted+"> A;typedef Box<int*,"+converted+"> B;"
                    +"typedef Box<int,!"+converted+"> C;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
            healthy(p);value(p,"a",2);value(p,"b",8);value(p,"c",1);
            healthy(NameResolver.resolve(p.edit().build()));
            healthy(resolve(Cpg.sources(p,Language.CPP).get("main.cpp")));
        }
    }

    @Test void booleanPartialHeaderEditsPreserveNamedDependencies(){
        var original=resolve("const int seed=-7;const int fixed=seed;"
                +"template<class T,bool B> struct Box {char data;char tail;};"
                +"template<class T> struct Box<T,fixed> {long data;char tail;};"
                +"typedef Box<int,true> A;unsigned long result=__builtin_offsetof(A,tail);");
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"result",8);
            assertTrue(nodes(variable(p,"fixed").initializer().orElseThrow(),NameExpression.class).stream().anyMatch(n->n.name().equals("seed")));
            var editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(0).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",1);value(p,"result",8);
            healthy(resolve(Cpg.sources(changed,Language.CPP).get("main.cpp")));
            assertTrue(ProjectJson.encode(changed).toText("  ").contains("OffsetofExpression"));
        }
    }

    @Test void booleanPartialNormalizationRetainsOversizedWrittenDependencies(){
        var bounded=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        String source="struct Giant {char bytes[16];};const int fixed=sizeof(Giant);"
                +"template<class T,bool B> struct Box {char data;char tail;};"
                +"template<class T> struct Box<T,fixed> {long data;char tail;};"
                +"typedef Box<int,true> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
        var original=resolve(bounded,source);healthy(original);
        var oversized=resolve(bounded,source.replace("bytes[16]","bytes[17]"));
        assertTrue(oversized.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),oversized.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(oversized,bounded).evaluate(variable(oversized,"result").initializer().orElseThrow()).isEmpty());
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);
            var editor=new ProjectEditor(p);
            editor.replace(((ArrayType)variable(p,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
            var changed=NameResolver.resolve(editor.build());
            assertTrue(changed.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),changed.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(changed,bounded).evaluate(variable(changed,"result").initializer().orElseThrow()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(changed,Language.CPP));
        }
    }

    @Test void booleanPartialNormalizationDoesNotHideIncompatibleEnumArithmetic(){
        var compatible=EnumStorageLayoutTest.P;
        var incompatible=compatible.toBuilder().enumUnderlyingType("unsigned-first").build();
        String source="enum E{A=1};const int fixed=+A;"
                +"template<class T,bool B> struct Box {char data;char tail;};"
                +"template<class T> struct Box<T,fixed> {long data;char tail;};"
                +"typedef Box<int,true> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
        healthy(resolve(compatible,source));
        var original=resolve(incompatible,source);
        for(var p:List.of(original,NameResolver.resolve(original.edit().build()),NameResolver.resolve(ConstantFolder.fold(original)))){
            assertTrue(p.semanticDiagnostics().toString().contains("UNSUPPORTED_ENUM_SEMANTICS"),p.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(p,incompatible).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
        }
    }

    @Test void unsignedPartialNormalizationRetainsOversizedWrittenDependencies(){
        var bounded=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        String source="struct Giant {char bytes[16];};const int fixed=sizeof(Giant)*16;"
                +"template<class T,unsigned char B> struct Box {char data;char tail;};"
                +"template<class T> struct Box<T,fixed> {long data;char tail;};"
                +"typedef Box<int,0> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
        var original=resolve(bounded,source);healthy(original);
        var oversized=resolve(bounded,source.replace("bytes[16]","bytes[17]"));
        assertTrue(oversized.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),oversized.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(oversized,bounded).evaluate(variable(oversized,"result").initializer().orElseThrow()).isEmpty());
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);
            var editor=new ProjectEditor(p);
            editor.replace(((ArrayType)variable(p,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
            var changed=NameResolver.resolve(editor.build());
            assertTrue(changed.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),changed.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(changed,bounded).evaluate(variable(changed,"result").initializer().orElseThrow()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(changed,Language.CPP));
        }
    }

    @Test void unsignedPartialNormalizationDoesNotHideIncompatibleEnumArithmetic(){
        var compatible=EnumStorageLayoutTest.P;
        var incompatible=compatible.toBuilder().enumUnderlyingType("unsigned-first").build();
        String source="enum E{A=1};const int fixed=+A+256;"
                +"template<class T,unsigned char B> struct Box {char data;char tail;};"
                +"template<class T> struct Box<T,fixed> {long data;char tail;};"
                +"typedef Box<int,1> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
        healthy(resolve(compatible,source));
        var original=resolve(incompatible,source);
        for(var p:List.of(original,NameResolver.resolve(original.edit().build()),NameResolver.resolve(ConstantFolder.fold(original)))){
            assertTrue(p.semanticDiagnostics().toString().contains("UNSUPPORTED_ENUM_SEMANTICS"),p.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(p,incompatible).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
        }
    }

    @Test void booleanPartialConversionsDoNotHideAmbiguityOrNonconstantValues(){
        var p=resolve("const int fixed=-7;template<class T,class U,bool B> struct Box {char tail;};"
                +"template<class T> struct Box<T,int,fixed> {char tail;};"
                +"template<class U> struct Box<int,U,2> {char tail;};Box<int,int,true> object;");
        assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("ambiguous-class-specialization")),p.semanticDiagnostics().toString());
        for(String declaration:List.of("int fixed=2;","extern const int fixed;","const volatile int fixed=2;","const double fixed=2.0;")){
            var invalid=resolve(declaration+"template<class T,bool B> struct Box {char tail;};"
                    +"template<class T> struct Box<T,fixed> {char tail;};Box<int,true> object;");
            assertTrue(invalid.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),invalid.semanticDiagnostics().toString());
        }
    }

    @Test void convertedPartialPatternValuesCannotFallThroughToTheWrongPrimary(){
        for(String[] row:List.of(new String[]{"signed char","128","0"},new String[]{"short","65535","-1"})){
            var p=resolve("template<class T,"+row[0]+" N> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<T,"+row[1]+"> {long data;char tail;};"
                    +"typedef Box<int,"+row[2]+"> Selected;unsigned long result=__builtin_offsetof(Selected,tail);");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
        }
    }

    @Test void unsignedModuloConversionsShareMatchingOrderingAndRedeclarations(){
        for(String[] row:List.of(new String[]{"unsigned char","-1","255"},new String[]{"unsigned char","512","0"},
                new String[]{"unsigned short","65538","2"},new String[]{"unsigned int","-1","4294967295UL"},
                new String[]{"unsigned long","-1","18446744073709551615UL"})){
            var p=resolve("const long seed="+row[1]+";const long fixed=seed;"
                    +"template<class T,"+row[0]+" N> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<T,fixed>;"
                    +"template<class U> struct Box<U,"+row[2]+"> {short data;char tail;};"
                    +"template<class T> struct Box<T*,fixed> {long data;char tail;};"
                    +"typedef Box<int,"+row[2]+"> A;typedef Box<int*,fixed> B;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);");
            healthy(p);value(p,"a",2);value(p,"b",8);
            healthy(NameResolver.resolve(p.edit().build()));
            healthy(resolve(Cpg.sources(p,Language.CPP).get("main.cpp")));
        }
    }

    @Test void unsignedModuloUsesSelectedWidthAndCharSignedness(){
        for(var profile:List.of(PlatformProfile.builder().sizeBits("short",32).build(),PlatformProfile.builder().charSigned(false).build())){
            String type=profile.charSigned()?"unsigned short":"char";
            String maximum=profile.charSigned()?"4294967295UL":"255";
            var p=resolve(profile,"template<"+type+" N> struct Box {int data;};template<> struct Box<"+maximum+"> {long data;};Box<-1> object;");
            healthy(p);assertEquals(TemplateKind.FULL_SPECIALIZATION,p.classTemplateInstances().getFirst().selected().templateKind());
        }
    }

    @Test void unsignedModuloDoesNotHideAmbiguityOrInvalidInitializers(){
        var p=resolve("const int fixed=-1;template<class T,class U,unsigned char N> struct Box {};"
                +"template<class T,class U> struct Box<T*,U,fixed> {};template<class T,class U> struct Box<T,U*,511> {};Box<int*,int*,255> object;");
        assertTrue(p.hasErrors());
        for(String declaration:List.of("const int fixed=fixed;","const int fixed=2147483647+1;","const volatile int fixed=-1;","const double fixed=-1.0;")){
            var bad=resolve(declaration+"template<class T,unsigned char N> struct Box {};template<class T> struct Box<T,fixed> {};Box<int,255> object;");
            assertTrue(bad.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),bad.semanticDiagnostics().toString());
        }
    }

    @Test void unsignedDefaultsFullSpecializationsAndParameterTypesUseConvertedValues(){
        var p=resolve("const int seed=-1;template<unsigned char N=seed> struct Box {char data[sizeof(N)];char tail;};"
                +"template<> struct Box<511> {long data;char tail;};typedef Box<> A;typedef Box<256> B;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);");
        healthy(p);value(p,"a",8);value(p,"b",1);
    }

    @Test void wrappedActualAndDefaultBodiesRetainUnsignedValueAndOverloadType(){
        var p=resolve("int pick(unsigned char n){return 1;}int pick(int n){return 2;}"
                +"template<unsigned char N=-1> struct Box {int f(){return pick(N);}};Box<> box;int answer=box.f();"
                +"template<unsigned long N=-1> struct Wide {char data[N>0?sizeof(N):2];char tail;};"
                +"typedef Wide<> A;typedef Wide<-1> B;unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);");
        healthy(p);value(p,"a",8);value(p,"b",8);
        var calls=p.classTemplateInstances().stream().flatMap(i->nodes(i.declaration(),CallExpression.class).stream())
                .filter(c->c.target().map(t->t.name().equals("pick")).orElse(false)).toList();
        assertFalse(calls.isEmpty());
        for(var call:calls){
            var type=call.target().orElseThrow().parameters().getFirst().type().orElseThrow();
            assertEquals("unsigned char",TypeSemantics.integerType(type,PROFILE).orElseThrow().spelling());
        }
    }

    @Test void unsignedPatternHeaderEditsPreserveDependenciesAcrossSnapshots(){
        var original=resolve("const int seed=-1;const int fixed=seed;template<class T,unsigned char N> struct Box {char data;char tail;};"
                +"template<class T> struct Box<T,fixed> {long data;char tail;};"
                +"typedef Box<int,255> A;unsigned long result=__builtin_offsetof(A,tail);");
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"result",8);
            var editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(256).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",1);value(p,"result",8);
            String source=Cpg.sources(changed,Language.CPP).get("main.cpp");assertTrue(source.contains("fixed"));healthy(resolve(source));
            assertTrue(ProjectJson.encode(changed).toText("  ").contains("OffsetofExpression"));
        }
    }

    @Test void precedingConcretePatternTypesNormalizeIntegralValues(){
        for(String[] row:List.of(new String[]{"int","2","2"},new String[]{"bool","-7","true"},
                new String[]{"unsigned char","511","255"},new String[]{"unsigned short","65538","2"},
                new String[]{"unsigned int","-1","4294967295UL"},new String[]{"unsigned long","-1","18446744073709551615UL"})){
            var p=resolve("const long fixed="+row[1]+";template<class T,T N,class U> struct Box {char data;char tail;};"
                    +"template<class U> struct Box<"+row[0]+",fixed,U> {long data;char tail;};"
                    +"typedef Box<"+row[0]+","+row[2]+",int> A;unsigned long result=__builtin_offsetof(A,tail);");
            healthy(p);value(p,"result",8);
            healthy(NameResolver.resolve(p.edit().build()));
            healthy(resolve(Cpg.sources(p,Language.CPP).get("main.cpp")));
        }
        var later=resolve("const int fixed=-1;template<class X,class T,T N,class U> struct Box {char data;char tail;};"
                +"template<class X,class U> struct Box<X,unsigned char,fixed,U> {long data;char tail;};"
                +"typedef Box<int,unsigned char,255,char> A;unsigned long result=__builtin_offsetof(A,tail);");
        healthy(later);value(later,"result",8);
    }

    @Test void concretePatternTypesShareOrderingRedeclarationAndFullSelection(){
        var p=resolve("typedef unsigned char Byte;const int fixed=-1;"
                +"template<class T,T N,class U> struct Box {char data;char tail;};"
                +"template<class U> struct Box<Byte,fixed,U>;"
                +"template<class V> struct Box<unsigned char,255,V> {short data;char tail;};"
                +"template<class U> struct Box<Byte,fixed,U*> {long data;char tail;};"
                +"template<> struct Box<Byte,511,char*> {int data;char tail;};"
                +"typedef Box<Byte,255,int> A;typedef Box<Byte,255,int*> B;typedef Box<Byte,255,char*> C;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
        healthy(p);value(p,"a",2);value(p,"b",8);value(p,"c",4);
    }

    @Test void concretePatternParameterBodiesRetainTheirDeclaredType(){
        var p=resolve("int pick(unsigned char n){return 1;}int pick(int n){return 2;}"
                +"template<class T,T N,class U> struct Box {};"
                +"template<unsigned char N,class U> struct Box<unsigned char,N,U*> {char data[sizeof(N)];char tail;int f(){return pick(N);}};"
                +"Box<unsigned char,-1,int*> box;int answer=box.f();typedef Box<unsigned char,255,int*> A;unsigned long result=__builtin_offsetof(A,tail);");
        healthy(p);value(p,"result",1);
        var calls=p.classTemplateInstances().stream().flatMap(i->nodes(i.declaration(),CallExpression.class).stream())
                .filter(c->c.target().map(t->t.name().equals("pick")).orElse(false)).toList();
        assertFalse(calls.isEmpty());
        for(var call:calls)assertEquals("unsigned char",TypeSemantics.integerType(call.target().orElseThrow().parameters().getFirst().type().orElseThrow(),PROFILE).orElseThrow().spelling());
    }

    @Test void concretePatternTypesRetainHeaderDependenciesAfterCopyFoldAndEdit(){
        var original=resolve("const int seed=-1;const int fixed=seed;"
                +"template<class T,T N,class U> struct Box {char data;char tail;};"
                +"template<class U> struct Box<unsigned char,fixed,U> {long data;char tail;};"
                +"typedef Box<unsigned char,255,int> A;unsigned long result=__builtin_offsetof(A,tail);");
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(original)))){
            healthy(p);value(p,"result",8);
            var editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(256).build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",1);value(p,"result",8);
            String source=Cpg.sources(changed,Language.CPP).get("main.cpp");assertTrue(source.contains("fixed"));healthy(resolve(source));
            assertTrue(ProjectJson.encode(changed).toText("  ").contains("OffsetofExpression"));
        }
    }

    @Test void concretePatternTypesDoNotUseActualTypesToCertifyDependentPatterns(){
        for(String source:List.of(
                "template<class T,T N> struct Box {char tail;};template<class T> struct Box<T,1> {long data;char tail;};typedef Box<int,1> A;",
                "const int fixed=1;template<class T,T N> struct Box {char tail;};template<class T> struct Box<T,fixed> {long data;char tail;};typedef Box<int,1> A;",
                "const int fixed=128;template<class T,T N,class U> struct Box {char tail;};template<class U> struct Box<signed char,fixed,U> {long data;char tail;};typedef Box<signed char,0,int> A;")){
            var p=resolve(source+"unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-class-template-arguments")),p.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
        }
        var ambiguous=resolve("const int fixed=-1;template<class T,T N,class U,class V> struct Box {};"
                +"template<class U,class V> struct Box<unsigned char,fixed,U*,V> {};template<class U,class V> struct Box<unsigned char,511,U,V*> {};Box<unsigned char,255,int*,int*> object;");
        assertTrue(ambiguous.semanticDiagnostics().stream().anyMatch(d->d.code().equals("ambiguous-class-specialization")),ambiguous.semanticDiagnostics().toString());
    }

    @Test void concretePatternTypesPreservePointerAndReferenceIdentity(){
        for(String[] row:List.of(new String[]{"const int*","&first","&second"},new String[]{"const int&","first","second"})){
            var p=resolve("extern const int first=1;extern const int second=1;"
                    +"template<class T,T N,class U> struct Box {char data;char tail;};"
                    +"template<class U> struct Box<"+row[0]+","+row[1]+",U> {long data;char tail;};"
                    +"typedef Box<"+row[0]+","+row[1]+",int> A;typedef Box<"+row[0]+","+row[2]+",int> B;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);");
            healthy(p);value(p,"a",8);value(p,"b",1);
        }
    }

    @Test void concretePatternTypesPreserveLayoutAndEnumGuards(){
        var bounded=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        String source="struct Giant {char bytes[16];};const int fixed=sizeof(Giant)*16;"
                +"template<class T,T N,class U> struct Box {char data;char tail;};"
                +"template<class U> struct Box<unsigned char,fixed,U> {long data;char tail;};"
                +"typedef Box<unsigned char,0,int> A;unsigned long result=__builtin_offsetof(A,tail);";
        var original=resolve(bounded,source);healthy(original);
        assertEquals(BigInteger.valueOf(8),new IntegralConstants(original,bounded).evaluate(variable(original,"result").initializer().orElseThrow()).orElseThrow().number());
        for(var p:List.of(original,original.edit().build(),NameResolver.resolve(ConstantFolder.fold(original)))){
            var editor=new ProjectEditor(p);editor.replace(((ArrayType)variable(p,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
            var changed=NameResolver.resolve(editor.build());
            assertTrue(changed.semanticDiagnostics().toString().contains("SUPPORTED_OBJECT_LIMIT"),changed.semanticDiagnostics().toString());
            assertTrue(new IntegralConstants(changed,bounded).evaluate(variable(changed,"result").initializer().orElseThrow()).isEmpty());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(changed,Language.CPP));
        }
        source="enum E{A=1};const int fixed=+A+256;template<class T,T N,class U> struct Box {char data;char tail;};"
                +"template<class U> struct Box<unsigned char,fixed,U> {long data;char tail;};"
                +"typedef Box<unsigned char,1,int> Selected;unsigned long result=__builtin_offsetof(Selected,tail);";
        var compatible=resolve(EnumStorageLayoutTest.P,source);healthy(compatible);
        assertEquals(BigInteger.valueOf(8),new IntegralConstants(compatible,EnumStorageLayoutTest.P).evaluate(variable(compatible,"result").initializer().orElseThrow()).orElseThrow().number());
        var incompatible=EnumStorageLayoutTest.P.toBuilder().enumUnderlyingType("unsigned-first").build();
        var p=resolve(incompatible,source);
        assertTrue(p.semanticDiagnostics().toString().contains("UNSUPPORTED_ENUM_SEMANTICS"),p.semanticDiagnostics().toString());
        assertTrue(new IntegralConstants(p,incompatible).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
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
