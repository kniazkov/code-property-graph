/* CLI/API/JSON and strict native switch regressions for issue #88. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
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

class SwitchCaseConversionsIT {
    @TempDir Path temp;
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED);
    private List<Map<?, ?>> caseDiagnostics(Object value) {
        var result = new ArrayList<Map<?, ?>>();
        if (value instanceof Map<?, ?> map) {
            if ("CaseStatement".equals(map.get("kind")) && map.get("diagnostics") instanceof List<?> diagnostics)
                for (Object diagnostic : diagnostics) if (diagnostic instanceof Map<?, ?> item && "duplicate-case".equals(item.get("code"))) result.add(item);
            for (Object child : map.values()) result.addAll(caseDiagnostics(child));
        } else if (value instanceof List<?> list) for (Object child : list) result.addAll(caseDiagnostics(child));
        return result;
    }
    @Test void convertedCollisionsReachCliJsonAtTheConflictingLabel() throws Exception {
        List<String> declarations = List.of("", "struct C{operator unsigned int();};", "struct S{unsigned int bits:32;};", "enum E{top=4294967295U};");
        List<String> types = List.of("unsigned int", "C", "S", "E"), conditions = List.of("x", "x", "x.bits", "x");
        for (int i = 0; i < declarations.size(); i++) {
            Path work = Files.createDirectory(temp.resolve("invalid-" + i));
            String source = declarations.get(i) + "\nvoid f(" + types.get(i) + " x){switch(" + conditions.get(i) + "){\ncase -1:break;\ncase 4294967295U:break;}}\n";
            var cli = new CliSandbox(work).source("main.cpp", source);
            var project = Cpg.readRecovering(cli.input, OPTIONS);
            assertEquals(1, project.semanticDiagnostics().stream().filter(d -> d.code().equals("duplicate-case")).count());
            var run = cli.run("cpp", "--stage", "resolved").expectStatus(1); run.diagnosticSnapshot();
            var json = Json.parse(run.work().resolve("snapshot.json").toFile());
            assertEquals(caseDiagnostics(ProjectJson.encode(project).toJavaObject()), caseDiagnostics(json.toJavaObject()));
            var diagnostics = caseDiagnostics(json.toJavaObject()); assertEquals(1, diagnostics.size());
            var range = (Map<?, ?>) diagnostics.getFirst().get("primaryRange");
            var start = (Map<?, ?>) range.get("start");
            assertEquals(4, ((Number) start.get("line")).intValue());
            assertEquals(1, ((Number) start.get("column")).intValue());
            assertEquals(source.indexOf("case 4294967295U"), ((Number) start.get("offset")).intValue());
            assertThrows(IllegalArgumentException.class, () -> Cpg.sources(project, Language.CPP));
            // Invalid examples are compiled only, never executed. This establishes
            // compiler agreement for these ordinary 32-bit-unsigned cases, not arbitrary profiles.
            for (String compiler : List.of("g++", "clang++"))
                assertNotEquals(0, command(List.of(compiler, "-std=c++03", "-pedantic-errors", "-fsyntax-only", cli.input.resolve("main.cpp").toString()), work).status());
        }
    }
    @Test void explicitProfilesControlCliConversionsIndependentlyOfHost() throws Exception {
        for (String kind : List.of("signed", "enum", "bitfield")) {
            Path work = Files.createDirectory(temp.resolve(kind));
            String prefix = kind.equals("enum") ? "enum E{zero};" : kind.equals("bitfield") ? "struct S{int bits:32;};" : "";
            String type = kind.equals("enum") ? "E" : kind.equals("bitfield") ? "S" : "int";
            String condition = kind.equals("bitfield") ? "x.bits" : "x";
            var cli = new CliSandbox(work).source("main.cpp", prefix + "void f(" + type + " x){switch(" + condition + "){case -1:break;case 4294967295U:break;}}");
            var builder = PlatformProfile.defaults().toBuilder();
            PlatformProfile profile = kind.equals("enum") ? builder.enumUnderlyingType("unsigned-first").build()
                    : kind.equals("bitfield") ? builder.bitFieldSignedness("unsigned").build() : builder.signedIntegerConversion("twos-complement").build();
            Path settings = work.resolve("profile.json"); Files.writeString(settings, Json.serialize(profile.toSettings()));
            var baseline = cli.run("cpp", "--stage", "resolved").expectStatus(0);
            assertTrue(caseDiagnostics(Json.parse(baseline.work().resolve("snapshot.json").toFile()).toJavaObject()).isEmpty());
            var run = cli.run("cpp", "--stage", "resolved", "--platform-profile", settings.toString()).expectStatus(1); run.diagnosticSnapshot();
            var project = Cpg.readRecovering(cli.input, OPTIONS.withPlatform(profile));
            var json = Json.parse(run.work().resolve("snapshot.json").toFile());
            assertEquals(caseDiagnostics(ProjectJson.encode(project).toJavaObject()), caseDiagnostics(json.toJavaObject()));
            assertEquals(1, caseDiagnostics(json.toJavaObject()).size());
        }
    }
    @Test void validOriginalAndGeneratedSourcesRunOnBothStrictCompilers() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", """
            struct C { operator unsigned int() const { return 7U; } };
            enum E { zero, one };
            struct S { unsigned int bits:3; };
            int main() {
                int result=0; C c; S s; s.bits=2;
                switch(c) { case 7:result+=1;break; case 8:result+=100;break; }
                E e=one;
                switch(e) { case zero:result+=100;break; case one:result+=2;break; }
                switch(s.bits) { case 2:result+=4;break; case 3:result+=100;break; }
                switch(s.bits+0U) { case 2U:result+=8;break; case 3U:result+=100;break; }
                switch(unsigned int n=5) { case 5:result+=16;break; default:result+=100; }
                return result==31 ? 0 : 1;
            }
            """);
        var project = Cpg.read(cli.input, OPTIONS);
        assertFalse(project.hasErrors(), project.semanticDiagnostics().toString());
        var run = cli.run("cpp", "--stage", "resolved").expectStatus(0);
        assertEquals(caseDiagnostics(ProjectJson.encode(project).toJavaObject()),
                caseDiagnostics(Json.parse(run.work().resolve("snapshot.json").toFile()).toJavaObject()));
        assertEquals(Cpg.sources(project, Language.CPP).get("main.cpp"), Files.readString(run.work().resolve("generated/main.cpp")));
        for (String compiler : List.of("g++", "clang++")) for (Path input : List.of(cli.input, run.work().resolve("generated"))) {
            Path binary = Files.createTempFile(temp, "switch-native-", ".out");
            var compile = command(List.of(compiler, "-std=c++03", "-pedantic-errors", input.resolve("main.cpp").toString(), "-o", binary.toString()), temp);
            assertEquals(0, compile.status(), compile.output());
            var execution = command(List.of(binary.toString()), temp);
            assertEquals(0, execution.status(), execution.output()); assertEquals("", execution.output());
        }
    }
    private record Result(int status, String output) {}
    private Result command(List<String> command, Path work) throws Exception {
        Path log = Files.createTempFile(work, "native-", ".log");
        Process process = new ProcessBuilder(command).directory(work.toFile()).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            assertTrue(process.waitFor(45, TimeUnit.SECONDS), "Timed out: " + command);
            return new Result(process.exitValue(), Files.readString(log));
        } finally { if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); } }
    }
}
