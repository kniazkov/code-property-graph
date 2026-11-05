/* Code Property Graph: identity-directed declaration rename. MIT License. */
package com.kniazkov.cpg.utilities;

import com.kniazkov.cpg.analysis.InheritanceSemantics;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.CppLexer;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;

/** Physical identifier edits selected by resolved declaration identities, never by global spelling.
 * Supports ordinary functions/methods, variables, typedefs, enums, enumerators, parameters and
 * template parameters, classes, namespaces and namespace aliases. Class renames include explicit
 * constructor/destructor declarations and definitions. Qualified tokens are identified using
 * lexical ownership and resolved declarations. Unresolvable qualifier contexts are refused.
 * Operator/conversion names cannot be selected for identifier renaming.
 */
public final class DeclarationRename {
    private DeclarationRename() {}

    /** Preview the complete declaration/redeclaration and virtual override family, without editing.
     * The returned immutable list contains nodes from the original snapshot, in traversal order.
     * Multiple inheritance can join otherwise separate base families through a common override.
     */
    public static List<Declaration> affectedDeclarations(Project project, Declaration selected) {
        Objects.requireNonNull(project); Objects.requireNonNull(selected);
        List<Node> nodes = nodes(project);
        if (nodes.stream().noneMatch(n -> n == selected)) throw rejected("declaration is outside the project");
        if (!(selected instanceof CallableDeclaration || selected instanceof VariableDeclaration
                || selected instanceof TypedefDeclaration || selected instanceof EnumDeclaration
                || selected instanceof EnumeratorDeclaration || selected instanceof ParameterDeclaration
                || selected instanceof TemplateParameterDeclaration || selected instanceof ClassDeclaration
                || selected instanceof NamespaceDeclaration || selected instanceof NamespaceAliasDeclaration))
            throw rejected("select the named declaration, not an import or directive");
        if (spelling(selected).isEmpty() || selected instanceof CallableDeclaration c && (c.kind() != CallableDeclaration.Kind.FUNCTION || !simple(c.name()).matches("[A-Za-z_][A-Za-z_0-9]*")))
            throw rejected("expected an ordinary named declaration");
        Set<Declaration> affected = identitySet(); affected.add(selected);
        boolean changed;
        do {
            int count = affected.size();
            for (Node node : nodes) if (node instanceof Declaration declaration) {
                if (affected.contains(declaration)) {
                    declaration.previousDeclaration().ifPresent(affected::add);
                    declaration.definition().ifPresent(affected::add);
                    if (!(selected instanceof ClassDeclaration) && declaration instanceof CallableDeclaration callable) {
                        affected.addAll(InheritanceSemantics.overrides(project, callable));
                        affected.addAll(InheritanceSemantics.overriddenBy(project, callable));
                    }
                }
                if (affected.contains(declaration.previousDeclaration().orElse(null))
                        || affected.contains(declaration.definition().orElse(null))) affected.add(declaration);
                // One written declaration can be parsed in several translation units.
                if (!affected.contains(declaration) && declaration.provenance().isPresent())
                    for (Declaration member : List.copyOf(affected))
                        if (member.getClass() == declaration.getClass() && member.range().equals(declaration.range())) {
                            if (!Source.render(member, Language.CPP).equals(Source.render(declaration, Language.CPP)))
                                throw rejected("conflicting include contexts for one physical declaration");
                            affected.add(declaration); break;
                        }
            }
            if (selected instanceof ClassDeclaration) {
                for (Node node : nodes) if (node instanceof CallableDeclaration callable
                        && (callable.kind() == CallableDeclaration.Kind.CONSTRUCTOR || callable.kind() == CallableDeclaration.Kind.DESTRUCTOR)
                        && InheritanceSemantics.owningClass(project, callable).filter(affected::contains).isPresent())
                    affected.add(callable);
            }
            changed = count != affected.size();
        } while (changed);
        if (affected.stream().anyMatch(d -> nodes.stream().noneMatch(n -> n == d)))
            throw rejected("family contains a declaration outside the captured project");
        return nodes.stream().filter(affected::contains).map(Declaration.class::cast).toList();
    }

    /** Rename the previewed family, its resolved uses and using declarations, preserving other bytes.
     * All occurrences must have unambiguous editable token provenance; otherwise no edit is applied.
     * Returns a fresh parsed and resolved snapshot. Conflicts are retained as diagnostics in that
     * result, so callers must inspect hasErrors() before emitting it. Accidental capture and
     * overload imports that would need splitting into separate using declarations likewise
     * produce an error graph instead of silently changing binding. No files are written.
     * Constructors and destructors are renamed through their owning class, not independently.
     */
    public static Project rename(Project project, Declaration selected, String name) {
        Objects.requireNonNull(name);
        if (!name.matches("[A-Za-z_][A-Za-z_0-9]*") || CppLexer.scan("rename", name).tokens().stream()
                .noneMatch(t -> t.kind().name().equals("IDENTIFIER")) || KEYWORDS.contains(name))
            throw rejected("new name is not a C++03 identifier");
                if (selected instanceof ParameterDeclaration || selected instanceof TemplateParameterDeclaration)
            return Documentation.renameParameter(project, selected, name);
        List<Declaration> family = affectedDeclarations(project, selected);
        Set<Declaration> declarations = identitySet(); declarations.addAll(family);
        String old = simple(spelling(selected));
        if (!old.matches("[A-Za-z_][A-Za-z_0-9]*")) throw rejected("unsupported declaration spelling");
        List<Node> nodes = nodes(project);
        if (project.hasErrors() || project.files().isEmpty()) throw rejected("requires a valid captured project");
        var originals = new LinkedHashMap<String, String>();
        for (SourceFile file : project.files()) {
            if (file.language() != Language.CPP || file.provenance().isEmpty()
                    || file.provenance().orElseThrow().unit().configuration() == null)
                throw rejected("requires captured C++ provenance");
            file.provenance().orElseThrow().unit().sources().forEach((path, text) -> {
                String prior = originals.putIfAbsent(path, text);
                if (prior != null && !prior.equals(text)) throw rejected("conflicting physical sources");
            });
        }
        if (!Source.physicalSources(project).equals(originals)) throw rejected("emit and reparse existing edits first");
        Set<Node> affected = identitySet(); affected.addAll(family);
        for (Node node : nodes) {
            Declaration target = target(node);
            if (target != null && declarations.contains(target)) {
                if (node instanceof CallExpression call) affected.add(call.callee());
                else affected.add(node);
            }
        }
        Set<SourceRange> patches = new LinkedHashSet<>();
        for (Node node : affected) {
            var token = identifier(node, old);
            if (!token.expansionIds().isEmpty() || !token.spellingRange().equals(token.expansionRange()))
                throw rejected("identifier originates in a macro expansion");
            if (!originals.containsKey(token.spellingRange().file())) throw rejected("identifier source is not captured");
            patches.add(token.spellingRange());
        }
        var qualifiers = QualifiedIdentifierIndex.index(project);
        qualifiers.targets().forEach((range, declaration) -> {
            if (declarations.contains(declaration)) {
                if (qualifiers.unsafe().contains(range)) throw rejected("qualifier originates in a macro expansion");
                patches.add(range);
            }
        });
        for (SourceRange range : qualifiers.unresolved()) {
            String text = originals.get(range.file());
            if (text != null && range.start().offset() >= 0 && range.end().offset() <= text.length()
                    && text.substring(range.start().offset(), range.end().offset()).equals(old))
                throw rejected("qualifier has no unambiguous declaration identity");
        }
        // A shared physical token must not stand for a different entity in another include context.
        for (Node node : nodes) if (!affected.contains(node) && simple(spelling(node)).equals(old)
                && node.provenance().isPresent()) {
            var token = identifier(node, old);
            if (patches.contains(token.spellingRange())) throw rejected("physical identifier has conflicting bindings");
        }
        var sources = new LinkedHashMap<>(originals);
        for (var file : originals.entrySet()) {
            var ranges = patches.stream().filter(r -> r.file().equals(file.getKey()))
                    .sorted(Comparator.comparingInt((SourceRange r) -> r.start().offset()).reversed()).toList();
            var text = new StringBuilder(file.getValue()); int previous = text.length();
            for (SourceRange range : ranges) {
                int start = range.start().offset(), end = range.end().offset();
                if (start < 0 || end > previous || !text.substring(start, end).equals(old)) throw rejected("stale or overlapping identifier range");
                text.replace(start, end, name); previous = start;
            }
            sources.put(file.getKey(), text.toString());
        }
        var result = Project.create(); project.build().ifPresent(result::setBuild);
        for (SourceFile file : project.files()) {
            var unit = file.provenance().orElseThrow().unit();
            result.addFile(Parser.parse(unit.configuration(), sources.get(unit.translationUnit()), sources, unit.translationTime()));
        }
        Project resolved = UnreadWrites.analyze(NameResolver.resolve(result.build()));
        Project verified = verifyBindings(project, resolved, patches, name.length() - old.length());
        if (!old.equals(name) && !OverloadedUsingConflict.find(project, family).isEmpty()) {
            var editor = new ProjectEditor(verified).retainDiagnostics();
            editor.diagnose(verified, new Diagnostic("rename-overload-import-conflict", Diagnostic.Severity.ERROR,
                    "Renaming only part of an imported overload set requires separate using declarations"));
            return editor.build();
        }
        return verified;
    }

    private record Location(Class<?> type, String file, int start, int end) {}
    private static Location location(Node node, Set<SourceRange> patches, int delta) {
        if (node.range().isEmpty()) return null;
        SourceRange range = node.range().orElseThrow();
        return new Location(node.getClass(), range.file(), shifted(range.file(), range.start().offset(), patches, delta),
                shifted(range.file(), range.end().offset(), patches, delta));
    }
    private static int shifted(String file, int offset, Set<SourceRange> patches, int delta) {
        return offset + delta * (int) patches.stream().filter(p -> p.file().equals(file) && p.start().offset() < offset).count();
    }
    /** Detect accidental capture of both edited and unchanged resolved uses after fresh lookup. */
    private static Project verifyBindings(Project before, Project after, Set<SourceRange> patches, int delta) {
        if (after.hasErrors()) return after;
        var available = new HashMap<Location, ArrayDeque<Node>>();
        for (Node node : nodes(after)) {
            Location key = location(node, Set.of(), 0);
            if (key != null) available.computeIfAbsent(key, ignored -> new ArrayDeque<>()).add(node);
        }
        var correspondence = new IdentityHashMap<Node, Node>();
        for (Node node : nodes(before)) {
            var matches = available.get(location(node, patches, delta));
            if (matches != null && !matches.isEmpty()) correspondence.put(node, matches.removeFirst());
        }
        var editor = new ProjectEditor(after).retainDiagnostics();
        boolean changed = false;
        // Fresh lookup can legally merge declarations (namespace reopenings, a class forward
        // declaration, or function prototypes) without producing a language diagnostic. A rename
        // must not silently merge two formerly independent entities, even when neither is used.
        var oldFamilies = declarationFamilies(before);
        var newFamilies = declarationFamilies(after);
        var originalFamilyByNew = new IdentityHashMap<Declaration, Declaration>();
        for (var entry : correspondence.entrySet()) {
            if (!(entry.getKey() instanceof Declaration oldDeclaration)
                    || !(entry.getValue() instanceof Declaration newDeclaration)) continue;
            Declaration oldFamily = representative(oldFamilies, oldDeclaration);
            Declaration newFamily = representative(newFamilies, newDeclaration);
            Declaration prior = originalFamilyByNew.putIfAbsent(newFamily, oldFamily);
            if (prior != null && prior != oldFamily) {
                editor.diagnose(newDeclaration, new Diagnostic("rename-identity-conflict", Diagnostic.Severity.ERROR,
                        "Renaming merges formerly independent declaration identities",
                        newDeclaration.range().orElse(null), List.of()));
                changed = true;
            }
        }
        for (Node node : nodes(before)) {
            Declaration oldTarget = target(node);
            if (oldTarget == null) continue;
            Node counterpart = correspondence.get(node);
            Node expected = correspondence.get(oldTarget);
            if (node.provenance().isEmpty() || oldTarget.provenance().isEmpty()) continue; // Non-written synthesized facts.
            Declaration actual = counterpart == null ? null : target(counterpart);
            if (counterpart == null || expected == null || actual != expected && !sameEntity(actual, expected)) {
                Node owner = counterpart == null ? after : counterpart;
                editor.diagnose(owner, new Diagnostic("rename-binding-conflict", Diagnostic.Severity.ERROR,
                        "Renaming changes the resolved declaration of this use", owner.range().orElse(null), List.of()));
                changed = true;
            }
        }
        var oldQualifiers = QualifiedIdentifierIndex.index(before);
        var newQualifiers = QualifiedIdentifierIndex.index(after);
        var qualifierTargets = new HashMap<Location, Declaration>();
        newQualifiers.targets().forEach((range, declaration) -> qualifierTargets.put(
                new Location(null, range.file(), range.start().offset(), range.end().offset()), declaration));
        for (var entry : oldQualifiers.targets().entrySet()) {
            SourceRange range = entry.getKey();
            Declaration actual = qualifierTargets.get(new Location(null, range.file(),
                    shifted(range.file(), range.start().offset(), patches, delta),
                    shifted(range.file(), range.end().offset(), patches, delta)));
            Node expected = correspondence.get(entry.getValue());
            if (expected != null && actual != expected && !sameEntity(actual, expected)) {
                editor.diagnose(after, new Diagnostic("rename-binding-conflict", Diagnostic.Severity.ERROR,
                        "Renaming changes a resolved qualifier declaration"));
                changed = true;
            }
        }
        return changed ? editor.build() : after;
    }
    private static Map<Declaration, Declaration> declarationFamilies(Project project) {
        var families = new IdentityHashMap<Declaration, Declaration>();
        for (Node node : nodes(project)) if (node instanceof Declaration declaration) {
            representative(families, declaration);
            for (Declaration linked : java.util.stream.Stream.concat(declaration.previousDeclaration().stream(),
                    declaration.definition().stream()).toList()) {
                Declaration left = representative(families, declaration), right = representative(families, linked);
                if (left != right) families.put(right, left);
            }
        }
        return families;
    }
    private static Declaration representative(Map<Declaration, Declaration> families, Declaration declaration) {
        Declaration parent = families.putIfAbsent(declaration, declaration);
        if (parent == null || parent == declaration) return declaration;
        Declaration root = representative(families, parent);
        families.put(declaration, root);
        return root;
    }
    private static boolean sameEntity(Declaration actual, Node expected) {
        if (actual == null || !(expected instanceof Declaration declaration)) return false;
        Set<Declaration> seen = identitySet();
        var pending = new ArrayDeque<Declaration>(); pending.add(actual);
        while (!pending.isEmpty()) {
            Declaration next = pending.removeFirst();
            if (next == declaration) return true;
            if (!seen.add(next)) continue;
            next.previousDeclaration().ifPresent(pending::add); next.definition().ifPresent(pending::add);
        }
        return false;
    }
    private static Preprocessing.Token identifier(Node node, String name) {
        var provenance = node.provenance().orElseThrow(() -> rejected("family or use has no editable provenance"));
        // Child ranges exclude return types, parameters, bodies, initializers and receiver expressions.
        var children = node.children().stream().flatMap(n -> n.provenance().stream())
                .filter(p -> p.unit() == provenance.unit()).toList();
        var matches = new ArrayList<Preprocessing.Token>();
        for (int i = provenance.startToken(); i < provenance.endToken(); i++) {
            int index = i;
            var token = provenance.unit().tokens().get(i);
            if (token.spelling().equals(name) && children.stream().noneMatch(p -> index >= p.startToken() && index < p.endToken())) matches.add(token);
        }
        if (matches.isEmpty()) throw rejected("identifier has no source spelling: " + spelling(node));
        if (node instanceof NamespaceAliasDeclaration) return matches.getFirst();
        if (matches.size() > 1 && !spelling(node).contains("::"))
            throw rejected("identifier has ambiguous source spelling: " + spelling(node));
        return matches.getLast();
    }
    private static Declaration target(Node node) {
        return switch (node) {
            case CallExpression n -> n.target().orElse(null);
            case NameExpression n -> n.target().orElse(null);
            case MemberExpression n -> n.target().orElse(null);
            case NamedType n -> n.alias().<Declaration>map(a -> a).orElse(n.declaration().orElse(null));
            case UsingDeclaration n -> n.target().orElse(null);
            case MemberInitializer n -> n.target().orElse(null);
            default -> null;
        };
    }
    private static String spelling(Node node) {
        return switch (node) {
            case CallableDeclaration n -> n.name(); case VariableDeclaration n -> n.name();
            case ParameterDeclaration n -> n.name(); case TemplateParameterDeclaration n -> n.name();
            case TypedefDeclaration n -> n.name(); case EnumeratorDeclaration n -> n.name();
            case EnumDeclaration n -> n.name(); case ClassDeclaration n -> n.name();
            case NamespaceDeclaration n -> n.name(); case NamespaceAliasDeclaration n -> n.name();
            case NameExpression n -> n.name();
            case MemberExpression n -> n.member(); case NamedType n -> n.name();
            case UsingDeclaration n -> n.qualifiedName(); case MemberInitializer n -> n.name();
            default -> "";
        };
    }
    private static String simple(String name) { int separator = name.lastIndexOf("::"); return separator < 0 ? name : name.substring(separator + 2); }
    private static List<Node> nodes(Project project) {
        var nodes = new ArrayList<Node>();
        project.walk(new NodeVisitor() { @Override public void visit(Node node) { nodes.add(node); } });
        return nodes;
    }
    private static <T> Set<T> identitySet() { return Collections.newSetFromMap(new IdentityHashMap<>()); }
    private static IllegalArgumentException rejected(String reason) { return new IllegalArgumentException("Cannot rename declaration safely: " + reason); }
    private static final Set<String> KEYWORDS = Set.of(("asm auto bool break case catch char class const const_cast continue default delete do double dynamic_cast else enum explicit export extern false float for friend goto if inline int long mutable namespace new operator private protected public register reinterpret_cast return short signed sizeof static static_cast struct switch template this throw true try typedef typeid typename union unsigned using virtual void volatile wchar_t while and and_eq bitand bitor compl not not_eq or or_eq xor xor_eq").split(" "));
}
