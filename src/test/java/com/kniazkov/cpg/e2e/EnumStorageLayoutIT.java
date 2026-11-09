/* Independent native evidence for the optional bounded enum-storage profile. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
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

class EnumStorageLayoutIT {
    @TempDir Path temp;
    private int nativeExecution;
    private static final PlatformProfile PROFILE=PlatformProfile.builder().offsetof(
            "gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,
            "gcc-clang-x86_64-default-enum-v1").build();
    private static final String PROGRAM="""
            #include <cstddef>
            enum Selected {selectedValue=1};
            struct Owner {char prefix;Selected selected;char tail;};
            struct Bytes {char data[4];};
            unsigned long tail=offsetof(Owner,tail);
            unsigned long size=sizeof(Selected);
            unsigned long index=offsetof(Bytes,data[2]);
            enum {extent=offsetof(Owner,tail)+1};
            char array[extent];
            int exact(std::size_t){return 1;}
            template<class T> int exact(T){return 0;}
            int exact_result=exact(offsetof(Owner,tail));
            int check_case(int n){switch(n){case offsetof(Owner,tail):return 1;default:return 0;}}
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
    private static void snapshot(Project p,String name){
        healthy(p);
        var v=nodes(p.files().getFirst(),VariableDeclaration.class).stream().filter(n->n.name().equals(name) && n.initializer().isPresent()).findFirst().orElseThrow();
        assertTrue(v.initializer().isPresent(),name);
    }
    private static Project enumEdit(Project p,boolean wide){
        var e=nodes(p.files().getFirst(),EnumeratorDeclaration.class).stream().filter(n->n.name().equals("selectedValue")).findFirst().orElseThrow();
        var editor=new ProjectEditor(p);
        editor.replace(e.value().orElseThrow(),IntegerLiteral.create().setValue(BigInteger.valueOf(wide?4294967296L:1L)).setCppType(wide?"unsigned long":"int").build());
        return NameResolver.resolve(editor.build());
    }
    @Test void nativeStorageAcrossStagesCopiesReparseEditsAndPackagedCli()throws Exception {
        Path input=source("original",PROGRAM);verifyMain(input,false);
        for(var stage:List.of(Cpg.Stage.RESOLVED,Cpg.Stage.GREEN,Cpg.Stage.FOLDED)){
            Project p=Cpg.read(input.getParent(),options(stage));healthy(p);
            assertTrue(ProjectJson.encode(p).toText("  ").contains("enumStorage"));
            snapshot(p,"tail");snapshot(p,"index");
            Path emitted=source("generated-"+stage,generated(p));verifyMain(emitted,false);
            Project reparsed=Cpg.read(emitted.getParent(),options(Cpg.Stage.RESOLVED));snapshot(reparsed,"tail");
            Project copy=NameResolver.resolve(p.edit().build());snapshot(copy,"tail");
            verifyMain(source("copy-"+stage,generated(copy)),false);
            Project edited=enumEdit(p,true);snapshot(edited,"tail");
            verifyMain(source("wide-"+stage,generated(edited)),true);
            Project reversed=enumEdit(edited,false);snapshot(reversed,"tail");
            verifyMain(source("reverse-"+stage,generated(reversed)),false);
            snapshot(p,"tail");assertEquals(generated(p),generated(copy));
        }
        var cli=new CliSandbox(Files.createTempDirectory(temp,"cli")).source("main.cpp",PROGRAM);
        Path profile=temp.resolve("profile.json");Files.writeString(profile,Json.serialize(PROFILE.toSettings()));
        var run=cli.run("cpp","--stage","folded","--cpp-standard","c++03","--platform-profile",profile.toString()).expectStatus(0);
        assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("enumStorage"));
        verifyMain(run.work().resolve("generated/main.cpp"),false);
    }
    @Test void omittedPolicyIsUnsupportedInApiAndCli()throws Exception {
        var old=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647").build();
        var cli=new CliSandbox(Files.createTempDirectory(temp,"omitted")).source("main.cpp",PROGRAM);
        var p=Cpg.read(cli.input,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03").withPlatform(old));
        assertTrue(p.hasErrors());assertTrue(p.semanticDiagnostics().toString().contains("Enum storage requires"));
        assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
        Path profile=temp.resolve("omitted.json");Files.writeString(profile,Json.serialize(old.toSettings()));
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",profile.toString()).expectStatus(1);
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("Enum storage requires"));
        assertFalse(Files.exists(run.work().resolve("generated/main.cpp")));
    }
    @Test void lexicalNarrowTypesAndFoldedNonconstantObjectTypeEdits()throws Exception {
        StringBuilder text=new StringBuilder("#include <cstddef>\n");int i=0;
        for(String type:List.of("char","signed char","unsigned char","short","unsigned short")){
            text.append("enum N").append(i).append("{A").append(i).append("=static_cast<").append(type).append(">(1),B").append(i)
                .append("=static_cast<").append(type).append(">(2),D").append(i).append("=true?A").append(i).append(":B").append(i)
                .append(",C").append(i).append("=sizeof(true?A").append(i).append(":B").append(i).append("),F").append(i).append("=sizeof(D").append(i)
                .append(")};struct S").append(i).append("{char bytes[C").append(i).append("+F").append(i).append("];char tail;};unsigned long narrow")
                .append(i).append("=offsetof(S").append(i).append(",tail);\n");i++;
        }
        text.append("typedef int T;enum E{A=sizeof(T)*536870912UL};int calls=0;E get(){++calls;return A;}E object=get();")
            .append("struct S{char bytes[sizeof(+object)];char tail;};unsigned long result=offsetof(S,tail);int main(){return 0;}\n");
        Path input=source("lexical",text.toString());
        for(var stage:List.of(Cpg.Stage.GREEN,Cpg.Stage.FOLDED)){
            Project p=Cpg.read(input.getParent(),options(stage));snapshot(p,"result");
            for(i=0;i<5;i++)snapshot(p,"narrow"+i);
            Project edited=aliasEdit(p,"long");snapshot(edited,"result");
            Project reversed=aliasEdit(edited,"int");snapshot(reversed,"result");snapshot(p,"result");
            for(Project q:List.of(p,edited,reversed)){
                boolean wide=q==edited;
                verify(source("lexical-"+stage,generated(q)),"lexical",wide,
                        "if(result!="+(wide?8:4)+" || calls!=1 || narrow0!=2 || narrow1!=2 || narrow2!=2 || narrow3!=4 || narrow4!=4)return 20;\n"
                        +"std::printf(\"offset=%lu narrow=%lu calls=%d\\n\",result,narrow0,calls);\n",
                        "offset="+(wide?8:4)+" narrow=2 calls=1\n");
            }
        }
    }
    private static Project aliasEdit(Project p,String type){
        var alias=nodes(p.files().getFirst(),TypedefDeclaration.class).stream().filter(n->n.name().equals("T")).findFirst().orElseThrow();
        var editor=new ProjectEditor(p);editor.replace(alias.type(),NamedType.create().setName(type).build());return NameResolver.resolve(editor.build());
    }
    private void verifyMain(Path source,boolean wide)throws Exception {
        snapshot(Cpg.read(source.getParent(),options(Cpg.Stage.RESOLVED)),"tail");
        verify(source,"main",wide,"""
                if(tail!=%d || size!=%d || index!=2 || sizeof(Selected)!=%d) return 11;
                if(exact_result!=1 || sizeof(array)!=%d || check_case(%d)!=1) return 12;
                struct Alignment {char c;Selected value;};
                if(__builtin_offsetof(Alignment,value)!=%d) return 13;
                std::printf("tail=%%lu size=%%lu index=%%lu extent=%%lu\\n",tail,size,index,(unsigned long)sizeof(array));
                """.formatted(wide?16:8,wide?8:4,wide?8:4,wide?17:9,wide?16:8,wide?8:4),
                wide?"tail=16 size=8 index=2 extent=17\n":"tail=8 size=4 index=2 extent=9\n");
    }
    private void verify(Path input,String category,boolean edited,String checks,String expected)throws Exception {
        Path expanded=materialize(input);healthy(Cpg.read(expanded.getParent(),options(Cpg.Stage.RESOLVED)));
        for(Path tested:List.of(input,expanded))for(String compiler:List.of("g++","clang++")){
            Path work=Files.createTempDirectory(temp,"native"),wrapper=work.resolve("wrapper.cpp");
            Files.writeString(wrapper,"""
                    #include <cstddef>
                    #include <climits>
                    #include <cstdio>
                    #if !defined(__x86_64__) || !defined(__LP64__)
                    #error selected target mismatch
                    #endif
                    #define main tested_main
                    #include "%s"
                    #undef main
                    int main(){
                        if(CHAR_BIT!=8 || sizeof(int)!=4 || sizeof(long)!=8 || sizeof(void*)!=8) return 10;
                        %s
                        return tested_main();
                    }
                    """.formatted(tested.toAbsolutePath().toString().replace("\\","\\\\").replace("\"","\\\""),checks));
            nativeRun(compiler,wrapper,tested,category,edited,tested.equals(expanded),expected,true);
        }
    }
    /** Modern compiler observations include DR-era promotion and mixed ranges: comparison only. */
    @Test void modernNativeRangeComparisonIsNotOriginalCpp03Admission()throws Exception {
        String probe="""
                #include <cstdio>
                #include <climits>
                enum Empty{}; enum Small{S=1}; enum Negative{N=-1}; enum UInt{U=UINT_MAX};
                enum Wide{W=4294967296UL}; enum LongMax{L=LONG_MAX}; enum ULongMax{UL=ULONG_MAX};
                enum Mixed{M=-1,MX=UINT_MAX};
                int kind(int){return 1;} int kind(unsigned int){return 2;}
                int kind(long){return 3;} int kind(unsigned long){return 4;}
                int main(){
                    std::printf("storage=%lu,%lu,%lu,%lu,%lu,%lu,%lu,%lu promotion=%d,%d,%d,%d,%d,%d,%d\\n",
                        (unsigned long)sizeof(Empty),(unsigned long)sizeof(Small),(unsigned long)sizeof(Negative),
                        (unsigned long)sizeof(UInt),(unsigned long)sizeof(Wide),(unsigned long)sizeof(LongMax),
                        (unsigned long)sizeof(ULongMax),(unsigned long)sizeof(Mixed),kind(+S),kind(+N),kind(+U),kind(+W),kind(+L),kind(+UL),kind(+M));
                }
                """;
        Path file=source("comparison",probe);
        for(String compiler:List.of("g++","clang++"))nativeRun(compiler,file,file,"modern-comparison-only",false,false,
                "storage=4,4,4,4,8,8,8,8 promotion=1,1,2,3,3,4,3\n",false);
    }
    private void nativeRun(String compiler,Path wrapper,Path tested,String category,boolean edited,boolean materialized,String expected,boolean selected)throws Exception {
        Path work=wrapper.getParent(),binary=work.resolve("probe-"+compiler);
        List<String> command=List.of(compiler,"-std=c++03","-pedantic-errors",wrapper.toString(),"-o",binary.toString());
        success(command,work);Run result=success(List.of(binary.toString()),work);assertEquals(expected,result.out());
        Path evidence=Files.createDirectories(Path.of("target/enum-storage-layout-oracle",category,
                String.format("%03d-%s",++nativeExecution,compiler)));
        Files.copy(tested,evidence.resolve("tested.cpp"),StandardCopyOption.REPLACE_EXISTING);
        Files.copy(wrapper,evidence.resolve("wrapper.cpp"),StandardCopyOption.REPLACE_EXISTING);
        Files.writeString(evidence.resolve("observations.txt"),result.out());
        var metadata=new TreeMap<String,Object>();metadata.put("compiler",compiler);
        metadata.put("version",success(List.of(compiler,"--version"),work).out());
        metadata.put("target",success(List.of(compiler,"-dumpmachine"),work).out());
        metadata.put("command",command);metadata.put("sourceSha256",hash(tested));metadata.put("compiledSourceSha256",hash(wrapper));
        metadata.put("materialized",materialized);metadata.put("edited",edited);metadata.put("category",category);
        if(selected){
            Path profile=evidence.resolve("profile.json");Files.writeString(profile,Json.serialize(canonical(PROFILE.toSettings())));
            metadata.put("profileFileSha256",hash(profile));metadata.put("stableIdentity",PROFILE.identityHash());
            metadata.put("profile",canonical(PROFILE.toSettings()));
        }else metadata.put("scope","Modern native comparison only; not original C++03 normative proof or CPG admission");
        Files.writeString(evidence.resolve("metadata.json"),Json.serialize(metadata));
    }
    private static Object canonical(Object value){
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}
        if(value instanceof List<?> list)return list.stream().map(EnumStorageLayoutIT::canonical).toList();return value;
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
