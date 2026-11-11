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

class IntegralTypedefTemplateDefaultsTest {
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
    @Test void typedefTypeDefaultsShareActualAndPatternIdentityWithoutRewritingSyntax(){
        for(String pattern:List.of("Key<T>","Key<T,unsigned char>","Key<T,unsigned char,2>")){
            var original=resolve("namespace cfg {typedef unsigned char Byte;}template<class T,class V=cfg::Byte,V N=258> struct Key {};template<class T> struct Box {char data;char tail;};"
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

    @Test void typedefTypeDefaultsPreserveCvAndAllIntegralKinds(){
        for(String type:List.of("bool","char","signed char","unsigned char","wchar_t","short","unsigned short","int","unsigned int","long","unsigned long","const int","volatile int","const volatile int")){
            var p=resolve("typedef "+type+" Base;namespace cfg {typedef Base Alias;}template<class T,class V=cfg::Alias> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};"
                    +"typedef Box<Key<char> > A;typedef Box<Key<char,"+type+"> > B;typedef Box<Key<char,"+(type.contains("const")||type.contains("volatile")?"int":"double")+"> > C;"
                    +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);unsigned long c=__builtin_offsetof(C,tail);");
            healthy(p);value(p,"a",8);value(p,"b",8);value(p,"c",1);
            assertEquals(2,p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).count());

        }
    }

    @Test void typedefTypeDefaultsConvertValuesAndRespectTargetWidths(){
        for(String[] row:List.of(new String[]{"bool","-7","true"},new String[]{"unsigned char","258","2"},
                new String[]{"signed char","127","127"},new String[]{"const int","-2","-2"})){
            var p=resolve("typedef "+row[0]+" Base;namespace cfg {typedef Base Alias;}template<class T,class V=cfg::Alias,V N="+row[1]+"> struct Key {};template<class T> struct Box {char data;char tail;};"
                    +"template<class T> struct Box<Key<T> > {long data;char tail;};typedef Box<Key<int,"+row[0]+","+row[2]+"> > A;unsigned long result=__builtin_offsetof(A,tail);");
            healthy(p);value(p,"result",8);
        }
        var narrow=PlatformProfile.builder().sizeBits("long",32).alignmentBits("long",32).build();
        String source="typedef unsigned long Wide;template<class T,class V=Wide,V N=4294967295UL+2UL> struct Key {};template<class T> struct Box {char data;char tail;};"
                +"template<class T> struct Box<Key<T> > {short data;char tail;};typedef Box<Key<int,unsigned long,1> > A;";
        // A symbolic 32-bit-long target is not an LP64 layout/native oracle.
        for(var profile:List.of(PROFILE,narrow)){
            var p=resolve(profile,source);healthy(p);
            var outer=p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Box")).findFirst().orElseThrow();
            assertEquals(profile==narrow?TemplateKind.PARTIAL_SPECIALIZATION:TemplateKind.PRIMARY,outer.selected().templateKind());
        }
    }

    @Test void typedefTypeAndValueDefaultsKeepSharedNodesIndependentInOneUse(){
        for(boolean reversed:List.of(false,true)){
            String left=reversed?"int":"unsigned char",right=reversed?"unsigned char":"int";
            String lv=reversed?"258":"2",rv=reversed?"2":"258";
            String omittedLeft=reversed?",int":"",omittedRight=reversed?"":",int";
            var original=resolve("typedef unsigned char Byte;typedef int Integer;const int fixed=258;template<class T,class V=Byte,V N=fixed,bool B=7,class W=Integer,int M=2> struct Key {};"
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

    @Test void typedefTypeDefaultEditsAndConstChainEditsReselectWithoutMutatingSnapshots(){
        var original=resolve("namespace cfg {typedef unsigned char Byte;typedef Byte Count;}const int seed=258;const int fixed=seed;template<class T,class V=cfg::Count,V N=fixed> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};"
                +"typedef Box<Key<int,unsigned char,2> > A;unsigned long result=__builtin_offsetof(A,tail);");
        for(var p:List.of(original,NameResolver.resolve(original.edit().build()),TypeNormalizer.green(original),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))))){
            healthy(p);value(p,"result",8);
            String source=Cpg.sources(p,Language.CPP).get("main.cpp"),json=ProjectJson.encode(p).toText("  ");
            var alias=nodes(p.files().getFirst(),TypedefDeclaration.class).stream().filter(t->t.name().equals("Byte")).findFirst().orElseThrow();
            var editor=new ProjectEditor(p);editor.replace(alias.type(),NamedType.create().setName("unsigned short").build());
            var changed=NameResolver.resolve(editor.build());healthy(changed);value(changed,"result",1);
            editor=new ProjectEditor(p);editor.replace(variable(p,"seed").initializer().orElseThrow(),IntegerLiteral.create().setValue(259).build());
            var changedValue=NameResolver.resolve(editor.build());healthy(changedValue);value(changedValue,"result",1);
            for(var edited:List.of(changed,changedValue)){var reparsed=resolve(Cpg.sources(edited,Language.CPP).get("main.cpp"));healthy(reparsed);value(reparsed,"result",1);}
            value(p,"result",8);assertEquals(source,Cpg.sources(p,Language.CPP).get("main.cpp"));assertEquals(json,ProjectJson.encode(p).toText("  "));
        }
    }

    @Test void typedefTypeDefaultsShareOrderingRedeclarationsAndAmbiguity(){
        String prefix="namespace cfg {typedef unsigned char Byte;}template<class T,class V=cfg::Byte,V N=258> struct Key {};template<class A,class B> struct Box {char data;char tail;};";
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

    @Test void typedefTypeDefaultsRespectNamespaceHeadsAndRepeatedDeductions(){
        var p=resolve("typedef int Alias;namespace other {template<class T,class V=Alias> struct Key {};}namespace api {template<class T,class V=Alias> struct Key {};"
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

    @Test void typedefTypeDefaultsKeepOwnHeaderVisibility(){
        String box="template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};";
        for(String source:List.of("typedef int Alias;template<class T,class V> struct Key {};"+box+"template<class T,class V=Alias> struct Key;",
                "typedef int Alias;template<class T,class V> struct Key;"+box+"template<class T,class V=Alias> struct Key {};",
                "typedef int Alias;template<class T,class V=Alias> struct Key;template<class T,class V> struct Key {};"+box)){
            var p=resolve(source+"typedef Box<Key<int,int> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors(),source);assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        }
    }

    @Test void typedefTypeDefaultsRetainSelectedAndUnselectedHiddenLayoutDependencies(){
        var bounded=PROFILE.toBuilder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","16").build();
        for(String type:List.of("bool","unsigned char"))for(boolean selected:List.of(false,true)){
            String actual=type.equals("bool")?(selected?"true":"false"):(selected?"0":"1");
            var original=resolve(bounded,"typedef "+type+" Base;namespace cfg {typedef Base Alias;}struct Giant {char bytes[16];};const int seed=sizeof(Giant)*16;const int fixed=seed;"
                    +"template<class T,class V=cfg::Alias,V N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
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

    @Test void typedefTypeDefaultsRetainSelectedAndUnselectedEnumPolicyGuards(){
        for(String actual:List.of("1","2")){
            String source="typedef unsigned char Byte;typedef Byte Count;enum E {enumerated=1};const int seed=+enumerated+256;const int fixed=seed;"
                    +"template<class T,class V=Count,V N=fixed> struct Key {};template<class T> struct Box {char data;char tail;};"
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

    @Test void aliasSpellingAndDefinitionTimeBindingSurviveLaterShadowing(){
        var p=resolve("namespace cfg {typedef unsigned char Byte;typedef Byte Chain;}"
                +"template<class T,class V=cfg::Chain,V N=258> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};"
                +"namespace use {typedef unsigned short Byte;typedef Box<Key<int> > Omitted;typedef Box<Key<int,cfg::Byte> > Explicit;"
                +"typedef Box<Key<int,Byte,258> > Different;unsigned long a=__builtin_offsetof(Omitted,tail);"
                +"unsigned long b=__builtin_offsetof(Explicit,tail);unsigned long c=__builtin_offsetof(Different,tail);}");
        for(var copy:List.of(p,NameResolver.resolve(p.edit().build()),TypeNormalizer.green(p),NameResolver.resolve(ConstantFolder.fold(p)))){
            healthy(copy);value(copy,"a",8);value(copy,"b",8);value(copy,"c",1);
            var parameter=nodes(copy.files().getFirst(),TemplateParameterDeclaration.class).stream().filter(t->t.name().equals("V")).findFirst().orElseThrow();
            var written=assertInstanceOf(NamedType.class,parameter.defaultArgument().orElseThrow().type().orElseThrow());
            assertEquals("cfg::Chain",written.name());
            assertEquals("Chain",written.alias().orElseThrow().name());
            assertEquals("Byte",assertInstanceOf(NamedType.class,written.alias().orElseThrow().type()).alias().orElseThrow().name());
            assertEquals("unsigned char",written.canonicalIdentity());
            String generated=Cpg.sources(copy,Language.CPP).get("main.cpp");
            assertTrue(generated.contains("cfg::Chain"),generated);
            var reparsed=resolve(generated);healthy(reparsed);value(reparsed,"a",8);value(reparsed,"b",8);value(reparsed,"c",1);
        }
    }

    @Test void nonIntegralAndMemberAliasDefaultsNeverFallThroughToPrimaryLayout(){
        for(String declaration:List.of("typedef int* Alias;","typedef int& Alias;","typedef int Alias[2];",
                "typedef int Alias();","struct Owner {};typedef int Owner::* Alias;","typedef double Alias;",
                "enum E {e=1};typedef E Alias;","struct Object {};typedef Object Alias;",
                "struct Owner {typedef int Member;};typedef Owner::Member Alias;",
                "struct Owner {typedef int Member;};typedef Owner::Member First;typedef First Alias;"))
            for(boolean omittedPattern:List.of(false,true)){
                var p=resolve(declaration+"template<class T,class V=Alias> struct Key {};template<class T> struct Box {char data;char tail;};"
                        +"template<class T> struct Box<Key<T"+(omittedPattern?"":",Alias")+"> > {long data;char tail;};"
                        +"typedef Box<Key<int"+(omittedPattern?",Alias":"")+"> > A;unsigned long result=__builtin_offsetof(A,tail);");
                assertTrue(p.hasErrors(),declaration+" "+omittedPattern);
                assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty(),declaration);
            }
    }

    @Test void unresolvedDependentLateAndDependentValueAliasDefaultsStayGuarded(){
        for(String[] fixture:List.of(
                new String[]{"","class V=Missing",""},
                new String[]{"","class V=Alias","typedef int Alias;"},
                new String[]{"","class V=typename T::Alias",""},
                new String[]{"typedef int Alias;","class V=Alias,V N=sizeof(V)",""},
                new String[]{"typedef int Alias;","class V=Alias,V N=sizeof(T)",""},
                new String[]{"typedef int Alias;","int M=2,class V=Alias,V N=M",""},
                new String[]{"typedef signed char Alias;","class V=Alias,V N=128",""})){
            var p=resolve(fixture[0]+"template<class T,"+fixture[1]+"> struct Key {};"+fixture[2]
                    +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};"
                    +"typedef Box<Key<int> > A;unsigned long result=__builtin_offsetof(A,tail);");
            assertTrue(p.hasErrors(),Arrays.toString(fixture));
            assertTrue(new IntegralConstants(p,PROFILE).evaluate(variable(p,"result").initializer().orElseThrow()).isEmpty());
        }
    }

    @Test void eligibilityRequiresOwnedNamespaceChainsAndRejectsCyclesAndForeignNodes(){
        var a=TypedefDeclaration.create().setName("A");
        var b=TypedefDeclaration.create().setName("B");
        a.setType(NamedType.create().setName("B").setAlias(b));
        b.setType(NamedType.create().setName("A").setAlias(a));
        var cyclic=Project.create().addFile(SourceFile.create().setPath("cycle.cpp").setLanguage(Language.CPP).addItem(a).addItem(b)).build();
        assertFalse(new ClassTemplateSemantics.IntegralTypeDefaults(cyclic).accepts(nodes(cyclic,TypedefDeclaration.class).getFirst().type(),PROFILE));
        var foreign=TypedefDeclaration.create().setName("Foreign").setType(NamedType.create().setName("int"));
        var use=TypedefDeclaration.create().setName("Use").setType(NamedType.create().setName("Foreign").setAlias(foreign));
        assertThrows(IllegalStateException.class,()->Project.create().addFile(SourceFile.create().setPath("foreign.cpp").setLanguage(Language.CPP).addItem(use)).build());
        assertFalse(new ClassTemplateSemantics.IntegralTypeDefaults(Project.create().build()).accepts(use.build().type(),PROFILE));
        var p=resolve("typedef int Global;namespace cfg {typedef Global Chain;}struct Owner {typedef int Member;};int f(){typedef int Local;return 0;}");
        var index=new ClassTemplateSemantics.IntegralTypeDefaults(p);
        for(var alias:nodes(p,TypedefDeclaration.class)){
            // A detached use bound to a predecessor node cannot gain ownership by matching its spelling.
            var detached=NamedType.create().setName(alias.name()).setAlias(alias.edit()).build();
            assertFalse(index.accepts(detached,PROFILE),alias.name());
        }
        assertTrue(index.accepts(nodes(p,TypedefDeclaration.class).stream().filter(t->t.name().equals("Chain")).findFirst().orElseThrow().type(),PROFILE));
        for(String scope:List.of("struct Owner {typedef int Member;typedef Member Use;};","int f(){typedef int Local;typedef Local Use;return 0;}")){
            var scoped=resolve(scope);
            var alias=nodes(scoped,TypedefDeclaration.class).stream().filter(t->t.name().equals("Use")).findFirst().orElseThrow();
            assertFalse(new ClassTemplateSemantics.IntegralTypeDefaults(scoped).accepts(alias.type(),PROFILE));
        }
    }

    @Test void qualifiedAliasEditsRestoreSelectionWithoutLosingCv(){
        var original=resolve("typedef int Base;typedef const Base Const;template<class T,class V=Const> struct Key {};"
                +"template<class T> struct Box {char data;char tail;};template<class T> struct Box<Key<T> > {long data;char tail;};"
                +"typedef Box<Key<int,const int> > A;typedef Box<Key<int,int> > B;"
                +"unsigned long a=__builtin_offsetof(A,tail);unsigned long b=__builtin_offsetof(B,tail);");
        for(var p:List.of(original,NameResolver.resolve(original.edit().build()),NameResolver.resolve(ConstantFolder.fold(TypeNormalizer.green(original))))){
            healthy(p);value(p,"a",8);value(p,"b",1);
            var alias=nodes(p,TypedefDeclaration.class).stream().filter(t->t.name().equals("Base")).findFirst().orElseThrow();
            var edit=new ProjectEditor(p);edit.replace(alias.type(),NamedType.create().setName("short").build());
            var changed=NameResolver.resolve(edit.build());healthy(changed);value(changed,"a",1);value(changed,"b",1);
            var restore=new ProjectEditor(changed);
            restore.replace(nodes(changed,TypedefDeclaration.class).stream().filter(t->t.name().equals("Base")).findFirst().orElseThrow().type(),NamedType.create().setName("int").build());
            var restored=NameResolver.resolve(restore.build());healthy(restored);value(restored,"a",8);value(restored,"b",1);
            value(p,"a",8);value(p,"b",1);
        }
    }
}
