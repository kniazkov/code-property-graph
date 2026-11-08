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
            assertTrue(definition.range().file().contains("<cpg-library:"));
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
}
