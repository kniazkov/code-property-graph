/* C++03 target library macros through API, CLI, edits and native generation. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class LibraryMacrosIT {
    @TempDir Path temp;
    @Test void profilesProvenanceApiCliCopyAndEditedGeneration() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", Files.readString(Path.of("src/test/resources/library-macros/runtime.cpp")));
        var lp64 = PlatformProfile.defaults();
        var ilp32 = lp64.toBuilder().sizeBits("long", 32).alignmentBits("long", 32)
                .floatingFormat("long double", "binary80").nullPointerConstant("0L").maxMultibyteLength(4).build();
        nativePrograms(cli.input);
        int number = 0;
        for (var profile : List.of(lp64, ilp32)) {
            var options = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03").withPlatform(profile);
            var project = Cpg.read(cli.input, options);
            verify(project, profile);
            verify(project.edit().build(), profile);
            Path settings = temp.resolve("profile-" + number++ + ".json");
            // Full effective settings are used by CLI, never inferred from its machine.
            Files.writeString(settings, Json.serialize(profile.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03", "--platform-profile", settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
            assertEquals(Cpg.sources(project, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
            assertEquals(Files.readString(cli.input.resolve("main.cpp")), Files.readString(run.work().resolve("generated/main.cpp")));
            nativePrograms(run.work().resolve("generated"));
            var chosen = project.files().getFirst().items().stream().filter(n -> n instanceof VariableDeclaration v && v.name().equals("chosen"))
                    .map(VariableDeclaration.class::cast).findFirst().orElseThrow();
            var editor = new ProjectEditor(project);
            editor.replace(chosen.initializer().orElseThrow(), IntegerLiteral.create().setValue(7).build());
            var edited = editor.build();
            assertFalse(edited.hasErrors(), edited.semanticDiagnostics().toString());
            Path output = Files.createDirectory(temp.resolve("edited-" + number));
            var sources = Cpg.sources(edited, Language.CPP);
            assertEquals(Set.of("main.cpp"), sources.keySet());
            String generated = sources.get("main.cpp");
            assertTrue(generated.contains("#include <climits>"));
            assertFalse(generated.contains("chosen = INT_MAX"));
            Files.writeString(output.resolve("main.cpp"), generated);
            assertFalse(Cpg.read(output, options).hasErrors());
            nativePrograms(output);
        }
    }
    private void verify(Project project, PlatformProfile profile) {
        assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics());
        var preprocessing = project.files().getFirst().provenance().orElseThrow().unit();
        assertEquals(profile, preprocessing.configuration().platform());
        assertEquals(Set.of(temp.resolve("input sources/main.cpp").toString()), preprocessing.sources().keySet());
        var library = preprocessing.definitions().stream().filter(d -> d.originKind().equals("library")).toList();
        assertFalse(library.isEmpty());
        for (var definition : library) {
            var external = project.externalInput(definition.sourceKey()).orElseThrow();
            assertEquals("library-macro", external.role());
            String signature = definition.name() + (definition.functionLike()
                    ? "(" + String.join(",", definition.parameters()) + ")" : "");
            String body = definition.unavailableReason() == null ? definition.rawReplacement()
                    : "[unavailable: " + definition.unavailableReason() + "]";
            assertEquals(signature + " " + body, external.text());
            if (definition.name().equals("offsetof")) {
                assertTrue(definition.functionLike());
                assertEquals(List.of("type", "member"), definition.parameters());
                assertEquals("Library macro offsetof requires an explicit offsetof compiler/layout capability",
                        definition.unavailableReason());
            } else {
                assertFalse(definition.functionLike(), definition.name());
                assertNull(definition.unavailableReason(), definition.name());
                assertEquals(definition.name() + " " + definition.rawReplacement(), external.text());
            }
            assertTrue(definition.sourceKey().contains(profile.identityHash()));
            assertTrue(definition.range().file().contains(".cpg-library-"));
        }
        assertTrue(preprocessing.expansions().stream().anyMatch(e -> e.name().equals("INT_MAX")));
        assertTrue(preprocessing.expansions().stream().anyMatch(e -> e.name().equals("LDBL_EPSILON")));
    }
    @Test void unknownFloatingTargetProducesContextualErrorJsonAndNoGeneratedSources() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", "#include <cfloat>\nint main(){return 0;}\n");
        Path settings = temp.resolve("unknown.json");
        Files.writeString(settings, "{\"inherits\":\"default\",\"floatingFormats\":{\"long double\":\"unknown\"}}");
        var run = cli.run("cpp", "--stage", "resolved", "--platform-profile", settings.toString()).expectStatus(1);
        Json.parse(run.work().resolve("snapshot.json").toFile());
        String json = Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("explicit floatingFormats.long double"), json);
        assertTrue(json.contains("preprocessor-error"), json);
        assertFalse(Files.exists(run.work().resolve("generated")));
    }
    private void nativePrograms(Path source) throws Exception {
        for (String compiler : List.of("g++", "clang++")) {
            Path work = Files.createTempDirectory(temp, "native-");
            Path binary = work.resolve("library-macros");
            command(List.of(compiler, "-std=c++03", "-pedantic-errors", source.resolve("main.cpp").toString(), "-o", binary.toString()), work);
            command(List.of(binary.toString()), work);
        }
    }
    private static void command(List<String> command, Path work) throws Exception {
        Path log = Files.createTempFile(work, "process-", ".log");
        var process = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), () -> "Timed out: " + command);
            assertEquals(0, process.exitValue(), () -> command + "\n" + read(log));
        } finally {
            if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
        }
    }
    private static String read(Path path) {
        try { return Files.readString(path); } catch (java.io.IOException error) { return error.toString(); }
    }
    private static final List<String> INTEGER_LIMIT_NAMES=List.of("CHAR_BIT","SCHAR_MIN","SCHAR_MAX","UCHAR_MAX","CHAR_MIN","CHAR_MAX",
            "MB_LEN_MAX","SHRT_MIN","SHRT_MAX","USHRT_MAX","INT_MIN","INT_MAX","UINT_MAX","LONG_MIN","LONG_MAX","ULONG_MAX");
    private int integerEvidence;
    /** ILP32 below is a synthetic integer-expression target, never native object-layout certification. */
    @Test void actualMaterializedIntegerLimitsHaveIndependentLp64AndIlp32NumericOracles() throws Exception {
        var lp64=PlatformProfile.defaults();
        var ilp32=lp64.toBuilder().sizeBits("long",32).alignmentBits("long",32)
                .sizeBits("pointer",32).alignmentBits("pointer",32).maxMultibyteLength(4).build();
        for(boolean synthetic:List.of(false,true))for(boolean legacy:List.of(false,true)){
            var profile=synthetic?ilp32:lp64;
            String program=(legacy?"#include <limits.h>\n":"#include <climits>\n")+"""
                    long signed_limits[]={SCHAR_MIN,SCHAR_MAX,CHAR_MIN,CHAR_MAX,SHRT_MIN,SHRT_MAX,INT_MIN,INT_MAX,LONG_MIN,LONG_MAX};
                    unsigned long unsigned_limits[]={CHAR_BIT,UCHAR_MAX,MB_LEN_MAX,USHRT_MAX,UINT_MAX,ULONG_MAX};
                    int kind(int){return 1;} int kind(unsigned int){return 2;}
                    int kind(long){return 3;} int kind(unsigned long){return 4;}
                    int kinds[]={kind(INT_MAX),kind(UINT_MAX),kind(LONG_MAX),kind(ULONG_MAX)};
                    int main(){return 0;}
                    """;
            Path input=integerSource("integer-original",program);
            integerOracle(input,profile,synthetic,legacy,"original");
            for(var stage:List.of(Cpg.Stage.RESOLVED,Cpg.Stage.GREEN,Cpg.Stage.FOLDED)){
                var options=new Cpg.Options(Language.CPP,stage,"c++03").withPlatform(profile);
                var p=Cpg.read(input.getParent(),options);
                assertFalse(p.hasErrors(),()->p.parseProblems()+" / "+p.semanticDiagnostics());
                assertTrue(ProjectJson.encode(p).toText("  ").contains(profile.identityHash()));
                Path generated=integerSource("integer-"+stage,Cpg.sources(p,Language.CPP).get("main.cpp"));
                assertFalse(Cpg.read(generated.getParent(),options).hasErrors());
                integerOracle(generated,profile,synthetic,legacy,stage.toString());
            }
        }
    }
    private Path integerSource(String label,String text)throws Exception {
        Path file=Files.createTempDirectory(temp,label).resolve("main.cpp");Files.writeString(file,text);return file;
    }
    private record IntegerMaterialization(Path file,List<String> testedExpansionNames) {}
    private IntegerMaterialization materializeIntegerLimits(Path input,PlatformProfile profile,boolean original)throws Exception {
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(input),input.getParent(),List.of(),List.of(),"c++03").withPlatform(profile);
        var pp=com.kniazkov.cpg.parser.CppPreprocessor.preprocess(unit,Files.readString(input));
        var expansions=pp.expansions().stream().map(e->e.name()).filter(INTEGER_LIMIT_NAMES::contains).distinct().sorted().toList();
        // Original input proves actual expansion of all sixteen selected macros. Later stages
        // may have replaced some invocations with constants: those prove transformed values,
        // not another complete macro-expansion event.
        if(original)assertTrue(expansions.containsAll(INTEGER_LIMIT_NAMES),expansions.toString());
        StringBuilder out=new StringBuilder();
        // No tested macro identifier may survive. Do not reinclude host limits headers.
        for(String name:INTEGER_LIMIT_NAMES)out.append("#undef ").append(name).append('\n');
        for(var expanded:pp.tokens()){
            var token=expanded.token();
            if(token.kind()==com.kniazkov.cpg.parser.PreprocessingToken.Kind.HEADER_NAME
                    ||token.kind()==com.kniazkov.cpg.parser.PreprocessingToken.Kind.EOF)continue;
            assertFalse(INTEGER_LIMIT_NAMES.contains(token.spelling()),token.spelling());
            out.append(token.spelling()).append(' ');
        }
        return new IntegerMaterialization(integerSource("integer-materialized",out.append('\n').toString()),expansions);
    }
    private void integerOracle(Path input,PlatformProfile profile,boolean synthetic,boolean legacy,String stage)throws Exception {
        var materialization=materializeIntegerLimits(input,profile,stage.equals("original"));
        Path expanded=materialization.file();
        // A host LP64 compiler can evaluate explicit ILP32 numeric constants, but cannot certify ILP32 long width.
        for(Path tested:synthetic?List.of(expanded):List.of(input,expanded))for(String compiler:List.of("g++","clang++")){
            Path work=Files.createTempDirectory(temp,"integer-native"),wrapper=work.resolve("wrapper.cpp"),binary=work.resolve("probe");
            String signed=synthetic?"-128,127,-128,127,-32768,32767,-2147483647L-1,2147483647L,-2147483647L-1,2147483647L"
                    :"-128,127,-128,127,-32768,32767,-2147483647L-1,2147483647L,-9223372036854775807L-1,9223372036854775807L";
            String unsigned=synthetic?"8UL,255UL,4UL,65535UL,4294967295UL,4294967295UL"
                    :"8UL,255UL,16UL,65535UL,4294967295UL,18446744073709551615UL";
            Files.writeString(wrapper,"""
                    #include <cstdio>
                    #define main tested_main
                    #include "%s"
                    #undef main
                    int main(){
                        const long signed_expected[]={%s};
                        const unsigned long unsigned_expected[]={%s};
                        for(int i=0;i<10;++i){if(signed_limits[i]!=signed_expected[i])return 10+i;std::printf("%%ld ",signed_limits[i]);}
                        for(int i=0;i<6;++i){if(unsigned_limits[i]!=unsigned_expected[i])return 30+i;std::printf("%%lu ",unsigned_limits[i]);}
                        for(int i=0;i<4;++i){if(kinds[i]!=i+1)return 40+i;std::printf("%%d ",kinds[i]);}
                        std::printf("\\n");return tested_main();
                    }
                    """.formatted(tested.toAbsolutePath().toString().replace("\\","\\\\").replace("\"","\\\""),signed,unsigned));
            List<String> compile=List.of(compiler,"-std=c++03","-pedantic-errors",wrapper.toString(),"-o",binary.toString());
            command(compile,work);
            String observed=integerOutput(List.of(binary.toString()),work);
            String expected="-128 127 -128 127 -32768 32767 -2147483648 2147483647 "+(synthetic?"-2147483648 2147483647":"-9223372036854775808 9223372036854775807")
                    +" 8 255 "+(synthetic?"4":"16")+" 65535 4294967295 "+(synthetic?"4294967295":"18446744073709551615")+" 1 2 3 4 \n";
            assertEquals(expected,observed);
            Path artifact=Files.createDirectories(Path.of("target/library-macros-oracle",String.format("%03d-%s",++integerEvidence,compiler)));
            Files.copy(input,artifact.resolve("input.cpp"),StandardCopyOption.REPLACE_EXISTING);
            Files.copy(expanded,artifact.resolve("materialized.cpp"),StandardCopyOption.REPLACE_EXISTING);
            Files.copy(tested,artifact.resolve("tested.cpp"),StandardCopyOption.REPLACE_EXISTING);
            Files.copy(wrapper,artifact.resolve("wrapper.cpp"),StandardCopyOption.REPLACE_EXISTING);
            Path settings=artifact.resolve("profile.json");Files.writeString(settings,Json.serialize(profile.toSettings()));
            Files.writeString(artifact.resolve("observations.txt"),observed);
            var metadata=new TreeMap<String,Object>();metadata.put("compiler",compiler);
            metadata.put("version",integerOutput(List.of(compiler,"--version"),work));metadata.put("target",integerOutput(List.of(compiler,"-dumpmachine"),work));
            metadata.put("compileCommand",compile);metadata.put("executionCommand",List.of(binary.toString()));
            metadata.put("profileSha256",integerHash(settings));metadata.put("profileIdentity",profile.identityHash());
            for(String name:List.of("input.cpp","materialized.cpp","tested.cpp","wrapper.cpp"))metadata.put(name+"Sha256",integerHash(artifact.resolve(name)));
            metadata.put("materialized",tested.equals(expanded));metadata.put("syntheticIlp32",synthetic);metadata.put("legacyHeader",legacy);metadata.put("stage",stage);
            metadata.put("testedExpansionNames",materialization.testedExpansionNames());
            metadata.put("completeOriginalExpansionProof",tested.equals(expanded)&&stage.equals("original"));
            metadata.put("materializationEvidence",stage.equals("original")?"Original input expands all sixteen tested integer-limit macros"
                    :"Generated input may already contain transformed constants; expansion names record only macros actually expanded in this pass");
            metadata.put("scope",synthetic?"Synthetic ILP32 integer replacement values and overloads on an LP64 compiler; no object-layout or floating/long-double conformance claim"
                    :"LP64 integer-limit replacement values and overloads only; no floating/long-double conformance claim");
            Files.writeString(artifact.resolve("metadata.json"),Json.serialize(metadata));
        }
    }
    private static String integerOutput(List<String> args,Path work)throws Exception {
        Path log=Files.createTempFile(work,"integer-output",".txt");
        var p=new ProcessBuilder(args).directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try{assertTrue(p.waitFor(45,TimeUnit.SECONDS),args.toString());String text=Files.readString(log);assertEquals(0,p.exitValue(),args+"\n"+text);return text;}
        finally{if(p.isAlive()){p.destroyForcibly();p.waitFor();}}
    }
    private static String integerHash(Path path)throws Exception {
        return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path)));
    }

}
