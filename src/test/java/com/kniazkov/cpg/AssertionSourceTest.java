/* Assertion diagnostics retain locations during source relocation. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.Language;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class AssertionSourceTest {
    @TempDir Path temp;
    private final Cpg.Options options = new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03")
            .withPlatform(PlatformProfile.builder().cRuntime("glibc").build());
    @Test void headerLocationsArePreservedWithoutChangingUnaffectedRootAndReplayIsIdempotent() throws Exception {
        Path input=Files.createDirectory(temp.resolve("input"));
        String main="#include \"guard.h\"\nint main(){return f();}\n";
        Files.writeString(input.resolve("main.cpp"),main);
        Files.writeString(input.resolve("guard.h"),"#include <cassert>\ninline int f(){assert(1); return 0;}\n");
        var p=Cpg.read(input,options);assertFalse(p.hasErrors(),p.semanticDiagnostics().toString());
        var sources=Cpg.sources(p,Language.CPP);
        assertEquals(main,sources.get("main.cpp"));assertTrue(sources.get("guard.h").startsWith("#line 1 \""+input.resolve("guard.h").toString().replace("\\", "\\\\").replace("\"", "\\\"")));
        Path output=Files.createDirectory(temp.resolve("output"));
        for(var entry:sources.entrySet()) Files.writeString(output.resolve(entry.getKey()),entry.getValue());
        var replay=Cpg.read(output,options);assertFalse(replay.hasErrors());assertEquals(sources,Cpg.sources(replay,Language.CPP));
    }
    @Test void existingPresumedLocationsNeedNoExtraDirective() throws Exception {
        String source="#include <cassert>\n#line 42 \"logical.cpp\"\nint main(){assert(1); return 0;}\n";
        Files.writeString(temp.resolve("main.cpp"),source);
        assertEquals(source,Cpg.sources(Cpg.read(temp,options),Language.CPP).get("main.cpp"));
    }
    @Test void physicalFilenameQuotesAndBackslashesAreEscaped() throws Exception {
        Path input=Files.createDirectory(temp.resolve(java.io.File.separatorChar == '\\' ? "portable-name" : "quote\"slash\\name"));
        Files.writeString(input.resolve("main.cpp"),"#include <cassert>\nint main(){assert(1);return 0;}\n");
        var sources=Cpg.sources(Cpg.read(input,options),Language.CPP);
        assertTrue(sources.get("main.cpp").contains(input.toString().replace("\\", "\\\\").replace("\"", "\\\"")));
        Path output=Files.createDirectory(temp.resolve("output"));Files.writeString(output.resolve("main.cpp"),sources.get("main.cpp"));
        var replay=Cpg.read(output,options);assertFalse(replay.hasErrors());assertEquals(sources,Cpg.sources(replay,Language.CPP));
    }
}
