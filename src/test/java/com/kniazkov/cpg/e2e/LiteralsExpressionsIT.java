/* Code Property Graph: C++03 expression integration. MIT License. */
package com.kniazkov.cpg.e2e;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class LiteralsExpressionsIT {
    @TempDir Path temp;
    @Test void valuesTypesConversionsAndPrecedenceReachApiAndCli() throws Exception {
        LiteralsExpressionsAcceptance.verify(Path.of("src/test/resources/cpp03/literals-expressions"), temp);
    }
}
