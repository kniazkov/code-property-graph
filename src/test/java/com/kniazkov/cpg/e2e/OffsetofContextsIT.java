/* Independent strict C++03 oracle for context-local offsetof layout. MIT License. */
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

class OffsetofContextsIT {
    @TempDir Path temp;
    private int nativeExecution;
    private static final PlatformProfile PROFILE=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build();
    private static final String PROGRAM="""
            #include <cstddef>
            template<class T> struct Box {char c;T member;};
            template<> struct Box<short> {char c;double member;};
            template<class T> struct Box<T*> {char c;T* member;};
            template<class T,int N> struct Seq {char c;T items[N];};
            typedef Box<int> Selected;
            typedef Box<short> Full;
            typedef Box<long*> Partial;
            typedef Seq<int,3> Fixed;
            union Payload {char bytes[9];double weight;};
            typedef Payload Pair[2];
            int calls=0; Payload object;
            Payload make(){++calls;return object;}
            const unsigned long count=sizeof(Pair);
            struct Owner {char data[count];int tail;};
            unsigned long primary=offsetof(Selected,member);
            unsigned long full=offsetof(Full,member);
            unsigned long partial=offsetof(Partial,member);
            unsigned long fixed=offsetof(Fixed,items[2]);
            unsigned long tail=offsetof(Owner,tail);
            unsigned long index=offsetof(Owner,data[sizeof(make())-1]);
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
    private static VariableDeclaration variable(Project p,String name){return nodes(p.files().getFirst(),VariableDeclaration.class).stream().filter(v->v.name().equals(name)).findFirst().orElseThrow();}
    private Path source(String label,String text)throws Exception {Path dir=Files.createTempDirectory(temp,label);Path file=dir.resolve("main.cpp");Files.writeString(file,text);return file;}
    @Test void actualTemplateAndUnionMacroExpansionsSurviveStagesCopiesCliAndEdits() throws Exception {
        Path input=source("input",PROGRAM);
        for(var stage:List.of(Cpg.Stage.RESOLVED,Cpg.Stage.GREEN,Cpg.Stage.FOLDED)) {
            Project p=Cpg.read(input.getParent(),options(stage));healthy(p);
            for(Project q:List.of(p,p.edit().build(),NameResolver.resolve(p.edit().build()))) {
                healthy(q); assertFalse(q.classTemplateInstances().isEmpty());
                assertTrue(ProjectJson.encode(q).toText("  ").contains("OffsetofExpression"));
            }
            Path generated=source("generated-"+stage,Cpg.sources(p,Language.CPP).get("main.cpp"));
            verify(generated,false);
            Project copied=NameResolver.resolve(p.edit().build());healthy(copied);
            verify(source("copied-"+stage,Cpg.sources(copied,Language.CPP).get("main.cpp")),false);
            var editor=new ProjectEditor(p);
            var alias=nodes(p.files().getFirst(),TypedefDeclaration.class).stream().filter(t->t.name().equals("Selected")).findFirst().orElseThrow();
            var argument=nodes(alias,NamedType.class).stream().filter(t->t.name().equals("int")).findFirst().orElseThrow();
            editor.replace(argument,NamedType.create().setName("long").build());
            var bound=((ArrayType)variable(p,"bytes").type().orElseThrow()).bound().orElseThrow();
            editor.replace(bound,IntegerLiteral.create().setValue(17).build());
            Project edited=NameResolver.resolve(editor.build());healthy(edited);
            String text=Cpg.sources(edited,Language.CPP).get("main.cpp");
            assertTrue(text.replace(" ","").contains("Box<long>"));assertTrue(text.contains("17"));
            verify(source("edited-"+stage,text),true);
        }
        verify(input,false);
        var cli=new CliSandbox(Files.createTempDirectory(temp,"cli")).source("main.cpp",PROGRAM);
        Path profile=temp.resolve("profile.json");Files.writeString(profile,Json.serialize(PROFILE.toSettings()));
        var run=cli.run("cpp","--stage","folded","--cpp-standard","c++03","--platform-profile",profile.toString()).expectStatus(0);
        assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
        verify(run.work().resolve("generated/main.cpp"),false);
    }

    @Test void foldedTypeArgumentEditReselectsNativeSpecialization() throws Exception {
        for (boolean alias : List.of(false, true)) verifyTypeArgumentReselection(alias);
    }
    private void verifyTypeArgumentReselection(boolean alias) throws Exception {
        String argument = alias ? "typedef char Shape[sizeof(Giant)-15];typedef Box<Shape> Selected;"
                : "typedef Box<char[sizeof(Giant)-15]> Selected;";
        String text="#include <cstddef>\nstruct Giant {char bytes[16];};template<class T> struct Box {char data;char tail;};"
                +"template<> struct Box<char[1]> {long data;char tail;};"+argument
                +"unsigned long result=offsetof(Selected,tail);int main(){return 0;}\n";
        Path original=source("specialization",text);
        Project folded=Cpg.read(original.getParent(),options(Cpg.Stage.FOLDED));healthy(folded);
        assertFalse(nodes(folded.files().getFirst(),SizeofExpression.class).isEmpty());
        var editor=new ProjectEditor(folded);
        editor.replace(((ArrayType)variable(folded,"bytes").type().orElseThrow()).bound().orElseThrow(),IntegerLiteral.create().setValue(17).build());
        Project edited=NameResolver.resolve(editor.build());healthy(edited);
        for(boolean changed:List.of(false,true)) {
            Path generated=source("reselected",Cpg.sources(changed?edited:folded,Language.CPP).get("main.cpp"));
            healthy(Cpg.read(generated.getParent(),options(Cpg.Stage.RESOLVED)));
            Path expanded=materialize(generated);
            for(Path tested:List.of(generated,expanded)) for(String compiler:List.of("g++","clang++")) {
                Path work=Files.createTempDirectory(temp,"specialization-native");Path wrapper=work.resolve("wrapper.cpp"),binary=work.resolve("probe");
                Files.writeString(wrapper,"#include <cstddef>\nstruct Host {"+(changed?"char":"long")+" data;char tail;};"
                        +"const unsigned long expected=offsetof(Host,tail);\n#define main tested_main\n#include \""
                        +tested.toAbsolutePath().toString().replace("\\","\\\\").replace("\"","\\\"")+"\"\n#undef main\n"
                        +"int main(){return result==expected && result=="+(changed?1:8)+" ? tested_main() : 1;}\n");
                List<String> command=List.of(compiler,"-std=c++03","-pedantic-errors",wrapper.toString(),"-o",binary.toString());
                success(command,work);success(List.of(binary.toString()),work);
                Path evidence=Files.createDirectories(Path.of("target/offsetof-context-oracle/reselection").resolve(String.format("%03d-%s", ++nativeExecution, hash(tested).substring(0,16))).resolve(compiler.equals("g++")?"gcc":"clang"));
                Files.writeString(evidence.resolve("tested.cpp"),Files.readString(tested));Files.writeString(evidence.resolve("wrapper.cpp"),Files.readString(wrapper));
                Files.writeString(evidence.resolve("metadata.json"),Json.serialize(Map.of("command",command,"profile",PROFILE.toSettings(),"expected",changed?1:8,"materialized",tested.equals(expanded),"sourceSha256",hash(tested))));
            }
        }
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
                    struct HostPrimary {char c;%s member;};
                    union HostPayload {char bytes[%d];double weight;};
                    struct HostOwner {char data[sizeof(HostPayload[2])];int tail;};
                    const unsigned long host_primary=offsetof(HostPrimary,member);
                    const unsigned long host_tail=offsetof(HostOwner,tail);
                    #define main tested_main
                    #include "%s"
                    #undef main
                    int main(){
                        if(CHAR_BIT!=8 || sizeof(int)!=4 || sizeof(long)!=8 || sizeof(void*)!=8) return 10;
                        if(primary!=host_primary || tail!=host_tail || index!=sizeof(HostPayload)-1) return 11;
                        if(full!=8 || partial!=8 || fixed!=12 || exact_result!=1 || calls!=0) return 12;
                        if(sizeof(array)!=host_tail+1 || check_case(host_tail)!=1) return 13;
                        std::printf("primary=%%lu tail=%%lu index=%%lu extent=%%lu\\n",primary,tail,index,(unsigned long)sizeof(array));
                        return tested_main();
                    }
                    """.formatted(edited?"long":"int",edited?17:9,tested.toAbsolutePath().toString().replace("\\","\\\\").replace("\"","\\\"")));
            List<String> command=List.of(compiler,"-std=c++03","-pedantic-errors",wrapper.toString(),"-o",binary.toString());
            success(command,work);Run result=success(List.of(binary.toString()),work);
            assertEquals(edited?"primary=8 tail=48 index=23 extent=49\n":"primary=4 tail=32 index=15 extent=33\n",result.out());
            Path artifact=Files.createDirectories(Path.of("target/offsetof-context-oracle").resolve(String.format("%03d-%s", ++nativeExecution, hash(tested).substring(0,16))).resolve(compiler.equals("g++")?"gcc":"clang"));
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
