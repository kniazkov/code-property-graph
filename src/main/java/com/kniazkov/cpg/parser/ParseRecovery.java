/* Code Property Graph: bounded recovery state for one source file. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Only safe expression boundaries recover; all other failures stop this unit. */
final class ParseRecovery {
    private final Language language;
    private final Map<String, String> sources;
    private final List<ParseProblem> problems = new ArrayList<>();
    private SourceRange stoppedAt;
    private final DiagnosticContext context;

    ParseRecovery(Language language, Map<String, String> sources) {
        this(language, sources, new DiagnosticContext(sources.keySet().iterator().next(), null, List.of(), List.of()));
    }
    ParseRecovery(Language language, Map<String, String> sources, DiagnosticContext context) {
        this.context = context;
        this.language = language;
        this.sources = sources;
    }
    boolean stopped() { return stoppedAt != null; }
    ParseProblem recovered(ParseException error, SourceRange range) {
        var problem = problem(error, range, true);
        problems.add(problem);
        return problem;
    }
    void stop(ParseException error, SourceRange fallback) {
        if (stopped()) return;
        var range = error.range().orElse(fallback);
        stoppedAt = new SourceRange(range.file(), range.start(), range.start());
        String source = sources.get(range.file());
        if (source != null && range.start().offset() <= source.length())
            range = new SourceRange(range.file(), range.start(), position(source, source.length()));
        problems.add(problem(error, range, false));
    }
    private ParseProblem problem(ParseException error, SourceRange range, boolean recovered) {
        String source = sources.get(range.file());
        String skipped = source != null && range.end().offset() <= source.length()
                ? source.substring(range.start().offset(), range.end().offset()) : "";
        String message = error.reason() + (language == Language.CPP && !error.reason().contains("C++03") ? " (C++03)" : "");
        var physical = error.range().orElse(range);
        boolean sameFile = error.file().equals(physical.file())
                || error.file().equals(java.nio.file.Path.of(physical.file()).getFileName().toString());
        if (!sameFile || error.line() != physical.start().line() || error.column() != physical.start().column())
            message += " (logical location: " + error.file() + ":" + error.line() + ":" + error.column() + ")";
        return new ParseProblem(error.code(), message, range, skipped, recovered, physical,
                error.causeReason(), error.relatedRanges(), error.context() == null ? context : error.context());
    }
    void attach(SourceFile.Builder result) {
        result.setParseProblems(problems);
        if (stoppedAt != null) result.setStoppedAt(stoppedAt);
    }
    static SourcePosition position(String source, int offset) {
        int line = 1, column = 1;
        for (int i = 0; i < offset; i++) {
            char ch = source.charAt(i);
            if (ch == '\r') { if (i + 1 < offset && source.charAt(i + 1) == '\n') i++; line++; column = 1; }
            else if (ch == '\n') { line++; column = 1; }
            else column++;
        }
        return new SourcePosition(line, column, offset);
    }
}
