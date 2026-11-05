/* Code Property Graph: source-preserving documentation-aware rename. MIT License. */
package com.kniazkov.cpg.utilities;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;

/** Explicit physical edits; generic comment-catalogue mutation remains unsupported. */
public final class Documentation {
    private Documentation() {}
    private record Patch(SourceRange range, String before) {}

    /**
     * Renames one exact parameter occurrence, its resolved uses, and only documentation names
     * bound to that occurrence. Descriptions, unknown commands, whitespace and other source bytes
     * are untouched. A declaration's parameter and its definition's parameter are independent.
     * Returns a freshly parsed and resolved project with original configurations and translation
     * times; performs no I/O. Requires an unmodified, complete captured C++ snapshot. Macro names,
     * conflicting physical occurrences and potentially capturing names are rejected conservatively.
     */
    public static Project renameParameter(Project project, Declaration parameter, String name) {
        Objects.requireNonNull(project); Objects.requireNonNull(parameter); Objects.requireNonNull(name);
        String old = parameter instanceof ParameterDeclaration p ? p.name()
                : parameter instanceof TemplateParameterDeclaration p ? p.name() : null;
        if (old == null || old.isEmpty()) throw rejected("expected a named parameter or template parameter");
        var identifier = com.kniazkov.cpg.parser.CppLexer.scan("rename", name).tokens().stream()
                .filter(t -> !t.spelling().isBlank()).toList();
        if (!name.matches("[A-Za-z_][A-Za-z_0-9]*") || identifier.size() != 1
                || !identifier.getFirst().kind().name().equals("IDENTIFIER")
                || Set.of(("asm auto bool break case catch char class const const_cast continue default delete do double dynamic_cast else enum explicit export extern false float for friend goto if inline int long mutable namespace new operator private protected public register reinterpret_cast return short signed sizeof static static_cast struct switch template this throw true try typedef typeid typename union unsigned using virtual void volatile wchar_t while").split(" ")).contains(name))
            throw rejected("new name is not a C++ identifier");
        var nodes = new ArrayList<Node>();
        project.walk(new NodeVisitor() { @Override public void visit(Node n) { nodes.add(n); } });
        if (nodes.stream().noneMatch(n -> n == parameter)) throw rejected("parameter is outside the project");
        if (project.hasErrors() || project.files().isEmpty()) throw rejected("requires a complete captured C++ snapshot");
        var originals = new LinkedHashMap<String, String>();
        for (var file : project.files()) {
            if (file.language() != Language.CPP || file.provenance().isEmpty()
                    || file.provenance().orElseThrow().unit().configuration() == null)
                throw rejected("requires captured C++ provenance");
            file.provenance().orElseThrow().unit().sources().forEach((path, text) -> {
                String previous = originals.putIfAbsent(path, text);
                if (previous != null && !previous.equals(text)) throw rejected("conflicting physical sources");
            });
        }
        if (!Source.physicalSources(project).equals(originals))
            throw rejected("emit and reparse existing syntax edits before renaming");
        if (old.equals(name)) return reparse(project, originals);
        // Reject possible capture, including currently unresolved uses. This deliberately favors
        // a safe refusal over a spelling-only transformation with changed name lookup.
        if (nodes.stream().anyMatch(n -> n != parameter && spelling(n).equals(name))
                || originals.entrySet().stream().anyMatch(file -> com.kniazkov.cpg.parser.CppLexer.scan(file.getKey(), file.getValue()).tokens().stream().anyMatch(t -> t.canonicalSpelling().equals(name)))
                || project.files().stream().anyMatch(file -> file.provenance().orElseThrow().unit().definitions().stream().anyMatch(d -> d.name().equals(name))))
            throw rejected("new name already occurs in the project and may change lookup");
        var affected = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        affected.add(parameter);
        var parameters = Collections.newSetFromMap(new IdentityHashMap<Declaration, Boolean>());
        parameters.add(parameter);
        for (Node n : nodes) if (n instanceof Declaration d && n != parameter
                && n.getClass() == parameter.getClass() && n.range().equals(parameter.range())) {
            if (!Source.render(n, Language.CPP).equals(Source.render(parameter, Language.CPP)))
                throw rejected("shared physical parameter has conflicting include contexts");
            parameters.add(d); affected.add(d);
        }
        for (Node n : nodes) {
            if (n instanceof NameExpression use && parameters.contains(use.target().orElse(null))) affected.add(n);
            if (n instanceof NamedType type && parameters.contains(type.declaration().orElse(null))) affected.add(n);
        }
        var patches = new LinkedHashMap<SourceRange, Patch>();
        for (Node node : affected) {
            var provenance = node.provenance().orElseThrow(() -> rejected("missing token provenance"));
            var matches = provenance.unit().tokens().subList(provenance.startToken(), provenance.endToken()).stream()
                    .filter(t -> t.spelling().equals(old)).toList();
            if (matches.size() != 1) throw rejected("identifier has ambiguous source spelling");
            var token = matches.getFirst();
            if (!token.expansionIds().isEmpty() || !token.spellingRange().equals(token.expansionRange()))
                throw rejected("identifier originates in a macro expansion");
            patches.put(token.spellingRange(), new Patch(token.spellingRange(), old));
        }
        // An unchanged occurrence in another include/configuration cannot silently be renamed.
        for (Node n : nodes) if (!affected.contains(n) && spelling(n).equals(old) && n.provenance().isPresent()) {
            var p = n.provenance().orElseThrow();
            if (p.unit().tokens().subList(p.startToken(), p.endToken()).stream()
                    .anyMatch(t -> patches.containsKey(t.spellingRange())))
                throw rejected("identifier is shared by another include or configuration occurrence");
        }
        for (Node n : nodes) if (n instanceof DocumentationEntry entry
                && entry.bindings().stream().anyMatch(b -> parameters.contains(b.target()))) {
            if (entry.bindings().stream().anyMatch(b -> !parameters.contains(b.target())))
                throw rejected("documentation name has conflicting include bindings");
            var range = entry.nameRange().orElseThrow(() -> rejected("documentation has no exact name range"));
            if (!entry.name().equals(old)) throw rejected("documentation binding and name disagree");
            patches.put(range, new Patch(range, old));
        }
        var sources = new LinkedHashMap<>(originals);
        for (var file : originals.entrySet()) {
            var edits = patches.values().stream().filter(p -> p.range().file().equals(file.getKey()))
                    .sorted(Comparator.comparingInt((Patch p) -> p.range().start().offset()).reversed()).toList();
            var text = new StringBuilder(file.getValue());
            int previous = text.length();
            for (var edit : edits) {
                int start = edit.range().start().offset(), end = edit.range().end().offset();
                if (start < 0 || end > previous || !text.substring(start, end).equals(edit.before()))
                    throw rejected("overlapping or stale physical identifier range");
                text.replace(start, end, name); previous = start;
            }
            sources.put(file.getKey(), text.toString());
        }
        Project result = reparse(project, sources);
        if (result.hasErrors()) throw rejected("renamed sources failed parsing or analysis");
        return result;
    }
    private static String spelling(Node n) {
        return switch (n) {
            case ParameterDeclaration p -> p.name();
            case TemplateParameterDeclaration p -> p.name();
            case VariableDeclaration v -> v.name();
            case CallableDeclaration c -> c.name();
            case ClassDeclaration c -> c.name();
            case TypedefDeclaration t -> t.name();
            case EnumeratorDeclaration e -> e.name();
            case NameExpression use -> use.name();
            case NamedType type -> type.name();
            default -> "";
        };
    }
    private static Project reparse(Project project, Map<String, String> sources) {
        var result = Project.create(); project.build().ifPresent(result::setBuild);
        for (var file : project.files()) {
            var unit = file.provenance().orElseThrow().unit();
            result.addFile(Parser.parse(unit.configuration(), sources.get(unit.translationUnit()), sources, unit.translationTime()));
        }
        return UnreadWrites.analyze(NameResolver.resolve(result.build()));
    }
    private static IllegalArgumentException rejected(String reason) {
        return new IllegalArgumentException("Cannot rename documented parameter safely: " + reason);
    }
}
