/* Strict native returns-twice and CPG replacement replay evidence. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.parser.CppLexer;
import com.kniazkov.cpg.parser.PreprocessingToken;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StaticSetjmpRuntimeIT {
    @TempDir Path temp;
    private int execution;
    private static final PlatformProfile PROFILE = PlatformProfile.builder().cRuntime("glibc").setjmp("gcc-clang-glibc-static-v2").build();
    private static Cpg.Options options(Cpg.Stage stage) { return new Cpg.Options(Language.CPP,stage,"c++03").withPlatform(PROFILE); }
    // Static/global state only; save frame remains alive and no automatic
    // declaration is skipped by longjmp, including under original C++03 wording.
    private static String program(String headers) {
        String type = headers.startsWith("#include <csetjmp>") ? "std::jmp_buf" : "jmp_buf";
        return headers + "#include <cstdio>\n" + """
                namespace storage { BUFFER env; BUFFER spare; int marker; }
                BUFFER global_env;
                int visits;
                int probe(int value) {
                    static BUFFER env; static BUFFER spare; static int marker;
                    marker = 0;
                    switch(setjmp(env)) {
                        case 0: marker = 5; JUMP(env, value);
                        case 1: return marker + 1;
                        case 7: return marker + 7;
                        case 9: return marker + 9;
                        default: return -1;
                    }
                }
                int namespace_probe(int value) {
                    storage::marker = 0;
                    switch(setjmp(storage::env)) {
                        case 0: storage::marker = 10; JUMP(storage::env, value);
                        case 1: return storage::marker + 1;
                        case 7: return storage::marker + 7;
                        default: return -1;
                    }
                }
                int discarded() {
                    visits = 0;
                    (void)setjmp(global_env);
                    if (!visits) { visits = 3; JUMP(global_env, 0); }
                    return visits;
                }
                int main() {
                    int zero = probe(0); int nonzero = probe(7);
                    int global_zero = namespace_probe(0); int global_seven = namespace_probe(7);
                    int count = discarded();
                    std::printf("zero=%d nonzero=%d global_zero=%d global_seven=%d discarded=%d\\n",
                                zero, nonzero, global_zero, global_seven, count);
                    return zero == 6 && global_zero == 11 && global_seven == 17 && count == 3 ? 0 : 1;
                }
                """.replace("BUFFER",type).replace("JUMP", headers.contains("<csetjmp>") ? "std::longjmp" : "::longjmp");
    }
    @Test void apiCliNativeReplaysAndRealEditsExecuteReturnsTwiceAtOptimization() throws Exception {
        int fixture = 0;
        for (String headers : List.of("#include <csetjmp>\n", "#include <setjmp.h>\n", "#include <csetjmp>\n#include <setjmp.h>\n", "#include <setjmp.h>\n#include <csetjmp>\n")) {
            Path work = Files.createDirectory(temp.resolve("case-" + fixture++));
            var cli = new CliSandbox(work).source("main.cpp", program(headers));
            Project project = Cpg.read(cli.input, options(Cpg.Stage.RESOLVED)); healthy(project);
            assertEquals(3, nodes(project,SetjmpExpression.class).size());
            Path settings = work.resolve("profile.json"); Files.writeString(settings,Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),Json.parse(run.work().resolve("snapshot.json").toFile()));
            assertEquals(Cpg.sources(project,Language.CPP).get("main.cpp"),Files.readString(run.work().resolve("generated/main.cpp")));
            nativeRun(cli.input,"original",false); nativeRun(run.work().resolve("generated"),"cli-generated",false);
            var copy=project.edit().build(); assertLocal(copy,project);
            checkGenerated(copy,"copy",false);
            nativeRun(materialize(project,Cpg.sources(project,Language.CPP)),"cpg-materialized",false);
            var edited = edit(project, true); checkGenerated(edited,"edited",true);
            nativeRun(materialize(edited,Cpg.sources(edited,Language.CPP)),"edited-materialized",true);
            var reparsed = Cpg.read(writeSources(Cpg.sources(edited,Language.CPP)), options(Cpg.Stage.RESOLVED));
            checkGenerated(reparsed,"edited-reparsed",true);
            var reversed = edit(edited,false); checkGenerated(reversed,"reversed",false);
            nativeRun(materialize(reversed,Cpg.sources(reversed,Language.CPP)),"reversed-materialized",false);
            assertEquals(program(headers),Files.readString(cli.input.resolve("main.cpp")));
            for (var stage : List.of(Cpg.Stage.GREEN,Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(cli.input,options(stage)); healthy(transformed);
                checkGenerated(transformed,stage.name(),false);
                checkGenerated(edit(transformed,true),stage.name()+"-edited",true);
            }
        }
    }
    @Test void oldPolicyStillRejectsStaticBuffers() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", program("#include <csetjmp>\n"));
        var old = PlatformProfile.builder().cRuntime("glibc").setjmp("gcc-clang-glibc").build();
        Project rejected = Cpg.read(cli.input, new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03").withPlatform(old));
        assertTrue(rejected.hasErrors());
        assertThrows(IllegalArgumentException.class, () -> Cpg.sources(rejected,Language.CPP));
        Path settings = temp.resolve("profile.json"); Files.writeString(settings,Json.serialize(old.toSettings()));
        var run = cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--platform-profile",settings.toString());
        assertNotEquals(0,run.status());
        assertFalse(Files.exists(run.work().resolve("generated/main.cpp")));
    }
    private Project edit(Project project, boolean forward) {
        String before = ProjectJson.encode(project).toString();
        Map<String,String> sources = Cpg.sources(project,Language.CPP);
        String from = forward ? "env" : "spare", to = forward ? "spare" : "env";
        var editor = new ProjectEditor(project);
        var function = nodes(project,CallableDeclaration.class).stream().filter(f -> f.name().equals("probe")).findFirst().orElseThrow();
        // Rebind BOTH the save and restore operands to an independent local buffer.
        function.walk(new NodeVisitor() { @Override public void visit(NameExpression name) {
            if(name.name().equals(from)) editor.replace(name,NameExpression.create().setName(to).build());
        } });
        var call = nodes(project,CallExpression.class).stream().filter(c -> c.target().map(t -> t.name().equals("probe")).orElse(false)
                && c.arguments().getFirst() instanceof IntegerLiteral literal && literal.bigValue().intValue()==(forward ? 7 : 9)).findFirst().orElseThrow();
        editor.replace(call.arguments().getFirst(),IntegerLiteral.create().setValue(forward ? 9 : 7).build());
        var result=NameResolver.resolve(editor.build()); healthy(result);
        var editedFunction=nodes(result,CallableDeclaration.class).stream().filter(f -> f.name().equals("probe")).findFirst().orElseThrow();
        var saves = new ArrayList<SetjmpExpression>(); editedFunction.walk(new NodeVisitor(){ @Override public void visit(SetjmpExpression node){ saves.add(node); } });
        assertEquals(to,((VariableDeclaration)((NameExpression)saves.getFirst().buffer()).target().orElseThrow()).name());
        assertEquals(before,ProjectJson.encode(project).toString());
        assertEquals(sources,Cpg.sources(project,Language.CPP));
        assertLocal(result,project);
        return result;
    }
    private static void assertLocal(Project snapshot,Project original) {
        Set<Node> owned=Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> old=Collections.newSetFromMap(new IdentityHashMap<>());
        snapshot.walk(new NodeVisitor(){ @Override public void visit(Node n){owned.add(n);} });
        original.walk(new NodeVisitor(){ @Override public void visit(Node n){old.add(n);} });
        for(Node n:owned) {
            assertFalse(old.contains(n));
            for(Node reference:n.references()) assertTrue(owned.contains(reference),"Reference must remain snapshot-local");
        }
    }
    private void checkGenerated(Project project,String label,boolean edited) throws Exception {
        healthy(project); Path directory=writeSources(Cpg.sources(project,Language.CPP));
        healthy(Cpg.read(directory,options(Cpg.Stage.RESOLVED))); nativeRun(directory,label,edited);
    }
    /** Replays the exact CPG-recorded macro after removing the host macro.
     * Generated _setjmp identifier tokens are routed through that replacement,
     * including genuinely edited save operands. */
    private Path materialize(Project project,Map<String,String> sources) throws Exception {
        var file=project.files().getFirst();
        var definition=file.provenance().orElseThrow().unit().definitions().stream().filter(d -> d.name().equals("setjmp")).findFirst().orElseThrow();
        assertEquals("library",definition.originKind()); assertTrue(definition.functionLike()); assertNull(definition.unavailableReason());
        assertTrue(definition.rawReplacement().contains("_setjmp"));
        String source=sources.get("main.cpp");
        var lexed=CppLexer.scan("main.cpp",source);
        var tokens=lexed.tokens().stream().filter(t -> t.kind()!=PreprocessingToken.Kind.WHITESPACE
                && t.kind()!=PreprocessingToken.Kind.NEWLINE && t.kind()!=PreprocessingToken.Kind.EOF).toList();
        var routed=new StringBuilder(source);
        for(int i=tokens.size()-1;i>=0;i--) {
            var token=tokens.get(i);
            if(token.kind()!=PreprocessingToken.Kind.IDENTIFIER || !token.canonicalSpelling().equals("_setjmp")) continue;
            assertTrue(i+1<tokens.size() && tokens.get(i+1).canonicalSpelling().equals("("),
                    "Only generated bridge calls may be routed through the recorded macro");
            routed.replace(token.range().start().offset(),token.range().end().offset(),"setjmp");
            // The recorded macro supplies its own global qualification. Remove the
            // generated bridge's :: token, not surrounding whitespace or comments.
            if(i>0 && tokens.get(i-1).canonicalSpelling().equals("::")) {
                var qualifier=tokens.get(i-1);
                routed.delete(qualifier.range().start().offset(),qualifier.range().end().offset());
            }
        }
        assertEquals(lexed.comments().stream().map(CppLexer.CommentToken::text).toList(),
                CppLexer.scan("main.cpp",routed.toString()).comments().stream().map(CppLexer.CommentToken::text).toList());
        source=routed.toString();
        assertTrue(java.util.regex.Pattern.compile("(?<![_a-zA-Z])setjmp\\s*\\(").matcher(source).find(),"Replay must use graph-recorded replacement");
        String macros="\n#undef setjmp\n#define setjmp("+String.join(",",definition.parameters())+") "+definition.rawReplacement()+"\n";
        int end=0;
        for(String line:source.split("(?<=\\n)")) {
            if(line.isBlank() || line.stripLeading().startsWith("#include")) end+=line.length(); else break;
        }
        assertTrue(end>0,source);
        Path directory=writeSources(Map.of("main.cpp",source.substring(0,end)+macros+source.substring(end)));
        healthy(Cpg.read(directory,options(Cpg.Stage.RESOLVED)));
        return directory;
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
            assertEquals("zero=6 nonzero=" + (edited ? 14 : 12) + " global_zero=11 global_seven=17 discarded=3\n", run.out());
            Path evidence = Files.createDirectories(Path.of("target/static-setjmp-runtime-oracle")
                    .resolve(String.format("%03d-%s-%s", ++execution, label, compiler.equals("g++") ? "gcc" : "clang")));
            var hashes = new TreeMap<String,String>();
            for (String name : List.of("main.cpp")) {
                Files.copy(directory.resolve(name), evidence.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                hashes.put(name, hash(directory.resolve(name)));
            }
            var metadata=new LinkedHashMap<String,Object>();
            metadata.put("compiler",compiler);
            metadata.put("versionCommand",List.of(compiler,"--version"));
            metadata.put("version",success(List.of(compiler,"--version"),work).out());
            metadata.put("targetCommand",List.of(compiler,"-dumpmachine"));
            metadata.put("target",success(List.of(compiler,"-dumpmachine"),work).out());
            metadata.put("command",args); metadata.put("runCommand",List.of(binary.toString()));
            metadata.put("profile",PROFILE.toSettings()); metadata.put("sourceSha256",hashes);
            metadata.put("compileExitCode",compile.code()); metadata.put("compileStdout",compile.out());
            metadata.put("compileStderr",compile.err()); metadata.put("exitCode",run.code());
            metadata.put("stdout",run.out()); metadata.put("stderr",run.err());
            Files.writeString(evidence.resolve("metadata.json"),Json.serialize(metadata));
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
