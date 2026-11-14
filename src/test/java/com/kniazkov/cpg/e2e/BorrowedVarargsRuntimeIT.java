/* Bounded C++03 varargs against independent strict native compiler oracles. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.CppLexer;
import com.kniazkov.cpg.parser.PreprocessingToken;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class BorrowedVarargsRuntimeIT {
    @TempDir Path temp;
    private int execution;
    private static final PlatformProfile PROFILE = PlatformProfile.builder()
            .floatingFormat("long double", "binary80")
            .varargs("gcc-clang-builtin", "x86_64-sysv-lp64-borrowed-v2").build();
    private static final List<String> MACROS = List.of("va_start", "va_arg", "va_end");
    private static Cpg.Options options(Cpg.Stage stage) {
        return new Cpg.Options(Language.CPP, stage, "c++03").withPlatform(PROFILE);
    }
    private static String program(String includes, String listType) {
        return includes + "#include <cstdio>\n" + """
                typedef LIST List;
                int many(List ap) {
                    int result = 0;
                    for (int i = 0; i < 8; ++i) result += va_arg(ap, int);
                    double tail = va_arg(ap, double);
                    return result + int(tail);
                }
                long leaf(List ap) {
                    long value;
                    value = va_arg(ap, int);
                    return value;
                }
                int owner(int marker, ...) {
                    List ap; va_start(ap, marker);
                    int result = many(ap);
                    va_end(ap); return result;
                }
                long one(int marker, ...) {
                    List ap; va_start(ap, marker);
                    long result = leaf(ap);
                    va_end(ap); return result;
                }
                int main() {
                    int sum = owner(0, 1, 2, 3, 4, 5, 6, 7, 8, 6.0);
                    long single = one(0, 41);
                    std::printf("sum=%d single=%ld\\n", sum, single);
                    return 0;
                }
                """.replace("LIST", listType);
    }
    @Test void borrowedLeafOriginalCopiesStagesEditsCliAndRecordedMacros() throws Exception {
        int fixture = 0;
        for (String headers : List.of("#include <cstdarg>\n#include <stdarg.h>\n",
                "#include <stdarg.h>\n#include <cstdarg>\n")) {
            Path work = Files.createDirectory(temp.resolve("case-" + fixture++));
            String source = program(headers, headers.startsWith("#include <cstdarg>") ? "std::va_list" : "va_list");
            var cli = new CliSandbox(work).source("main.cpp", source);
            nativeRun(cli.input, "original", false);
            Project project = Cpg.read(cli.input, options(Cpg.Stage.RESOLVED));
            healthy(project);
            Project copy = project.edit().build();
            healthy(copy); assertLocal(copy, project); checkGenerated(copy, "copy", false);
            checkGenerated(NameResolver.resolve(copy), "reanalyzed", false);
            Path settings = work.resolve("profile.json");
            Files.writeString(settings, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                    "--platform-profile", settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
            Map<String,String> generated = Cpg.sources(project, Language.CPP);
            for (var entry : generated.entrySet()) assertEquals(entry.getValue(),
                    Files.readString(run.work().resolve("generated").resolve(entry.getKey())));
            nativeRun(run.work().resolve("generated"), "cli-generated", false);
            nativeRun(materialize(project, generated), "recorded-macros", false);
            for (var stage : List.of(Cpg.Stage.RESOLVED, Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                Project transformed = Cpg.read(cli.input, options(stage));
                healthy(transformed); checkGenerated(transformed, stage.name(), false);
                Project edited = edit(transformed, true);
                checkGenerated(edited, stage.name() + "-edited", true);
                nativeRun(materialize(edited, Cpg.sources(edited, Language.CPP)), stage.name() + "-edited-macros", true);
                Project reparsed = Cpg.read(writeSources(Cpg.sources(edited, Language.CPP)), options(Cpg.Stage.RESOLVED));
                checkGenerated(reparsed, stage.name() + "-edited-reparsed", true);
                Project reversed = edit(edited, false);
                checkGenerated(reversed, stage.name() + "-reversed", false);
                nativeRun(materialize(reversed, Cpg.sources(reversed, Language.CPP)), stage.name() + "-reversed-macros", false);
            }
            assertEquals(source, Files.readString(cli.input.resolve("main.cpp")));
        }
    }
    @Test void previousPolicyAndLifecycleBoundariesAreNeverExecuted() throws Exception {
        String good = program("#include <cstdarg>\n", "std::va_list");
        PlatformProfile old = PlatformProfile.builder().floatingFormat("long double", "binary80")
                .varargs("gcc-clang-builtin", "x86_64-sysv-lp64-v1").build();
        List<String> programs = List.of(good,
                good.replace("int result = many(ap);", "int result = many(ap); result += va_arg(ap, int);"),
                good.replace("return result + int(tail);", "va_end(ap); return result + int(tail);"));
        for (int i = 0; i < programs.size(); i++) {
            Path work = Files.createDirectory(temp.resolve("negative-" + i));
            var cli = new CliSandbox(work).source("main.cpp", programs.get(i));
            PlatformProfile profile = i == 0 ? old : PROFILE;
            Project rejected = Cpg.read(cli.input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03").withPlatform(profile));
            assertTrue(rejected.hasErrors());
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(rejected, Language.CPP));
            Path settings = work.resolve("profile.json");
            Files.writeString(settings, Json.serialize(profile.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                    "--platform-profile", settings.toString()).expectStatus(1);
            assertFalse(Files.exists(run.work().resolve("generated/main.cpp")));
        }
    }
    private Project edit(Project project, boolean forward) {
        String before = ProjectJson.encode(project).toString();
        Map<String,String> sources = Cpg.sources(project, Language.CPP);
        var editor = new ProjectEditor(project);
        var leaf = nodes(project, FunctionDeclaration.class).stream()
                .filter(f -> f.name().equals("leaf") && f.body().isPresent()).findFirst().orElseThrow();
        var parameter = leaf.parameters().getFirst();
        String name = forward ? "renamed_cursor" : "ap";
        editor.replace(parameter, parameter.edit().setName(name).build());
        for (var reference : nodes(project, NameExpression.class))
            if (reference.target().orElse(null) == parameter)
                editor.replace(reference, reference.edit().setName(name).build());
        leaf.walk(new NodeVisitor() { @Override public void visit(Node n) {
            if (n instanceof VaListExpression va && va.kind() == VaListExpression.Kind.ARG)
                editor.replace(va.requestedType().orElseThrow(), NamedType.create().setName(forward ? "long" : "int").build());
        }});
        for (var call : nodes(project, CallExpression.class))
            if (call.target().map(t -> t.name().equals("one")).orElse(false))
                editor.replace(call.arguments().get(1), IntegerLiteral.create().setValue(forward ? 42 : 41)
                        .setCppType(forward ? "long" : "int").build());
        Project result = NameResolver.resolve(editor.build());
        healthy(result); assertLocal(result, project);
        String emitted = Cpg.sources(result, Language.CPP).get("main.cpp");
        assertEquals(forward, emitted.contains("renamed_cursor"));
        assertEquals(before, ProjectJson.encode(project).toString());
        assertEquals(sources, Cpg.sources(project, Language.CPP));
        return result;
    }
    private static void assertLocal(Project snapshot, Project original) {
        Set<Node> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        snapshot.walk(new NodeVisitor() { @Override public void visit(Node node) { owned.add(node); } });
        Set<Node> old = Collections.newSetFromMap(new IdentityHashMap<>());
        original.walk(new NodeVisitor() { @Override public void visit(Node node) { old.add(node); } });
        for (Node node : owned) {
            assertFalse(old.contains(node), "Copies must not retain original syntax nodes");
            for (Node reference : node.references()) assertTrue(owned.contains(reference), "Reference must be snapshot-local");
        }
    }
    private void checkGenerated(Project project, String label, boolean edited) throws Exception {
        healthy(project);
        Path directory = writeSources(Cpg.sources(project, Language.CPP));
        healthy(Cpg.read(directory, options(Cpg.Stage.RESOLVED)));
        nativeRun(directory, label, edited);
    }
    /** Installs the actual graph-recorded replacements after all real includes. No host va macro survives. */
    private Path materialize(Project project, Map<String,String> sources) throws Exception {
        var materialized = new LinkedHashMap<String,String>();
        for (var file : project.files()) {
            String name = file.path();
            if (!sources.containsKey(name)) continue;
            var definitions = file.provenance().orElseThrow().unit().definitions();
            StringBuilder macros = new StringBuilder("\n#undef va_start\n#undef va_arg\n#undef va_end\n#undef va_copy\n#undef __va_copy\n"
                    + "#if defined(va_start) || defined(va_arg) || defined(va_end) || defined(va_copy) || defined(__va_copy)\n"
                    + "#error Host varargs macros survived\n#endif\n");
            var intrinsicMacros = new LinkedHashMap<String,String>();
            for (String macro : MACROS) {
                var definition = definitions.stream().filter(d -> d.name().equals(macro)).findFirst().orElseThrow();
                assertNull(definition.unavailableReason()); assertTrue(definition.functionLike());
                assertEquals("library", definition.originKind());
                var replacementTokens = tokens(definition.rawReplacement());
                var intrinsics = replacementTokens.stream().filter(t -> t.startsWith("__builtin_va_")).toList();
                assertEquals(1, intrinsics.size(), "The adapter must directly call exactly one intrinsic");
                String intrinsic = intrinsics.getFirst();
                var expectedTokens = new ArrayList<String>(List.of("(", intrinsic, "("));
                for (String parameter : definition.parameters()) {
                    if (!expectedTokens.getLast().equals("(")) expectedTokens.add(",");
                    expectedTokens.add(parameter);
                }
                expectedTokens.addAll(List.of(")", ")"));
                assertEquals(expectedTokens, replacementTokens, "Materialization requires exact argument forwarding");
                assertNull(intrinsicMacros.put(intrinsic, macro));
                macros.append("#define ").append(macro).append('(').append(String.join(",", definition.parameters()))
                        .append(") ").append(definition.rawReplacement()).append('\n');
            }
            // Re-route only generated intrinsic identifier tokens, never comments, strings,
            // operands or the prologue. The mapping is verified against this graph's adapter.
            String source = routeThroughRecordedMacros(name, sources.get(name), intrinsicMacros);
            if (source.contains("renamed_cursor"))
                assertTrue(containsTokens(tokens(source), List.of("va_arg", "(", "renamed_cursor", ",", "long", ")")),
                        "Edited retrieval must exercise the graph-recorded adapter");
            for (String macro : MACROS) assertTrue(java.util.regex.Pattern.compile("\\b" + macro + "\\s*\\(").matcher(source).find(),
                    "Materialization must exercise actual CPG replacement for " + macro);
            int end = 0;
            for (String line : source.split("(?<=\\n)")) {
                if (line.isBlank() || line.stripLeading().startsWith("#include")) end += line.length();
                else break;
            }
            assertTrue(end > 0, source);
            materialized.put(name, source.substring(0,end) + macros + source.substring(end));
        }
        assertEquals(sources.keySet(), materialized.keySet());
        Path directory = writeSources(materialized);
        healthy(Cpg.read(directory, options(Cpg.Stage.RESOLVED)));
        return directory;
    }
    private static List<String> tokens(String source) {
        return CppLexer.scan("materialization.cpp", source).tokens().stream()
                .filter(t -> t.kind() != PreprocessingToken.Kind.WHITESPACE
                        && t.kind() != PreprocessingToken.Kind.NEWLINE && t.kind() != PreprocessingToken.Kind.EOF)
                .map(PreprocessingToken::canonicalSpelling).toList();
    }
    private static boolean containsTokens(List<String> source, List<String> expected) {
        for (int i = 0; i <= source.size() - expected.size(); i++)
            if (source.subList(i, i + expected.size()).equals(expected)) return true;
        return false;
    }
    private static String routeThroughRecordedMacros(String name, String source, Map<String,String> mapping) {
        var lexed = CppLexer.scan(name, source);
        var result = new StringBuilder(source);
        for (var token : lexed.tokens().reversed()) {
            if (token.kind() != PreprocessingToken.Kind.IDENTIFIER) continue;
            String replacement = mapping.get(token.canonicalSpelling());
            if (replacement == null) continue;
            int start = token.range().start().offset(), end = token.range().end().offset();
            assertEquals(token.spelling(), source.substring(start, end));
            result.replace(start, end, replacement);
        }
        String body = result.toString();
        assertFalse(CppLexer.scan(name, body).tokens().stream().anyMatch(t ->
                t.kind() == PreprocessingToken.Kind.IDENTIFIER && t.canonicalSpelling().startsWith("__builtin_va_")),
                "Materialized body must use recorded public macros, not direct intrinsics");
        assertEquals(lexed.comments().stream().map(CppLexer.CommentToken::text).toList(),
                CppLexer.scan(name, body).comments().stream().map(CppLexer.CommentToken::text).toList());
        return body;
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
            var args = List.of(compiler, "-std=c++03", "-pedantic-errors", "-O2", directory.resolve("main.cpp").toString(), "-o", binary.toString());
            Run compile = success(args, work); Run run = success(List.of(binary.toString()), work);
            assertEquals("sum=42 single=" + (edited ? 42 : 41) + "\n", run.out());
            Path evidence = Files.createDirectories(Path.of("target/borrowed-varargs-runtime-oracle")
                    .resolve(String.format("%03d-%s-%s", ++execution, label, compiler.equals("g++") ? "gcc" : "clang")));
            var hashes = new TreeMap<String,String>();
            for (String name : List.of("main.cpp")) {
                Files.copy(directory.resolve(name), evidence.resolve(name), StandardCopyOption.REPLACE_EXISTING);
                hashes.put(name, hash(directory.resolve(name)));
            }
            var metadata = new LinkedHashMap<String,Object>();
            metadata.put("compiler", compiler);
            metadata.put("versionCommand", List.of(compiler, "--version"));
            metadata.put("version", success(List.of(compiler,"--version"), work).out());
            metadata.put("targetCommand", List.of(compiler, "-dumpmachine"));
            metadata.put("target", success(List.of(compiler,"-dumpmachine"), work).out());
            metadata.put("command", args); metadata.put("runCommand", List.of(binary.toString()));
            metadata.put("profile", PROFILE.toSettings()); metadata.put("sourceSha256", hashes);
            metadata.put("compileExitCode", compile.code());
            metadata.put("compileStdout", compile.out()); metadata.put("compileStderr", compile.err());
            metadata.put("exitCode", run.code()); metadata.put("stdout", run.out()); metadata.put("stderr", run.err());
            Files.writeString(evidence.resolve("metadata.json"), Json.serialize(metadata));
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
