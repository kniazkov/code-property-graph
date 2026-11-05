/* New C++ types must not escape the bounded Java translation contract. MIT License. */
package com.kniazkov.cpg.e2e;

import java.nio.file.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class DeclaratorTranslationIT {
    @TempDir Path temp;
    @Test void incompatibleTypesFailBeforeAnyExportsAreWritten() throws Exception {
        int index = 0;
        for (String statement : List.of("int *p = 0;", "int a[3];", "unsigned int x = 0;", "int x = 1U;", "int x = sizeof(int);")) {
            var directory = Files.createDirectory(temp.resolve("case" + index++));
            var cli = new CliSandbox(directory).source("A.cpp", "class A { public: static void main(){ " + statement + " } }; int main(){ A::main(); return 0; }");
            var run = cli.run("cpp", "--stage", "parsed", "--to", "java").expectStatus(1);
            assertTrue(run.stderr().contains("Cannot generate"), run.stderr());
            assertFalse(Files.exists(run.work().resolve("generated")));
            assertFalse(Files.exists(run.work().resolve("snapshot.json")));
        }
    }
}
