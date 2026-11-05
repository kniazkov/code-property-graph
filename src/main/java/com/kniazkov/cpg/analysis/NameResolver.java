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
    private record Match(Declaration declaration, List<TypeResolutionStep> steps, boolean ambiguous) {
        Match(Declaration declaration, List<TypeResolutionStep> steps) { this(declaration, steps, false); }
    }

    private static final class Engine {
        final Project project;
        final ProjectEditor editor;
        final Map<Node, Scope> scopes = new IdentityHashMap<>();
        final Map<Node, Integer> positions = new IdentityHashMap<>();
        final Map<Integer, Scope> pointScopes = new HashMap<>();
        final Map<ClassDeclaration, Integer> classEnds = new IdentityHashMap<>();
        final Map<EnumDeclaration, Integer> enumEnds = new IdentityHashMap<>();
        final Map<ParameterDeclaration, Integer> parameterEnds = new IdentityHashMap<>();
        final Map<Scope, CallableDeclaration> callableScopes = new IdentityHashMap<>();
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
            Scope owner = scope;
            if (node instanceof Declaration declaration) {
                if (declaration.isFriend()) while (owner.classScope && owner.parent != null) owner = owner.parent;
                String written = declaration instanceof CallableDeclaration callable ? callable.name()
                        : declaration instanceof VariableDeclaration variable ? variable.name() : "";
                int separator = written.lastIndexOf("::");
                if (separator >= 0) {
                    String qualifier = written.substring(0, separator);
                    Scope found = null;
                    for (Scope candidate = owner; candidate != null && found == null; candidate = candidate.parent) {
                        Scope next = candidate;
                        for (String part : qualifier.split("::")) { if (!part.isEmpty()) next = next == null ? null : next.nested.get(part); }
                        if (next != null) found = next;
                    }
                    if (found != null) owner = found;
                }
            }
            indexInScope(node, owner);
        }

        void indexInScope(Node node, Scope scope) {
            scopes.put(node, scope); pointScopes.put(position, scope); positions.put(node, position++);
            if (node instanceof Declaration declaration) {
                declarations.add(declaration);
                if (!(declaration instanceof CallableDeclaration callable) || callable.kind() == CallableDeclaration.Kind.FUNCTION) scope.add(name(declaration), declaration);
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
                owner.bases().forEach(base -> index(base, scope));
                owner.members().forEach(member -> index(member, nested));
                classEnds.put(owner, position);
            } else if (node instanceof CallableDeclaration callable) {
                Scope nested = new Scope(scope, scope.file, qualify(scope.qualified, callable.name()), true, scope.anonymous, false);
                functionScopes.add(nested); callableScopes.put(nested, callable);
                callable.writtenType().ifPresent(type -> index(type, scope));
                callable.returnType().ifPresent(type -> index(type, scope));
                callable.parameters().forEach(parameter -> index(parameter, nested));
                positions.put(callable, position++); // A function name follows its complete declarator.
                callable.initializers().forEach(initializer -> index(initializer, nested));
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
                variable.bitWidth().ifPresent(value -> index(value, scope));
                variable.initializer().ifPresent(value -> index(value, scope));
            } else if (node instanceof ParameterDeclaration parameter) {
                parameter.type().ifPresent(type -> index(type, scope));
                positions.put(parameter, position++);
                for (Node child : parameter.children()) if (!(child instanceof TypeNode)) index(child, scope);
                parameterEnds.put(parameter, position);
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
                @Override public void visit(MemberInitializer initializer) {
                    Scope scope = scopes.get(initializer);
                    while (scope != null && !scope.classScope) scope = scope.parent;
                    Scope context = scope == null ? null : new Scope(scope, scope.file, scope.qualified + "#initializer-id", true, scope.anonymous, false);
                    Match match = context == null ? null : unique(lookup(initializer.name(), context, positions.get(initializer), new HashSet<>()));
                    editor.bind(initializer, match == null ? null : match.declaration());
                }
                @Override public void visit(MemberExpression member) {
                    String written = expressionName(member);
                    Match match = member instanceof ScopeMemberExpression
                            ? isObjectQualified(member) ? unique(qualifiedObjectMatches(member))
                                    : written == null ? null : unique(lookup(written, scopes.get(member), positions.get(member), new HashSet<>()))
                            : memberMatch(member);
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
                        steps.addAll(target.steps()); result.add(new Match(target.declaration(), List.copyOf(steps), target.ambiguous()));
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
                        steps.addAll(target.steps()); result.add(new Match(target.declaration(), List.copyOf(steps), target.ambiguous()));
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
        ClassDeclaration scopeClass(Scope scope) {
            for (var entry : introduced.entrySet())
                if (entry.getValue() == scope && entry.getKey() instanceof ClassDeclaration cls && cls.isDefinition()) return cls;
            return null;
        }
        ClassDeclaration resolvedBase(BaseSpecifier base) {
            TypeNode type = TypeSemantics.unqualified(base.type());
            if (type instanceof NamedType named) {
                Match binding = typeBindings.get(named);
                Declaration target = binding == null ? named.declaration().orElse(null) : binding.declaration();
                if (target instanceof ClassDeclaration cls) {
                    ClassDeclaration definition = scopeClass(introduced.get(cls));
                    return definition == null ? cls : definition;
                }
            }
            return null;
        }
        private record InheritedMatch(Match match, String subobject) {}
        List<Match> direct(String name, Scope scope, int point, Set<Declaration> active, boolean tagsOnly, boolean completeClassContext) {
            List<Match> own = ownDirect(name, scope, point, active, tagsOnly, completeClassContext);
            ClassDeclaration cls = scopeClass(scope);
            if (!own.isEmpty() || cls == null) return own;
            var inherited = new ArrayList<InheritedMatch>();
            inherited(name, cls, point, active, tagsOnly, completeClassContext, "root", new HashSet<>(), inherited);
            // A declaration in a more-derived subobject dominates a declaration
            // reached through its shared virtual base (C++03 member lookup).
            List<InheritedMatch> all = List.copyOf(inherited);
            inherited.removeIf(candidate -> all.stream().anyMatch(other -> {
                ClassDeclaration more = scopeClass(scopes.get(other.match().declaration()));
                return more != null && !other.subobject().equals(candidate.subobject())
                        && containsSubobject(more, other.subobject(), candidate.subobject(), new HashSet<>());
            }));
            Set<String> subobjects = new HashSet<>();
            for (InheritedMatch match : inherited) {
                Declaration declaration = match.match().declaration();
                // Static members and types do not require a unique object subobject.
                if (!modifiers(declaration).contains("static") && !isType(declaration)) subobjects.add(match.subobject());
            }
            boolean ambiguous = subobjects.size() > 1 || inherited.stream()
                    .map(m -> scopes.get(m.match().declaration())).distinct().count() > 1;
            return distinct(inherited.stream().map(m -> new Match(m.match().declaration(), m.match().steps(), ambiguous)).toList());
        }
        // Compare actual subobject paths, not just class ancestry. A nonvirtual base
        // nested inside a shared virtual base is also shared; an identically typed
        // nonvirtual base on another branch is a different object.
        boolean containsSubobject(ClassDeclaration cls, String path, String target, Set<ClassDeclaration> visiting) {
            if (path.equals(target)) return true;
            if (!visiting.add(cls)) return false;
            int index = 0;
            for (BaseSpecifier base : cls.bases()) {
                ClassDeclaration parent = resolvedBase(base);
                String next = base.isVirtual() ? "virtual:" + System.identityHashCode(parent) : path + "/" + index;
                index++;
                if (parent != null && containsSubobject(parent, next, target, visiting)) {
                    visiting.remove(cls); return true;
                }
            }
            visiting.remove(cls);
            return false;
        }
        void inherited(String name, ClassDeclaration cls, int point, Set<Declaration> active, boolean tagsOnly,
                boolean completeContext, String path, Set<ClassDeclaration> visiting, List<InheritedMatch> result) {
            if (!visiting.add(cls)) return;
            int index = 0;
            for (BaseSpecifier base : cls.bases()) {
                ClassDeclaration parent = resolvedBase(base);
                String next = base.isVirtual() ? "virtual:" + System.identityHashCode(parent) : path + "/" + index;
                index++;
                if (parent == null || introduced.get(parent) == null) continue;
                List<Match> own = ownDirect(name, introduced.get(parent), point, active, tagsOnly, completeContext);
                if (own.isEmpty()) inherited(name, parent, point, active, tagsOnly, completeContext, next, visiting, result);
                else for (Match match : own) result.add(new InheritedMatch(match, next));
            }
            visiting.remove(cls);
        }
        List<Match> ownDirect(String name, Scope scope, int point, Set<Declaration> active, boolean tagsOnly, boolean completeClassContext) {
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
            if (matches.stream().anyMatch(Match::ambiguous)) return null;
            if (matches.size() == 1) return matches.getFirst();
            if (matches.size() > 1 && matches.stream().allMatch(m -> m.declaration() instanceof CallableDeclaration)
                    && matches.stream().map(m -> signature((CallableDeclaration)m.declaration())).distinct().count() == 1
                    && matches.stream().allMatch(m -> scopes.get(m.declaration()) == scopes.get(matches.getFirst().declaration())))
                return matches.getLast();
            return null;
        }

        boolean isObjectQualified(MemberExpression member) {
            return member instanceof ScopeMemberExpression && member.receiver() instanceof MemberExpression qualifier
                    && !(qualifier instanceof ScopeMemberExpression);
        }
        List<Match> qualifiedObjectMatches(MemberExpression member) {
            MemberExpression qualifier = (MemberExpression)member.receiver();
            ClassDeclaration receiver = receiverClass(qualifier.receiver(), qualifier instanceof PointerMemberExpression);
            Match match = unique(lookup(qualifier.member(), scopes.get(member), positions.get(member), new HashSet<>()));
            if (receiver == null || match == null || !(match.declaration() instanceof ClassDeclaration owner)
                    || receiver != owner && !inheritanceCycle(receiver, owner, new HashSet<>())) return List.of();
            Scope scope = introduced.get(owner);
            return scope == null ? List.of() : direct(member.member(), scope, Integer.MAX_VALUE, new HashSet<>(), false, true);
        }
        Match memberMatch(MemberExpression member) { return unique(isObjectQualified(member) ? qualifiedObjectMatches(member) : memberMatches(member)); }
        List<Match> memberMatches(MemberExpression member) {
            ClassDeclaration owner = receiverClass(member.receiver(), member instanceof PointerMemberExpression);
            if (owner == null) return List.of();
            Scope scope = introduced.get(owner);
            if (scope == null && owner.definition().orElse(null) instanceof ClassDeclaration definition) scope = introduced.get(definition);
            return scope == null ? List.of() : direct(member.member(), scope, Integer.MAX_VALUE, new HashSet<>(), false, true);
        }
        ClassDeclaration receiverClass(Expression receiver, boolean pointer) {
            if (receiver instanceof ConversionExpression conversion) return receiverClass(conversion.operand(), pointer);
            if (receiver instanceof NameExpression name && name.name().equals("this")) {
                Scope scope = scopes.get(receiver);
                while (scope != null && !scope.classScope) scope = scope.parent;
                for (var entry : introduced.entrySet()) if (entry.getValue() == scope && entry.getKey() instanceof ClassDeclaration cls && cls.isDefinition()) return cls;
                return null;
            }
            Declaration target = null;
            if (receiver instanceof NameExpression name) {
                Match match = unique(lookup(name.name(), scopes.get(name), positions.get(name), new HashSet<>()));
                if (match != null) target = match.declaration();
            } else if (receiver instanceof MemberExpression member) {
                Match match = memberMatch(member); if (match != null) target = match.declaration();
            }
            if (receiver instanceof CallExpression call) {
                Match function = call.callee() instanceof NameExpression name
                        ? unique(lookup(name.name(), scopes.get(name), positions.get(name), new HashSet<>()))
                        : call.callee() instanceof MemberExpression member ? memberMatch(member) : null;
                if (function != null) target = function.declaration();
            }
            TypeNode type = target instanceof VariableDeclaration variable ? variable.type().orElse(null)
                    : target instanceof ParameterDeclaration parameter ? parameter.type().orElse(null)
                    : target instanceof CallableDeclaration callable ? callable.returnType().orElse(null)
                    : receiver instanceof InitializationExpression initialization ? initialization.type().orElse(null) : null;
            if (type == null) return null;
            type = TypeSemantics.unqualified(type);
            if (type instanceof ReferenceType reference) type = TypeSemantics.unqualified(reference.referredType());
            if (pointer && type instanceof PointerType ptr) type = TypeSemantics.unqualified(ptr.pointee());
            if (type instanceof NamedType name) {
                Match match = typeBindings.get(name);
                Declaration declaration = match == null ? name.declaration().orElse(null) : match.declaration();
                if (declaration instanceof ClassDeclaration cls) return cls;
            }
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
            validateClasses();
            validateInheritance();
            for (Declaration declaration : declarations)
                if (declaration instanceof CallableDeclaration callable && scopes.get(callable).file.language() == Language.CPP)
                    validateDefaults(callable);
            project.walk(new NodeVisitor() {
                @Override public void visit(CallExpression call) { resolveCall(call); }
                @Override public void visit(NameExpression name) { diagnoseAmbiguity(name, name.name(), false); }
                @Override public void visit(MemberExpression member) {
                    String name = expressionName(member); diagnoseAmbiguity(member, name == null ? member.member() : name, false);
                }
                @Override public void visit(NamedType type) { diagnoseAmbiguity(type, type.name(), type.elaboratedKeyword().isPresent()); }
                @Override public void visit(NewExpression expression) { validateNew(expression); }
                @Override public void visit(DeleteExpression expression) { validateDelete(expression); }
                @Override public void visit(TypeidExpression expression) {
                    var profile = TypeSemantics.profile(scopes.get(expression).file);
                    expression.type().or(() -> expression.operand().flatMap(operand -> TypeSemantics.expressionType(operand, profile)))
                            .map(type -> TypeSemantics.unqualified(TypeSemantics.withoutReference(type)))
                            .filter(type -> type instanceof NamedType named && named.declaration().orElse(null) instanceof ClassDeclaration)
                            .filter(type -> !completeType(type, positions.get(expression), new HashSet<>()))
                            .ifPresent(type -> error(expression, "invalid-typeid", "typeid requires a complete class type"));
                }
                @Override public void visit(ConversionExpression expression) {
                    if (!expression.isExplicit()) return;
                    TypeNode target = TypeSemantics.unqualified(expression.targetType());
                    if (target instanceof ArrayType || target instanceof FunctionType) error(expression, "invalid-cast", "Cast target cannot be an array or function type");
                    if (expression.kind() == ConversionExpression.Kind.CONST_CAST
                            && !(target instanceof PointerType || target instanceof ReferenceType || target instanceof MemberPointerType))
                        error(expression, "invalid-cast", "const_cast requires a pointer, reference or member-pointer target");
                    if (expression.kind() == ConversionExpression.Kind.DYNAMIC_CAST && !(target instanceof PointerType || target instanceof ReferenceType))
                        error(expression, "invalid-cast", "dynamic_cast requires a pointer or reference target");
                }
                @Override public void visit(InitializationExpression expression) {
                    if (expression.form() != InitializationExpression.Form.TEMPORARY) return;
                    expression.type().map(TypeSemantics::unqualified).ifPresent(type -> {
                        if (type instanceof ArrayType || type instanceof FunctionType) error(expression, "invalid-cast", "Functional conversion cannot target an array or function type");
                        if (type instanceof NamedType named && named.fundamentalKind().isPresent() && expression.arguments().size() > 1)
                            error(expression, "invalid-cast", "Scalar functional conversion requires at most one argument");
                    });
                }
                @Override public void visit(SizeofExpression expression) {
                    Scope scope = scopes.get(expression);
                    expression.type().or(() -> expression.operand().flatMap(operand -> TypeSemantics.expressionType(operand,
                                    TypeSemantics.profile(scope.file)))).ifPresent(type -> {
                        TypeNode plain = TypeSemantics.unqualified(TypeSemantics.withoutReference(type));
                        if (plain instanceof FunctionType || plain instanceof NamedType named && named.fundamentalKind().orElse(null) == FundamentalKind.VOID)
                            error(expression, "invalid-sizeof", "sizeof requires an object type, not void or a function");
                        else if (!completeType(type, positions.get(expression), new HashSet<>()))
                            error(expression, "incomplete-type", "sizeof requires a complete type at this source position");
                    });
                    expression.operand().ifPresent(operand -> {
                        Expression written = operand;
                        while (written instanceof ConversionExpression conversion && !conversion.isExplicit()) written = conversion.operand();
                        Declaration target = written instanceof NameExpression name ? name.target().orElse(null)
                                : written instanceof MemberExpression member ? member.target().orElse(null) : null;
                        if (target instanceof VariableDeclaration variable && variable.bitWidth().isPresent()) error(expression, "invalid-sizeof", "sizeof cannot be applied to a bit-field");
                    });
                }
            });
            return editor.build();
        }
        void validateNew(NewExpression expression) {
            TypeNode type = expression.type().canonicalType();
            var profile = TypeSemantics.profile(scopes.get(expression).file);
            boolean first = true;
            while (TypeSemantics.unqualified(type) instanceof ArrayType array) {
                if (array.bound().isEmpty()) error(expression, "invalid-new", "Every allocated array dimension requires a bound");
                else {
                    TypeSemantics.expressionType(array.bound().orElseThrow(), profile)
                            .map(boundType -> TypeSemantics.unqualified(TypeSemantics.withoutReference(boundType)))
                            .ifPresent(boundType -> {
                                if (boundType instanceof PointerType || boundType instanceof MemberPointerType
                                        || boundType instanceof ArrayType || boundType instanceof FunctionType
                                        || boundType instanceof NamedType named && named.fundamentalKind()
                                            .filter(kind -> Set.of(FundamentalKind.VOID, FundamentalKind.FLOAT,
                                                    FundamentalKind.DOUBLE, FundamentalKind.LONG_DOUBLE).contains(kind)).isPresent())
                                    error(expression, "invalid-new", "Allocated array bound requires an integral or enumeration value");
                            });
                    var value = ConstantEvaluator.evaluate(array.bound().orElseThrow(), profile);
                    if ((!first || expression.isParenthesizedType()) && value.isEmpty()
                            && runtimeBound(array.bound().orElseThrow(), profile, Collections.newSetFromMap(new IdentityHashMap<>())))
                        error(expression, "invalid-new", "Only the first unparenthesized new-array bound may be dynamic");
                    final boolean outer = first;
                    value.ifPresent(bound -> { if (bound.number().signum() < 0 || (!outer || expression.isParenthesizedType()) && bound.number().signum() == 0) error(expression, "invalid-new", "Invalid allocated array bound"); });
                }
                first = false; type = array.elementType().canonicalType();
            }
            TypeNode element = TypeSemantics.unqualified(type);
            if (element instanceof ReferenceType || element instanceof FunctionType || element instanceof NamedType named && named.fundamentalKind().orElse(null) == FundamentalKind.VOID)
                error(expression, "invalid-new", "new requires a non-reference object type");
            else if (!completeType(element, positions.get(expression), new HashSet<>())) error(expression, "incomplete-type", "new requires a complete allocated type");
            if (!first && !expression.initializerArguments().isEmpty()) error(expression, "invalid-new", "C++03 array new permits only an empty initializer");
            if (!expression.hasInitializer() && type instanceof QualifiedType qualified && qualified.isConst()
                    && element instanceof NamedType named && named.fundamentalKind().isPresent())
                error(expression, "invalid-new", "Allocation of a const fundamental type requires an initializer");
            if (element instanceof NamedType named && named.fundamentalKind().isPresent() && expression.initializerArguments().size() > 1)
                error(expression, "invalid-new", "Scalar new requires at most one initializer argument");
        }
        /** Failure to evaluate is not proof of a runtime value: sizeof can be symbolic
         * when the target profile intentionally leaves aggregate representation unknown. */
        boolean runtimeBound(Expression expression, com.kniazkov.cpg.build.PlatformProfile profile, Set<Expression> active) {
            if (!active.add(expression)) return false;
            try {
                if (ConstantEvaluator.evaluate(expression, profile).isPresent() || expression instanceof SizeofExpression) return false;
                if (expression instanceof NameExpression || expression instanceof MemberExpression) {
                    Declaration target = expression instanceof NameExpression name ? name.target().orElse(null)
                            : ((MemberExpression) expression).target().orElse(null);
                    if (target instanceof ParameterDeclaration) return true;
                    if (target instanceof VariableDeclaration variable) {
                        TypeNode type = variable.type().map(TypeNode::canonicalType).orElse(null);
                        if (type == null) return false;
                        if (!(type instanceof QualifiedType qualified) || !qualified.isConst() || qualified.isVolatile()) return true;
                        return variable.initializer().map(value -> runtimeBound(value, profile, active)).orElse(false);
                    }
                    return false;
                }
                if (expression instanceof CallExpression || expression instanceof NewExpression || expression instanceof DeleteExpression
                        || expression instanceof AssignmentExpression || expression instanceof CompoundAssignmentExpression
                        || expression instanceof PrefixIncrementExpression || expression instanceof PrefixDecrementExpression
                        || expression instanceof PostfixIncrementExpression || expression instanceof PostfixDecrementExpression) return true;
                if (expression instanceof ConditionalExpression conditional) {
                    var condition = ConstantEvaluator.evaluate(conditional.condition(), profile);
                    if (condition.isPresent()) return runtimeBound(condition.orElseThrow().number().signum() == 0
                            ? conditional.elseExpression() : conditional.thenExpression(), profile, active);
                }
                for (Node child : expression.children()) if (child instanceof Expression operand && runtimeBound(operand, profile, active)) return true;
                return false;
            } finally { active.remove(expression); }
        }
        void validateDelete(DeleteExpression expression) {
            var profile = TypeSemantics.profile(scopes.get(expression).file);
            TypeSemantics.expressionType(expression.operand(), profile).map(type -> TypeSemantics.unqualified(TypeSemantics.withoutReference(type))).ifPresent(type -> {
                if (type instanceof PointerType pointer) {
                    TypeNode pointee = TypeSemantics.unqualified(pointer.pointee());
                    if (pointee instanceof FunctionType || pointee instanceof NamedType named && named.fundamentalKind().orElse(null) == FundamentalKind.VOID)
                        error(expression, "invalid-delete", "delete requires a pointer to an object type");
                } else if (type instanceof NamedType named && named.fundamentalKind().isPresent() || type instanceof MemberPointerType || type instanceof FunctionType)
                    error(expression, "invalid-delete", "delete requires a pointer or a class convertible to a pointer");
            });
        }
        void validateClasses() {
            for (Declaration declaration : declarations) {
                if (declaration instanceof VariableDeclaration field && field.bitWidth().isPresent()) {
                    if (field.modifiers().contains("static")) error(field, "invalid-bit-field", "A bit-field cannot be static");
                    var profile = TypeSemantics.profile(scopes.get(field).file);
                    ConstantEvaluator.evaluate(field.bitWidth().orElseThrow(), profile).ifPresent(width -> {
                        if (width.number().signum() < 0 || width.number().signum() == 0 && !field.name().isEmpty())
                            error(field, "invalid-bit-field", "Bit-field width must be nonnegative; zero width requires an unnamed field");
                    });
                }
                if (!(declaration instanceof ClassDeclaration owner) || !owner.isDefinition()) continue;
                for (var member : owner.members()) if (member instanceof CallableDeclaration constructor && constructor.kind() == CallableDeclaration.Kind.CONSTRUCTOR) {
                    var seen = new HashSet<Object>();
                    for (var initializer : constructor.initializers()) {
                        Declaration target = initializer.target().orElse(null);
                        ClassDeclaration baseClass = ClassSemantics.initializedBase(initializer).orElse(null);
                        boolean valid = ClassSemantics.fields(owner).contains(target)
                                || owner.bases().stream().anyMatch(base -> ClassSemantics.objectClass(base.type()).filter(baseType -> ClassSemantics.sameClass(baseType, baseClass)).isPresent());
                        if (ClassSemantics.hasCompleteHierarchy(owner)) {
                            boolean virtualBase = ClassSemantics.virtualBases(owner).stream().anyMatch(base ->
                                    ClassSemantics.objectClass(base.type()).filter(baseType -> ClassSemantics.sameClass(baseType, baseClass)).isPresent());
                            boolean directNonvirtualBase = owner.bases().stream().filter(base -> !base.isVirtual()).anyMatch(base ->
                                    ClassSemantics.objectClass(base.type()).filter(baseType -> ClassSemantics.sameClass(baseType, baseClass)).isPresent());
                            if (!valid && !virtualBase || virtualBase && directNonvirtualBase)
                                error(initializer, "invalid-member-initializer", "Initializer must name an unambiguous non-static field, direct base or virtual base: " + initializer.name());
                        }
                        Object identity = baseClass != null ? baseClass.typeIdentity().orElse(baseClass.name()) : target != null ? target : initializer.name();
                        if (!seen.add(identity)) error(initializer, "duplicate-member-initializer", "Subobject initialized more than once: " + initializer.name());
                    }
                    if (owner.keyword().equals("union") && constructor.initializers().size() > 1)
                        error(constructor, "invalid-union-initialization", "Only one union member may be initialized");
                }
            }
        }
        boolean inheritanceCycle(ClassDeclaration current, ClassDeclaration target, Set<ClassDeclaration> seen) {
            if (current == target) return true;
            if (!seen.add(current)) return false;
            for (BaseSpecifier base : current.bases()) {
                ClassDeclaration next = resolvedBase(base);
                if (next != null && inheritanceCycle(next, target, seen)) return true;
            }
            return false;
        }
        void validateInheritance() {
            for (Declaration declaration : declarations) {
                if (declaration instanceof CallableDeclaration method && method.isPureVirtual()
                        && (!InheritanceSemantics.isVirtual(project, method) || method.modifiers().contains("static")))
                    error(method, "invalid-pure-virtual", "A pure specifier requires a virtual non-static member function");
                if (!(declaration instanceof ClassDeclaration cls)) continue;
                if (!VirtualInheritanceValidation.conflictingMethods(project, cls).isEmpty())
                    error(cls, "multiple-final-overriders", "A virtual function has multiple final overriders in " + cls.name());
                for (BaseSpecifier base : cls.bases()) {
                    ClassDeclaration parent = resolvedBase(base);
                    if (parent == null) {
                        TypeNode type = TypeSemantics.unqualified(base.type());
                        if (type instanceof NamedType named && (named.fundamentalKind().isPresent() || named.declaration().isPresent()))
                            error(base, "invalid-base", "A base specifier must name a class");
                    } else if (inheritanceCycle(parent, cls, new HashSet<>()))
                        error(base, "inheritance-cycle", "Inheritance must not contain a cycle");
                    else if (parent.keyword().equals("union") || cls.keyword().equals("union"))
                        error(base, "invalid-base", "A union cannot participate in inheritance");
                }
            }
        }
        void diagnoseAmbiguity(Node node, String name, boolean tagsOnly) {
            var matches = node instanceof MemberExpression member && !(member instanceof ScopeMemberExpression)
                    ? memberMatches(member) : lookup(name, scopes.get(node), positions.get(node), new HashSet<>(), tagsOnly);
            if (matches.stream().anyMatch(Match::ambiguous) || matches.size() > 1 && !matches.stream().allMatch(match -> match.declaration() instanceof CallableDeclaration))
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
            if ((declaration instanceof EnumDeclaration || declaration instanceof VariableDeclaration) && name(declaration).isEmpty()) return null;
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
        List<CallExpression.DefaultArgument> applicableDefaults(CallableDeclaration declaration, int point) {
            var result = new LinkedHashMap<Integer, CallExpression.DefaultArgument>();
            Scope scope = scopes.get(declaration);
            for (Declaration candidate : scope.names.getOrDefault(name(declaration), List.of())) {
                if (!(candidate instanceof CallableDeclaration callable) || positions.get(candidate) > point
                        || !parameterSignature(callable).equals(parameterSignature(declaration))) continue;
                for (int i = 0; i < callable.parameters().size(); i++) {
                    ParameterDeclaration parameter = callable.parameters().get(i);
                    if (parameter.defaultValue().isPresent() && parameterEnds.get(parameter) <= point)
                        result.putIfAbsent(i, new CallExpression.DefaultArgument(i, callable, parameter));
                }
            }
            return result.values().stream().sorted(Comparator.comparingInt(CallExpression.DefaultArgument::parameterIndex)).toList();
        }
        void validateDefaults(CallableDeclaration callable) {
            var prior = applicableDefaults(callable, positions.get(callable) - 1);
            Set<Integer> available = new HashSet<>();
            prior.forEach(argument -> available.add(argument.parameterIndex()));
            boolean defaultSeen = false;
            for (int i = 0; i < callable.parameters().size(); i++) {
                ParameterDeclaration parameter = callable.parameters().get(i);
                if (parameter.defaultValue().isPresent()) {
                    if (!available.add(i)) error(parameter, "duplicate-default-argument", "Default argument already specified in this scope");
                    validateDefaultExpression(parameter.defaultValue().orElseThrow());
                }
                if (available.contains(i)) defaultSeen = true;
                else if (defaultSeen) error(parameter, "missing-default-argument", "A parameter after a default argument requires a default argument");
            }
        }
        void validateDefaultExpression(Node expression) {
            // N1638 [dcl.fct.default]/9 forbids parameter names even in sizeof.
            if (expression instanceof NameExpression name) {
                name.target().filter(target -> target instanceof ParameterDeclaration
                        || target instanceof VariableDeclaration && scopes.get(target).local)
                        .ifPresent(target -> error(name, "invalid-default-argument", "A C++03 default argument cannot use a parameter or local variable"));
            }
            expression.children().forEach(this::validateDefaultExpression);
        }
        void resolveDefaults(CallExpression call, List<Match> visible) {
            editor.defaultArguments(call, List.of());
            if (scopes.get(call).file.language() != Language.CPP) return;
            Match unique = unique(visible);
            if (unique == null || !(unique.declaration() instanceof CallableDeclaration declaration)) {
                // Pointer calls have a type but no statically selected implementation or defaults.
                TypeSemantics.callType(call, TypeSemantics.profile(scopes.get(call).file)).ifPresent(type ->
                        checkArity(call, type.parameters().size(), type.variadic(), List.of()));
                return;
            }
            var defaults = applicableDefaults(declaration, positions.get(call)).stream()
                    .filter(argument -> argument.parameterIndex() >= call.arguments().size()).toList();
            editor.defaultArguments(call, defaults);
            checkArity(call, declaration.parameters().size(), declaration.variadic(), defaults);
        }
        void checkArity(CallExpression call, int count, boolean variadic, List<CallExpression.DefaultArgument> defaults) {
            if (!variadic && call.arguments().size() > count)
                error(call, "excess-call-arguments", "Too many arguments for the uniquely known function type");
            Set<Integer> supplied = new HashSet<>();
            defaults.forEach(argument -> supplied.add(argument.parameterIndex()));
            for (int i = call.arguments().size(); i < count; i++) if (!supplied.contains(i)) {
                error(call, "missing-call-argument", "A required argument is missing"); break;
            }
        }
        void resolveCall(CallExpression call) {
            String written = expressionName(call.callee());
            List<Match> visible = call.callee() instanceof MemberExpression qualified && isObjectQualified(qualified)
                    ? qualifiedObjectMatches(qualified)
                    : call.callee() instanceof MemberExpression member && !(member instanceof ScopeMemberExpression)
                    ? memberMatches(member)
                    : written == null ? List.of() : lookup(written, scopes.get(call), positions.get(call), new HashSet<>());
            List<CallableDeclaration> declarations = visible.stream().map(Match::declaration)
                    .filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).toList();
            // Historical Java/class-qualified examples omit a class declaration in the
            // caller file. Preserve that limited fallback, never for unqualified names.
            if (declarations.isEmpty() && visible.isEmpty() && written != null && written.contains("::")
                    && (!(call.callee() instanceof MemberExpression) || call.callee() instanceof ScopeMemberExpression)) {
                String owner = written.substring(0, written.indexOf("::"));
                if (lookup(owner, scopes.get(call), positions.get(call), new HashSet<>()).isEmpty())
                    declarations = this.declarations.stream().filter(MethodDeclaration.class::isInstance)
                            .map(CallableDeclaration.class::cast).filter(candidate -> qualify(scopes.get(candidate).qualified, candidate.name()).equals(written))
                            .filter(candidate -> differentPhysicalFile(scopes.get(call).file, scopes.get(candidate).file)).toList();
            }
            if (declarations.size() > 1 && visible.stream().noneMatch(Match::ambiguous)) {
                List<CallableDeclaration> viable = declarations.stream().filter(declaration -> {
                    int count = call.arguments().size();
                    if (!declaration.variadic() && count > declaration.parameters().size()) return false;
                    Set<Integer> defaults = new HashSet<>();
                    applicableDefaults(declaration, positions.get(call)).forEach(argument -> defaults.add(argument.parameterIndex()));
                    for (int i = count; i < declaration.parameters().size(); i++) if (!defaults.contains(i)) return false;
                    return true;
                }).toList();
                if (!viable.isEmpty()) {
                    declarations = viable;
                    visible = visible.stream().filter(match -> viable.contains(match.declaration())).toList();
                }
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
                    : visible.stream().anyMatch(Match::ambiguous) ? CallExpression.Resolution.AMBIGUOUS
                    : candidates.size() == 1 ? CallExpression.Resolution.RESOLVED : CallExpression.Resolution.AMBIGUOUS;
            editor.resolve(call, candidates, status);
            resolveDefaults(call, visible);
            Match staticMatch = unique(visible);
            if (status == CallExpression.Resolution.RESOLVED && staticMatch != null
                    && staticMatch.declaration() instanceof CallableDeclaration selected
                    && !(call.callee() instanceof ScopeMemberExpression)
                    && InheritanceSemantics.isVirtual(project, selected)) {
                ClassDeclaration receiver = call.callee() instanceof MemberExpression member
                        ? receiverClass(member.receiver(), member instanceof PointerMemberExpression) : scopeClass(scopes.get(selected));
                List<CallableDeclaration> virtualCandidates = InheritanceSemantics.possibleTargets(project, selected, receiver);
                CallableDeclaration enclosing = null;
                for (Scope current = scopes.get(call); current != null && enclosing == null; current = current.parent)
                    enclosing = callableScopes.get(current);
                boolean thisReceiver = call.callee() instanceof NameExpression
                        || call.callee() instanceof MemberExpression member && member.receiver() instanceof NameExpression name && name.name().equals("this");
                if (thisReceiver && enclosing != null && enclosing.kind() != CallableDeclaration.Kind.FUNCTION) {
                    ClassDeclaration constructing = scopeClass(scopes.get(enclosing));
                    if (constructing != null) {
                        List<CallableDeclaration> allowed = virtualCandidates.stream().filter(candidate -> {
                            ClassDeclaration owner = InheritanceSemantics.owningClass(project, candidate).orElse(null);
                            return owner != null && (owner == constructing || InheritanceSemantics.derivesFrom(constructing, owner));
                        }).toList();
                        virtualCandidates = allowed.stream().filter(candidate -> allowed.stream().noneMatch(other -> other != candidate
                                && InheritanceSemantics.overrides(project, other).contains(candidate))).toList();
                    }
                }
                var implementations = new LinkedHashSet<CallableDeclaration>();
                for (CallableDeclaration candidate : virtualCandidates) {
                    List<CallableDeclaration> definitions = callableGroup(candidate, scopes.get(call).file).stream()
                            .filter(implementation -> implementation.body().isPresent()).toList();
                    implementations.addAll(definitions.isEmpty() ? List.of(candidate) : definitions);
                }
                editor.virtualTargets(call, List.copyOf(implementations), false);
            }

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
        List<ParameterDeclaration> parameters = declaration.parameters();
        boolean variadic = declaration.variadic(), constant = declaration.isConst(), vol = declaration.isVolatile();
        if (declaration.writtenType().map(TypeNode::canonicalType).orElse(null) instanceof FunctionType written) {
            parameters = written.parameters(); variadic = written.variadic(); constant = written.isConst(); vol = written.isVolatile();
        }
        return "function(" + parameters.stream().map(parameter -> parameter.type().map(TypeIdentity::parameterIdentity).orElse("named(<unknown>)"))
                .collect(java.util.stream.Collectors.joining(",")) + (variadic ? ",..." : "") + ")->void"
                + (constant ? " const" : "") + (vol ? " volatile" : "");
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
    private static String unqualified(String name) { int at = name.lastIndexOf("::"); return at < 0 ? name : name.substring(at + 2); }
    private static String name(Declaration declaration) {
        if (declaration instanceof CallableDeclaration callable) return unqualified(callable.name());
        if (declaration instanceof VariableDeclaration variable) return unqualified(variable.name());
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
