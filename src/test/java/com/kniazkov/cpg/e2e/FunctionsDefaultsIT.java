/* Code Property Graph: function/default-argument integration. MIT License. */
package com.kniazkov.cpg.e2e;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class FunctionsDefaultsIT {
    @TempDir Path temp;
    @Test void omittedArgumentsReferenceOneVisibleDefaultWithoutDuplicatingSyntax() throws Exception {
        FunctionsDefaultsAcceptance.verify(Path.of("src/test/resources/cpp03/functions-defaults"), temp);
    }
}
