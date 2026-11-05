/* Code Property Graph: platform and declarator integration. MIT License. */
package com.kniazkov.cpg.e2e;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlatformDeclaratorsIT {
    @TempDir Path temp;
    @Test void profilesAndRecursiveDeclaratorsReachApiAndPackagedCli() throws Exception {
        PlatformDeclaratorsAcceptance.verify(Path.of("src/test/resources/cpp03/platform-declarators"), temp);
    }
}
