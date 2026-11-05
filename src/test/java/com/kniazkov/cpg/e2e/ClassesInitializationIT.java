/* C++03 class initialization integration. MIT License. */
package com.kniazkov.cpg.e2e;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
class ClassesInitializationIT {
    @TempDir Path temp;
    @Test void classOwnershipConstructionAndLifetimesSurviveRoundTrip() throws Exception {
        ClassesInitializationAcceptance.verify(Path.of("src/test/resources/cpp03/classes-initialization"), temp);
    }
}
