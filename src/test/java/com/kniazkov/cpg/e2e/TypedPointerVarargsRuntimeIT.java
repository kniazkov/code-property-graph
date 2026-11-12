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

class TypedPointerVarargsRuntimeIT {
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
                namespace types {
                    typedef int (*Callback)(int);
                    typedef Callback CallbackAlias;
                    typedef CallbackAlias EditableCallback;
                    typedef void (*Mutator)(int *);
                    typedef int (*Row)[3];
                    typedef const int (*ConstRow)[3];
                }
                int other(int last, ...);
                int global_value = 1;
                int inc(int value) { return value + 1; }
                void mutate(int *value) { *value = 9; global_value = 11; }
                enum Kind { first_kind };
                LIST type_only_list;
                unsigned long global_float_size = sizeof(va_arg(type_only_list, float));
                unsigned long type_only(LIST ap) { return sizeof(va_arg(ap, Kind)); }
                int consume(int anchor, ...) {
                    LIST cursor; va_start(cursor, anchor);
                    types::EditableCallback callback;
                    callback = va_arg(cursor, types::EditableCallback);
                    types::Row row = va_arg(cursor, types::Row);
                    va_end(cursor); return int(callback((*row)[1]));
                }
                int callback_anchor(types::CallbackAlias last, ...) {
                    LIST ap; va_start(ap, last); int value = va_arg(ap, int);
                    va_end(ap); return int(last(value));
                }
                int row_anchor(types::Row last, ...) {
                    LIST ap; va_start(ap, last); int value = va_arg(ap, int);
                    va_end(ap); return value + (*last)[0];
                }
                int effects(int last, ...) {
                    LIST ap; va_start(ap, last);
                    types::Mutator action = va_arg(ap, types::Mutator);
                    types::Row row = va_arg(ap, types::Row);
                    types::ConstRow fixed = va_arg(ap, types::ConstRow);
                    int local = 2; global_value = 3;
                    action(&local); (*row)[2] = 12;
                    va_end(ap); return local + global_value + (*row)[2] + (*fixed)[1];
                }
                int lifecycle(int last, ...) {
                    LIST a; LIST b; va_start(a, last); va_start(b, last);
                    unsigned long width = sizeof(va_arg(a, types::CallbackAlias));
                    unsigned long floating = sizeof(va_arg(a, float));
                    unsigned long enumeration = sizeof(va_arg(a, Kind));
                    types::CallbackAlias cb = va_arg(a, types::CallbackAlias);
                    va_arg(b, types::CallbackAlias);
                    types::Row row = va_arg(b, types::Row);
                    int value;
                    if (last) value = int(cb((*row)[0]));
                    else value = int(cb((*row)[1]));
                    va_end(a); va_end(b); va_start(a, last);
                    types::CallbackAlias restarted = va_arg(a, types::CallbackAlias);
                    va_end(a);
                    if (width != sizeof(types::CallbackAlias) || floating != sizeof(float) || enumeration != sizeof(Kind)) return -1;
                    return value + int(restarted(1));
                }
                int main() {
                    int values[3] = {2,4,6}; const int fixed[3] = {3,5,7};
                    types::Row row = &values; types::ConstRow constant = &fixed;
                    types::CallbackAlias callback = &inc; types::Mutator mutator = &mutate;
                    int c = consume(0, callback, row);
                    int f = callback_anchor(callback, 3); int r = row_anchor(row, 5);
                    int l = lifecycle(1, callback, row) + lifecycle(0, callback, row);
                    int e = effects(0, mutator, row, constant);
                    int o = other(0, callback, row);
                    int direct = int(inc(8));
                    if (c != 5 || f != 4 || r != 7 || l != 12 || e != 37 || o != 13 || direct != 9) return 1;
                    if (global_float_size != sizeof(float) || sizeof(va_arg(type_only_list, Kind)) != sizeof(Kind)) return 2;
                    std::printf("consume=%d callback=%d row=%d lifecycle=%d effects=%d other=%d width=%lu result=%lu\\n",c,f,r,l,e,o,sizeof(*row),sizeof(callback(0)));
                    return 0;
                }
                """.replace("LIST", listType);
    }
    private static final String OTHER = """
            #include <stdarg.h>
            namespace types { typedef int (*Callback)(int); typedef int (*Row)[3]; }
            int other(int last, ...) {
                va_list ap; va_start(ap, last);
                types::Callback callback = va_arg(ap, types::Callback);
                types::Row row = va_arg(ap, types::Row);
                va_end(ap); return int(callback((*row)[2]));
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
            var originalFiles = Map.of("main.cpp", Files.readString(cli.input.resolve("main.cpp")),
                    "other.cpp", Files.readString(cli.input.resolve("other.cpp")));
            nativeRun(cli.input, "original", false);
            Project project = Cpg.read(cli.input, options(Cpg.Stage.RESOLVED));
            healthy(project); healthy(project.edit().build());
            assertLocal(project.edit().build(), project);
            checkGenerated(NameResolver.resolve(project.edit().build()), "reanalyzed", false);
            assertTrue(nodes(project, VaListExpression.class).stream().anyMatch(v -> v.kind() == VaListExpression.Kind.ARG));
            Path settings = work.resolve("profile.json");
            Files.writeString(settings, Json.serialize(PROFILE.toSettings()));
            var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03",
                    "--platform-profile", settings.toString()).expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
            Map<String,String> generated = Cpg.sources(project, Language.CPP);
            for (var entry : generated.entrySet()) assertEquals(entry.getValue(),
                    Files.readString(run.work().resolve("generated").resolve(entry.getKey())));
            nativeRun(run.work().resolve("generated"), "cli-generated", false);
            checkGenerated(project.edit().build(), "copy", false);
            nativeRun(materialize(project, generated), "cpg-materialized", false);
            Project edited = edit(project);
            for (var entry : originalFiles.entrySet())
                assertEquals(entry.getValue(), Files.readString(cli.input.resolve(entry.getKey())));
            checkGenerated(edited, "edited", true);
            nativeRun(materialize(edited, Cpg.sources(edited, Language.CPP)), "edited-materialized", true);
            for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
                var transformed = Cpg.read(cli.input, options(stage));
                healthy(transformed); checkGenerated(transformed, stage.name(), false);
            }
        }
    }
    @Test void structuralAliasEditContainingMacroRemainsExplicitlyRejected() throws Exception {
        Path directory = writeSources(Map.of("main.cpp", """
                #include <cstdarg>
                typedef int (*Callback)(int);
                int read(int last, ...) {
                    std::va_list ap; va_start(ap, last);
                    Callback callback = va_arg(ap, Callback);
                    va_end(ap); return callback(3);
                }
                """));
        Project original = Cpg.read(directory, options(Cpg.Stage.RESOLVED));
        healthy(original);
        String before = ProjectJson.encode(original).toString();
        var editor = new ProjectEditor(original);
        for (var alias : nodes(original, TypedefDeclaration.class)) {
            if (alias.name().equals("Callback"))
                editor.replace(alias, alias.edit().setName("RenamedCallback").build());
        }
        for (var type : nodes(original, NamedType.class)) {
            if (type.name().equals("Callback"))
                editor.replace(type, type.edit().setName("RenamedCallback").build());
        }
        Project edited = NameResolver.resolve(editor.build());
        healthy(edited); assertLocal(edited, original);
        var error = assertThrows(IllegalArgumentException.class, () -> Cpg.sources(edited, Language.CPP));
        assertTrue(error.getMessage().contains("structural edit would flatten other macro calls"), error.getMessage());
        assertEquals(before, ProjectJson.encode(original).toString());
    }

    private Project edit(Project project) {
        var before = ProjectJson.encode(project).toString();
        var sources = Cpg.sources(project, Language.CPP);
        var editor = new ProjectEditor(project);
        // Change the actual typedef syntax and both declarations of its signature.
        for (var declaration : nodes(project, TypedefDeclaration.class)) {
            if (declaration.name().equals("Callback")) {
                declaration.type().walk(new NodeVisitor() { @Override public void visit(Node n) {
                    if (n instanceof NamedType t && t.name().equals("int"))
                        editor.replace(t, NamedType.create().setName("long").build());
                }});
            }
            if (declaration.name().equals("EditableCallback"))
                editor.replace(declaration, declaration.edit().setName("RenamedCallback").build());
        }
        for (var function : nodes(project, CallableDeclaration.class)) {
            if (function.name().equals("inc")) function.walk(new NodeVisitor() { @Override public void visit(Node n) {
                if (n instanceof NamedType t && t.name().equals("int"))
                    editor.replace(t, NamedType.create().setName("long").build());
            }});
            if (function.name().equals("consume")) {
                var parameter = function.parameters().getFirst();
                editor.replace(parameter, parameter.edit().setName("renamed_anchor").build());
            }
        }
        for (var array : nodes(project, ArrayType.class)) {
            if (array.bound().isPresent()) editor.replace(array.bound().orElseThrow(), IntegerLiteral.create().setValue(4).build());
        }
        for (var declaration : nodes(project, VariableDeclaration.class)) {
            if (declaration.name().equals("cursor")) editor.replace(declaration, declaration.edit().setName("renamed_cursor").build());
        }
        for (var type : nodes(project, NamedType.class)) {
            if (type.name().equals("EditableCallback") || type.name().endsWith("::EditableCallback"))
                editor.replace(type, type.edit().setName(type.name().replace("EditableCallback", "RenamedCallback")).build());
        }
        for (var name : nodes(project, NameExpression.class)) {
            if (name.name().equals("cursor") || name.name().equals("anchor"))
                editor.replace(name, name.edit().setName("renamed_" + name.name()).build());
        }
        for (var call : nodes(project, CallExpression.class)) {
            if (call.target().map(t -> t.name().equals("inc")).orElse(false))
                editor.replace(call.arguments().getFirst(), IntegerLiteral.create().setValue(8).setCppType("long").build());
        }
        Project result = NameResolver.resolve(editor.build()); healthy(result);
        String emitted = Cpg.sources(result, Language.CPP).get("main.cpp");
        assertTrue(emitted.contains("renamed_cursor")); assertTrue(emitted.contains("renamed_anchor"));
        assertTrue(emitted.contains("RenamedCallback")); assertFalse(emitted.contains("EditableCallback"));
        assertTrue(emitted.contains("CallbackAlias"));
        assertEquals(before, ProjectJson.encode(project).toString());
        assertEquals(sources, Cpg.sources(project, Language.CPP));
        assertLocal(result, project);
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
            if (source.contains("RenamedCallback") && name.equals("main.cpp")) {
                assertTrue(containsTokens(tokens(source), List.of("va_arg", "(", "renamed_cursor", ",",
                        "types", "::", "RenamedCallback", ")")), "Edited retrieval must exercise the recorded adapter");
            }
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
            var args = List.of(compiler, "-std=c++03", "-pedantic-errors", directory.resolve("main.cpp").toString(),
                    directory.resolve("other.cpp").toString(), "-o", binary.toString());
            Run compile = success(args, work); Run run = success(List.of(binary.toString()), work);
            assertEquals("consume=5 callback=4 row=7 lifecycle=12 effects=37 other=13 width="
                    + (edited ? 16 : 12) + " result=" + (edited ? 8 : 4) + "\n", run.out());
            Path evidence = Files.createDirectories(Path.of("target/typed-pointer-varargs-runtime-oracle")
                    .resolve(String.format("%03d-%s-%s", ++execution, label, compiler.equals("g++") ? "gcc" : "clang")));
            var hashes = new TreeMap<String,String>();
            for (String name : List.of("main.cpp", "other.cpp")) {
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
