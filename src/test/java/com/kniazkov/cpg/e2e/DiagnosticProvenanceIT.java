/* Code Property Graph: diagnostic origins and stable cross-TU grouping. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.Language;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class DiagnosticProvenanceIT {
    @TempDir Path temp;
    @Test void twoIndependentFailuresInsideOneMacroStaySeparate() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", "#define BAD int x = ; int y = ;\nint main() { BAD }\n");
        var project = Cpg.readRecovering(cli.input, new Cpg.Options(Language.CPP, Cpg.Stage.PARSED));
        assertEquals(2, project.parseProblems().size());
        assertEquals(2, project.diagnosticGroups().size(), "Distinct definition origins are independent causes");
        var run = cli.run("cpp", "--stage", "parsed").expectStatus(1);
        run.diagnosticSnapshot();
        assertEquals(2, run.stderr().lines().filter(l -> l.contains(": error [")).count());
    }
    @Test void directiveFailuresKeepExpandedOperandsAsDistinctCauses() throws Exception {
        DiagnosticProvenanceAcceptance.directiveCauses(temp);
    }
    @Test void nestedAssignmentCannotClaimRecoveryAtAnUnsafeBoundary() throws Exception {
        for (String fragment : List.of("int x = (y = ;", "if (x = ;", "f(x = ;")) {
            Path directory = Files.createTempDirectory(temp, "unsafe-");
            var cli = new CliSandbox(directory).source("main.cpp", "int main() { " + fragment + " int z = ; }\n");
            var project = Cpg.readRecovering(cli.input,
                    new Cpg.Options(Language.CPP, Cpg.Stage.PARSED));
            assertEquals(1, project.parseProblems().size());
            assertFalse(project.parseProblems().getFirst().recovered());
            assertTrue(project.files().getFirst().stoppedAt().isPresent());
            var run = cli.run("cpp", "--stage", "parsed").expectStatus(1);
            run.diagnosticSnapshot();
            assertEquals(1, run.stderr().lines().filter(l -> l.contains(": error [")).count());
        }
    }
    @Test void provenanceAndStableGroupingReachApiJsonAndCli() throws Exception {
        DiagnosticProvenanceAcceptance.verify(Path.of("src/test/resources/cpp03/diagnostic-provenance"), temp);
    }
}
