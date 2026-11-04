/* Code Property Graph: physical-coordinate acceptance for C++03 lexical phases. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.Language;
import com.kniazkov.cpg.parser.CppLexer;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

/** Inspects the same physical source through lexical API, graph API and packaged CLI. */
class LexicalPhasesIT {
    @TempDir Path temp;

    @Test void physicalMappingSurvivesTrigraphsSplicesAndAlternativeTokens() throws Exception {
        var cli = new CliSandbox(temp).directory(Path.of("src/test/resources/cpp03/lexical-phases/sources"));
        Path source = cli.input.resolve("main.cpp");
        var lexical = CppLexer.scan(source.toString(), Files.readString(source));
        var main = lexical.tokens().stream().filter(t -> t.spelling().equals("main")).findFirst().orElseThrow();
        assertEquals(1, main.range().start().line());
        assertEquals(8, main.range().start().column());
        assertEquals(2, main.range().end().line());
        assertEquals(3, main.range().end().column());
        assertEquals(1, lexical.characterRanges().get(main.logicalStart()).start().line());
        assertEquals(8, lexical.characterRanges().get(main.logicalStart()).start().column());
        assertEquals(2, lexical.characterRanges().get(main.logicalStart() + 2).start().line());
        assertEquals(1, lexical.characterRanges().get(main.logicalStart() + 2).start().column());
        assertFalse(lexical.text().contains("@"), "Continued comment must hide the next physical line");
        var alternative = lexical.tokens().stream().filter(t -> t.spelling().equals("not_eq")).findFirst().orElseThrow();
        assertEquals("!=", alternative.canonicalSpelling());
        assertEquals(7, alternative.range().start().line());
        assertEquals(15, alternative.range().start().column());
        var closing = lexical.tokens().stream().filter(t -> t.spelling().equals("}") && t.range().start().line() == 10)
                .findFirst().orElseThrow();
        assertEquals(1, closing.range().start().column());
        assertEquals(4, closing.range().end().column());
        assertEquals(11, lexical.eof().line());
        var run = cli.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(0);
        assertEquals(ProjectJson.encode(Cpg.read(cli.input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED))),
                Json.parse(run.work().resolve("snapshot.json").toFile()));
        var graph = run.snapshot();
        graph.containsNode("{\"kind\":\"NotEqualExpression\"}");
        graph.containsNode("{\"kind\":\"NameExpression\",\"name\":\"value\",\"range\":{\"start\":{\"line\":8,\"column\":12},\"end\":{\"line\":9,\"column\":3}}}");
    }
}
