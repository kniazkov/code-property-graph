/* Strict C++03 native oracle for the named bool bit-field layout policy extension. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.analysis.ConstantFolder;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.math.BigInteger;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BoolBitFieldLayoutIT {
    @TempDir Path temp;
    private int nativeExecution;
    private static final PlatformProfile PROFILE=PlatformProfile.builder().offsetof(
            "gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,
            "x86_64-sysv-bool-v6").build();
    private static final String PROGRAM="""
            #include <cstddef>
            struct S {char prefix;bool bits:1;char tail;};
            struct Mixed {unsigned int a:31;bool b:2;char tail;};
            struct Crossing {bool a:7;bool b:2;char tail;};
            struct W3 {bool b:3;char tail;};struct W4 {bool b:4;char tail;};
            struct W5 {bool b:5;char tail;};struct W6 {bool b:6;char tail;};
            union U {bool b:8;char tail;};
            typedef const bool Flag;
            struct Inner {Flag flag:8;char tail;};
            struct Nested {char prefix;Inner a[2];char tail;};
            struct Old {char big:9;char tail;};
            unsigned long selected=offsetof(S,tail);
            unsigned long mixed=offsetof(Mixed,tail);
            unsigned long crossing=offsetof(Crossing,tail);
            unsigned long nested=offsetof(Nested,tail);
            unsigned long old=offsetof(Old,tail);
            enum {extent=offsetof(S,tail)+1};
            char array[extent];
            int main(){return 0;}
            """;
    private static Cpg.Options options(Cpg.Stage stage){return new Cpg.Options(Language.CPP,stage,"c++03").withPlatform(PROFILE);}
    private static void healthy(Project p){assertFalse(p.hasErrors(),()->p.parseProblems()+" / "+p.semanticDiagnostics());}
    private static <T extends Node> List<T> nodes(Node root,Class<T> type){
        var list=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))list.add(type.cast(n));}});return list;
    }
    private Path source(String label,String text)throws Exception {
        Path file=Files.createTempDirectory(temp,label).resolve("main.cpp");Files.writeString(file,text);return file;
    }
    private static String generated(Project p){healthy(p);return Cpg.sources(p,Language.CPP).get("main.cpp");}
    private static Project edit(Project p,String type,int width){
        var field=nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals("bits")&&v.bitWidth().isPresent()).findFirst().orElseThrow();
        var editor=new ProjectEditor(p);editor.replace(field.type().orElseThrow(),NamedType.create().setName(type).build());
        editor.replace(field.bitWidth().orElseThrow(),IntegerLiteral.create().setValue(width).build());return NameResolver.resolve(editor.build());
    }
    @Test void originalsStagesCopiesEditsReparseAndCli()throws Exception {
        verify(source("original",PROGRAM),false);
        for(var stage:List.of(Cpg.Stage.RESOLVED,Cpg.Stage.GREEN,Cpg.Stage.FOLDED)){
            var p=Cpg.read(source("input",PROGRAM).getParent(),options(stage));healthy(p);
            assertTrue(ProjectJson.encode(p).toText("  ").contains("x86_64-sysv-bool-v6"));
            var copy=NameResolver.resolve(p.edit().build());assertEquals(generated(p),generated(copy));
            verify(source("generated",generated(p)),false);verify(source("copy",generated(copy)),false);
            var wide=edit(p,"bool",8);verify(source("width",generated(wide)),false);
            var typed=edit(wide,"int",9);verify(source("type",generated(typed)),true);
            var reparse=Cpg.read(source("reparse",generated(typed)).getParent(),options(Cpg.Stage.RESOLVED));verify(source("reparsed",generated(reparse)),true);
            var reverse=edit(typed,"bool",1);verify(source("reversed",generated(reverse)),false);
        }
        var cli=new CliSandbox(Files.createTempDirectory(temp,"cli")).source("main.cpp",PROGRAM);
        Path config=temp.resolve("profile.json");Files.writeString(config,Json.serialize(PROFILE.toSettings()));
        var run=cli.run("cpp","--stage","folded","--cpp-standard","c++03","--platform-profile",config.toString()).expectStatus(0);
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("x86_64-sysv-bool-v6"));
        verify(run.work().resolve("generated/main.cpp"),false);
    }
    @Test void unsupportedBoundariesNeverRunNatively()throws Exception {
        for(String field:List.of("bool bits:9;","bool bits:0;","bool :0;","bool :3;","enum E{a,b};E bits:1;")){
            var cli=new CliSandbox(Files.createTempDirectory(temp,"negative")).source("main.cpp","#include <cstddef>\nstruct S{char c;"+field+"char tail;};unsigned long result=offsetof(S,tail);");
            var p=Cpg.read(cli.input,options(Cpg.Stage.RESOLVED));assertTrue(p.hasErrors());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
            Path config=cli.input.resolve("profile.json");Files.writeString(config,Json.serialize(PROFILE.toSettings()));
            var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",config.toString()).expectStatus(1);
            assertFalse(Files.exists(run.work().resolve("generated/main.cpp")));
        }
    }
    private void verify(Path input,boolean integral)throws Exception {
        healthy(Cpg.read(input.getParent(),options(Cpg.Stage.RESOLVED)));
        Path expanded=materialize(input);healthy(Cpg.read(expanded.getParent(),options(Cpg.Stage.RESOLVED)));
        for(Path tested:List.of(input,expanded))for(String compiler:List.of("g++","clang++")){
            Path work=Files.createTempDirectory(temp,"native"),wrapper=work.resolve("wrapper.cpp"),binary=work.resolve("probe");
            Files.writeString(wrapper,"""
                    #include <cstddef>
                    #include <climits>
                    #if !defined(__x86_64__) || !defined(__LP64__)
                    #error selected target mismatch
                    #endif
                    #define main tested_main
                    #include "%s"
                    #undef main
                    int main(){
                        if(CHAR_BIT!=8 || sizeof(bool)!=1 || sizeof(int)!=4 || sizeof(long)!=8) return 10;
                        if(selected!=%d || sizeof(S)!=%d || mixed!=5 || sizeof(Mixed)!=8 || crossing!=2 || sizeof(Crossing)!=3) return 11;
                        if(__builtin_offsetof(W3,tail)!=1 || sizeof(W3)!=2 || __builtin_offsetof(W4,tail)!=1 || sizeof(W4)!=2) return 14;
                        if(__builtin_offsetof(W5,tail)!=1 || sizeof(W5)!=2 || __builtin_offsetof(W6,tail)!=1 || sizeof(W6)!=2) return 15;
                        if(nested!=5 || sizeof(Nested)!=6 || sizeof(U)!=1 || old!=2 || sizeof(Old)!=3) return 12;
                        struct A {char c;S s;};struct B {char c;Mixed m;};struct C {char c;U u;};
                        if(__builtin_offsetof(A,s)!=%d || __builtin_offsetof(B,m)!=4 || __builtin_offsetof(C,u)!=1 || sizeof(array)!=%d) return 13;
                        return tested_main();
                    }
                    """.formatted(tested.toAbsolutePath().toString().replace("\\","\\\\").replace("\"","\\\""),integral?3:2,integral?4:3,integral?4:1,integral?4:3));
            List<String> command=List.of(compiler,"-std=c++03","-pedantic-errors",wrapper.toString(),"-o",binary.toString());
            success(command,work);Run result=success(List.of(binary.toString()),work);
            Path evidence=Files.createDirectories(Path.of("target/bool-bitfield-oracle",String.format("%03d-%s",++nativeExecution,compiler)));
            Files.copy(tested,evidence.resolve("tested.cpp"),StandardCopyOption.REPLACE_EXISTING);
            Files.copy(wrapper,evidence.resolve("wrapper.cpp"),StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(evidence.resolve("observations.txt"),"exit=0 selected="+(integral?3:2)+" size="+(integral?4:3)+" alignment="+(integral?4:1)+"\n");
            Path profile=evidence.resolve("profile.json");Files.writeString(profile,Json.serialize(canonical(PROFILE.toSettings())));
            var metadata=new TreeMap<String,Object>();metadata.put("compiler",compiler);metadata.put("version",success(List.of(compiler,"--version"),work).out());
            metadata.put("target",success(List.of(compiler,"-dumpmachine"),work).out());metadata.put("command",command);
            metadata.put("sourceSha256",hash(tested));metadata.put("compiledSourceSha256",hash(wrapper));metadata.put("materialized",tested.equals(expanded));
            metadata.put("profileFileSha256",hash(profile));metadata.put("stableIdentity",PROFILE.identityHash());
            Files.writeString(evidence.resolve("metadata.json"),Json.serialize(metadata));
        }
    }
    private static Object canonical(Object value){
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}
        if(value instanceof List<?> list)return list.stream().map(BoolBitFieldLayoutIT::canonical).toList();return value;
    }
    private Path materialize(Path file)throws Exception {
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(file),file.getParent(),List.of(),List.of(),"c++03").withPlatform(PROFILE);
        var result=CppPreprocessor.preprocess(unit,Files.readString(file));
        assertTrue(result.expansions().stream().anyMatch(e->e.name().equals("offsetof")));
        StringBuilder out=new StringBuilder();boolean body=false;
        for(var expanded:result.tokens()){var token=expanded.token();
            if(token.kind()==PreprocessingToken.Kind.HEADER_NAME){assertFalse(body);out.append("#include ").append(token.spelling()).append('\n');}
            else if(token.kind()!=PreprocessingToken.Kind.EOF){if(!body){out.append("#undef offsetof\n");body=true;}assertNotEquals("offsetof",token.spelling());out.append(token.spelling()).append(' ');}
        }
        assertTrue(out.toString().contains("__builtin_offsetof"));return source("materialized",out.append('\n').toString());
    }
    private record Run(int code,String out,String err){}
    private static Run success(List<String> args,Path work)throws Exception {
        Path out=Files.createTempFile(work,"out",".txt"),err=Files.createTempFile(work,"err",".txt");
        Process process=new ProcessBuilder(args).directory(work.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        try{assertTrue(process.waitFor(60,TimeUnit.SECONDS),args.toString());var result=new Run(process.exitValue(),Files.readString(out),Files.readString(err));assertEquals(0,result.code(),()->args+"\n"+result.out()+result.err());return result;}
        finally{if(process.isAlive()){process.destroyForcibly();process.waitFor();}}
    }
    private static String hash(Path path)throws Exception{return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));}
}
