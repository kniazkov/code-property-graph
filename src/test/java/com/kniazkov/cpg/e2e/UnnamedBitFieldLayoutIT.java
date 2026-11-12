/* Strict C++03 native oracle for ordinary members beside selected bit-fields. MIT License. */
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

class UnnamedBitFieldLayoutIT {
    @TempDir Path temp;
    private int nativeExecution;
    private static final PlatformProfile PROFILE=PlatformProfile.builder().offsetof(
            "gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,
            "x86_64-sysv-unsigned-v3").build();
    private static final String PROGRAM="""
            #include <cstddef>
            struct Selected {char prefix;unsigned int :3;char tail;};
            struct Typed {char prefix;unsigned short :0;char tail;};
            struct Nested {char prefix;Typed values[2];char tail;};
            struct Crossing {char prefix;unsigned long :57;char tail;};
            struct Trailing {char prefix;unsigned long :0;};
            struct TrailingBox {Trailing values[2];char tail;};
            struct Consecutive {char prefix;unsigned short :0;unsigned long :0;unsigned int :0;char tail;};
            struct LeadingZero {unsigned long :0;char first;char tail;};
            struct AllZero {unsigned char :0;unsigned short :0;unsigned int :0;unsigned long :0;};
            struct EmptyBox {char prefix;AllZero values[2];char tail;};
            union Choice {unsigned long :64;char bytes[3];unsigned short :0;};
            struct Named {unsigned short named:3;unsigned long :0;char tail;};
            typedef unsigned long Padding;
            struct Alias {char prefix;Padding :0;char tail;};
            struct CharZero {char prefix;unsigned char :0;char tail;};
            struct PositiveTrailing {char prefix;unsigned long :3;};
            struct PositiveBox {PositiveTrailing value;char tail;};
            struct ShortPositive {char prefix;unsigned short :9;char tail;};
            struct CharPositive {char prefix;unsigned char :8;char tail;};
            struct MixedPadding {char prefix;unsigned char :7;unsigned short :10;unsigned int :20;unsigned long :40;char tail;};
            union ZeroUnion {unsigned long :0;};
            struct ZeroUnionBox {char prefix;ZeroUnion value;char tail;};
            unsigned long shortPositive=offsetof(ShortPositive,tail);
            unsigned long charPositive=offsetof(CharPositive,tail);
            unsigned long mixedPadding=offsetof(MixedPadding,tail);
            unsigned long zeroUnion=offsetof(ZeroUnionBox,tail);
            unsigned long selected=offsetof(Selected,tail);
            unsigned long typed=offsetof(Typed,tail);
            unsigned long nested=offsetof(Nested,tail);
            unsigned long element=offsetof(Nested,values[1].tail);
            unsigned long crossing=offsetof(Crossing,tail);
            unsigned long trailing=offsetof(TrailingBox,tail);
            unsigned long consecutive=offsetof(Consecutive,tail);
            unsigned long leading=offsetof(LeadingZero,tail);
            unsigned long empty=offsetof(EmptyBox,tail);
            unsigned long choice=offsetof(Choice,bytes[2]);
            unsigned long named=offsetof(Named,tail);
            unsigned long alias=offsetof(Alias,tail);
            unsigned long charZero=offsetof(CharZero,tail);
            unsigned long positiveTrailing=offsetof(PositiveBox,tail);
            enum {extent=offsetof(Selected,tail)+1};
            char array[extent];
            int exact(std::size_t){return 1;}
            template<class T> int exact(T){return 0;}
            int exact_result=exact(offsetof(Selected,tail));
            int check_case(int n){switch(n){case offsetof(Selected,tail):return 1;default:return 0;}}
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
        for(String name:List.of("selected","typed","nested","element","crossing","trailing","consecutive",
                "leading","empty","choice","named","alias","charZero","positiveTrailing","shortPositive","charPositive","mixedPadding","zeroUnion")){
            var variable=nodes(folded.files().getFirst(),VariableDeclaration.class).stream()
                    .filter(n->n.name().equals(name)&&n.initializer().isPresent()).findFirst().orElseThrow();
            assertInstanceOf(OffsetofExpression.class,variable.initializer().orElseThrow(),name);
        }
        assertEquals(BigInteger.valueOf(widthEdit?0:3),assertInstanceOf(IntegerLiteral.class,padding(folded,"Selected").bitWidth().orElseThrow()).bigValue());
        assertEquals(typeEdit?FundamentalKind.UNSIGNED_LONG:FundamentalKind.UNSIGNED_SHORT,
                assertInstanceOf(NamedType.class,padding(folded,"Typed").type().orElseThrow()).fundamentalKind().orElseThrow());
    }
    private static VariableDeclaration padding(Project p,String owner){
        return nodes(p.files().getFirst(),ClassDeclaration.class).stream().filter(c->c.name().equals(owner)).findFirst().orElseThrow()
                .members().stream().filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast)
                .filter(v->v.name().isEmpty()&&v.bitWidth().isPresent()).findFirst().orElseThrow();
    }
    private static Project widthEdit(Project p,int width){
        var editor=new ProjectEditor(p);editor.replace(padding(p,"Selected").bitWidth().orElseThrow(),IntegerLiteral.create().setValue(BigInteger.valueOf(width)).build());
        return NameResolver.resolve(editor.build());
    }
    private static Project typeEdit(Project p,String type){
        var editor=new ProjectEditor(p);editor.replace(padding(p,"Typed").type().orElseThrow(),NamedType.create().setName(type).build());
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
            Project width=widthEdit(p,0);snapshot(width,true,false);
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
    @Test void previousPoliciesAndUnsupportedPaddingAreRejectedWithoutNativeExecution()throws Exception {
        for(int fixture=0;fixture<7;fixture++){
            String version=fixture==0?"x86_64-sysv-unsigned-v1":fixture==1?"x86_64-sysv-unsigned-v2":"x86_64-sysv-unsigned-v3";
            var profile=PlatformProfile.builder().offsetof("gcc-clang-builtin","x86_64-sysv-lp64-v1","2147483647",null,null,version).build();
            String input=switch(fixture){
                case 0,1->PROGRAM;
                case 2->"#include <cstddef>\nstruct S {unsigned int :3;char tail;};unsigned long result=offsetof(S,tail);";
                case 3->"#include <cstddef>\nstruct S {char prefix;private:unsigned int :3;public:char tail;};unsigned long result=offsetof(S,tail);";
                case 4->"#include <cstddef>\nstruct S {char prefix;const unsigned int :3;char tail;};unsigned long result=offsetof(S,tail);";
                case 5->PROGRAM.replace("offsetof(Named,tail)","offsetof(Named,named)");
                default->"#include <cstddef>\nstruct Padding {unsigned int :3;};struct S {char prefix;Padding value;char tail;};unsigned long result=offsetof(S,tail);";
            };
            var cli=new CliSandbox(Files.createTempDirectory(temp,"unsupported")).source("main.cpp",input);
            var p=Cpg.read(cli.input,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03").withPlatform(profile));
            assertTrue(p.hasErrors());assertTrue(p.semanticDiagnostics().stream().anyMatch(d->d.code().equals("unsupported-offsetof")),p.semanticDiagnostics().toString());
            assertThrows(IllegalArgumentException.class,()->Cpg.sources(p,Language.CPP));
            Path config=temp.resolve("unsupported-"+fixture+".json");Files.writeString(config,Json.serialize(profile.toSettings()));
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
                        if(shortPositive!=4 || sizeof(ShortPositive)!=5 || charPositive!=2 || sizeof(CharPositive)!=3
                                || mixedPadding!=13 || sizeof(MixedPadding)!=14 || zeroUnion!=2 || sizeof(ZeroUnion)!=1 || sizeof(ZeroUnionBox)!=3) return 19;
                        if(selected!=%d || sizeof(Selected)!=%d || typed!=%d || sizeof(Typed)!=%d) return 11;
                        if(nested!=%d || element!=%d || sizeof(Nested)!=%d) return 12;
                        if(crossing!=16 || sizeof(Crossing)!=17 || trailing!=16 || sizeof(Trailing)!=8 || sizeof(TrailingBox)!=17) return 13;
                        if(consecutive!=8 || sizeof(Consecutive)!=9 || leading!=1 || sizeof(LeadingZero)!=2) return 14;
                        if(empty!=3 || sizeof(AllZero)!=1 || sizeof(EmptyBox)!=4 || choice!=2 || sizeof(Choice)!=8) return 15;
                        if(named!=8 || sizeof(Named)!=10 || alias!=8 || sizeof(Alias)!=9 || charZero!=1 || sizeof(CharZero)!=2
                                || positiveTrailing!=2 || sizeof(PositiveTrailing)!=2 || sizeof(PositiveBox)!=3) return 16;
                        struct ASelected {char c;Selected v;};struct ATyped {char c;Typed v;};struct ANamed {char c;Named v;};
                        struct AChoice {char c;Choice v;};struct AEmpty {char c;AllZero v;};
                        if(__builtin_offsetof(ASelected,v)!=1 || __builtin_offsetof(ATyped,v)!=1 || __builtin_offsetof(ANamed,v)!=2
                                || __builtin_offsetof(AChoice,v)!=1 || __builtin_offsetof(AEmpty,v)!=1) return 17;
                        if(exact_result!=1 || sizeof(array)!=%d || check_case(%d)!=1) return 18;
                        std::printf("selected=%%lu typed=%%lu nested=%%lu element=%%lu extent=%%lu\\n",
                                selected,typed,nested,element,(unsigned long)sizeof(array));
                        return tested_main();
                    }
                    """.formatted(tested.toAbsolutePath().toString().replace("\\","\\\\").replace("\"","\\\""),
                            widthEdit?4:2,widthEdit?5:3,typeEdit?8:2,typeEdit?9:3,typeEdit?19:7,typeEdit?18:6,typeEdit?20:8,widthEdit?5:3,widthEdit?4:2));
            List<String> command=List.of(compiler,"-std=c++03","-pedantic-errors",wrapper.toString(),"-o",binary.toString());
            success(command,work);Run result=success(List.of(binary.toString()),work);
            assertEquals("selected="+(widthEdit?4:2)+" typed="+(typeEdit?8:2)+" nested="+(typeEdit?19:7)+" element="+(typeEdit?18:6)+" extent="+(widthEdit?5:3)+"\n",result.out());
            Path evidence=Files.createDirectories(Path.of("target/unnamed-bit-field-layout-oracle",String.format("%03d-%s",++nativeExecution,compiler)));
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
        if(value instanceof List<?> list)return list.stream().map(UnnamedBitFieldLayoutIT::canonical).toList();return value;
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
