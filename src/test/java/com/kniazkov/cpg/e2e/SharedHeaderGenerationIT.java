/* Code Property Graph: all-variant physical header generation acceptance. MIT License. */
package com.kniazkov.cpg.e2e;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SharedHeaderGenerationIT {
    @TempDir Path temp;
    private static final Path FIXTURE = Path.of("src/test/resources/cpp03/shared-headers");

    @Test void serverOnlyEditOfCommonLiteralConflictsBeforeAnyExport() throws Exception {
        SharedHeaderAcceptance.conflict(FIXTURE.resolve("common"), temp);
    }

    @Test void identicalEditsProduceOnePhysicalHeaderForBothVariants() throws Exception {
        SharedHeaderAcceptance.identical(FIXTURE.resolve("common"), temp);
    }

    @Test void serverBranchEditPreservesInactiveBytesAndConditionalDirectives() throws Exception {
        SharedHeaderAcceptance.conditional(FIXTURE.resolve("conditional"), temp);
    }
}
