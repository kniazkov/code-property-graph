/* Code Property Graph: point-aware C++03 scopes and linkage. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Resolves written names first, then connects compatible declarations to definitions.
 * An overload set remains ambiguous: selecting the best overload is a separate analysis. */
public final class NameResolver {
    private NameResolver() {}
    public static Project resolve(Project project) {
        // Type bindings must be published before signatures are compared. Both passes copy
        // through one identity context, including forward and cross-file reference cycles.
        Project typed = new Engine(StandardConversions.written(project)).bindNames();
        return StandardConversions.analyze(new Engine(typed).linkDeclarations());
    }

    private static final class Scope {
        final Scope parent;
        final SourceFile file;
        final String qualified;
        final boolean local, anonymous, classScope;
        final Map<String, List<Declaration>> names = new LinkedHashMap<>();
        final Map<String, Scope> nested = new LinkedHashMap<>();
        final List<UsingDirective> directives = new ArrayList<>();
        final List<NamespaceDeclaration> anonymousNamespaces = new ArrayList<>();
        Scope(Scope parent, SourceFile file, String qualified, boolean local, boolean anonymous, boolean classScope) {
            this.parent = parent; this.file = file; this.qualified = qualified;
            this.local = local; this.anonymous = anonymous; this.classScope = classScope;
        }
        void add(String name, Declaration declaration) {
            if (!name.isEmpty()) names.computeIfAbsent(name, ignored -> new ArrayList<>()).add(declaration);
        }
    }
    private record Match(Declaration declaration, List<TypeResolutionStep> steps) {}

    private static final class Engine {
        final Project project;
        final ProjectEditor editor;
        final Map<Node, Scope> scopes = new IdentityHashMap<>();
        final Map<Node, Integer> positions = new IdentityHashMap<>();
        final Map<Integer, Scope> pointScopes = new HashMap<>();
        final Map<ClassDeclaration, Integer> classEnds = new IdentityHashMap<>();
        final Map<EnumDeclaration, Integer> enumEnds = new IdentityHashMap<>();
        final Set<Scope> functionScopes = Collections.newSetFromMap(new IdentityHashMap<>());
        final Map<Declaration, Scope> introduced = new IdentityHashMap<>();
        final Map<SourceFile, Scope> roots = new IdentityHashMap<>();
        final List<Declaration> declarations = new ArrayList<>();
        final Map<NamedType, Match> typeBindings = new IdentityHashMap<>();
        final Map<Declaration, Declaration.Linkage> linkages = new IdentityHashMap<>();
        final Map<Declaration, String> keys = new IdentityHashMap<>();
        final Map<String, List<Declaration>> groups = new LinkedHashMap<>();
        final Set<Declaration> conflicting = Collections.newSetFromMap(new IdentityHashMap<>());
        int position;
        Engine(Project project) {
            this.project = project; editor = new ProjectEditor(project);
            for (SourceFile file : project.files()) {
                Scope root = new Scope(null, file, "", false, false, false);
                roots.put(file, root); index(file, root);
            }
        }

        void index(Node node, Scope scope) {
            scopes.put(node, scope); pointScopes.put(position, scope); positions.put(node, position++);
            if (node instanceof Declaration declaration) {
                declarations.add(declaration); scope.add(name(declaration), declaration);
            }
            if (node instanceof NamespaceDeclaration namespace) {
                String name = namespace.anonymous() ? "(anonymous)" : namespace.name();
                Scope nested = scope.nested.computeIfAbsent(name, ignored -> new Scope(scope, scope.file,
                        qualify(scope.qualified, name), false, scope.anonymous || namespace.anonymous(), false));
                introduced.put(namespace, nested);
                if (namespace.anonymous()) scope.anonymousNamespaces.add(namespace);
                namespace.members().forEach(member -> index(member, nested));
            } else if (node instanceof ClassDeclaration owner) {
                Scope nested = scope.nested.computeIfAbsent(owner.name(), ignored -> new Scope(scope, scope.file,
                        qualify(scope.qualified, owner.name()), scope.local, scope.anonymous, true));
                introduced.put(owner, nested);
                owner.members().forEach(member -> index(member, nested));
                classEnds.put(owner, position);
            } else if (node instanceof CallableDeclaration callable) {
                Scope nested = new Scope(scope, scope.file, qualify(scope.qualified, callable.name()), true, scope.anonymous, false);
                functionScopes.add(nested);
                callable.writtenType().ifPresent(type -> index(type, scope));
                callable.returnType().ifPresent(type -> index(type, scope));
                callable.parameters().forEach(parameter -> index(parameter, nested));
                callable.body().ifPresent(body -> index(body, nested));
                index(callable.exit(), nested);
            } else if (node instanceof FunctionType function) {
                Scope nested = new Scope(scope, scope.file, scope.qualified + "#prototype" + positions.get(node), true, scope.anonymous, false);
                index(function.returnType(), scope);
                function.parameters().forEach(parameter -> index(parameter, nested));
            } else if (node instanceof BlockStatement block) {
                Scope nested = new Scope(scope, scope.file, scope.qualified + "#" + positions.get(node), true, scope.anonymous, false);
                block.items().forEach(item -> index(item, nested));
            } else if (node instanceof VariableDeclaration variable) {
                variable.type().ifPresent(type -> index(type, scope));
                positions.put(variable, position++); // The declarator precedes its initializer.
                variable.initializer().ifPresent(value -> index(value, scope));
            } else if (node instanceof ParameterDeclaration parameter) {
                parameter.type().ifPresent(type -> index(type, scope));
                positions.put(parameter, position++);
                for (Node child : parameter.children()) if (!(child instanceof TypeNode)) index(child, scope);
            } else {
                if (node instanceof UsingDirective directive) scope.directives.add(directive);
                for (Node child : node.children()) index(child, scope);
                if (node instanceof TypedefDeclaration || node instanceof EnumeratorDeclaration)
                    positions.put(node, position++);
                if (node instanceof EnumDeclaration enumeration) enumEnds.put(enumeration, position);
            }
        }

        Project bindNames() {
            for (Declaration declaration : declarations) {
                Scope scope = scopes.get(declaration);
                if (declaration instanceof ClassDeclaration || declaration instanceof EnumDeclaration) {
                    String identity = qualify(scope.qualified, name(declaration));
                    if (name(declaration).isEmpty()) identity += "(unnamed@" + positions.get(declaration) + ")";
                    if (scope.local || scope.anonymous || name(declaration).isEmpty()) identity = scope.file.variantId().orElse(scope.file.path()) + ":" + identity;
                    editor.identifyType(declaration, identity);
                }
                if (declaration instanceof CallableDeclaration || declaration instanceof VariableDeclaration) {
                    List<Declaration> priorDeclarations = scope.local
                            ? lookup(name(declaration), scope, positions.get(declaration) - 1, new HashSet<>()).stream().map(Match::declaration).toList()
                            : scope.names.getOrDefault(name(declaration), List.of());
                    for (Declaration prior : priorDeclarations) {
                        if (prior == declaration) break;
                        if (prior.languageLinkage().equals("C") && prior.getClass() == declaration.getClass()
                                && (!(declaration instanceof CallableDeclaration next) || parameterSignature((CallableDeclaration)prior).equals(parameterSignature(next)))) {
                            editor.languageLinkage(declaration, "C"); break;
                        }
                    }
                }
                if (declaration instanceof NamespaceAliasDeclaration alias)
                    editor.bindImport(alias, namespace(alias.targetName(), scopes.get(alias), positions.get(alias), new HashSet<>()));
                else if (declaration instanceof UsingDirective directive)
                    editor.bindImport(directive, namespace(directive.namespaceName(), scopes.get(directive), positions.get(directive), new HashSet<>()));
                else if (declaration instanceof UsingDeclaration using) {
                    var matches = lookup(using.qualifiedName(), scopes.get(using), positions.get(using) - 1, new HashSet<>());
                    editor.bindImport(using, matches.size() == 1 ? matches.getFirst().declaration() : null);
                }
            }
            project.walk(new NodeVisitor() {
                @Override public void visit(NamedType type) {
                    var matches = lookup(type.name(), scopes.get(type), positions.get(type), new HashSet<>(), type.elaboratedKeyword().isPresent());
                    Match match = unique(matches);
                    // Derived function-typedef signatures retain the typedef's original
                    // scope rather than rebinding its aliases at the new declarator.
                    if (type.range().isEmpty() && type.provenance().isEmpty() && type.alias().isPresent())
                        match = new Match(type.alias().orElseThrow(), List.of());
                    if (match != null && !isType(match.declaration())) match = null;
                    if (match != null) typeBindings.put(type, match);
                }
            });
            project.walk(new NodeVisitor() {
                @Override public void visit(NamedType type) {
                    Match match = typeBindings.get(type);
                    var steps = new ArrayList<TypeResolutionStep>();
                    if (match != null) {
                        steps.addAll(match.steps());
                        appendTypeChain(match.declaration(), steps, Collections.newSetFromMap(new IdentityHashMap<>()));
                    }
                    editor.bind(type, match == null ? null : match.declaration(),
                            match == null || complete(match.declaration(), positions.get(type), new HashSet<>()), steps);
                }
                @Override public void visit(NameExpression name) {
                    Match match = unique(lookup(name.name(), scopes.get(name), positions.get(name), new HashSet<>()));
                    if (match == null) editor.unbind(name); else editor.bind(name, match.declaration());
                }
                @Override public void visit(MemberExpression member) {
                    String written = expressionName(member);
                    Match match = written == null ? null : unique(lookup(written, scopes.get(member), positions.get(member), new HashSet<>()));
                    editor.bind(member, match == null ? null : match.declaration());
                }
            });
            return editor.build();
        }

        void appendTypeChain(Declaration declaration, List<TypeResolutionStep> steps, Set<Declaration> seen) {
            if (!seen.add(declaration)) return;
            if (declaration instanceof TypedefDeclaration alias) {
                Declaration next = null;
                Match binding = null;
                if (alias.type() instanceof NamedType written) {
                    binding = typeBindings.get(written);
                    next = binding == null ? written.declaration().orElse(written.alias().orElse(null)) : binding.declaration();
                }
                steps.add(new TypeResolutionStep("typedef", alias, next == null ? alias : next));
                if (binding != null) steps.addAll(binding.steps());
                if (next != null) appendTypeChain(next, steps, seen);
            } else steps.add(new TypeResolutionStep("declaration", declaration, declaration));
        }

        boolean complete(Declaration declaration, int point, Set<Declaration> seen) {
            if (!seen.add(declaration)) return false;
            if (declaration instanceof ClassDeclaration owner) {
                Scope scope = scopes.get(owner);
                return scope.names.getOrDefault(owner.name(), List.of()).stream()
                        .anyMatch(candidate -> candidate instanceof ClassDeclaration && candidate.isDefinition()
                                && (classEnds.getOrDefault((ClassDeclaration)candidate, positions.get(candidate)) <= point
                                    || completeClassContext(introduced.get(candidate), point)));
            }
            if (declaration instanceof EnumDeclaration enumeration) return enumEnds.getOrDefault(enumeration, 0) <= point;
            if (declaration instanceof TypedefDeclaration alias) return completeType(alias.type(), point, seen);
            return true;
        }
        boolean completeClassContext(Scope owner, int point) {
            boolean insideFunction = false;
            for (Scope current = pointScopes.get(point); current != null; current = current.parent) {
                if (current == owner) return insideFunction;
                if (functionScopes.contains(current)) insideFunction = true;
            }
            return false;
        }
        boolean completeType(TypeNode type, int point, Set<Declaration> seen) {
            if (type instanceof PointerType || type instanceof MemberPointerType) return true;
            if (type instanceof QualifiedType qualified) return completeType(qualified.baseType(), point, seen);
            if (type instanceof ReferenceType reference) return completeType(reference.referredType(), point, seen);
            if (type instanceof ArrayType array) return array.bound().isPresent() && completeType(array.elementType(), point, seen);
            if (type instanceof NamedType name) {
                Match match = typeBindings.get(name);
                Declaration declaration = match == null ? name.declaration().orElse(name.alias().orElse(null)) : match.declaration();
                return declaration == null || complete(declaration, point, seen);
            }
            return true;
        }

        List<Match> lookup(String written, Scope scope, int point, Set<Declaration> active) {
            return lookup(written, scope, point, active, false);
        }
        List<Match> lookup(String written, Scope scope, int point, Set<Declaration> active, boolean tagsOnly) {
            if (scope == null || written.isEmpty()) return List.of();
            String text = written.replace(".", "::");
            boolean absolute = text.startsWith("::");
            if (absolute) { while (scope.parent != null) scope = scope.parent; text = text.substring(2); }
            int separator = text.indexOf("::");
            if (separator < 0) return unqualified(text, scope, point, active, !absolute, tagsOnly);
            List<Match> owners = unqualified(text.substring(0, separator), scope, point, active, !absolute);
            var result = new ArrayList<Match>();
            for (Match owner : owners) {
                Scope nested = namespaceScope(owner.declaration(), active);
                if (nested != null) {
                    for (Match target : qualified(text.substring(separator + 2), nested, point, active, tagsOnly)) {
                        var steps = new ArrayList<TypeResolutionStep>(owner.steps());
                        if (owner.declaration() instanceof NamespaceAliasDeclaration alias) {
                            NamespaceDeclaration destination = namespace(alias.targetName(), scopes.get(alias), positions.get(alias) - 1, active);
                            if (destination != null) steps.add(new TypeResolutionStep("namespace-alias", alias, destination));
                        }
                        steps.addAll(target.steps()); result.add(new Match(target.declaration(), List.copyOf(steps)));
                    }
                }
            }
            return distinct(result);
        }
        List<Match> qualified(String text, Scope scope, int point, Set<Declaration> active, boolean tagsOnly) {
            int separator = text.indexOf("::");
            if (separator < 0) return unqualified(text, scope, point, active, false, tagsOnly);
            var result = new ArrayList<Match>();
            for (Match owner : unqualified(text.substring(0, separator), scope, point, active, false)) {
                Scope nested = namespaceScope(owner.declaration(), active);
                if (nested != null) {
                    for (Match target : qualified(text.substring(separator + 2), nested, point, active, tagsOnly)) {
                        var steps = new ArrayList<TypeResolutionStep>(owner.steps());
                        if (owner.declaration() instanceof NamespaceAliasDeclaration alias) {
                            NamespaceDeclaration destination = namespace(alias.targetName(), scopes.get(alias), positions.get(alias) - 1, active);
                            if (destination != null) steps.add(new TypeResolutionStep("namespace-alias", alias, destination));
                        }
                        steps.addAll(target.steps()); result.add(new Match(target.declaration(), List.copyOf(steps)));
                    }
                }
            }
            return distinct(result);
        }
        List<Match> unqualified(String name, Scope scope, int point, Set<Declaration> active, boolean parents) {
            return unqualified(name, scope, point, active, parents, false);
        }
        List<Match> unqualified(String name, Scope scope, int point, Set<Declaration> active, boolean parents, boolean tagsOnly) {
            if (!parents) {
                var direct = direct(name, scope, point, active, tagsOnly, false);
                if (!direct.isEmpty()) return collapse(direct);
                return collapse(imported(name, scope, point, active, tagsOnly, null));
            }
            // An unqualified using-directive introduces candidates at the nearest
            // common enclosing namespace, not at the directive's lexical scope.
            Map<Scope, List<Match>> pending = new IdentityHashMap<>();
            for (Scope current = scope; current != null; current = current.parent) {
                imported(name, current, point, active, tagsOnly, pending);
                var matches = new ArrayList<>(direct(name, current, point, active, tagsOnly, scope.local));
                matches.addAll(pending.getOrDefault(current, List.of()));
                if (!matches.isEmpty()) return collapse(matches);
            }
            return List.of();
        }
        List<Match> direct(String name, Scope scope, int point, Set<Declaration> active, boolean tagsOnly, boolean completeClassContext) {
            var direct = new ArrayList<Match>();
            for (Declaration declaration : scope.names.getOrDefault(name, List.of())) {
                if (positions.get(declaration) > point && !(scope.classScope && completeClassContext)) continue;
                if (tagsOnly && !(declaration instanceof ClassDeclaration || declaration instanceof EnumDeclaration || declaration instanceof UsingDeclaration)) continue;
                if (declaration instanceof UsingDeclaration using) {
                    if (!active.add(using)) continue;
                    for (Match target : lookup(using.qualifiedName(), scope, positions.get(using) - 1, active, tagsOnly)) {
                        var steps = new ArrayList<TypeResolutionStep>();
                        steps.add(new TypeResolutionStep("using-declaration", using, target.declaration())); steps.addAll(target.steps());
                        direct.add(new Match(target.declaration(), List.copyOf(steps)));
                    }
                    active.remove(using);
                } else direct.add(new Match(declaration, List.of()));
            }
            if (!tagsOnly && direct.stream().anyMatch(match -> !(match.declaration() instanceof ClassDeclaration || match.declaration() instanceof EnumDeclaration)))
                direct.removeIf(match -> match.declaration() instanceof ClassDeclaration || match.declaration() instanceof EnumDeclaration);
            return direct;
        }
        List<Match> imported(String name, Scope scope, int point, Set<Declaration> active, boolean tagsOnly, Map<Scope, List<Match>> pending) {
            var imported = new ArrayList<Match>();
            for (UsingDirective directive : scope.directives) {
                if (positions.get(directive) > point || !active.add(directive)) continue;
                NamespaceDeclaration namespace = namespace(directive.namespaceName(), scope, positions.get(directive), active);
                if (namespace != null) {
                    var matches = new ArrayList<Match>();
                    Scope targetScope = introduced.get(namespace);
                    List<Match> nominated = pending == null ? unqualified(name, targetScope, point, active, false, tagsOnly)
                            : nominated(name, targetScope, point, active, tagsOnly);
                    for (Match target : nominated) {
                        var steps = new ArrayList<TypeResolutionStep>();
                        steps.add(new TypeResolutionStep("using-directive", directive, target.declaration())); steps.addAll(target.steps());
                        matches.add(new Match(target.declaration(), List.copyOf(steps)));
                    }
                    if (pending == null) imported.addAll(matches);
                    else for (Match match : matches)
                        pending.computeIfAbsent(commonNamespace(scope, nominationScope(match)), ignored -> new ArrayList<>()).add(match);
                }
                active.remove(directive);
            }
            for (NamespaceDeclaration namespace : scope.anonymousNamespaces) {
                if (positions.get(namespace) <= point && active.add(namespace)) {
                    var matches = unqualified(name, introduced.get(namespace), point, active, false, tagsOnly);
                    if (pending == null) imported.addAll(matches);
                    else pending.computeIfAbsent(scope, ignored -> new ArrayList<>()).addAll(matches);
                    active.remove(namespace);
                }
            }
            return imported;
        }
        List<Match> nominated(String name, Scope scope, int point, Set<Declaration> active, boolean tagsOnly) {
            var result = new ArrayList<>(direct(name, scope, point, active, tagsOnly, false));
            // Transitive directives participate even when a nominated namespace has a
            // direct declaration. Qualified lookup intentionally uses different hiding.
            for (UsingDirective directive : scope.directives) {
                if (positions.get(directive) > point || !active.add(directive)) continue;
                NamespaceDeclaration target = namespace(directive.namespaceName(), scope, positions.get(directive), active);
                if (target != null) for (Match match : nominated(name, introduced.get(target), point, active, tagsOnly)) {
                    var steps = new ArrayList<TypeResolutionStep>();
                    steps.add(new TypeResolutionStep("using-directive", directive, match.declaration())); steps.addAll(match.steps());
                    result.add(new Match(match.declaration(), List.copyOf(steps)));
                }
                active.remove(directive);
            }
            for (NamespaceDeclaration namespace : scope.anonymousNamespaces) {
                if (positions.get(namespace) <= point && active.add(namespace)) {
                    result.addAll(nominated(name, introduced.get(namespace), point, active, tagsOnly)); active.remove(namespace);
                }
            }
            return distinct(result);
        }
        Scope nominationScope(Match match) {
            for (TypeResolutionStep step : match.steps())
                if (step.kind().equals("using-declaration")) return scopes.get(step.via());
            return scopes.get(match.declaration());
        }
        Scope commonNamespace(Scope left, Scope right) {
            Set<Scope> ancestors = Collections.newSetFromMap(new IdentityHashMap<>());
            for (Scope current = left; current != null; current = current.parent)
                if (!current.local && !current.classScope) ancestors.add(current);
            for (Scope current = right; current != null; current = current.parent)
                if (ancestors.contains(current)) return current;
            return roots.get(left.file);
        }
        NamespaceDeclaration namespace(String name, Scope scope, int point, Set<Declaration> active) {
            Match match = unique(lookup(name, scope, point, active));
            if (match == null) return null;
            if (match.declaration() instanceof NamespaceDeclaration namespace) return namespace;
            if (match.declaration() instanceof NamespaceAliasDeclaration alias && active.add(alias)) {
                NamespaceDeclaration result = namespace(alias.targetName(), scopes.get(alias), positions.get(alias) - 1, active);
                active.remove(alias); return result;
            }
            return null;
        }
        Scope namespaceScope(Declaration declaration, Set<Declaration> active) {
            if (declaration instanceof TypedefDeclaration alias && active.add(alias)) {
                TypeNode type = alias.type().canonicalType();
                Declaration target = null;
                if (type instanceof NamedType named) {
                    Match binding = typeBindings.get(named);
                    if (binding == null) binding = unique(lookup(named.name(), scopes.get(named), positions.get(named), active));
                    target = binding == null ? named.declaration().orElse(null) : binding.declaration();
                }
                Scope result = target == null ? null : namespaceScope(target, active);
                active.remove(alias); return result;
            }
            if (declaration instanceof NamespaceAliasDeclaration alias && active.add(alias)) {
                NamespaceDeclaration target = namespace(alias.targetName(), scopes.get(alias), positions.get(alias) - 1, active);
                active.remove(alias); return target == null ? null : introduced.get(target);
            }
            return introduced.get(declaration);
        }
        List<Match> collapse(List<Match> matches) {
            var result = new ArrayList<Match>();
            for (Match match : distinct(matches)) {
                // Repeated declarations in one scope denote one name. Distinct imported
                // namespaces are not collapsed merely because they spell the same name.
                if (!(match.declaration() instanceof CallableDeclaration))
                    result.removeIf(old -> !(old.declaration() instanceof CallableDeclaration)
                            && scopes.get(old.declaration()) == scopes.get(match.declaration())
                            && old.declaration().getClass() == match.declaration().getClass());
                result.add(match);
            }
            return result;
        }
        List<Match> distinct(List<Match> matches) {
            Set<Declaration> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            return matches.stream().filter(match -> seen.add(match.declaration())).toList();
        }
        Match unique(List<Match> matches) {
            if (matches.size() == 1) return matches.getFirst();
            if (matches.size() > 1 && matches.stream().allMatch(m -> m.declaration() instanceof CallableDeclaration)
                    && matches.stream().map(m -> signature((CallableDeclaration)m.declaration())).distinct().count() == 1
                    && matches.stream().allMatch(m -> scopes.get(m.declaration()) == scopes.get(matches.getFirst().declaration())))
                return matches.getLast();
            return null;
        }

        Project linkDeclarations() {
            for (Declaration declaration : declarations) {
                Declaration.Linkage linkage = linkage(declaration);
                linkages.put(declaration, linkage);
                Scope scope = scopes.get(declaration);
                if (scope.parent != null && functionScopes.contains(scope.parent)) {
                    for (Declaration parameter : scope.parent.names.getOrDefault(name(declaration), List.of())) {
                        if (parameter instanceof ParameterDeclaration) {
                            error(declaration, "conflicting-declaration", "Parameter redeclared in the outer function body: " + name(declaration));
                            error(parameter, "conflicting-declaration", "Parameter redeclared in the outer function body: " + name(declaration));
                        }
                    }
                }
                if (modifiers(declaration).contains("static") && !scope.local && !scope.classScope) {
                    for (Declaration prior : scope.names.getOrDefault(name(declaration), List.of())) {
                        if (prior == declaration) break;
                        if (prior.getClass() != declaration.getClass() || linkages.get(prior) != Declaration.Linkage.EXTERNAL) continue;
                        if (prior instanceof CallableDeclaration callable && declaration instanceof CallableDeclaration next
                                && !signature(callable).equals(signature(next))) continue;
                        error(prior, "conflicting-declaration", "External declaration followed by internal declaration of " + name(declaration));
                        error(declaration, "conflicting-declaration", "External declaration followed by internal declaration of " + name(declaration));
                    }
                }
                String key = entityKey(declaration, linkage);
                keys.put(declaration, key);
                if (key != null) groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(declaration);
                else editor.declare(declaration, linkage, isDefinition(declaration), null, null);
            }
            for (List<Declaration> group : groups.values()) connect(group);
            project.walk(new NodeVisitor() {
                @Override public void visit(CallExpression call) { resolveCall(call); }
                @Override public void visit(NameExpression name) { diagnoseAmbiguity(name, name.name(), false); }
                @Override public void visit(MemberExpression member) {
                    String name = expressionName(member); if (name != null) diagnoseAmbiguity(member, name, false);
                }
                @Override public void visit(NamedType type) { diagnoseAmbiguity(type, type.name(), type.elaboratedKeyword().isPresent()); }
                @Override public void visit(SizeofExpression expression) {
                    Scope scope = scopes.get(expression);
                    expression.type().or(() -> expression.operand().flatMap(operand -> TypeSemantics.expressionType(operand,
                                    TypeSemantics.profile(scope.file))))
                            .filter(type -> !completeType(type, positions.get(expression), new HashSet<>()))
                            .ifPresent(type -> error(expression, "incomplete-type", "sizeof requires a complete type at this source position"));
                }
            });
            return editor.build();
        }
        void diagnoseAmbiguity(Node node, String name, boolean tagsOnly) {
            var matches = lookup(name, scopes.get(node), positions.get(node), new HashSet<>(), tagsOnly);
            if (matches.size() > 1 && !matches.stream().allMatch(match -> match.declaration() instanceof CallableDeclaration))
                error(node, "ambiguous-name", "Ambiguous name at this source position: " + name);
        }
        Declaration.Linkage linkage(Declaration declaration) {
            Scope scope = scopes.get(declaration);
            if (declaration instanceof EnumDeclaration && name(declaration).isEmpty()) return Declaration.Linkage.NONE;
            if (!(declaration instanceof CallableDeclaration || declaration instanceof VariableDeclaration
                    || declaration instanceof ClassDeclaration || declaration instanceof EnumDeclaration)) return Declaration.Linkage.NONE;
            if (scope.local) {
                if (!(declaration instanceof FunctionDeclaration || declaration instanceof VariableDeclaration variable && variable.modifiers().contains("extern")))
                    return Declaration.Linkage.NONE;
                for (Match match : lookup(name(declaration), scope, positions.get(declaration) - 1, new HashSet<>())) {
                    Declaration prior = match.declaration();
                    if (prior.getClass() != declaration.getClass()) continue;
                    if (prior instanceof CallableDeclaration callable && declaration instanceof CallableDeclaration next
                            && !signature(callable).equals(signature(next))) continue;
                    Declaration.Linkage previous = linkages.get(prior);
                    if (previous != null && previous != Declaration.Linkage.NONE) return previous;
                }
                return Declaration.Linkage.EXTERNAL;
            }
            if (scope.anonymous) return Declaration.Linkage.INTERNAL;
            List<String> modifiers = modifiers(declaration);
            if (!scope.classScope && modifiers.contains("static")) return Declaration.Linkage.INTERNAL;
            // Later declarations preserve linkage, independently for each overload.
            for (Declaration prior : scope.names.getOrDefault(name(declaration), List.of())) {
                if (prior == declaration) break;
                if (prior.getClass() != declaration.getClass()) continue;
                if (prior instanceof CallableDeclaration callable && declaration instanceof CallableDeclaration next
                        && !signature(callable).equals(signature(next))) continue;
                Declaration.Linkage priorLinkage = linkages.get(prior);
                if (priorLinkage != null && priorLinkage != Declaration.Linkage.NONE) return priorLinkage;
            }
            if (declaration instanceof VariableDeclaration variable && !scope.classScope && !modifiers.contains("extern")
                    && variable.type().map(type -> type.canonicalType() instanceof QualifiedType q && q.isConst()).orElse(false))
                return Declaration.Linkage.INTERNAL;
            return Declaration.Linkage.EXTERNAL;
        }
        String entityKey(Declaration declaration, Declaration.Linkage linkage) {
            Scope scope = scopes.get(declaration);
            if (declaration instanceof EnumDeclaration && name(declaration).isEmpty()) return null;
            String category;
            if (declaration instanceof CallableDeclaration callable) category = "function:" + signature(callable);
            else if (declaration instanceof VariableDeclaration) category = "variable";
            else if (declaration instanceof ClassDeclaration) category = "class";
            else if (declaration instanceof NamespaceDeclaration) category = "namespace";
            else if (declaration instanceof TypedefDeclaration) category = "typedef";
            else if (declaration instanceof EnumDeclaration) category = "enum";
            else return null;
            boolean external = linkage == Declaration.Linkage.EXTERNAL && (!scope.classScope || scope.local)
                    && (declaration instanceof FunctionDeclaration || declaration instanceof VariableDeclaration);
            String qualified = declaration.languageLinkage().equals("C") && external ? name(declaration)
                    : entityName(declaration);
            String owner = external && project.composition().isPresent() ? "build" : "tu" + project.files().indexOf(scope.file);
            return owner + ":" + declaration.languageLinkage() + ":" + qualified + ":" + category;
        }
        void connect(List<Declaration> group) {
            List<Declaration> definitions = group.stream().filter(this::isDefinition).toList();
            boolean badType = group.stream().map(this::declaredType).distinct().count() > 1;
            if (badType) for (Declaration declaration : group) {
                error(declaration, "conflicting-declaration", "Incompatible declarations of " + name(declaration));
            }
            boolean multiple = definitions.size() > 1 && !(group.getFirst() instanceof NamespaceDeclaration)
                    && !(group.getFirst() instanceof TypedefDeclaration)
                    && !permittedRepeatedDefinitions(definitions);
            if (multiple) for (Declaration definition : definitions) {
                conflicting.add(definition); error(definition, "odr-conflict", "Multiple definitions of " + name(definition)
                        + " in one translation unit or selected build composition");
            }
            Declaration previous = null;
            for (Declaration declaration : group) {
                Declaration definition = definitions.size() == 1 ? definitions.getFirst() : null;
                if (definitions.isEmpty() && linkages.get(declaration) == Declaration.Linkage.EXTERNAL) {
                    List<? extends Declaration> alternatives = declaration instanceof CallableDeclaration callable
                            ? callableGroup(callable, scopes.get(declaration).file) : externalVariables(declaration);
                    var targets = alternatives.stream().filter(this::isDefinition).toList();
                    if (targets.size() == 1 && declaredType(targets.getFirst()).equals(declaredType(declaration))) definition = targets.getFirst();
                }
                // Multiple inline header instances retain the definition in their own TU.
                if (definition == null && !multiple) definition = definitions.stream()
                        .filter(candidate -> scopes.get(candidate).file == scopes.get(declaration).file).findFirst().orElse(null);
                editor.declare(declaration, linkages.get(declaration), isDefinition(declaration), previous,
                        definition == declaration ? null : definition);
                previous = declaration;
            }
        }
        boolean permittedRepeatedDefinitions(List<Declaration> definitions) {
            if (definitions.stream().anyMatch(declaration -> !(declaration instanceof CallableDeclaration callable)
                    || !(callable instanceof MethodDeclaration) && !callable.modifiers().contains("inline"))) return false;
            Set<SourceFile> files = Collections.newSetFromMap(new IdentityHashMap<>());
            if (definitions.stream().anyMatch(declaration -> !files.add(scopes.get(declaration).file))) return false;
            // Compare syntax, not token locations, across repeated inline definitions.
            return definitions.stream().map(declaration -> declaration.source(Language.CPP)).distinct().count() == 1;
        }
        String declaredType(Declaration declaration) {
            if (declaration instanceof CallableDeclaration callable)
                return callable.returnType().map(TypeNode::canonicalIdentity).orElse("void")
                        + (callable.languageLinkage().equals("C") ? ":" + parameterSignature(callable) : "");
            if (declaration instanceof VariableDeclaration variable) return variable.type().map(TypeNode::canonicalIdentity).orElse("");
            if (declaration instanceof TypedefDeclaration alias) return alias.type().canonicalIdentity();
            return declaration.getClass().getSimpleName();
        }
        boolean isDefinition(Declaration declaration) {
            if (declaration instanceof CallableDeclaration callable) return callable.body().isPresent();
            if (declaration instanceof VariableDeclaration variable)
                return !(scopes.get(variable).classScope && variable.modifiers().contains("static"))
                        && (variable.initializer().isPresent() || !variable.modifiers().contains("extern"));
            return declaration.isDefinition();
        }
        void resolveCall(CallExpression call) {
            String written = expressionName(call.callee());
            List<Match> visible = written == null ? List.of() : lookup(written, scopes.get(call), positions.get(call), new HashSet<>());
            List<CallableDeclaration> declarations = visible.stream().map(Match::declaration)
                    .filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).toList();
            // Historical Java/class-qualified examples omit a class declaration in the
            // caller file. Preserve that limited fallback, never for unqualified names.
            if (declarations.isEmpty() && visible.isEmpty() && written != null && written.contains("::")) {
                String owner = written.substring(0, written.indexOf("::"));
                if (lookup(owner, scopes.get(call), positions.get(call), new HashSet<>()).isEmpty())
                    declarations = this.declarations.stream().filter(MethodDeclaration.class::isInstance)
                            .map(CallableDeclaration.class::cast).filter(candidate -> qualify(scopes.get(candidate).qualified, candidate.name()).equals(written))
                            .filter(candidate -> differentPhysicalFile(scopes.get(call).file, scopes.get(candidate).file)).toList();
            }
            var candidates = new ArrayList<CallableDeclaration>();
            Set<String> seen = new LinkedHashSet<>();
            boolean odr = false;
            for (CallableDeclaration declaration : declarations) {
                String identity = entityName(declaration) + ":" + signature(declaration)
                        + ":" + declaration.languageLinkage() + ":" + linkages.get(declaration);
                if (!seen.add(identity)) continue;
                List<CallableDeclaration> related = callableGroup(declaration, scopes.get(call).file);
                List<CallableDeclaration> definitions = related.stream().filter(candidate -> candidate.body().isPresent()).toList();
                List<CallableDeclaration> selected = definitions.isEmpty() ? List.of(declaration) : definitions;
                candidates.addAll(selected);
                if (selected.stream().anyMatch(conflicting::contains)) odr = true;
            }
            candidates = new ArrayList<>(new LinkedHashSet<>(candidates));
            CallExpression.Resolution status = candidates.isEmpty() ? CallExpression.Resolution.UNRESOLVED
                    : odr ? CallExpression.Resolution.ODR_CONFLICT
                    : candidates.size() == 1 ? CallExpression.Resolution.RESOLVED : CallExpression.Resolution.AMBIGUOUS;
            editor.resolve(call, candidates, status);
            if (odr) error(call, "odr-conflict", "Multiple definitions in one translation unit or selected build composition");
        }
        List<CallableDeclaration> callableGroup(CallableDeclaration declaration, SourceFile caller) {
            List<CallableDeclaration> local = groups.get(keys.get(declaration)).stream()
                    .filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).toList();
            Scope scope = scopes.get(declaration);
            if (project.composition().isPresent()) {
                var definitions = local.stream().filter(candidate -> candidate.body().isPresent()).map(Declaration.class::cast).toList();
                if (definitions.size() > 1 && permittedRepeatedDefinitions(definitions)) {
                    var own = local.stream().filter(candidate -> candidate.body().isPresent() && scopes.get(candidate).file == caller).toList();
                    return own.isEmpty() ? List.of((CallableDeclaration)definitions.getFirst()) : own;
                }
                return local;
            }
            if (linkages.get(declaration) != Declaration.Linkage.EXTERNAL || scope.classScope
                    || local.stream().anyMatch(candidate -> candidate.body().isPresent())) return local;
            // Unknown compositions expose alternatives as candidates, never an ODR error.
            return declarations.stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast)
                    .filter(candidate -> linkages.get(candidate) == Declaration.Linkage.EXTERNAL)
                    .filter(candidate -> candidate.languageLinkage().equals(declaration.languageLinkage()))
                    .filter(candidate -> signature(candidate).equals(signature(declaration)))
                    .filter(candidate -> declaration.languageLinkage().equals("C") ? candidate.name().equals(declaration.name())
                            : entityName(candidate).equals(entityName(declaration)))
                    .filter(candidate -> scopes.get(candidate).file == caller || differentPhysicalFile(caller, scopes.get(candidate).file)).toList();
        }
        String entityName(Declaration declaration) {
            Scope scope = scopes.get(declaration);
            if (scope.local && (declaration instanceof FunctionDeclaration
                    || declaration instanceof VariableDeclaration variable && variable.modifiers().contains("extern")))
                while (scope.parent != null && (scope.local || scope.classScope)) scope = scope.parent;
            return qualify(scope.qualified, name(declaration));
        }
        List<Declaration> externalVariables(Declaration declaration) {
            if (!(declaration instanceof VariableDeclaration)) return List.of();
            return declarations.stream().filter(VariableDeclaration.class::isInstance)
                    .filter(candidate -> linkages.get(candidate) == Declaration.Linkage.EXTERNAL)
                    .filter(candidate -> candidate.languageLinkage().equals(declaration.languageLinkage()))
                    .filter(candidate -> entityName(candidate).equals(entityName(declaration)))
                    .filter(candidate -> scopes.get(candidate).file == scopes.get(declaration).file
                            || differentPhysicalFile(scopes.get(candidate).file, scopes.get(declaration).file)).toList();
        }
        boolean differentPhysicalFile(SourceFile a, SourceFile b) {
            return !a.range().map(SourceRange::file).orElse(a.path()).equals(b.range().map(SourceRange::file).orElse(b.path()));
        }
        void error(Node node, String code, String message) { editor.diagnose(node, new Diagnostic(code, Diagnostic.Severity.ERROR, message)); }
    }

    private static String signature(CallableDeclaration declaration) {
        return declaration.languageLinkage().equals("C") ? "C" : parameterSignature(declaration);
    }
    private static String parameterSignature(CallableDeclaration declaration) {
        var function = FunctionType.create().setReturnType(NamedType.create().setName("void"))
                .setVariadic(declaration.variadic()).setConst(declaration.isConst()).setVolatile(declaration.isVolatile());
        List<ParameterDeclaration> parameters = declaration.parameters();
        if (declaration.writtenType().map(TypeNode::canonicalType).orElse(null) instanceof FunctionType written) {
            parameters = written.parameters(); function.setVariadic(written.variadic()).setConst(written.isConst()).setVolatile(written.isVolatile());
        }
        parameters.forEach(parameter -> {
            var builder = parameter.edit();
            if (parameter.type().isEmpty()) builder.setType(NamedType.create().setName("<unknown>"));
            function.addParameter(builder);
        });
        return function.build().canonicalIdentity();
    }
    private static String expressionName(Expression expression) {
        if (expression instanceof NameExpression name) return name.name();
        if (expression instanceof ScopeMemberExpression || expression instanceof DotMemberExpression) {
            var member = (MemberExpression)expression;
            String owner = expressionName(member.receiver());
            return owner == null ? null : owner + "::" + member.member();
        }
        return null;
    }
    private static boolean isType(Declaration declaration) {
        return declaration instanceof TypedefDeclaration || declaration instanceof ClassDeclaration || declaration instanceof EnumDeclaration;
    }
    private static String qualify(String owner, String name) { return owner.isEmpty() ? name : owner + "::" + name; }
    private static List<String> modifiers(Declaration declaration) {
        if (declaration instanceof CallableDeclaration callable) return callable.modifiers();
        if (declaration instanceof VariableDeclaration variable) return variable.modifiers();
        if (declaration instanceof ClassDeclaration owner) return owner.modifiers();
        return List.of();
    }
    private static String name(Declaration declaration) {
        if (declaration instanceof CallableDeclaration callable) return callable.name();
        if (declaration instanceof VariableDeclaration variable) return variable.name();
        if (declaration instanceof ParameterDeclaration parameter) return parameter.name();
        if (declaration instanceof ClassDeclaration owner) return owner.name();
        if (declaration instanceof TypedefDeclaration alias) return alias.name();
        if (declaration instanceof NamespaceDeclaration namespace) return namespace.anonymous() ? "(anonymous)" : namespace.name();
        if (declaration instanceof NamespaceAliasDeclaration alias) return alias.name();
        if (declaration instanceof EnumDeclaration enumeration) return enumeration.name();
        if (declaration instanceof EnumeratorDeclaration enumerator) return enumerator.name();
        if (declaration instanceof UsingDeclaration using) {
            String name = using.qualifiedName(); return name.substring(name.lastIndexOf("::") + 2);
        }
        return "";
    }
}
