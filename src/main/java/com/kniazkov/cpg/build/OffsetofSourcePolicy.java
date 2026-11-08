/* Source-level restrictions on the selected offsetof layout contract. MIT License. */
package com.kniazkov.cpg.build;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.CppLexer;
import com.kniazkov.cpg.parser.PreprocessingToken;
import java.util.List;
import java.util.Optional;

/** Pragmas are retained by generic preprocessing, not interpreted as layout policy.
 * The offsetof subset therefore rejects any active TU pragma except exactly once,
 * which controls header inclusion rather than object representation. Push/pop and
 * reset pragmas do not certify earlier definitions. Inactive directives are harmless. */
public final class OffsetofSourcePolicy {
    private OffsetofSourcePolicy() {}
    public static Optional<String> violation(Preprocessing unit) {
        return unsupportedPragma(unit).map(d -> "Active " + d.rawSource().strip() + " at "
                + d.range().file() + ":" + d.range().start().line()).or(() -> unit.tokens().stream()
                .filter(token -> pragmaOperator(token.spelling())).findFirst()
                .map(token -> "Active pragma operator " + token.spelling() + " at "
                        + token.expansionRange().file() + ":" + token.expansionRange().start().line()));
    }
    public static Optional<String> violation(SourceFile source) {
        return source.provenance().map(TokenProvenance::unit).flatMap(OffsetofSourcePolicy::violation)
                .or(() -> syntaxViolation(source));
    }
    /** Newly inserted call/name syntax is not present in the original preprocessing tokens. */
    public static Optional<String> syntaxViolation(Node root) {
        String[] found = {null};
        root.walk(new NodeVisitor() {
            private void check(String spelling) {
                if (found[0] == null && java.util.Arrays.stream(spelling.split("::")).anyMatch(OffsetofSourcePolicy::pragmaOperator))
                    found[0] = "Pragma operator syntax " + spelling;
            }
            @Override public void visit(NameExpression name) { check(name.name()); }
            @Override public void visit(MemberExpression member) { check(member.member()); }
        });
        return Optional.ofNullable(found[0]);
    }
    private static boolean pragmaOperator(String spelling) { return spelling.equals("_Pragma") || spelling.equals("__pragma"); }
    public static Optional<Preprocessing.Directive> unsupportedPragma(Preprocessing unit) {
        return unit.directives().stream().filter(d -> d.active() && d.kind().equals("pragma") && !once(d)).findFirst();
    }
    private static boolean once(Preprocessing.Directive directive) {
        try {
            return CppLexer.scan(directive.range().file(), directive.rawSource()).tokens().stream()
                    .filter(t -> t.kind() != PreprocessingToken.Kind.WHITESPACE
                            && t.kind() != PreprocessingToken.Kind.NEWLINE && t.kind() != PreprocessingToken.Kind.EOF)
                    .map(PreprocessingToken::canonicalSpelling).toList().equals(List.of("#", "pragma", "once"));
        } catch (RuntimeException error) { return false; }
    }
}
