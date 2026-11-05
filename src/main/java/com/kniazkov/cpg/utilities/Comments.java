/* Code Property Graph: explicit, non-mutating comment removal. MIT License. */
package com.kniazkov.cpg.utilities;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.Language;
import com.kniazkov.cpg.model.Project;
import com.kniazkov.cpg.parser.CppLexer;
import com.kniazkov.cpg.parser.Parser;
import java.util.LinkedHashMap;
import java.util.Objects;

/** Optional library operation; never run by the normal parsing or generation pipeline. */
public final class Comments {
    private Comments() {}

    /**
     * Returns a new, independently owned C++03 project with all captured source comments removed,
     * including headers, macro definitions and inactive conditional arms. The input is untouched.
     * Physical generation first validates and incorporates current syntax edits; edits that cannot
     * be represented safely are rejected rather than silently discarded. Original build settings,
     * composition and translation time are retained. Provenance, symbol references and flow facts
     * are rebuilt against the cleaned sources, not copied with stale ranges or raw comment text.
     * This operation requires complete C++ snapshots with replayable captured physical sources.
     * It performs no filesystem reads or writes and adds no CLI command.
     *
     * @throws IllegalArgumentException for unsupported snapshots or unrepresentable syntax edits
     */
    public static Project remove(Project project) {
        Objects.requireNonNull(project, "project");
        if (project.hasErrors()) throw new IllegalArgumentException("Comment removal requires a complete C++ snapshot");
        for (var file : project.files())
            if (file.language() != Language.CPP || file.provenance().isEmpty()
                    || file.provenance().orElseThrow().unit().configuration() == null)
                throw new IllegalArgumentException("Comment removal requires captured C++ source provenance");
        var sources = new LinkedHashMap<String, String>();
        if (!project.files().isEmpty())
            Source.physicalSources(project).forEach((file, text) -> sources.put(file, remove(file, text)));
        var result = Project.create();
        project.build().ifPresent(result::setBuild);
        for (var file : project.files()) {
            var unit = file.provenance().orElseThrow().unit();
            result.addFile(Parser.parse(unit.configuration(), sources.get(unit.translationUnit()),
                    sources, unit.translationTime()));
        }
        return UnreadWrites.analyze(NameResolver.resolve(result.build()));
    }

    /**
     * Removes actual phase-3 comments from one physical C++03 source. Quoted literals and header
     * names are left intact. Comment whitespace prevents token merging. Every physical newline
     * inside a comment is retained as a line splice, preserving __LINE__ without accidentally
     * terminating a directive. Newlines outside comment ranges (including // endings) are intact.
     */
    public static String remove(String file, String source) {
        Objects.requireNonNull(file, "file");
        Objects.requireNonNull(source, "source");
        var comments = CppLexer.scan(file, source).comments();
        var result = new StringBuilder(source);
        for (int i = comments.size() - 1; i >= 0; i--) {
            var range = comments.get(i).range();
            var replacement = new StringBuilder();
            for (int j = range.start().offset(); j < range.end().offset(); j++) {
                char ch = source.charAt(j);
                if (ch == '\r' || ch == '\n') {
                    replacement.append('\\').append(ch);
                    if (ch == '\r' && j + 1 < range.end().offset() && source.charAt(j + 1) == '\n') {
                        replacement.append('\n'); j++;
                    }
                } else replacement.append(' ');
            }
            result.replace(range.start().offset(), range.end().offset(), replacement.toString());
        }
        return result.toString();
    }
}
