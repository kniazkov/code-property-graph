/* Code Property Graph: target encoding at the packaged CLI boundary. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.Language;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class PlatformEncodingIT {
    @TempDir Path temp;
    @Test void preservesNonUtf8RootAndHeaderBytesAndReparsesWithTheSameProfile() throws Exception {
        var cli = new CliSandbox(temp);
        String root = "#include \"shared.hpp\"\n// café\nint main() { return answer; }\n";
        String header = "#if 'é' == 233\nint answer = 7;\n#endif\n";
        Files.writeString(cli.input.resolve("main.cpp"), root, StandardCharsets.ISO_8859_1);
        Files.writeString(cli.input.resolve("shared.hpp"), header, StandardCharsets.ISO_8859_1);
        Path settings = temp.resolve("latin.json");
        Files.writeString(settings, "{\"inherits\":\"default\",\"sourceEncoding\":\"ISO-8859-1\",\"executionEncoding\":\"ISO-8859-1\"}");
        var run = cli.run("cpp", "--stage", "parsed", "--platform-profile", settings.toString()).expectStatus(0);
        Path generated = run.work().resolve("generated");
        assertArrayEquals(root.getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(generated.resolve("main.cpp")));
        assertArrayEquals(header.getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(generated.resolve("shared.hpp")));
        var reparsed = Cpg.read(generated, new Cpg.Options(Language.CPP, Cpg.Stage.PARSED).withPlatform(PlatformProfile.read(settings)));
        assertFalse(reparsed.hasErrors());
        assertEquals(1, reparsed.files().size());
    }
    @Test void rejectsUnrepresentableGeneratedTextBeforeAnyExports() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", "int caf\\u00e9 = 1; int main() { return caf\\u00e9; }");
        Path settings = temp.resolve("ascii.json");
        Files.writeString(settings, "{\"inherits\":\"default\",\"sourceEncoding\":\"US-ASCII\"}");
        var run = cli.run("cpp", "--stage", "parsed", "--platform-profile", settings.toString()).expectStatus(1);
        assertTrue(run.stderr().contains("Generated source main.cpp is not representable in US-ASCII"), run.stderr());
        run.expectNoExports();
    }
    @Test void rejectsConflictingPhysicalFileEncodingsBeforeAnyExports() throws Exception {
        var cli = new CliSandbox(temp).source("main.cpp", "#define VALUE 7\nint main() { return VALUE; }\n");
        Path database = cli.input.resolve("compile_commands.json");
        Files.writeString(database, """
                [{"directory":".","file":"main.cpp","arguments":["c++","main.cpp"]},
                 {"directory":".","file":"main.cpp","arguments":["c++","main.cpp"],
                  "platformProfile":{"inherits":"default","sourceEncoding":"ISO-8859-1"}}]
                """);
        var run = cli.runDatabase(database, "--stage", "parsed").expectStatus(1);
        assertTrue(run.stderr().contains("Conflicting source encodings"), run.stderr());
        run.expectNoExports();
    }
}
