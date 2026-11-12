/* Strict native returns-twice and CPG replacement replay evidence. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class SetjmpRuntimeIT {
    @TempDir Path temp;
    private int execution;
    private static final PlatformProfile PROFILE = PlatformProfile.builder().cRuntime("glibc").setjmp("gcc-clang-glibc").build();
    private static Cpg.Options options(Cpg.Stage stage) { return new Cpg.Options(Language.CPP,stage,"c++03").withPlatform(PROFILE); }
    // All automatic objects exist before save. No automatic declarations occur
    // in skipped scopes: this respects the original 2003 wording, not its later correction.
    private static String program(String headers) {
        String type = headers.startsWith("#include <csetjmp>") ? "std::jmp_buf" : "jmp_buf";
        return headers + "#include <cstdio>\n" + """
                int probe(int value) {
                    BUFFER env; BUFFER spare; volatile int marker = 0;
                    switch(setjmp(env)) {
                        case 0: marker = 5; JUMP(env, value);
                        case 1: return marker + 1;
                        case 7: return marker + 7;
                        case 9: return marker + 9;
                        default: return -1;
                    }
                }
                int discarded() {
                    BUFFER env; volatile int visits = 0;
                    (void)setjmp(env);
                    if (!visits) { visits = 3; JUMP(env, 1); }
                    return visits;
                }
                int main() {
                    int zero = probe(0); int nonzero = probe(7); int count = discarded();
                    std::printf("zero=%d nonzero=%d discarded=%d\\n", zero, nonzero, count);
                    return zero == 6 && count == 3 ? 0 : 1;
                }
                """.replace("BUFFER",type).replace("JUMP", headers.contains("<csetjmp>") ? "std::longjmp" : "::longjmp");
    }
    @Test void apiCliNativeReplaysAndRealEditsExecuteReturnsTwiceAtOptimization() throws Exception {
        int fixture = 0;
        for (String headers : List.of("#include <csetjmp>\n", "#include <setjmp.h>\n", "#include <csetjmp>\n#include <setjmp.h>\n", "#include <setjmp.h>\n#include <csetjmp>\n")) {
            Path work = Files.createDirectory(temp.resolve("case-" + fixture++));
            var cli = new CliSandbox(work).source("main.cpp", program(headers));
            Project project = Cpg.read(cli.input, options(Cpg.Stage.RESOLVED)); healthy(project);
            assertEquals(2, nodes(project,SetjmpExpression.class).size());
            Path settings = work.resolve("profile.json"); Files.writeString(settings,Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),Json.parse(run.work().resolve("snapshot.json").toFile()));
            assertEquals(Cpg.sources(project,Language.CPP).get("main.cpp"),Files.readString(run.work().resolve("generated/main.cpp")));
            nativeRun(cli.input,"original",false); nativeRun(run.work().resolve("generated"),"cli-generated",false);
            checkGenerated(project.edit().build(),"copy",false);
            nativeRun(materialize(project,Cpg.sources(project,Language.CPP)),"cpg-materialized",false);
            var edited = edit(project); checkGenerated(edited,"edited",true);
            nativeRun(materialize(edited,Cpg.sources(edited,Language.CPP)),"edited-materialized",true);
            for (var stage : List.of(Cpg.Stage.GREEN,Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(cli.input,options(stage)); healthy(transformed);
                checkGenerated(transformed,stage.name(),false);
            }
        }
    }
    @Test void invalidPlacementCliEmitsDiagnosticsButNoRunnableSources() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp","#include <csetjmp>\nint main(){std::jmp_buf env;return setjmp(env);}");
        Path settings = temp.resolve("profile.json"); Files.writeString(settings,Json.serialize(PROFILE.toSettings()));
        var run = cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",settings.toString());
        assertNotEquals(0,run.status());
        String json = Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("setjmp"));
        assertFalse(Files.exists(run.work().resolve("generated/main.cpp")));
    }
    private Project edit(Project project) {
        var editor = new ProjectEditor(project);
        var function = nodes(project,CallableDeclaration.class).stream().filter(f -> f.name().equals("probe")).findFirst().orElseThrow();
        // Rebind BOTH the save and restore operands to an independent local buffer.
        function.walk(new NodeVisitor() { @Override public void visit(NameExpression name) {
            if(name.name().equals("env")) editor.replace(name,NameExpression.create().setName("spare").build());
        } });
        var call = nodes(project,CallExpression.class).stream().filter(c -> c.target().map(t -> t.name().equals("probe")).orElse(false)
                && c.arguments().getFirst() instanceof IntegerLiteral literal && literal.bigValue().intValue()==7).findFirst().orElseThrow();
        editor.replace(call.arguments().getFirst(),IntegerLiteral.create().setValue(9).build());
        var result=NameResolver.resolve(editor.build()); healthy(result);
        var editedFunction=nodes(result,CallableDeclaration.class).stream().filter(f -> f.name().equals("probe")).findFirst().orElseThrow();
        var saves = new ArrayList<SetjmpExpression>(); editedFunction.walk(new NodeVisitor(){ @Override public void visit(SetjmpExpression node){ saves.add(node); } });
        assertEquals("spare",((VariableDeclaration)((NameExpression)saves.getFirst().buffer()).target().orElseThrow()).name());
        assertEquals("env",((NameExpression)nodes(project,SetjmpExpression.class).getFirst().buffer()).name());
        return result;
    }
    private void checkGenerated(Project project,String label,boolean edited) throws Exception {
        healthy(project); Path directory=writeSources(Cpg.sources(project,Language.CPP));
        healthy(Cpg.read(directory,options(Cpg.Stage.RESOLVED))); nativeRun(directory,label,edited);
    }
    /** Replays the exact CPG-recorded macro after removing the host macro.
     * Edited save expressions may already emit _setjmp directly; an unchanged
     * discarded() invocation must still exercise the actual replacement. */
    private Path materialize(Project project,Map<String,String> sources) throws Exception {
        var file=project.files().getFirst();
        var definition=file.provenance().orElseThrow().unit().definitions().stream().filter(d -> d.name().equals("setjmp")).findFirst().orElseThrow();
        assertEquals("library",definition.originKind()); assertTrue(definition.functionLike()); assertNull(definition.unavailableReason());
        assertTrue(definition.rawReplacement().contains("_setjmp"));
        String source=sources.get("main.cpp");
        assertTrue(java.util.regex.Pattern.compile("(?<![_a-zA-Z])setjmp\\s*\\(").matcher(source).find(),"Replay must use graph-recorded replacement");
        String macros="\n#undef setjmp\n#define setjmp("+String.join(",",definition.parameters())+") "+definition.rawReplacement()+"\n";
        int end=0;
        for(String line:source.split("(?<=\\n)")) {
            if(line.isBlank() || line.stripLeading().startsWith("#include")) end+=line.length(); else break;
        }
        assertTrue(end>0,source);
        return writeSources(Map.of("main.cpp",source.substring(0,end)+macros+source.substring(end)));
    }
    private Path writeSources(Map<String,String> sources) throws Exception {
        Path directory = Files.createTempDirectory(temp, "sources-");
        for (var entry : sources.entrySet()) { Path path = directory.resolve(entry.getKey()); Files.createDirectories(path.getParent()); Files.writeString(path, entry.getValue()); }
        return directory;
    }
    private void nativeRun(Path directory, String label, boolean edited) throws Exception {
        for (String compiler : List.of("g++", "clang++")) {
            Path work = Files.createTempDirectory(temp, "native-");
            Path binary = work.resolve("probe");
            var args = List.of(compiler, "-std=c++03", "-pedantic-errors", "-O2", directory.resolve("main.cpp").toString(),
                    "-o", binary.toString());
            Run compile = success(args, work); Run run = success(List.of(binary.toString()), work);
            assertEquals("zero=6 nonzero=" + (edited ? 14 : 12) + " discarded=3\n", run.out());
            Path evidence = Files.createDirectories(Path.of("target/setjmp-runtime-oracle")
                    .resolve(String.format("%03d-%s-%s", ++execution, label, compiler.equals("g++") ? "gcc" : "clang")));
            var hashes = new TreeMap<String,String>();
            for (String name : List.of("main.cpp")) {
                Files.copy(directory.resolve(name), evidence.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                hashes.put(name, hash(directory.resolve(name)));
            }
            Files.writeString(evidence.resolve("metadata.json"), Json.serialize(Map.of(
                    "compiler", compiler, "version", success(List.of(compiler,"--version"),work).out(),
                    "target", success(List.of(compiler,"-dumpmachine"),work).out(), "command", args,
                    "profile", PROFILE.toSettings(), "sourceSha256", hashes, "compileStderr", compile.err(),
                    "exitCode", run.code(), "stdout", run.out(), "stderr", run.err())));
        }
    }
    private static <T extends Node> List<T> nodes(Project project, Class<T> type) {
        var result = new ArrayList<T>();
        for (var file : project.files()) file.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    private static void healthy(Project project) { assertFalse(project.hasErrors(), () -> project.parseProblems() + " / " + project.semanticDiagnostics()); }
    private record Run(int code, String out, String err) {}
    private static Run success(List<String> args, Path work) throws Exception {
        Path out = Files.createTempFile(work,"out-",".txt"), err = Files.createTempFile(work,"err-",".txt");
        Process process = new ProcessBuilder(args).directory(work.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        try { assertTrue(process.waitFor(60,TimeUnit.SECONDS), args.toString());
            var result = new Run(process.exitValue(), Files.readString(out), Files.readString(err));
            assertEquals(0,result.code(), () -> args + "\n" + result.out() + result.err()); return result;
        } finally { if(process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
    }
    private static String hash(Path path) throws Exception { return HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))); }
}
