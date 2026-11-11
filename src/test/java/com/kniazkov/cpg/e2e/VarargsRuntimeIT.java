/* Bounded C++03 varargs against independent strict native compiler oracles. MIT License. */
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

class VarargsRuntimeIT {
    @TempDir Path temp;
    private int execution;
    private static final PlatformProfile PROFILE = PlatformProfile.builder()
            .floatingFormat("long double", "binary80")
            .varargs("gcc-clang-builtin", "x86_64-sysv-lp64-v1").build();
    private static final List<String> MACROS = List.of("va_start", "va_arg", "va_end");
    private static Cpg.Options options(Cpg.Stage stage) {
        return new Cpg.Options(Language.CPP, stage, "c++03").withPlatform(PROFILE);
    }
    private static String program(String includes, String listType) {
        return includes + "#include <cstdio>\n" + """
                int other(int count, ...);
                int category(int &value) { return 1; }
                int category(const int &value) { return 2; }
                int category_probe(int last, ...) {
                    LIST ap; va_start(ap, last);
                    int result = category(va_arg(ap, int)); va_end(ap); return result;
                }
                int mixed(int last, ...) {
                    LIST ap; va_start(ap, last);
                    unsigned long bytes = sizeof(va_arg(ap, double));
                    int a = va_arg(ap, int);
                    double b = va_arg(ap, double);
                    const int *p = va_arg(ap, const int *);
                    unsigned long d = va_arg(ap, unsigned long);
                    va_end(ap);
                    if (bytes != sizeof(double)) return -1;
                    return a + int(b) + *p + int(d);
                }
                // ISO C++03 18.7/3 does not carry over C's register restriction.
                int counted(register int count, ...) {
                    LIST ap; va_start(ap, count); int sum = 0;
                    for (int i = 0; i < count; ++i) sum += va_arg(ap, int);
                    va_end(ap); va_start(ap, count);
                    int first = va_arg(ap, int); va_end(ap);
                    return sum + first;
                }
                int independent(int last, ...) {
                    LIST a; LIST b; va_start(a, last); va_start(b, last);
                    int x = va_arg(a, int); int y = va_arg(b, int);
                    int z = va_arg(a, int); va_end(a); va_end(b);
                    return x + y + z;
                }
                int wide(int last, ...) {
                    LIST ap; va_start(ap, last);
                    unsigned int u = va_arg(ap, unsigned int);
                    long l = va_arg(ap, long);
                    long double d = va_arg(ap, long double);
                    va_end(ap); return int(u) + int(l) + int(d);
                }
                int editable(int anchor, ...) {
                    LIST cursor; va_start(cursor, anchor);
                    int value = va_arg(cursor, int); va_end(cursor); return value;
                }
                int main() {
                    if (sizeof(int) != 4 || sizeof(long) != 8 || sizeof(void *) != 8 || sizeof(long double) != 16) return 2;
                    const int object = 5; const int *pointer = &object;
                    char small = 2; float real = 3.0f;
                    int m = mixed(0, small, real, pointer, 6UL);
                    int c = counted(3, 2, 3, 4);
                    int i = independent(0, 2, 3);
                    int w = wide(0, 2U, 3L, 4.0L);
                    int o = other(2, 7, 8);
                    int e = editable(0, 41);
                    if (category_probe(0, 9) != 2) return 3;
                    if (m != 16 || c != 11 || i != 7 || w != 9 || o != 15) return 1;
                    std::printf("mixed=%d counted=%d independent=%d wide=%d other=%d edited=%d\\n", m,c,i,w,o,e);
                    return 0;
                }
                """.replace("LIST", listType);
    }
    private static final String OTHER = """
            #include <stdarg.h>
            typedef int Item;
            int other(int count, ...) {
                va_list ap; va_start(ap, count); int sum = 0;
                for (int i = 0; i < count; ++i) sum += va_arg(ap, Item);
                va_end(ap); return sum;
            }
            """;

    @Test void apiCliCopiesTransformsEditsAndMaterializedMacrosMatchBothCompilers() throws Exception {
        int fixture = 0;
        for (String headers : List.of("#include <cstdarg>\n", "#include <stdarg.h>\n",
                "#include <cstdarg>\n#include <stdarg.h>\n", "#include <stdarg.h>\n#include <cstdarg>\n")) {
            Path work = Files.createDirectory(temp.resolve("case-" + fixture++));
            var cli = new CliSandbox(work).source("main.cpp", program(headers,
                    headers.startsWith("#include <cstdarg>") ? "std::va_list" : "va_list"))
                    .source("other.cpp", OTHER);
            Project project = Cpg.read(cli.input, options(Cpg.Stage.RESOLVED));
            healthy(project); healthy(project.edit().build());
            assertTrue(nodes(project, VaListExpression.class).stream().anyMatch(v -> v.kind() == VaListExpression.Kind.ARG));
            Path settings = work.resolve("profile.json");
            Files.writeString(settings, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                    "--platform-profile", settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
            Map<String,String> generated = Cpg.sources(project, Language.CPP);
            for (var entry : generated.entrySet()) assertEquals(entry.getValue(),
                    Files.readString(run.work().resolve("generated").resolve(entry.getKey())));
            nativeRun(cli.input, "original", false);
            nativeRun(run.work().resolve("generated"), "cli-generated", false);
            checkGenerated(project.edit().build(), "copy", false);
            nativeRun(materialize(project, generated), "cpg-materialized", false);
            Project edited = edit(project);
            checkGenerated(edited, "edited", true);
            nativeRun(materialize(edited, Cpg.sources(edited, Language.CPP)), "edited-materialized", true);
            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(cli.input, options(stage));
                healthy(transformed); checkGenerated(transformed, stage.name(), false);
            }
        }
    }
    private Project edit(Project project) {
        var editor = new ProjectEditor(project);
        var function = nodes(project, CallableDeclaration.class).stream().filter(f -> f.name().equals("editable")).findFirst().orElseThrow();
        var operations = new ArrayList<VaListExpression>();
        function.walk(new NodeVisitor() { @Override public void visit(Node n) { if (n instanceof VaListExpression v) operations.add(v); } });
        var arg = operations.stream().filter(v -> v.kind() == VaListExpression.Kind.ARG).findFirst().orElseThrow();
        editor.replace(arg.requestedType().orElseThrow(), NamedType.create().setName("long").build());
        for (var declaration : nodes(project, VariableDeclaration.class)) {
            if (declaration.name().equals("cursor")) editor.replace(declaration, declaration.edit().setName("renamed_cursor").build());
        }
        var parameter = function.parameters().getFirst();
        editor.replace(parameter, parameter.edit().setName("renamed_anchor").build());
        for (var name : nodes(project, NameExpression.class)) {
            if (name.name().equals("cursor") || name.name().equals("anchor"))
                editor.replace(name, name.edit().setName("renamed_" + name.name()).build());
        }
        var call = nodes(project, CallExpression.class).stream().filter(c -> c.target().map(t -> t.name().equals("editable")).orElse(false)).findFirst().orElseThrow();
        editor.replace(call.arguments().get(1), IntegerLiteral.create().setValue(42).setCppType("long").build());
        Project result = NameResolver.resolve(editor.build()); healthy(result);
        String emitted = Cpg.sources(result, Language.CPP).get("main.cpp");
        assertTrue(emitted.contains("renamed_cursor")); assertTrue(emitted.contains("renamed_anchor"));
        return result;
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
            StringBuilder macros = new StringBuilder("\n#undef va_start\n#undef va_arg\n#undef va_end\n#undef va_copy\n#undef __va_copy\n");
            for (String macro : MACROS) {
                var definition = definitions.stream().filter(d -> d.name().equals(macro)).findFirst().orElseThrow();
                assertNull(definition.unavailableReason()); assertTrue(definition.functionLike());
                assertEquals("library", definition.originKind());
                assertTrue(definition.rawReplacement().contains("__builtin_va_"));
                macros.append("#define ").append(macro).append('(').append(String.join(",", definition.parameters()))
                        .append(") ").append(definition.rawReplacement()).append('\n');
            }
            String source = sources.get(name);
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
        return writeSources(materialized);
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
            var args = List.of(compiler, "-std=c++03", "-pedantic-errors", directory.resolve("main.cpp").toString(),
                    directory.resolve("other.cpp").toString(), "-o", binary.toString());
            Run compile = success(args, work); Run run = success(List.of(binary.toString()), work);
            assertEquals("mixed=16 counted=11 independent=7 wide=9 other=15 edited=" + (edited ? 42 : 41) + "\n", run.out());
            Path evidence = Files.createDirectories(Path.of("target/varargs-runtime-oracle")
                    .resolve(String.format("%03d-%s-%s", ++execution, label, compiler.equals("g++") ? "gcc" : "clang")));
            var hashes = new TreeMap<String,String>();
            for (String name : List.of("main.cpp", "other.cpp")) {
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
