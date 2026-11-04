/* Code Property Graph: alternative compilation configurations through the packaged CLI. MIT License. */
package com.kniazkov.cpg.e2e;

import java.nio.file.Path;
import java.nio.file.Files;
import java.util.List;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BuildVariantsIT {
    @TempDir Path temp;
    @Test void variantsResolveWithinSelectedCompositionsAndRemainIsolated() throws Exception {
        BuildVariantsAcceptance.verify(Path.of("src/test/resources/cpp03/build-variants"), temp);
    }
    @Test void knownDuplicateDefinitionsDifferFromUnknownBuildAmbiguity() throws Exception {
        Files.writeString(temp.resolve("main.cpp"), "int run(); int main() { return run(); }\n");
        Files.writeString(temp.resolve("a.cpp"), "int run() { return 1; }\n");
        Files.writeString(temp.resolve("b.cpp"), "int run() { return 2; }\n");
        Files.writeString(temp.resolve("compile_commands.json"), """
            [
              {"directory":".","file":"main.cpp","arguments":["g++","-c","main.cpp"]},
              {"directory":".","file":"a.cpp","arguments":["g++","-c","a.cpp"]},
              {"directory":".","file":"b.cpp","arguments":["g++","-c","b.cpp"]}
            ]
            """);
        var unknown = Cpg.readCompilationDatabase(temp.resolve("compile_commands.json"), BuildVariantsAcceptance.OPTIONS);
        var call = BuildVariantsAcceptance.calls(unknown).getFirst();
        assertEquals("AMBIGUOUS", call.resolution().name());
        assertEquals(2, call.candidates().size());
        var known = unknown.selectBuild(new Build.Composition("both-definitions",
                unknown.files().stream().map(f -> f.variantId().orElseThrow()).toList()));
        var conflict = BuildVariantsAcceptance.calls(known).getFirst();
        assertEquals("ODR_CONFLICT", conflict.resolution().name());
        assertTrue(conflict.target().isEmpty());
        assertEquals(2, conflict.candidates().size());
        assertTrue(conflict.diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR));
        assertEquals("AMBIGUOUS", call.resolution().name(), "Selecting a build must not mutate the original");
    }
}

