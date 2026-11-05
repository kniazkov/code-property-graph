/* Code Property Graph: source-point lookup and TU isolation acceptance. MIT License. */
package com.kniazkov.cpg.e2e;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScopesLinkageIT {
    @TempDir Path temp;
    @Test void declarationChainsAndInternalLinkageSurviveApiCliAndGeneration() throws Exception {
        ScopesLinkageAcceptance.verify(Path.of("src/test/resources/cpp03/scopes-linkage"), temp);
    }
}
