/* Code Property Graph: identity links for written scope qualifiers. MIT License. */
package com.kniazkov.cpg.utilities;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.analysis.InheritanceSemantics;
import java.util.*;

/** Conservative identity index for tokens preceding :: and namespace import targets.
 * Unresolved and unsafe tokens are explicit, so callers must not silently omit them when
 * their spelling is being renamed. No source or graph is changed by building this index.
 */
public final class QualifiedIdentifierIndex {
    private QualifiedIdentifierIndex() {}
    public record Result(Map<SourceRange, Declaration> targets, Set<SourceRange> unresolved,
            Set<SourceRange> unsafe) {}
    public static Result index(Project project) {
        var index = new Index();
        for (SourceFile file : project.files()) index.file(file);
        return new Result(Collections.unmodifiableMap(index.targets), Set.copyOf(index.unresolved), Set.copyOf(index.unsafe));
    }
    private static final class Scope {
        final Scope parent;
        final Map<String, List<Declaration>> names = new LinkedHashMap<>();
        final Map<String, Scope> nested = new LinkedHashMap<>();
        final List<UsingDirective> directives = new ArrayList<>();
        final List<ClassDeclaration> bases = new ArrayList<>();
        Scope(Scope parent) { this.parent = parent; }
    }
    private static final class Index {
        final Map<SourceRange, Declaration> targets = new LinkedHashMap<>();
        final Set<SourceRange> unresolved = new LinkedHashSet<>(), unsafe = new LinkedHashSet<>();
        final Map<Node, Scope> scopes = new IdentityHashMap<>();
        final Map<Declaration, Scope> introduced = new IdentityHashMap<>();
        final Map<Declaration, Declaration> representatives = new IdentityHashMap<>();
        final List<Node> nodes = new ArrayList<>();
        void file(SourceFile file) {
            scopes.clear(); introduced.clear(); representatives.clear(); nodes.clear();
            Scope root = new Scope(null); collect(file, root);
            if (file.provenance().isEmpty()) return;
            var unit = file.provenance().orElseThrow().unit();
            var tokens = unit.tokens();
            var significant = new ArrayList<Integer>();
            for (int i = 0; i < tokens.size(); i++) {
                String s = tokens.get(i).spelling();
                if (!s.isBlank() && !s.startsWith("//") && !s.startsWith("/*")) significant.add(i);
            }
            for (int p = 0; p < significant.size(); p++) {
                int tokenIndex = significant.get(p);
                String word = tokens.get(tokenIndex).spelling();
                // A template-id qualifier requires instantiated lookup, which this index does
                // not guess. Mark its written name, and any following qualifier, explicitly.
                if (word.equals(">") && p + 1 < significant.size()
                        && tokens.get(significant.get(p + 1)).spelling().equals("::")) {
                    int depth = 1, q = p - 1;
                    for (; q >= 0 && depth > 0; q--) {
                        String part = tokens.get(significant.get(q)).spelling();
                        if (part.equals(">")) depth++;
                        else if (part.equals("<")) depth--;
                    }
                    if (depth == 0 && q >= 0 && identifier(tokens.get(significant.get(q)).spelling()))
                        record(tokens.get(significant.get(q)), null);
                }
                if (!identifier(word) || p + 1 >= significant.size()
                        || !tokens.get(significant.get(p + 1)).spelling().equals("::")) continue;
                Node owner = owner(unit, tokenIndex);
                Scope lexical = scopes.getOrDefault(owner, root);
                int first = p;
                while (first >= 2 && tokens.get(significant.get(first - 1)).spelling().equals("::")
                        && identifier(tokens.get(significant.get(first - 2)).spelling())) first -= 2;
                boolean global = first > 0 && tokens.get(significant.get(first - 1)).spelling().equals("::");
                Scope current = global ? root : lexical;
                Declaration found = null;
                for (int q = first; q <= p; q += 2) {
                    found = lookup(current, tokens.get(significant.get(q)).spelling(), tokenIndex, q == first && !global);
                    current = container(found);
                    if (found == null || q < p && current == null) { found = null; break; }
                }
                if (first >= 2 && tokens.get(significant.get(first - 1)).spelling().equals("::")
                        && tokens.get(significant.get(first - 2)).spelling().equals(">")) found = null;
                record(tokens.get(tokenIndex), found);
            }
            // The last token in a namespace import is a use too, despite lacking a following ::.
            for (Node node : nodes) {
                String written; Declaration target;
                if (node instanceof NamespaceAliasDeclaration alias) { written = alias.targetName(); target = alias.target().orElse(null); }
                else if (node instanceof UsingDirective directive) { written = directive.namespaceName(); target = directive.target().orElse(null); }
                else continue;
                if (node.provenance().isEmpty()) continue;
                var provenance = node.provenance().orElseThrow();
                String last = simple(written);
                Preprocessing.Token token = null;
                for (var candidate : provenance.tokens()) if (candidate.spelling().equals(last)) token = candidate;
                if (token != null) {
                    Scope current = scopes.get(node);
                    if (written.startsWith("::")) while (current != null && current.parent != null) current = current.parent;
                    boolean first = !written.startsWith("::");
                    Declaration writtenTarget = null;
                    for (String part : written.split("::")) if (!part.isEmpty()) {
                        writtenTarget = lookup(current, part, provenance.startToken() - 1, first);
                        current = container(writtenTarget); first = false;
                    }
                    // Alias spelling names the alias, not the namespace at the end of its chain.
                    if (target == null || container(writtenTarget) != container(target)) writtenTarget = null;
                    record(token, writtenTarget);
                }
            }
        }
        Node owner(Preprocessing unit, int token) {
            Node result = null; int length = Integer.MAX_VALUE;
            for (Node node : nodes) if (node.provenance().isPresent()) {
                var p = node.provenance().orElseThrow();
                if (p.unit() == unit && p.startToken() <= token && token < p.endToken() && p.endToken() - p.startToken() < length) {
                    result = node; length = p.endToken() - p.startToken();
                }
            }
            return result;
        }
        void collect(Node node, Scope lexical) {
            nodes.add(node); scopes.put(node, lexical);
            Scope scope = lexical;
            if (node instanceof Declaration declaration) {
                String name = name(declaration);
                if (declaration instanceof UsingDirective directive) scope.directives.add(directive);
                // Resolve explicit owners before indexing bodies of out-of-line definitions.
                if ((declaration instanceof CallableDeclaration || declaration instanceof VariableDeclaration) && name.contains("::")) {
                    String qualifier = name.substring(0, name.lastIndexOf("::"));
                    Scope found = resolveScope(lexical, qualifier);
                    if (found != null) scope = found;
                }
                if (!name.isEmpty()) scope.names.computeIfAbsent(simple(name), ignored -> new ArrayList<>()).add(declaration);
                if (declaration instanceof NamespaceDeclaration || declaration instanceof ClassDeclaration) {
                    Scope parent = scope;
                    Scope nested = parent.nested.computeIfAbsent(name, ignored -> new Scope(parent));
                    introduced.put(declaration, nested);
                    if (declaration instanceof ClassDeclaration type)
                        for (BaseSpecifier base : type.bases()) InheritanceSemantics.baseClass(base).ifPresent(nested.bases::add);
                    var peers = parent.names.get(simple(name));
                    Declaration representative = peers.stream().filter(d -> d.getClass() == declaration.getClass()).findFirst().orElse(declaration);
                    representatives.put(declaration, representative); scope = nested;
                } else if (declaration instanceof CallableDeclaration) scope = new Scope(scope);
            } else if (node instanceof BlockStatement || node instanceof ForStatement || node instanceof CatchClause
                    || node instanceof IfStatement || node instanceof WhileStatement || node instanceof SwitchStatement) scope = new Scope(scope);
            for (Node child : node.children()) collect(child, scope);
        }
        Scope resolveScope(Scope lexical, String name) {
            Scope current = lexical;
            if (name.startsWith("::")) { while (current.parent != null) current = current.parent; }
            boolean first = !name.startsWith("::");
            for (String part : name.split("::")) if (!part.isEmpty()) {
                current = container(lookup(current, part, Integer.MAX_VALUE, first)); first = false;
                if (current == null) return null;
            }
            return current;
        }
        Declaration lookup(Scope scope, String name, int token, boolean parents) {
            for (Scope current = scope; current != null; current = parents ? current.parent : null) {
                var candidates = visible(current, name, token, Collections.newSetFromMap(new IdentityHashMap<>()));
                if (candidates.isEmpty()) continue;
                Declaration selected = null;
                for (Declaration declaration : candidates) {
                    if (declaration instanceof UsingDeclaration use) declaration = use.target().orElse(null);
                    // A non-scope declaration shadows outer scopes; never guess through it.
                    if (!(declaration instanceof NamespaceDeclaration || declaration instanceof ClassDeclaration
                            || declaration instanceof NamespaceAliasDeclaration || declaration instanceof TypedefDeclaration)) return null;
                    Declaration candidate = representatives.getOrDefault(declaration, declaration);
                    if (selected != null && selected != candidate && !equivalent(selected, candidate)) return null;
                    selected = candidate;
                }
                return selected;
            }
            return null;
        }
        List<Declaration> visible(Scope scope, String name, int token, Set<Scope> visited) {
            if (scope == null || !visited.add(scope)) return List.of();
            var direct = scope.names.getOrDefault(name, List.of()).stream()
                    .filter(d -> d.provenance().isEmpty() || d.provenance().orElseThrow().startToken() <= token).toList();
            if (!direct.isEmpty()) return direct;
            var imported = new ArrayList<Declaration>();
            for (ClassDeclaration base : scope.bases) imported.addAll(visible(introduced.get(base), name, token, visited));
            for (UsingDirective directive : scope.directives) {
                if (directive.provenance().isPresent() && directive.provenance().orElseThrow().startToken() > token) continue;
                imported.addAll(visible(directive.target().map(introduced::get).orElse(null), name, token, visited));
            }
            return imported;
        }
        Scope container(Declaration declaration) {
            if (declaration instanceof NamespaceAliasDeclaration alias) return alias.target().map(introduced::get).orElse(null);
            if (declaration instanceof TypedefDeclaration alias) {
                for (Node node : alias.children()) if (node instanceof NamedType type) return type.declaration().map(introduced::get).orElse(null);
            }
            return introduced.get(declaration);
        }
        void record(Preprocessing.Token token, Declaration target) {
            SourceRange range = token.spellingRange();
            if (!token.expansionIds().isEmpty() || !range.equals(token.expansionRange())) unsafe.add(range);
            if (target == null) { unresolved.add(range); targets.remove(range); return; }
            target = representatives.getOrDefault(target, target);
            Declaration prior = targets.get(range);
            if (prior != null && !equivalent(prior, target)) { unresolved.add(range); targets.remove(range); }
            else if (!unresolved.contains(range)) targets.putIfAbsent(range, target);
        }
    }
    private static boolean equivalent(Declaration a, Declaration b) {
        if (a == b) return true;
        if (a.getClass() != b.getClass()) return false;
        if (a.range().isPresent() && a.range().equals(b.range())) return true;
        Set<Declaration> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Declaration>(); pending.add(a);
        while (!pending.isEmpty()) { Declaration d = pending.removeFirst(); if (d == b) return true;
            if (visited.add(d)) { d.previousDeclaration().ifPresent(pending::add); d.definition().ifPresent(pending::add); }
        }
        return false;
    }
    private static boolean identifier(String value) { return value.matches("[A-Za-z_][A-Za-z_0-9]*") && !KEYWORDS.contains(value); }
    private static final Set<String> KEYWORDS = Set.of(("asm auto bool break case catch char class const const_cast continue default delete do double dynamic_cast else enum explicit export extern false float for friend goto if inline int long mutable namespace new operator private protected public register reinterpret_cast return short signed sizeof static static_cast struct switch template this throw true try typedef typeid typename union unsigned using virtual void volatile wchar_t while and and_eq bitand bitor compl not not_eq or or_eq xor xor_eq").split(" "));
    private static String simple(String name) { int index = name.lastIndexOf("::"); return index < 0 ? name : name.substring(index + 2); }
    private static String name(Declaration node) {
        return switch (node) {
            case NamespaceDeclaration n -> n.name(); case NamespaceAliasDeclaration n -> n.name();
            case UsingDeclaration n -> n.qualifiedName();
            case ClassDeclaration n -> n.name(); case CallableDeclaration n -> n.name();
            case TypedefDeclaration n -> n.name(); case VariableDeclaration n -> n.name();
            case ParameterDeclaration n -> n.name(); case TemplateParameterDeclaration n -> n.name();
            case EnumDeclaration n -> n.name(); case EnumeratorDeclaration n -> n.name(); default -> "";
        };
    }
}
