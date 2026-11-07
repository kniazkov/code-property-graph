/* Explicit target facts verified against independently compiled real headers. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.*;
import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class RuntimeIntegerMacrosIT {
    @TempDir Path temp;
    // Independent required-name oracle, not generated from production catalogue.
    private static final List<String> NAMES=List.of("EDOM","ERANGE","EILSEQ","LC_ALL","LC_COLLATE","LC_CTYPE","LC_MONETARY","LC_NUMERIC","LC_TIME",
            "SIGABRT","SIGFPE","SIGILL","SIGINT","SIGSEGV","SIGTERM","_IOFBF","_IOLBF","_IONBF","BUFSIZ","EOF","FOPEN_MAX","FILENAME_MAX","L_tmpnam",
            "SEEK_CUR","SEEK_END","SEEK_SET","TMP_MAX","EXIT_FAILURE","EXIT_SUCCESS","RAND_MAX");
    private static final String HEADERS="#include <cerrno>\n#include <clocale>\n#include <csignal>\n#include <cstdio>\n#include <cstdlib>\n";
    private static final String OVERLOADS="int kind(int){return 1;} int kind(long){return 2;} int kind(unsigned int){return 3;} int kind(unsigned long){return 4;}\n";
    private record Run(int code,String out,String err) {}
    private Run command(List<String> args,Path work) throws Exception {
        Path out=Files.createTempFile(work,"stdout-",".txt"),err=Files.createTempFile(work,"stderr-",".txt");
        var p=new ProcessBuilder(args).directory(work.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        try {assertTrue(p.waitFor(60,TimeUnit.SECONDS),args.toString());return new Run(p.exitValue(),Files.readString(out),Files.readString(err));}
        finally {if(p.isAlive()){p.destroyForcibly();p.waitFor();}}
    }
    private Run success(List<String> args,Path work) throws Exception {var r=command(args,work);assertEquals(0,r.code,args+"\n"+r.out+"\n"+r.err);return r;}
    private String hash(Path file) throws Exception {return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(file)));}
    private PlatformProfile probe(String compiler) throws Exception {
        Path work=Files.createTempDirectory(temp,"oracle-");Path source=work.resolve("probe.cpp"),binary=work.resolve("probe");
        StringBuilder cpp=new StringBuilder(HEADERS).append("#include <climits>\n#ifdef __GLIBC__\n#include <gnu/libc-version.h>\n#endif\n").append("template<class T> int kind(T){return 5;}\n").append(OVERLOADS).append("int main(){\n");
        cpp.append("std::printf(\"WIDTHS %d %d %d %d\\n\", CHAR_BIT, int(sizeof(int)*CHAR_BIT), int(sizeof(long)*CHAR_BIT), int(sizeof(void*)*CHAR_BIT));\n");
        cpp.append("#ifdef __GLIBC__\nstd::printf(\"GLIBC %d %d\\n\", __GLIBC__, __GLIBC_MINOR__); std::printf(\"LIBC_RUNTIME %s\\n\", gnu_get_libc_version());\n#else\nstd::puts(\"LIBC unknown\");\n#endif\n");
        for(String name:NAMES) cpp.append("std::printf(\"").append(name).append(" %ld %d\\n\",static_cast<long>(").append(name).append("),kind(").append(name).append("));\n");
        cpp.append("return 0;}\n");Files.writeString(source,cpp);
        success(List.of(compiler,"-std=c++03","-pedantic-errors",source.toString(),"-o",binary.toString()),work);
        String observed=success(List.of(binary.toString()),work).out;
        assertTrue(observed.startsWith("WIDTHS 8 32 64 64\n"),"Native test oracle currently requires an LP64 host; production does not: "+observed);
        var b=PlatformProfile.builder().name("observed-native-int-facts");var found=new HashSet<String>();
        for(String line:observed.split("\n")) {String[] fields=line.split(" ");if(!NAMES.contains(fields[0]))continue;
            assertEquals("1",fields[2],"Actual macro is not int: "+line);assertTrue(found.add(fields[0]));b.runtimeIntegerMacro(fields[0],"int",fields[1]);}
        assertEquals(new HashSet<>(NAMES),found);
        var profile=b.build();
        // Evidence belongs to the test artifact, never to the production loading path.
        var metadata=new LinkedHashMap<String,Object>();metadata.put("compiler",compiler);
        metadata.put("version",success(List.of(compiler,"--version"),work).out);
        metadata.put("target",success(List.of(compiler,"-dumpmachine"),work).out.trim());
        metadata.put("headerAndRuntimeObservations",observed);
        metadata.put("includeSearch",success(List.of(compiler,"-std=c++03","-E","-v",source.toString(),"-o","/dev/null"),work).err);
        metadata.put("probeSourceSha256",hash(source));
        var headers=success(List.of(compiler,"-std=c++03","-H","-E",source.toString(),"-o","/dev/null"),work).err;
        var hashes=new TreeMap<String,String>();
        for(String line:headers.split("\n")) if(line.matches("\\.+ .*")) {Path file=Path.of(line.replaceFirst("^\\.+ ",""));if(Files.isRegularFile(file))hashes.put(file.toString(),hash(file));}
        assertFalse(hashes.isEmpty());metadata.put("selectedHeaderSha256",hashes);
        Path artifacts=Files.createDirectories(Path.of("target/runtime-integer-oracle"));String id=compiler.equals("g++")?"gcc":"clang";
        Path manifest=artifacts.resolve(id+"-profile.json");Files.writeString(manifest,Json.serialize(profile.toSettings()));metadata.put("profileSha256",hash(manifest));
        Files.writeString(artifacts.resolve(id+"-evidence.json"),Json.serialize(metadata));
        return profile;
    }
    private String program(PlatformProfile profile,boolean legacy) {
        String headers=legacy?"#include <errno.h>\n#include <locale.h>\n#include <signal.h>\n#include <stdio.h>\n#include <stdlib.h>\n":HEADERS;
        var cpp=new StringBuilder(headers).append(OVERLOADS).append("int editable = EDOM;\nint main(){\n");int i=1;
        for(String name:NAMES) {String value="("+profile.runtimeIntegerMacros().get(name).value()+"L)";
            cpp.append("#if ").append(name).append(" != ").append(value).append("\n#error wrong value for ").append(name).append("\n#endif\n");
            cpp.append("if (").append(name).append(" != ").append(value).append(") return ").append(i++).append(";\n");
            cpp.append("if (kind(").append(name).append(") != 1) return ").append(i++).append(";\n");}
        return cpp.append("return 0;}\n").toString();
    }
    private void nativeRun(Path source) throws Exception {for(String compiler:List.of("g++","clang++")) {Path work=Files.createTempDirectory(temp,"native-");Path binary=work.resolve("check");success(List.of(compiler,"-std=c++03","-pedantic-errors",source.toString(),"-o",binary.toString()),work);success(List.of(binary.toString()),work);}}
    private Path materialize(Path source,PlatformProfile profile) throws Exception {
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(source),source.getParent(),List.of(),List.of(),"c++03").withPlatform(profile);
        var r=CppPreprocessor.preprocess(unit,Files.readString(source));var cpp=new StringBuilder();
        for(var t:r.tokens()) if(t.token().kind()==PreprocessingToken.Kind.HEADER_NAME) cpp.append("\n#include ").append(t.token().spelling()).append('\n');
            else if(t.token().kind()!=PreprocessingToken.Kind.EOF) cpp.append(t.token().spelling()).append(' ');
        assertFalse(cpp.toString().contains("kind(EDOM"));
        Path file=Files.createTempDirectory(temp,"expanded-").resolve("main.cpp");Files.writeString(file,cpp.append('\n'));return file;
    }
    private void verify(Project project,PlatformProfile profile) {
        assertFalse(project.hasErrors(),()->project.parseProblems()+" / "+project.semanticDiagnostics());
        var pp=project.files().getFirst().provenance().orElseThrow().unit();assertEquals(profile,pp.configuration().platform());
        var definitions=pp.definitions().stream().filter(d->NAMES.contains(d.name())).toList();assertEquals(30,definitions.size());
        for(var d:definitions) {assertNull(d.unavailableReason());assertEquals("library",d.originKind());assertTrue(project.externalInput(d.sourceKey()).isPresent());}
        var calls=new ArrayList<CallExpression>();project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression c){calls.add(c);}});
        assertEquals(30,calls.size());for(var c:calls)assertEquals("int",c.target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
    }
    @Test void actualTargetAndIndependentSyntheticFactsThroughApiCliCopyEditsAndNativeOracles() throws Exception {
        var nativeProfile=probe("g++");assertEquals(nativeProfile.toSettings(),probe("clang++").toSettings());
        var synthetic=PlatformProfile.read(Path.of("src/test/resources/runtime-integer-macros/synthetic-profile.json"));
        assertNotEquals(nativeProfile.runtimeIntegerMacros(),synthetic.runtimeIntegerMacros());
        int number=0;
        for(var profile:List.of(nativeProfile,synthetic))for(boolean legacy:List.of(false,true)) {
            var cli=new CliSandbox(Files.createDirectory(temp.resolve("case-"+number++))).source("main.cpp",program(profile,legacy));
            var options=new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03").withPlatform(profile);
            var project=Cpg.read(cli.input,options);verify(project,profile);verify(project.edit().build(),profile);
            Path config=cli.input.getParent().resolve("profile.json");Files.writeString(config,Json.serialize(profile.toSettings()));
            var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",config.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),Json.parse(run.work().resolve("snapshot.json").toFile()));
            var variable=project.files().getFirst().items().stream().filter(n->n instanceof VariableDeclaration v && v.name().equals("editable")).map(VariableDeclaration.class::cast).findFirst().orElseThrow();
            assertEquals(profile.runtimeIntegerMacros().get("EDOM").value(), literalValue(variable.initializer().orElseThrow()));
            var editor=new ProjectEditor(project);editor.replace(variable.initializer().orElseThrow(),IntegerLiteral.create().setValue(7).build());
            var edited=editor.build();assertFalse(edited.hasErrors());
            assertEquals(java.math.BigInteger.valueOf(7),literalValue(editable(edited).initializer().orElseThrow()));
            Path editedFile=Files.createDirectory(cli.input.getParent().resolve("edited")).resolve("main.cpp");Files.writeString(editedFile,Cpg.sources(edited,Language.CPP).get("main.cpp"));
            var reparsed=Cpg.read(editedFile.getParent(),options);verify(reparsed,profile);
            assertEquals(java.math.BigInteger.valueOf(7),literalValue(editable(reparsed).initializer().orElseThrow()));
            if(profile==nativeProfile) {nativeRun(cli.input.resolve("main.cpp"));nativeRun(run.work().resolve("generated/main.cpp"));nativeRun(materialize(cli.input.resolve("main.cpp"),profile));nativeRun(editedFile);nativeRun(materialize(editedFile,profile));}
            else { nativeRun(materialize(cli.input.resolve("main.cpp"),profile)); nativeRun(materialize(editedFile,profile)); }
            // Synthetic expanded constants test matching int-width arithmetic and overloads only.
            // They never call runtime services or claim these numbers match host libc.
        }
    }
    private VariableDeclaration editable(Project p) {
        return p.files().getFirst().items().stream().filter(n->n instanceof VariableDeclaration v && v.name().equals("editable"))
                .map(VariableDeclaration.class::cast).findFirst().orElseThrow();
    }
    private java.math.BigInteger literalValue(Expression expression) {
        if(expression instanceof IntegerLiteral literal) return literal.bigValue();
        if(expression instanceof NegateExpression negative) return literalValue(negative.operand()).negate();
        throw new AssertionError("Unexpected initializer: "+expression.getClass());
    }
    @Test void missingFactsKeepContextualErrorJsonAndBlockGeneration() throws Exception {
        for(String use:List.of("#if EDOM == 0\n#endif\nint main(){return 0;}\n","int main(){return EDOM;}\n")) {
            var cli=new CliSandbox(Files.createTempDirectory(temp,"missing-")).source("main.cpp","#include <cerrno>\n"+use);
            var run=cli.run("cpp","--stage","resolved").expectStatus(1);String json=Files.readString(run.work().resolve("snapshot.json"));Json.parse(json);
            assertTrue(json.contains("unavailable-library-macro"));assertTrue(json.contains("runtimeIntegerMacros.EDOM"));assertTrue(json.contains("unavailableReason"));assertFalse(Files.exists(run.work().resolve("generated")));
        }
    }
}
