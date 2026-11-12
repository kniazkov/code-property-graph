/* Strict C++03 native oracle for the unsigned-short/long bit-field policy extension. MIT License. */
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

class ShortLongBitFieldLayoutIT {
    @TempDir Path temp;
    private int nativeExecution;
    private static final PlatformProfile PROFILE=PlatformProfile.builder().offsetof(
            "gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,
            "x86_64-sysv-unsigned-v2").build();
    private static final String PROGRAM="""
            #include <cstddef>
            struct Short {unsigned long selected:3;char tail;};
            struct FullShort {unsigned short bits:16;char tail;};
            struct MinLong {unsigned long bits:1;char tail;};
            struct Full {unsigned long full:64;char tail;};
            struct Crossing {unsigned short first:15;unsigned short second:2;char tail;};
            struct Mixed {unsigned short first:15;unsigned long second:2;char tail;};
            struct Reverse {unsigned long first:15;unsigned short second:2;char tail;};
            struct NarrowBoundary {unsigned long a:13;unsigned short b:4;char tail;};
            struct WideCrossByte {unsigned short a:15;unsigned long b:34;char tail;};
            struct Prefix {char prefix;unsigned short bits:9;char tail;};
            struct Fits {char prefix;unsigned short bits:8;char tail;};
            union Choice {unsigned long bits:3;char bytes[9];};
            struct Typed {unsigned short typed:3;char tail;};
            struct Nested {char prefix;Typed values[2];char tail;};
            typedef unsigned short US;typedef unsigned long UL;
            struct Alias {const volatile US s:9; const UL l:40; char tail;};
            struct AllBases {unsigned char a:7;unsigned short b:10;unsigned int c:20;unsigned long d:40;char tail;};
            struct AllReverse {unsigned long a:31;unsigned int b:2;unsigned char c:7;unsigned short d:9;char tail;};
            struct LongFits {char prefix;unsigned long bits:56;char tail;};
            struct LongCross {char prefix;unsigned long bits:57;char tail;};
            struct LongSplit {unsigned long a:63;unsigned long b:2;char tail;};
            unsigned long allBases=offsetof(AllBases,tail);
            unsigned long allReverse=offsetof(AllReverse,tail);
            unsigned long longFits=offsetof(LongFits,tail);
            unsigned long longCross=offsetof(LongCross,tail);
            unsigned long longSplit=offsetof(LongSplit,tail);
            unsigned long selected=offsetof(Short,tail);
            unsigned long fullShort=offsetof(FullShort,tail);
            unsigned long minLong=offsetof(MinLong,tail);
            unsigned long full=offsetof(Full,tail);
            unsigned long crossing=offsetof(Crossing,tail);
            unsigned long mixed=offsetof(Mixed,tail);
            unsigned long reverse=offsetof(Reverse,tail);
            unsigned long narrowBoundary=offsetof(NarrowBoundary,tail);
            unsigned long wideCrossByte=offsetof(WideCrossByte,tail);
            unsigned long prefix=offsetof(Prefix,tail);
            unsigned long fits=offsetof(Fits,tail);
            unsigned long choice=offsetof(Choice,bytes[8]);
            unsigned long alias=offsetof(Alias,tail);
            unsigned long nested=offsetof(Nested,tail);
            unsigned long element=offsetof(Nested,values[1].tail);
            enum {extent=offsetof(Short,tail)+1};
            char array[extent];
            int exact(std::size_t){return 1;}
            template<class T> int exact(T){return 0;}
            int exact_result=exact(offsetof(Short,tail));
            int check_case(int n){switch(n){case offsetof(Short,tail):return 1;default:return 0;}}
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
    private static void snapshot(Project p,boolean widthEdit,boolean typeEdit){
        healthy(p);Project folded=ConstantFolder.fold(p);healthy(folded);
        for(String name:List.of("selected","fullShort","minLong","full","crossing","mixed","reverse","narrowBoundary","wideCrossByte",
                "prefix","fits","choice","alias","nested","element","allBases","allReverse","longFits","longCross","longSplit")){
            var variable=nodes(folded.files().getFirst(),VariableDeclaration.class).stream()
                    .filter(n->n.name().equals(name)&&n.initializer().isPresent()).findFirst().orElseThrow();
            // The written operands must survive folding so declaration edits cannot use a stale number.
            assertInstanceOf(OffsetofExpression.class,variable.initializer().orElseThrow(),name);
        }
        var selected=nodes(folded.files().getFirst(),VariableDeclaration.class).stream()
                .filter(v->v.name().equals("selected")&&v.bitWidth().isPresent()).findFirst().orElseThrow();
        assertEquals(BigInteger.valueOf(widthEdit?49:3),assertInstanceOf(IntegerLiteral.class,selected.bitWidth().orElseThrow()).bigValue());
        var typed=nodes(folded.files().getFirst(),VariableDeclaration.class).stream()
                .filter(v->v.name().equals("typed")&&v.bitWidth().isPresent()).findFirst().orElseThrow();
        assertEquals(typeEdit?FundamentalKind.UNSIGNED_LONG:FundamentalKind.UNSIGNED_SHORT,
                assertInstanceOf(NamedType.class,typed.type().orElseThrow()).fundamentalKind().orElseThrow());
    }

    private static Project widthEdit(Project p,int width){
        var field=nodes(p.files().getFirst(),VariableDeclaration.class).stream().filter(v->v.name().equals("selected")&&v.bitWidth().isPresent()).findFirst().orElseThrow();
        var editor=new ProjectEditor(p);editor.replace(field.bitWidth().orElseThrow(),IntegerLiteral.create().setValue(BigInteger.valueOf(width)).build());
        return NameResolver.resolve(editor.build());
    }
    private static Project typeEdit(Project p,String type){
        var field=nodes(p.files().getFirst(),VariableDeclaration.class).stream().filter(v->v.name().equals("typed")&&v.bitWidth().isPresent()).findFirst().orElseThrow();
        var editor=new ProjectEditor(p);editor.replace(field.type().orElseThrow(),NamedType.create().setName(type).build());
        return NameResolver.resolve(editor.build());
    }
    @Test void ordinaryOffsetsSurviveStagesCopiesReparseWidthAndTypeEditsAndCli()throws Exception {
        Path input=source("original",PROGRAM);verify(input,false,false);
        for(var stage:List.of(Cpg.Stage.RESOLVED,Cpg.Stage.GREEN,Cpg.Stage.FOLDED)){
            Project p=Cpg.read(input.getParent(),options(stage));snapshot(p,false,false);
            assertTrue(ProjectJson.encode(p).toText("  ").contains("bitFieldStorage"));
            Path emitted=source("generated-"+stage,generated(p));verify(emitted,false,false);
            snapshot(Cpg.read(emitted.getParent(),options(Cpg.Stage.RESOLVED)),false,false);
            Project copy=NameResolver.resolve(p.edit().build());snapshot(copy,false,false);
            verify(source("copy-"+stage,generated(copy)),false,false);
            Project width=widthEdit(p,49);snapshot(width,true,false);
            verify(source("width-"+stage,generated(width)),true,false);
            Project typed=typeEdit(width,"unsigned long");snapshot(typed,true,true);
            verify(source("type-"+stage,generated(typed)),true,true);
            Project reversed=typeEdit(widthEdit(typed,3),"unsigned short");snapshot(reversed,false,false);
            verify(source("reverse-"+stage,generated(reversed)),false,false);
            snapshot(p,false,false);assertEquals(generated(p),generated(copy));
        }
        var cli=new CliSandbox(Files.createTempDirectory(temp,"cli")).source("main.cpp",PROGRAM);
        Path profile=temp.resolve("profile.json");Files.writeString(profile,Json.serialize(PROFILE.toSettings()));
        var run=cli.run("cpp","--stage","folded","--cpp-standard","c++03","--platform-profile",profile.toString()).expectStatus(0);
        assertNotNull(Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("bitFieldStorage"));
        verify(run.work().resolve("generated/main.cpp"),false,false);
    }
    @Test void previousPolicyAndBitfieldDesignatorRemainUnsupportedWithoutNativeExecution()throws Exception {
        var old=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,"x86_64-sysv-unsigned-v1").build();
        for(boolean designator:List.of(false,true)){
            var cli=new CliSandbox(Files.createTempDirectory(temp,"unsupported")).source("main.cpp",
                    designator?PROGRAM.replace("offsetof(Short,tail)","offsetof(Short,selected)"):PROGRAM);
            var profile=designator?PROFILE:old;
            var p=Cpg.read(cli.input,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03").withPlatform(profile));
            assertTrue(p.hasErrors());assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-offsetof")),p.semanticDiagnostics().toString());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
            Path config=temp.resolve("unsupported-"+designator+".json");Files.writeString(config,Json.serialize(profile.toSettings()));
            var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",config.toString()).expectStatus(1);
            assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("unsupported-offsetof"));
            assertFalse(Files.exists(run.work().resolve("generated/main.cpp")));
        }
    }
    private void verify(Path input,boolean widthEdit,boolean typeEdit)throws Exception {
        snapshot(Cpg.read(input.getParent(),options(Cpg.Stage.RESOLVED)),widthEdit,typeEdit);
        Path expanded=materialize(input);snapshot(Cpg.read(expanded.getParent(),options(Cpg.Stage.RESOLVED)),widthEdit,typeEdit);
        for(Path tested:List.of(input,expanded))for(String compiler:List.of("g++","clang++")){
            Path work=Files.createTempDirectory(temp,"native"),wrapper=work.resolve("wrapper.cpp"),binary=work.resolve("probe");
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
                        if(CHAR_BIT!=8 || sizeof(short)!=2 || sizeof(int)!=4 || sizeof(long)!=8 || sizeof(void*)!=8) return 10;
                        if(selected!=%d || full!=8 || crossing!=3 || mixed!=3 || reverse!=3 || prefix!=4 || fits!=2 || choice!=8 || alias!=7) return 11;
                        if(allBases!=13 || sizeof(AllBases)!=16 || allReverse!=8 || sizeof(AllReverse)!=16) return 19;
                        if(longFits!=8 || sizeof(LongFits)!=16 || longCross!=16 || sizeof(LongCross)!=24
                                || longSplit!=9 || sizeof(LongSplit)!=16) return 20;
                        if(fullShort!=2 || minLong!=1 || sizeof(FullShort)!=4 || sizeof(MinLong)!=8) return 17;
                        if(narrowBoundary!=3 || wideCrossByte!=7 || sizeof(NarrowBoundary)!=8 || sizeof(WideCrossByte)!=8) return 16;
                        if(nested!=%d || element!=%d || sizeof(Typed)!=%d || sizeof(Nested)!=%d) return 12;
                        if(sizeof(Short)!=8 || sizeof(Full)!=16 || sizeof(Crossing)!=4 || sizeof(Mixed)!=8 || sizeof(Reverse)!=8
                                || sizeof(Prefix)!=6 || sizeof(Fits)!=4 || sizeof(Choice)!=16 || sizeof(Alias)!=8) return 13;
                        struct AShort {char c;Short v;};struct ATyped {char c;Typed v;};struct ACrossing {char c;Crossing v;};
                        if(__builtin_offsetof(ACrossing,v)!=2) return 18;
                        if(__builtin_offsetof(AShort,v)!=8 || __builtin_offsetof(ATyped,v)!=%d) return 14;
                        if(exact_result!=1 || sizeof(array)!=%d || check_case(%d)!=1) return 15;
                        std::printf("selected=%%lu crossing=%%lu mixed=%%lu narrow=%%lu wide=%%lu prefix=%%lu nested=%%lu element=%%lu extent=%%lu\\n",
                                selected,crossing,mixed,narrowBoundary,wideCrossByte,prefix,nested,element,(unsigned long)sizeof(array));
                        return tested_main();
                    }
                    """.formatted(tested.toAbsolutePath().toString().replace("\\","\\\\").replace("\"","\\\""),
                            widthEdit?7:1,typeEdit?24:6,typeEdit?17:5,typeEdit?8:2,typeEdit?32:8,typeEdit?8:2,widthEdit?8:2,widthEdit?7:1));
            List<String> command=List.of(compiler,"-std=c++03","-pedantic-errors",wrapper.toString(),"-o",binary.toString());
            success(command,work);Run result=success(List.of(binary.toString()),work);
            assertEquals("selected="+(widthEdit?7:1)+" crossing=3 mixed=3 narrow=3 wide=7 prefix=4 nested="+(typeEdit?24:6)+" element="+(typeEdit?17:5)+" extent="+(widthEdit?8:2)+"\n",result.out());
            Path evidence=Files.createDirectories(Path.of("target/short-long-bit-field-layout-oracle",String.format("%03d-%s",++nativeExecution,compiler)));
            Files.copy(tested,evidence.resolve("tested.cpp"),StandardCopyOption.REPLACE_EXISTING);
            Files.copy(wrapper,evidence.resolve("wrapper.cpp"),StandardCopyOption.REPLACE_EXISTING);
            Files.writeString(evidence.resolve("observations.txt"),result.out());
            Path profile=evidence.resolve("profile.json");Files.writeString(profile,Json.serialize(canonical(PROFILE.toSettings())));
            var metadata=new TreeMap<String,Object>();metadata.put("compiler",compiler);
            metadata.put("version",success(List.of(compiler,"--version"),work).out());metadata.put("target",success(List.of(compiler,"-dumpmachine"),work).out());
            metadata.put("command",command);metadata.put("sourceSha256",hash(tested));metadata.put("compiledSourceSha256",hash(wrapper));
            metadata.put("materialized",tested.equals(expanded));metadata.put("widthEdit",widthEdit);metadata.put("typeEdit",typeEdit);
            metadata.put("profileFileSha256",hash(profile));metadata.put("stableIdentity",PROFILE.identityHash());metadata.put("profile",canonical(PROFILE.toSettings()));
            Files.writeString(evidence.resolve("metadata.json"),Json.serialize(metadata));
        }
    }
    private static Object canonical(Object value){
        if(value instanceof Map<?,?> map){var sorted=new TreeMap<String,Object>();map.forEach((k,v)->sorted.put(k.toString(),canonical(v)));return sorted;}
        if(value instanceof List<?> list)return list.stream().map(ShortLongBitFieldLayoutIT::canonical).toList();return value;
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
