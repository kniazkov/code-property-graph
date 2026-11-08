/* C++03 strstream CLI, graph and native lifecycle checks. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.LibraryDeclarations;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class StrstreamLibraryIT {
    @TempDir Path temp;
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var found = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) found.add(type.cast(node));
        } });
        return found;
    }
    private static void declarationOnly(Project project) {
        for (var callable : nodes(project, CallableDeclaration.class))
            if (LibraryDeclarations.isLibrary(callable)) assertTrue(callable.body().isEmpty(), callable.name());
    }
    private static void verifyRuntimeGraph(Project project) {
        assertFalse(project.hasErrors(), () -> project.semanticDiagnostics().toString());
        declarationOnly(project);
        for (var call : nodes(project.files().getFirst(), CallExpression.class)) assertTrue(call.target().isPresent(), () -> call.range().toString());
        for (var variable : nodes(project.files().getFirst(), VariableDeclaration.class))
            if (Set.of("source", "sink", "both", "dynamic").contains(variable.name())) {
                assertTrue(variable.constructorTarget().isPresent(), variable.name());
                assertTrue(variable.destructorTarget().isPresent(), variable.name());
            }
        var operators = nodes(project.files().getFirst(), Expression.class).stream()
                .filter(e -> e instanceof ShiftLeftExpression || e instanceof ShiftRightExpression).toList();
        assertFalse(operators.isEmpty());
        for (var operator : operators) assertTrue(operator.operatorTarget().isPresent(), () -> operator.range().toString());
        for (var call : nodes(project.files().getFirst(), CallExpression.class))
            if (call.target().map(c -> Set.of("freeze", "str").contains(c.name())).orElse(false))
                assertTrue(call.target().orElseThrow().body().isEmpty());
    }
    @Test void runtimeSurvivesGreenFoldedCliJsonAndNativeGeneration() throws Exception {
        // These template-heavy CLI children measure about 30 seconds in isolation;
        // allow whole-suite runner variation without changing any correctness checks.
        var cli = new CliSandbox(temp, Duration.ofSeconds(90)).source("main.cpp", Files.readString(Path.of("src/test/resources/library-catalogue/strstream-runtime.cpp")));
        nativePrograms(cli.input);
        for (var stage : List.of(Cpg.Stage.GREEN, Cpg.Stage.FOLDED)) {
            var project = Cpg.read(cli.input, new Cpg.Options(Language.CPP, stage, "c++03"));
            verifyRuntimeGraph(project);
            var run = cli.run("cpp", "--stage", stage.name().toLowerCase(Locale.ROOT), "--cpp-standard", "c++03").expectStatus(0);
            SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
            assertEquals(Cpg.sources(project, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
            nativePrograms(run.work().resolve("generated"));
        }
    }
    @Test void invalidCallRetainsDiagnosticsAndBlocksGeneration() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", "#include <strstream>\nvoid f(){std::ostrstream stream;stream.freeze(1,2);}\n");
        var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(1);
        assertTrue(Files.isRegularFile(run.work().resolve("snapshot.json")));
        Json.parse(run.work().resolve("snapshot.json").toFile());
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("no-viable-overload"));
        assertFalse(Files.exists(run.work().resolve("generated")));
        assertFalse(run.stderr().isBlank());
    }
    @Test void returnedBufferMembersAndTemplateCallsSurviveCliJsonAndNativeGeneration() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", """
                #include <strstream>
                template<class T> void put(T& stream) { stream.rdbuf()->sputc('x'); }
                int main() {
                    char storage[8] = {0};
                    std::ostrstream output(storage, 8);
                    put(output);
                    output.rdbuf()->sputc('y');
                    if (output.pcount() != 2 || storage[0] != 'x' || storage[1] != 'y') return 1;
                    std::istrstream input(storage, 2);
                    if (input.rdbuf()->sgetc() != 'x') return 2;
                    return 0;
                }
                """);
        var project = Cpg.read(cli.input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        assertFalse(project.hasErrors(), () -> project.semanticDiagnostics().toString());
        declarationOnly(project);
        var base = project.classTemplateInstances().stream().filter(i -> i.origin().name().equals("basic_streambuf")).findFirst().orElseThrow();
        var methods = new HashSet<Node>(base.declaration().members());
        var calls = new ArrayList<>(nodes(project.files().getFirst(), CallExpression.class));
        project.templateInstances().forEach(i -> calls.addAll(nodes(i.declaration(), CallExpression.class)));
        var bufferCalls = calls.stream().filter(call -> call.callee() instanceof MemberExpression member
                && Set.of("sputc", "sgetc").contains(member.member())).filter(call -> !call.dependent()).toList();
        assertEquals(3, bufferCalls.size());
        for (var call : bufferCalls) assertTrue(methods.contains(call.target().orElseThrow()));
        var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertEquals(Cpg.sources(project, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
        nativePrograms(cli.input);
        nativePrograms(run.work().resolve("generated"));
    }
    private void nativePrograms(Path source) throws Exception {
        for (String compiler : List.of("g++", "clang++")) {
            Path work = Files.createTempDirectory(temp, "native-");
            Path binary = work.resolve("strstream");
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
}
