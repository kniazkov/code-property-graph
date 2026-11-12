/* CPG assertion expansion linked to explicitly selected glibc; no helper bodies. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.LibraryDeclarations;
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
class AssertionRuntimeIT {
    @TempDir Path temp;
    private int execution;
    private static final PlatformProfile GLIBC = PlatformProfile.builder().cRuntime("glibc").build();
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03").withPlatform(GLIBC);
    @Test void successfulAssertionsResolveCopyEditAndRunThroughCliAndMaterializedExpansion() throws Exception {
        String fixture=Files.readString(Path.of("src/test/resources/assertion-runtime/success.cpp"))
                .replace("int disabled() {", "int disabled() { assert(++count);")
                .replace("int main() { first(); disabled(); last(); return count == 2 ? 0 : 1; }",
                        "int main() { int a=first(); int b=disabled(); int c=last(); std::printf(\"enabled=%d disabled=%d reenabled=%d\\n\",a,b,c); return b==a && c==a+1 ? 0 : 1; }");
        assertTrue(fixture.contains("int disabled() { assert(++count);"));
        assertTrue(fixture.contains("enabled=%d disabled=%d reenabled=%d"));
        var cli = new CliSandbox(temp).source("main.cpp", "#include <cstdio>\n"+fixture);
        var project = Cpg.read(cli.input, OPTIONS); verify(project); verify(project.edit().build());
        var settings = temp.resolve("glibc.json"); Files.writeString(settings, Json.serialize(GLIBC.toSettings()));
        var run = cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",settings.toString()).expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        nativeProgram(cli.input.resolve("main.cpp"), false,"success-original","enabled=1 disabled=1 reenabled=2\n");
        nativeProgram(run.work().resolve("generated/main.cpp"), false,"success-cli-generated","enabled=1 disabled=1 reenabled=2\n");
        nativeProgram(materialize(cli.input.resolve("main.cpp")), false,"success-cpg-materialized","enabled=1 disabled=1 reenabled=2\n");
        var editor = new ProjectEditor(project);
        // A condition-only graph edit must not leave stale stringized diagnostic text.
        var increments = new ArrayList<PrefixIncrementExpression>();
        project.files().getFirst().walk(new NodeVisitor(){ @Override public void visit(PrefixIncrementExpression n){increments.add(n);} });
        assertEquals(2,increments.size());
        editor.replace(increments.getFirst(), AddExpression.create().setLeft(increments.getFirst().edit()).setRight(IntegerLiteral.create().setValue(0)).build());
        var conditionEdit = editor.build(); assertFalse(conditionEdit.hasErrors(),conditionEdit.semanticDiagnostics().toString());
        assertTrue(assertThrows(IllegalArgumentException.class,()->Cpg.sources(conditionEdit,Language.CPP)).getMessage().contains("re-stringizing"));
        var variable = project.files().getFirst().items().stream().filter(n->n instanceof VariableDeclaration v && v.name().equals("count")).map(VariableDeclaration.class::cast).findFirst().orElseThrow();
        var safe = new ProjectEditor(project);
        safe.replace(variable.initializer().orElseThrow(),IntegerLiteral.create().setValue(4).build());
        var edited = NameResolver.resolve(safe.build()); assertFalse(edited.hasErrors(),edited.semanticDiagnostics().toString());
        String source = Cpg.sources(edited,Language.CPP).get("main.cpp");
        assertTrue(source.contains("#include <cassert>")); assertTrue(source.startsWith("#line 1 "),source);
        Path directory = Files.createDirectory(temp.resolve("edited")); Path file = directory.resolve("main.cpp"); Files.writeString(file,source);
        verify(Cpg.read(directory,OPTIONS)); verify(edited);
        nativeProgram(file,false,"success-edited-generated","enabled=5 disabled=5 reenabled=6\n");
        nativeProgram(materialize(file),false,"success-edited-cpg-materialized","enabled=5 disabled=5 reenabled=6\n");
        Path copied=Files.createDirectory(temp.resolve("copy")).resolve("main.cpp");
        Files.writeString(copied,Cpg.sources(project.edit().build(),Language.CPP).get("main.cpp"));
        verify(Cpg.read(copied.getParent(),OPTIONS));
        nativeProgram(copied,false,"success-copy-generated","enabled=1 disabled=1 reenabled=2\n");
        var separate=new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("separate.cpp")),temp,List.of(),List.of(),"c++03").withPlatform(GLIBC);
        var isolated=CppPreprocessor.preprocess(separate,"#ifdef assert\n#error leaked assert\n#endif\n#ifdef NDEBUG\n#error leaked NDEBUG\n#endif\nint independent;\n");
        assertTrue(isolated.definitions().stream().noneMatch(d->d.name().equals("assert") || d.name().equals("NDEBUG")));
    }
    private void verify(Project project) {
        assertFalse(project.hasErrors(),()->project.parseProblems()+" / "+project.semanticDiagnostics());
        var calls = new ArrayList<CallExpression>();
        project.files().getFirst().walk(new NodeVisitor(){ @Override public void visit(CallExpression c){ if(c.callee() instanceof NameExpression n && n.name().contains("__assert_fail")) calls.add(c); } });
        assertEquals(2,calls.size());
        for(var call:calls) {
            var target=call.target().orElseThrow(); assertTrue(LibraryDeclarations.isLibrary(target)); assertTrue(target.body().isEmpty());
            assertEquals("__assert_fail",target.name()); assertEquals("C",target.languageLinkage());
            assertEquals(List.of("pointer(const(char))", "pointer(const(char))", "unsigned int", "pointer(const(char))"), target.parameters().stream()
                    .map(p->p.type().orElseThrow().canonicalIdentity()).toList());
        }
        var pp=project.files().getFirst().provenance().orElseThrow().unit();
        var definitions=pp.definitions().stream().filter(d->d.name().equals("assert")).toList(); assertEquals(3,definitions.size());
        assertNotEquals(definitions.get(0).sourceKey(),definitions.get(1).sourceKey());
        for(var d:definitions) {
            assertTrue(d.functionLike());assertEquals("library",d.originKind());assertNull(d.unavailableReason());
            assertTrue(d.sourceKey().contains(GLIBC.identityHash()));
            assertTrue(project.externalInput(d.sourceKey()).orElseThrow().text().startsWith("assert(expression)"));
        }
        var expansions=pp.expansions().stream().filter(e->e.name().equals("assert")).toList();
        assertEquals(5,expansions.size());
        assertEquals(List.of(definitions.get(0).id(),definitions.get(1).id(),definitions.get(1).id(),definitions.get(1).id(),definitions.get(2).id()),expansions.stream().map(e->e.definitionId()).toList());
    }
    @Test void failureDiagnosesRawExpressionPresumedFileLineAndTerminates() throws Exception {
        Path dir=Files.createDirectory(temp.resolve("failure")); Path source=dir.resolve("main.cpp");
        Files.writeString(source,Files.readString(Path.of("src/test/resources/assertion-runtime/failure.cpp")));
        var project=Cpg.read(dir,OPTIONS);assertFalse(project.hasErrors(),()->project.parseProblems()+" / "+project.semanticDiagnostics());
        var condition = new ArrayList<ConversionExpression>();
        project.files().getFirst().walk(new NodeVisitor(){ @Override public void visit(ConversionExpression n) {
            if (n.isExplicit() && n.targetType().canonicalIdentity().equals("bool")) condition.add(n);
        }});
        var edit = new ProjectEditor(project);edit.replace(condition.getFirst().operand(),IntegerLiteral.create().setValue(1).build());
        assertTrue(assertThrows(IllegalArgumentException.class,()->Cpg.sources(edit.build(),Language.CPP)).getMessage().contains("re-stringizing"));
        nativeProgram(source,true,"failure-original",null); nativeProgram(materialize(source),true,"failure-cpg-materialized",null);
        Path generated=Files.createDirectory(temp.resolve("generated-failure")).resolve("main.cpp");
        Files.writeString(generated,Cpg.sources(project.edit().build(),Language.CPP).get("main.cpp"));nativeProgram(generated,true,"failure-copy-generated",null);
    }
    @Test void unknownRuntimeSupportsUnusedIncludeAndDisabledAssertionsButRetainsInvocationErrorJson() throws Exception {
        var cli=new CliSandbox(temp).source("main.cpp","#include <cassert>\nint main(){assert(1); return 0;}\n");
        var run=cli.run("cpp","--stage","resolved").expectStatus(1);
        String json=Files.readString(run.work().resolve("snapshot.json"));Json.parse(json);
        assertTrue(json.contains("unavailable-library-macro"));assertTrue(json.contains("unavailableReason"));
        assertFalse(Files.exists(run.work().resolve("generated")));
        Files.writeString(cli.input.resolve("main.cpp"),"#include <cassert>\n#define NDEBUG 0\n#include <assert.h>\nint main(){assert(undeclared++); return 0;}\n");
        var disabled=Cpg.read(cli.input,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03"));assertFalse(disabled.hasErrors());
        assertFalse(disabled.edit().build().hasErrors()); cli.run("cpp","--stage","resolved").expectStatus(0);
    }
    private Path materialize(Path source) throws Exception {
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(source),source.getParent(),List.of(),List.of(),"c++03").withPlatform(GLIBC);
        var pp=CppPreprocessor.preprocess(unit,Files.readString(source));var text=new StringBuilder();
        for(var token:pp.tokens()) {
            if(token.token().kind()==PreprocessingToken.Kind.HEADER_NAME) text.append("\n#include ").append(token.token().spelling()).append("\n#undef assert\n");
            else if(token.token().kind()!=PreprocessingToken.Kind.EOF) text.append(token.token().spelling()).append(' ');
        }
        assertTrue(text.toString().contains("__assert_fail"));
        var tokens=CppLexer.scan("materialized.cpp",text.toString()).tokens().stream().filter(t->!Set.of(PreprocessingToken.Kind.WHITESPACE,PreprocessingToken.Kind.NEWLINE,PreprocessingToken.Kind.EOF).contains(t.kind())).map(t->t.spelling()).toList();
        for(int i=0;i+1<tokens.size();i++)assertFalse(tokens.get(i).equals("assert") && tokens.get(i+1).equals("("),"Unexpanded assert invocation");
        Path output=Files.createTempDirectory(temp,"expanded-").resolve("main.cpp");Files.writeString(output,text.append("\n"));return output;
    }
    private void nativeProgram(Path source,boolean failure,String label,String expected) throws Exception {
        for(String compiler:List.of("g++","clang++")) {
            Path work=Files.createTempDirectory(temp,"native-");Path binary=work.resolve("assertion");
            var args=List.of(compiler,"-std=c++03","-pedantic-errors",source.toString(),"-o",binary.toString());
            var compile=command(args,work);
            assertEquals(0,compile.code,compile.output);
            var run=command(List.of(binary.toString()),work);
            if(failure) {assertNotEquals(0,run.code);assertTrue(run.stderr.contains("ZERO"),run.output);assertTrue(run.stderr.contains("assertion-input.cpp:73"),run.output);assertFalse(run.output.contains("AFTER_ASSERT"),run.output);assertFalse(run.output.contains("UNWOUND"),run.output);assertFalse(run.output.contains("ATEXIT"),run.output);}
            else {assertEquals(0,run.code,run.output);assertEquals(expected,run.stdout);}
            Path evidence=Files.createDirectories(Path.of("target/assertion-runtime-oracle").resolve(String.format("%03d-%s-%s",++execution,label,compiler.equals("g++")?"gcc":"clang")));
            Files.copy(source,evidence.resolve("main.cpp"),StandardCopyOption.REPLACE_EXISTING);
            var metadata=new LinkedHashMap<String,Object>();
            metadata.put("route",label);metadata.put("compiler",compiler);
            var version=command(List.of(compiler,"--version"),work);assertEquals(0,version.code);
            var target=command(List.of(compiler,"-dumpmachine"),work);assertEquals(0,target.code);
            metadata.put("version",version.stdout);metadata.put("target",target.stdout);
            metadata.put("command",args);metadata.put("runCommand",List.of(binary.toString()));
            Path profileFile=evidence.resolve("profile.json");Files.writeString(profileFile,Json.serialize(GLIBC.toSettings()));
            metadata.put("profile",GLIBC.toSettings());metadata.put("profileIdentity",GLIBC.identityHash());metadata.put("profileSha256",hash(profileFile));
            metadata.put("sourceSha256",Map.of("main.cpp",hash(source)));
            metadata.put("compileExitCode",compile.code);metadata.put("compileStdout",compile.stdout);metadata.put("compileStderr",compile.stderr);
            metadata.put("exitCode",run.code);metadata.put("stdout",run.stdout);metadata.put("stderr",run.stderr);
            metadata.put("expectedOutcome",failure?"nonzero termination with ZERO, assertion-input.cpp:73; no AFTER_ASSERT/UNWOUND/ATEXIT":expected);
            Files.writeString(evidence.resolve("metadata.json"),Json.serialize(metadata));
        }
    }
    private static String hash(Path path) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
    private record Result(int code,String output,String stdout,String stderr) {}
    private static Result command(List<String> args,Path work) throws Exception {
        Path log=Files.createTempFile(work,"stdout-",".log"), error=Files.createTempFile(work,"stderr-",".log");
        var p=new ProcessBuilder(args).directory(work.toFile()).redirectOutput(log.toFile()).redirectError(error.toFile()).start();
        try {assertTrue(p.waitFor(45,TimeUnit.SECONDS),args.toString());String out=Files.readString(log),err=Files.readString(error);return new Result(p.exitValue(),out+"\n"+err,out,err);}
        finally {if(p.isAlive()){p.destroyForcibly();p.waitFor();}}
    }
}
