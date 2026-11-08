/* Independent strict C++03 oracle for explicit member-pointer storage. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class MemberPointerLayoutIT {
    @TempDir Path temp;
    private int nativeExecution;
    private static final PlatformProfile PROFILE=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647","itanium-x86_64-lp64-v1").build();
    private static final String PROGRAM="""
            #include <cstddef>
            struct Incomplete;
            struct NonPod {virtual void method();private:int hidden;};
            typedef int Incomplete::* Data;
            typedef void (Incomplete::*Method)() const volatile;
            typedef void (*Callback)();
            typedef Callback Incomplete::* CallbackData;
            typedef int NonPod::* NonPodData;
            struct Owner {char prefix;Data selected;char tail;};
            struct Functions {char prefix;Method array[3];char tail;};
            struct Callbacks {char prefix;CallbackData member;char tail;};
            struct NonPods {char prefix;NonPodData member;char tail;};
            unsigned long selected=offsetof(Owner,tail);
            unsigned long functions=offsetof(Functions,tail);
            unsigned long callbacks=offsetof(Callbacks,tail);
            unsigned long nonpods=offsetof(NonPods,tail);
            enum {extent=offsetof(Owner,tail)+1};
            char array[extent];
            int exact(unsigned long){return 1;}
            template<class T> int exact(T){return 0;}
            int exact_result=exact(offsetof(Owner,tail));
            int check_case(int n){switch(n){case offsetof(Owner,tail):return 1;default:return 0;}}
            int main(){return 0;}
            """;
    private static Cpg.Options options(Cpg.Stage stage){return new Cpg.Options(Language.CPP,stage,"c++03").withPlatform(PROFILE);}
    private static void healthy(Project p){assertFalse(p.hasErrors(),()->p.parseProblems()+" / "+p.semanticDiagnostics());}
    private static <T extends Node> List<T> nodes(Node root,Class<T> type){var list=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))list.add(type.cast(n));}});return list;}
    private Path source(String label,String text)throws Exception {Path dir=Files.createTempDirectory(temp,label);Path file=dir.resolve("main.cpp");Files.writeString(file,text);return file;}
    @Test void actualMemberPointerLayoutSurvivesStagesCopiesCliAndCategoryEdit() throws Exception {
        Path input=source("input",PROGRAM);
        verify(input,false);
        for(var stage:List.of(Cpg.Stage.RESOLVED,Cpg.Stage.GREEN,Cpg.Stage.FOLDED)) {
            Project p=Cpg.read(input.getParent(),options(stage));healthy(p);
            assertTrue(ProjectJson.encode(p).toText("  ").contains("memberPointerStorage"));
            verify(source("generated-"+stage,Cpg.sources(p,Language.CPP).get("main.cpp")),false);
            Project copy=NameResolver.resolve(p.edit().build());healthy(copy);
            verify(source("copy-"+stage,Cpg.sources(copy,Language.CPP).get("main.cpp")),false);
            var field=nodes(p.files().getFirst(),VariableDeclaration.class).stream()
                    .filter(v->v.name().equals("selected") && v.type().orElse(null) instanceof NamedType).findFirst().orElseThrow();
            var editor=new ProjectEditor(p);
            editor.replace(field.type().orElseThrow(),NamedType.create().setName("Method").build());
            Project edited=NameResolver.resolve(editor.build());healthy(edited);
            String text=Cpg.sources(edited,Language.CPP).get("main.cpp");
            assertTrue(text.contains("Method selected"));
            verify(source("edited-"+stage,text),true);
        }
        var cli=new CliSandbox(Files.createTempDirectory(temp,"cli")).source("main.cpp",PROGRAM);
        Path profile=temp.resolve("profile.json");Files.writeString(profile,Json.serialize(PROFILE.toSettings()));
        var run=cli.run("cpp","--stage","folded","--cpp-standard","c++03","--platform-profile",profile.toString()).expectStatus(0);
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("memberPointerStorage"));
        verify(run.work().resolve("generated/main.cpp"),false);
    }
    @Test void omittedStoragePolicyRemainsUnsupportedInApiAndCliJson() throws Exception {
        var old=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build();
        var cli=new CliSandbox(Files.createTempDirectory(temp,"unavailable")).source("main.cpp",PROGRAM);
        var p=Cpg.read(cli.input,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03").withPlatform(old));
        assertTrue(p.hasErrors());
        assertTrue(p.semanticDiagnostics().toString().contains("Member-pointer storage requires"));
        assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
        Path profile=temp.resolve("old-profile.json");Files.writeString(profile,Json.serialize(old.toSettings()));
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",profile.toString()).expectStatus(1);
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("Member-pointer storage requires"));
        assertFalse(Files.exists(run.work().resolve("generated/main.cpp")));
    }
    private void verify(Path source,boolean edited)throws Exception {
        healthy(Cpg.read(source.getParent(),options(Cpg.Stage.RESOLVED)));
        Path expanded=materialize(source);healthy(Cpg.read(expanded.getParent(),options(Cpg.Stage.RESOLVED)));
        for(Path tested:List.of(source,expanded)) for(String compiler:List.of("g++","clang++")) {
            Path work=Files.createTempDirectory(temp,"native");Path wrapper=work.resolve("wrapper.cpp"),binary=work.resolve("probe");
            Files.writeString(wrapper,"""
                    #include <cstddef>
                    #include <climits>
                    #include <cstdio>
                    #if !defined(__x86_64__) || !defined(__LP64__)
                    #error selected target mismatch
                    #endif
                    template<class T> struct Alignment {char prefix;T value;};
                    template<class T> int layout(unsigned long bytes){return sizeof(T)==bytes && offsetof(Alignment<T>,value)==bytes;}
                    struct Host;
                    typedef int Host::* HD;
                    typedef void (Host::* HF)() const volatile;
                    struct AD {char c;HD v;};
                    struct AF {char c;HF v;};
                    const unsigned long data_alignment=offsetof(AD,v), function_alignment=offsetof(AF,v);
                    struct HostOwner {char c;%s member;char tail;};
                    const unsigned long host_tail=offsetof(HostOwner,tail);
                    #define main tested_main
                    #include "%s"
                    #undef main
                    int main(){
                        if(CHAR_BIT!=8 || sizeof(int)!=4 || sizeof(long)!=8 || sizeof(void*)!=8) return 10;
                        if(!layout<bool>(1) || !layout<char>(1) || !layout<short>(2) || !layout<int>(4) || !layout<long>(8)
                                || !layout<wchar_t>(4) || !layout<float>(4) || !layout<double>(8) || !layout<long double>(16) || !layout<void*>(8)) return 14;
                        if(sizeof(HD)!=8 || sizeof(HF)!=16 || data_alignment!=8 || function_alignment!=8) return 11;
                        if(selected!=host_tail || functions!=56 || callbacks!=16 || nonpods!=16) return 12;
                        if(exact_result!=1 || sizeof(array)!=host_tail+1 || check_case(host_tail)!=1) return 13;
                        std::printf("selected=%%lu functions=%%lu callbacks=%%lu nonpods=%%lu extent=%%lu\\n",selected,functions,callbacks,nonpods,(unsigned long)sizeof(array));
                        return tested_main();
                    }
                    """.formatted(edited?"HF":"HD",tested.toAbsolutePath().toString().replace("\\","\\\\").replace("\"","\\\"")));
            List<String> command=List.of(compiler,"-std=c++03","-pedantic-errors",wrapper.toString(),"-o",binary.toString());
            success(command,work);Run result=success(List.of(binary.toString()),work);
            assertEquals(edited?"selected=24 functions=56 callbacks=16 nonpods=16 extent=25\n":"selected=16 functions=56 callbacks=16 nonpods=16 extent=17\n",result.out());
            Path artifact=Files.createDirectories(Path.of("target/member-pointer-layout-oracle").resolve(String.format("%03d-%s",++nativeExecution,compiler)));
            Files.writeString(artifact.resolve("tested.cpp"),Files.readString(tested));Files.writeString(artifact.resolve("wrapper.cpp"),Files.readString(wrapper));
            Files.writeString(artifact.resolve("observations.txt"),result.out());
            Files.writeString(artifact.resolve("metadata.json"),Json.serialize(Map.of("compiler",compiler,"version",success(List.of(compiler,"--version"),work).out(),"target",success(List.of(compiler,"-dumpmachine"),work).out(),"command",command,"profile",PROFILE.toSettings(),"sourceSha256",hash(tested),"materialized",tested.equals(expanded),"edited",edited)));
        }
    }
    private Path materialize(Path file)throws Exception {
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(file),file.getParent(),List.of(),List.of(),"c++03").withPlatform(PROFILE);
        var result=CppPreprocessor.preprocess(unit,Files.readString(file));
        assertTrue(result.expansions().stream().anyMatch(e->e.name().equals("offsetof")));
        StringBuilder out=new StringBuilder();boolean body=false;
        for(var expanded:result.tokens()) {var token=expanded.token();
            if(token.kind()==PreprocessingToken.Kind.HEADER_NAME){assertFalse(body);out.append("#include ").append(token.spelling()).append('\n');}
            else if(token.kind()!=PreprocessingToken.Kind.EOF){if(!body){out.append("#undef offsetof\n");body=true;}assertNotEquals("offsetof",token.spelling());out.append(token.spelling()).append(' ');}
        }
        assertTrue(out.toString().contains("__builtin_offsetof"));return source("materialized",out.append('\n').toString());
    }
    private record Run(int code,String out,String err){}
    private static Run success(List<String> args,Path work)throws Exception {
        Path out=Files.createTempFile(work,"out",".txt"),err=Files.createTempFile(work,"err",".txt");
        Process process=new ProcessBuilder(args).directory(work.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        try {assertTrue(process.waitFor(60,TimeUnit.SECONDS),args.toString());var result=new Run(process.exitValue(),Files.readString(out),Files.readString(err));assertEquals(0,result.code(),()->args+"\n"+result.out()+result.err());return result;}
        finally{if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
    }
    private static String hash(Path path)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
