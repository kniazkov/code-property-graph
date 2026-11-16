/* CPG refusal policy on printed C++03 UB, not normative diagnostic oracles. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.Language;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class Cpp03UndefinedBehaviorPolicyIT {
    @TempDir Path temp;

    @Test void ubRefusalPolicySurvivesApiCliJsonCopyAndReanalysis() throws Exception {
        var messages=Map.of("empty-argument","Empty macro argument",
                "invalid-paste","Invalid token paste", "line-range","1..32767",
                "predefined-name","undefined behavior");
        var options=new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03");
        for (var entry:messages.entrySet()) {
            var cli=new CliSandbox(Files.createDirectory(temp.resolve(entry.getKey())))
                    .directory(Path.of("src/test/resources/cpp03-ub-policy",entry.getKey()));
            var project=Cpg.readRecovering(cli.input,options);
            for (var snapshot:java.util.List.of(project,project.edit().build(),
                    NameResolver.resolve(project.edit().build()))) {
                assertTrue(snapshot.hasErrors(),entry.getKey());
                assertTrue(ProjectJson.encode(snapshot).toText("  ").contains(entry.getValue()));
            }
            var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(1);
            assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains(entry.getValue()));
            assertFalse(Files.exists(run.work().resolve("generated")));
        }
        // Deliberately no native compiler invocation or runtime result assertion.
    }
}
