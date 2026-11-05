/* Structural C++03 class-template deduction and specialization selection. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import java.util.*;

/** Selects class specializations by bidirectional deduction, never by a specificity score. */
public final class ClassTemplateSemantics {
    private ClassTemplateSemantics() {}
    private record Located(ClassDeclaration declaration, SourceFile file, String scope, int position) {}
    private record Use(Node node, SourceFile file, String scope, String name, List<TemplateArgument> arguments, int position) {}
    private record Match(ClassDeclaration declaration, Map<TemplateParameterDeclaration, TemplateArgument> bindings) {}

    public static Project analyze(Project project) { return analyze(project, false); }
    /** Recomputes persistent class diagnostics after the binding pipeline. */
    public static Project diagnose(Project project) { return analyze(project, true); }
    private static Project analyze(Project project, boolean diagnosticsOnly) {
        var declarations = new ArrayList<Located>();
        var uses = new ArrayList<Use>();
        var unusedMethods = Collections.newSetFromMap(new IdentityHashMap<CallableDeclaration, Boolean>());
        project.classTemplateInstances().forEach(instance -> instance.declaration().walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration method) { unusedMethods.add(method); }
        }));
        unusedMethods.removeAll(demandedMethods(project).keySet());
        for (SourceFile file : project.librarySources()) collect(file, file, "", false, declarations, new ArrayList<>(), new int[]{0}, unusedMethods);
        for (SourceFile file : project.files()) collect(file, file, "", false, declarations, uses, new int[]{0}, unusedMethods);
        // Dependent types become concrete only inside their own instantiated body.
        for (ClassTemplateInstance instance : project.classTemplateInstances())
            collect(instance.declaration(), instance.source(), scopeOf(instance.origin(), declarations), false, declarations, uses, new int[]{Integer.MAX_VALUE / 2}, unusedMethods);
        for (TemplateInstance instance : project.templateInstances())
            collect(instance.declaration(), instance.source(), "", false, declarations, uses, new int[]{Integer.MAX_VALUE / 2}, unusedMethods);
        var requests = new ArrayList<ClassTemplateInstances.Request>();
        var failures = new IdentityHashMap<Node, Diagnostic>();
        for (Located candidate : declarations) if (candidate.declaration().templateKind() == TemplateKind.PARTIAL_SPECIALIZATION) {
            ClassDeclaration partial = candidate.declaration();
            var parameters = partial.templateParameters();
            var direct = new ArrayList<TemplateParameterDeclaration>();
            for (TemplateArgument argument : partial.templateArguments()) {
                Node value = argument.type().map(t -> (Node)t).orElseGet(() -> argument.expression().orElseThrow());
                var p = parameter(value, parameters);
                if (p == null || value instanceof NamedType n && (n.hasTemplateArguments() || n.qualifier().isPresent())) { direct.clear(); break; }
                direct.add(p);
            }
            if (direct.size() == partial.templateArguments().size() && new HashSet<>(direct).size() == direct.size())
                failures.put(partial, error("invalid-class-partial-specialization", "Partial specialization is not more specialized than the primary template: " + partial.name()));
            var deducible = new HashSet<TemplateParameterDeclaration>();
            partial.templateArguments().forEach(argument -> collectDeducible(argument, parameters, deducible));
            if (!deducible.containsAll(parameters)) failures.put(partial, error("invalid-class-partial-specialization", "Partial specialization has template parameters that cannot be deduced: " + partial.name()));
        }
        var known = new HashSet<String>();
        project.classTemplateInstances().forEach(i -> known.add(i.identity()));
        for (Use use : uses) {
            if (use.node() instanceof NamedType named && named.declaration().filter(d -> project.classTemplateInstances().stream().anyMatch(i -> i.declaration() == d)).isPresent()) continue;
            List<Located> family = family(use, declarations);
            ClassDeclaration primary = family.stream().map(Located::declaration).filter(c -> c.templateKind() == TemplateKind.PRIMARY).filter(ClassDeclaration::isDefinition).findFirst()
                    .orElseGet(() -> family.stream().map(Located::declaration).filter(c -> c.templateKind() == TemplateKind.PRIMARY).findFirst().orElse(null));
            if (primary == null) continue;
            PlatformProfile profile = TypeSemantics.profile(use.file());
            var arguments = completeArguments(primary, use.arguments(), profile);
            if (arguments == null) { failures.put(use.node(), error("invalid-class-template-arguments", "Invalid arguments for class template " + primary.name())); continue; }
            var matches = new ArrayList<Match>();
            for (Located candidate : family) {
                var declaration = candidate.declaration();
                if (failures.containsKey(declaration)) continue;
                if (declaration.templateKind() != TemplateKind.FULL_SPECIALIZATION && declaration.templateKind() != TemplateKind.PARTIAL_SPECIALIZATION) continue;
                var patternArguments=declaration.templateKind()==TemplateKind.FULL_SPECIALIZATION
                        ? completeArguments(primary,declaration.templateArguments(),profile) : declaration.templateArguments();
                var bindings = patternArguments==null ? null : deduce(declaration, patternArguments, arguments, profile);
                if (bindings != null) matches.add(new Match(declaration, bindings));
            }
            var full = matches.stream().filter(m -> m.declaration().templateKind() == TemplateKind.FULL_SPECIALIZATION).toList();
            if (!full.isEmpty()) matches = new ArrayList<>(full);
            var best = new ArrayList<Match>();
            for (Match candidate : matches) {
                boolean dominated = matches.stream().anyMatch(other -> other != candidate && moreSpecialized(other.declaration(), candidate.declaration(), profile));
                if (!dominated) best.add(candidate);
            }
            // Repeated declarations of one specialization are not competing candidates.
            var distinct = new LinkedHashMap<String, Match>();
            for (Match match : best) {
                String pattern = patternIdentity(match.declaration());
                Match previous = distinct.get(pattern);
                if (previous == null || !previous.declaration().isDefinition()) distinct.put(pattern, match);
            }
            if (distinct.size() > 1) { failures.put(use.node(), error("ambiguous-class-specialization", "Ambiguous partial specializations of " + primary.name())); continue; }
            Match selected = distinct.isEmpty() ? new Match(primary, bindParameters(primary.templateParameters(), arguments)) : distinct.values().iterator().next();
            if (!selected.declaration().isDefinition()) continue;
            String identity = primary.typeIdentity().orElse(qualify(family.getFirst().scope(), primary.name())) + "<" + arguments.stream().map(a -> argumentIdentity(a, profile)).reduce((a,b) -> a + "," + b).orElse("") + ">@" + profile.stableIdentity();
            if (!known.contains(identity) && known.size() >= 256) { failures.put(use.node(), error("template-instantiation-limit", "Class-template instantiation limit (256) reached")); continue; }
            known.add(identity);
            requests.add(new ClassTemplateInstances.Request(primary, selected.declaration(), use.node(), use.file(), arguments,
                    substitutions(selected.declaration(), selected.bindings()), identity));
        }
        if (!diagnosticsOnly) return ClassTemplateInstances.apply(project, requests);
        var parents = new IdentityHashMap<Node, Node>();
        project.walk(new NodeVisitor() { @Override public void visit(Node n) { n.children().forEach(child -> parents.put(child, n)); }});
        var completeClasses = Collections.newSetFromMap(new IdentityHashMap<ClassDeclaration, Boolean>());
        var sourceContexts = SourceContexts.index(project);
        project.walk(new NodeVisitor() {
            void require(TypeNode type) {
                type = TypeSemantics.unqualified(TypeSemantics.withoutReference(type));
                if (type instanceof PointerType pointer) type = pointer.pointee();
                ClassSemantics.objectClass(type).ifPresent(completeClasses::add);
            }
            @Override public void visit(MemberExpression member) {
                SourceFile source = SourceContexts.require(sourceContexts, member);
                TypeSemantics.expressionType(member.receiver(), TypeSemantics.profile(source)).ifPresent(this::require);
            }
            @Override public void visit(VariableDeclaration variable) {
                if (variable.modifiers().contains("extern")) return;
                variable.type().map(TypeNode::canonicalType).filter(t -> !(t instanceof PointerType || t instanceof ReferenceType)).ifPresent(this::require);
            }
        });
        for (ClassTemplateInstance instance : project.classTemplateInstances())
            if (completeClasses.contains(instance.declaration()) || instance.requests().stream().anyMatch(request -> requiresComplete(request, parents))) diagnoseSignature(instance.declaration(), failures);
        diagnoseDemandedBodies(project, failures);
        Project result = project;
        // withDiagnostics copies on change, so collect all findings per rule against each current snapshot.
        for (String code : List.of("ambiguous-class-specialization", "invalid-class-template-arguments", "template-instantiation-limit", "invalid-class-template-substitution", "class-template-body-member", "class-template-body-type", "class-template-body-call", "invalid-class-partial-specialization")) {
            var originalNodes = new ArrayList<Node>(); project.walk(new NodeVisitor() { @Override public void visit(Node n) { originalNodes.add(n); }});
            var currentNodes = new ArrayList<Node>(); result.walk(new NodeVisitor() { @Override public void visit(Node n) { currentNodes.add(n); }});
            var byNode = new IdentityHashMap<Node, Diagnostic>();
            for (int i = 0; i < originalNodes.size(); i++) {
                Diagnostic finding = failures.get(originalNodes.get(i));
                if (finding != null && finding.code().equals(code)) byNode.put(currentNodes.get(i), finding);
            }
            result = result.withDiagnostics(code, byNode);
        }
        return result;
    }
    private static void collectDeducible(Node node, List<TemplateParameterDeclaration> parameters, Set<TemplateParameterDeclaration> found) {
        if (node instanceof NamedType type) {
            if (type.qualifier().isPresent()) return;
            var p = parameter(type, parameters); if (p != null) found.add(p);
        }
        if (node instanceof Expression expression) {
            var p = parameter(expression, parameters); if (p != null) found.add(p);
            return; // Arithmetic value expressions are non-deduced contexts.
        }
        node.children().forEach(child -> collectDeducible(child, parameters, found));
    }
    private static boolean requiresComplete(Node request, Map<Node, Node> parents) {
        if (!(request instanceof NamedType)) return true;
        Node current = request;
        while (parents.containsKey(current)) {
            Node parent = parents.get(current);
            if (parent instanceof PointerType || parent instanceof ReferenceType || parent instanceof TemplateArgument || parent instanceof TypedefDeclaration) return false;
            if (parent instanceof VariableDeclaration variable) return !variable.modifiers().contains("extern");
            if (parent instanceof CallableDeclaration callable) return callable.body().isPresent();
            if (parent instanceof ParameterDeclaration) { current = parent; continue; }
            if (parent instanceof BaseSpecifier || parent instanceof SizeofExpression) return true;
            current = parent;
        }
        return true;
    }
    /** Concrete member definitions required by calls or explicit class instantiation, including transitive member calls. */
    public static Map<CallableDeclaration, List<Node>> demandedMethods(Project project) {
        var owners = new IdentityHashMap<CallableDeclaration, ClassTemplateInstance>();
        for (var instance : project.classTemplateInstances()) instance.declaration().walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration method) { owners.put(method, instance); }
        });
        var demanded = new LinkedHashMap<CallableDeclaration, List<Node>>();
        java.util.function.BiConsumer<CallableDeclaration, Node> addDemand = (method, request) -> {
            var all = demanded.computeIfAbsent(method, ignored -> new ArrayList<>());
            if (!all.contains(request)) all.add(request);
        };
        java.util.function.Consumer<Node> findCalls = new java.util.function.Consumer<>() {
            @Override public void accept(Node node) {
                if (node instanceof ClassDeclaration c && !c.templateParameters().isEmpty()
                        || node instanceof CallableDeclaration callable && !callable.templateParameters().isEmpty()) return;
                if (node instanceof CallExpression call) {
                    CallableDeclaration target = call.target().orElse(null);
                    if (target == null && call.callee() instanceof MemberExpression member && member.target().orElse(null) instanceof CallableDeclaration method) target = method;
                    if (target != null && owners.containsKey(target)) addDemand.accept(target, call);
                }
                node.children().forEach(this);
            }
        };
        project.files().forEach(findCalls);
        project.templateInstances().forEach(i -> findCalls.accept(i.declaration()));
        for (var instance : project.classTemplateInstances()) {
            var explicit = instance.requests().stream().filter(n -> n instanceof ClassDeclaration c && c.templateKind() == TemplateKind.EXPLICIT_INSTANTIATION).findFirst();
            if (explicit.isPresent()) owners.forEach((method, owner) -> { if (owner == instance) addDemand.accept(method, explicit.get()); });
        }
        var visited = Collections.newSetFromMap(new IdentityHashMap<CallableDeclaration, Boolean>());
        while (visited.size() < demanded.size())
            for (var method : new ArrayList<>(demanded.keySet()))
                if (visited.add(method) && method.templateParameters().isEmpty()) method.body().ifPresent(findCalls);
        demanded.replaceAll((method, requests) -> List.copyOf(requests));
        return Collections.unmodifiableMap(demanded);
    }
    private static void diagnoseDemandedBodies(Project project, Map<Node, Diagnostic> failures) {
        var owners = new IdentityHashMap<CallableDeclaration, ClassTemplateInstance>();
        for (var instance : project.classTemplateInstances()) instance.declaration().walk(new NodeVisitor() {
            @Override public void visit(CallableDeclaration method) { owners.put(method, instance); }
        });
        for (var entry : demandedMethods(project).entrySet()) {
                var method = entry.getKey();
                if (method.body().isEmpty() || !method.templateParameters().isEmpty()) continue;
                var instance = owners.get(method);
                var trace = new ArrayList<DiagnosticRelatedRange>();
                for (Node demand : entry.getValue()) demand.range().ifPresent(r -> trace.add(new DiagnosticRelatedRange("instantiation-request", "Member definition required here", r)));
                for (Node request : instance.requests()) request.range().ifPresent(r -> { if (trace.stream().noneMatch(t -> t.range().equals(r))) trace.add(new DiagnosticRelatedRange("instantiation-request", "Class specialization required here", r)); });
                method.body().get().walk(new NodeVisitor() {
                    void report(Node node, String code, String message) { failures.put(node, new Diagnostic(code, Diagnostic.Severity.ERROR, message, node.range().orElse(null), trace)); }
                    @Override public void visit(NamedType type) {
                        if (type.declaration().isEmpty() && type.alias().isEmpty() && type.fundamentalKind().isEmpty()) report(type, "class-template-body-type", "Invalid type '" + type.name() + "' in demanded class-template member");
                    }
                    @Override public void visit(CallExpression call) {
                        if (call.target().isEmpty() && call.callee() instanceof NameExpression name) report(call, "class-template-body-call", "No viable call to '" + name.name() + "' in demanded class-template member");
                    }
                    @Override public void visit(MemberExpression member) {
                        if (member.target().isPresent()) return;
                        TypeNode receiver = TypeSemantics.expressionType(member.receiver(), TypeSemantics.profile(instance.source())).orElse(null);
                        if (receiver == null && member instanceof ScopeMemberExpression && member.receiver() instanceof NameExpression name)
                            receiver = NamedType.create().setName(name.name()).build();
                        if (receiver == null) return;
                        receiver = TypeSemantics.unqualified(TypeSemantics.withoutReference(receiver));
                        if (member instanceof PointerMemberExpression && receiver instanceof PointerType pointer) receiver = pointer.pointee();
                        report(member, "class-template-body-member", "No member '" + member.member() + "' in substituted type " + receiver.canonicalIdentity());
                    }
                });
        }
    }
    private static void diagnoseSignature(Node node, Map<Node, Diagnostic> failures) {
        if (node instanceof CallableDeclaration callable) {
            callable.returnType().ifPresent(t -> diagnoseSignature(t, failures));
            callable.parameters().forEach(p -> diagnoseSignature(p, failures));
            return; // An unused member body is not instantiated with its enclosing class.
        }
        if (node instanceof NamedType type && type.qualifier().isPresent() && type.declaration().isEmpty()) {
            TypeNode qualifier = type.qualifier().get().canonicalType();
            if (qualifier instanceof NamedType name && (name.fundamentalKind().isPresent()
                    || type.typenameKeyword() && !TypeSemantics.dependent(name)))
                failures.put(type, error("invalid-class-template-substitution", "No type member " + type.name() + " in substituted type " + qualifier.canonicalIdentity()));
        }
        node.children().forEach(child -> diagnoseSignature(child, failures));
    }
    private static Diagnostic error(String code, String message) { return new Diagnostic(code, Diagnostic.Severity.ERROR, message); }
    private static String scopeOf(ClassDeclaration c, List<Located> declarations) { return declarations.stream().filter(d -> d.declaration() == c).map(Located::scope).findFirst().orElse(""); }
    private static String qualify(String scope, String name) { return scope.isEmpty() ? name : scope + "::" + name; }
    private static void collect(Node node, SourceFile file, String scope, boolean dependent, List<Located> declarations, List<Use> uses, int[] position, Set<CallableDeclaration> unusedMethods) {
        int point = position[0]++;
        if (node instanceof ClassDeclaration c) {
            if (c.templateKind() != TemplateKind.NONE) declarations.add(new Located(c, file, scope, point));
            if (c.templateKind() == TemplateKind.EXPLICIT_INSTANTIATION) uses.add(new Use(c, file, scope, c.name(), c.templateArguments(), point));
            dependent |= !c.templateParameters().isEmpty();
            scope = qualify(scope, c.name());
        } else if (node instanceof NamespaceDeclaration n) scope = qualify(scope, n.name());
        else if (node instanceof CallableDeclaration c) dependent |= !c.templateParameters().isEmpty();
        if (!dependent && node instanceof NamedType type && type.hasTemplateArguments() && !TypeSemantics.dependent(type))
            uses.add(new Use(type, file, scope, type.name(), type.templateArguments(), point));
        // A catalogue typedef is instantiated only when source syntax demands its type.
        if (!dependent && node instanceof NamedType alias && !LibraryDeclarations.isLibrary(alias) && alias.alias().isPresent()
                && alias.canonicalType() instanceof NamedType type && type != alias
                && type.hasTemplateArguments() && !TypeSemantics.dependent(type))
            uses.add(new Use(type, file, scope, type.name(), type.templateArguments(), point));
        if (!dependent && node instanceof NameExpression name && name.hasTemplateArguments() && name.templateArguments().stream().noneMatch(a -> a.type().map(TypeSemantics::dependent).orElse(false)))
            uses.add(new Use(name, file, scope, name.name(), name.templateArguments(), point));
        if (!dependent && node instanceof ScopeMemberExpression member && member.hasTemplateArguments()
                && member.templateArguments().stream().noneMatch(TypeSemantics::dependent)) {
            String name=qualifiedExpression(member);
            if(name!=null)uses.add(new Use(member,file,scope,name,member.templateArguments(),point));
        }
        for (Node child : node.children()) {
            if (node instanceof CallableDeclaration method && unusedMethods.contains(method) && method.body().orElse(null) == child) continue;
            collect(child, file, scope, dependent, declarations, uses, position, unusedMethods);
        }
    }
    private static String qualifiedExpression(Expression expression) {
        if(expression instanceof NameExpression name)return name.name();
        if(expression instanceof ScopeMemberExpression member) {
            String receiver=qualifiedExpression(member.receiver());
            return receiver==null?null:receiver+"::"+member.member();
        }
        return null;
    }
    private static List<Located> family(Use use, List<Located> declarations) {
        Declaration bound = use.node() instanceof NamedType type ? type.declaration().orElse(null)
                : use.node() instanceof NameExpression name ? name.target().orElse(null)
                : use.node() instanceof ScopeMemberExpression member ? member.target().orElse(null) : null;
        if (bound instanceof ClassDeclaration) {
            Located origin = declarations.stream().filter(d -> d.declaration() == bound).findFirst().orElse(null);
            if (origin != null) {
                var result = declarations.stream().filter(d -> d.scope().equals(origin.scope()) && d.declaration().name().equals(origin.declaration().name())
                        && (d.file() == use.file() && d.position() < use.position() || LibraryDeclarations.visible(use.file(), d.file()))).toList();
                if (result.stream().anyMatch(d -> d.declaration().templateKind() == TemplateKind.PRIMARY)) return result;
            }
        }
        String scope = use.scope();
        while (true) {
            String target = use.name().startsWith("::") ? use.name().substring(2) : qualify(scope, use.name());
            var result = declarations.stream().filter(d -> qualify(d.scope(), d.declaration().name()).equals(target))
                    .filter(d -> (d.file() == use.file() && d.position() < use.position() || LibraryDeclarations.visible(use.file(), d.file()))).toList();
            if (result.stream().anyMatch(d -> d.declaration().templateKind() == TemplateKind.PRIMARY)) return result;
            if (scope.isEmpty() || use.name().startsWith("::")) return List.of();
            int separator = scope.lastIndexOf("::"); scope = separator < 0 ? "" : scope.substring(0, separator);
        }
    }
    private static List<TemplateArgument> completeArguments(ClassDeclaration primary, List<TemplateArgument> written, PlatformProfile profile) {
        if (written.size() > primary.templateParameters().size()) return null;
        var result = new ArrayList<TemplateArgument>();
        var bindings = new LinkedHashMap<TemplateParameterDeclaration, TemplateArgument>();
        for (int i = 0; i < primary.templateParameters().size(); i++) {
            var parameter = primary.templateParameters().get(i);
            TemplateArgument argument = i < written.size() ? written.get(i) : parameter.defaultArgument().map(a -> substitute(a, bindings)).orElse(null);
            if (argument == null) return null;
            if (parameter.kind() == TemplateParameterDeclaration.Kind.VALUE) {
                Expression expression = argument.expression().orElse(null);
                if (expression == null) return null;
                var evaluated = ConstantEvaluator.evaluate(expression, profile);
                if (evaluated.isPresent()) argument = TemplateArgument.create().setExpression(IntegerLiteral.create().setValue(evaluated.get().number())).build();
                else {
                    TypeNode declared = parameter.type().orElseThrow().canonicalType();
                    if (!(declared instanceof PointerType || declared instanceof ReferenceType)
                            || !(expression instanceof AddressOfExpression || expression instanceof NameExpression)) return null;
                }
            } else {
                TypeNode type = argument.type().orElse(null);
                if (type == null || TypeSemantics.dependent(type)) return null;
                if (parameter.kind() == TemplateParameterDeclaration.Kind.TYPE && type instanceof NamedType name
                        && !name.hasTemplateArguments() && name.declaration().orElse(null) instanceof ClassDeclaration declaration
                        && declaration.templateKind() == TemplateKind.PRIMARY) return null;
                if (parameter.kind() == TemplateParameterDeclaration.Kind.TEMPLATE) {
                    if (!(type instanceof NamedType named) || !(named.declaration().orElse(null) instanceof ClassDeclaration cls) || cls.templateKind() != TemplateKind.PRIMARY) return null;
                    if (!compatibleTemplateParameters(parameter.templateParameters(), cls.templateParameters())) return null;
                    argument = TemplateArgument.create().setType(type.edit()).setKind(TemplateArgument.Kind.TEMPLATE).build();
                }
            }
            result.add(argument); bindings.put(parameter, argument);
        }
        return List.copyOf(result);
    }
    private static boolean compatibleTemplateParameters(List<TemplateParameterDeclaration> expected, List<TemplateParameterDeclaration> actual) {
        if (expected.size() != actual.size()) return false;
        for (int i = 0; i < expected.size(); i++) {
            var a = expected.get(i); var b = actual.get(i);
            if (a.kind() != b.kind()) return false;
            if (a.kind() == TemplateParameterDeclaration.Kind.VALUE && !a.type().orElseThrow().canonicalIdentity().equals(b.type().orElseThrow().canonicalIdentity())) return false;
            if (!compatibleTemplateParameters(a.templateParameters(), b.templateParameters())) return false;
        }
        return true;
    }
    private static Map<TemplateParameterDeclaration, TemplateArgument> bindParameters(List<TemplateParameterDeclaration> parameters, List<TemplateArgument> arguments) {
        var result = new LinkedHashMap<TemplateParameterDeclaration, TemplateArgument>();
        for (int i = 0; i < parameters.size(); i++) result.put(parameters.get(i), arguments.get(i));
        return result;
    }
    private static Map<TemplateParameterDeclaration, TemplateArgument> deduce(ClassDeclaration pattern, List<TemplateArgument> arguments, PlatformProfile profile) {
        return deduce(pattern,pattern.templateArguments(),arguments,profile);
    }
    private static Map<TemplateParameterDeclaration, TemplateArgument> deduce(ClassDeclaration pattern, List<TemplateArgument> patternArguments,
            List<TemplateArgument> arguments, PlatformProfile profile) {
        if (patternArguments.size() != arguments.size()) return null;
        var bindings = new LinkedHashMap<TemplateParameterDeclaration, TemplateArgument>();
        for (int i = 0; i < arguments.size(); i++) if (!matchArgument(patternArguments.get(i), arguments.get(i), pattern.templateParameters(), bindings, profile)) return null;
        return bindings.keySet().containsAll(pattern.templateParameters()) ? bindings : null;
    }
    private static boolean moreSpecialized(ClassDeclaration a, ClassDeclaration b, PlatformProfile profile) {
        return deduce(b, a.templateArguments(), profile) != null && deduce(a, b.templateArguments(), profile) == null;
    }
    private static TemplateParameterDeclaration parameter(Node node, List<TemplateParameterDeclaration> parameters) {
        Declaration target = node instanceof NamedType n ? n.declaration().orElse(null) : node instanceof NameExpression n ? n.target().orElse(null) : null;
        if (target != null) return target instanceof TemplateParameterDeclaration p && parameters.contains(p) ? p : null;
        // Unbound syntax is interpreted only in the explicit declaration's parameter scope.
        String name = node instanceof NamedType n ? n.name() : node instanceof NameExpression n ? n.name() : "";
        return parameters.stream().filter(p -> p.name().equals(name)).findFirst().orElse(null);
    }
    private static boolean bind(TemplateParameterDeclaration p, TemplateArgument a, Map<TemplateParameterDeclaration, TemplateArgument> bindings, PlatformProfile profile) {
        var previous = bindings.putIfAbsent(p, a);
        return previous == null || argumentIdentity(previous, profile).equals(argumentIdentity(a, profile));
    }
    private static boolean matchArgument(TemplateArgument pattern, TemplateArgument actual, List<TemplateParameterDeclaration> parameters, Map<TemplateParameterDeclaration, TemplateArgument> bindings, PlatformProfile profile) {
        if (pattern.type().isPresent() && actual.type().isPresent()) return matchType(pattern.type().get(), actual.type().get(), parameters, bindings, profile);
        if (pattern.expression().isPresent() && actual.expression().isPresent()) {
            var parameter = parameter(pattern.expression().get(), parameters);
            if (parameter != null) return bind(parameter, actual, bindings, profile);
            return argumentIdentity(substitute(pattern, bindings), profile).equals(argumentIdentity(actual, profile));
        }
        return false;
    }
    private static boolean matchType(TypeNode pattern, TypeNode actual, List<TemplateParameterDeclaration> parameters, Map<TemplateParameterDeclaration, TemplateArgument> bindings, PlatformProfile profile) {
        pattern = pattern.canonicalType(); actual = actual.canonicalType();
        if (pattern instanceof NamedType n) {
            var parameter = parameter(n, parameters);
            if (parameter != null && !n.hasTemplateArguments() && n.qualifier().isEmpty())
                return bind(parameter, TemplateArgument.create().setType(actual.edit()).build(), bindings, profile);
        }
        if (pattern.getClass() != actual.getClass()) return false;
        if (pattern instanceof PointerType p) return matchType(p.pointee(), ((PointerType)actual).pointee(), parameters, bindings, profile);
        if (pattern instanceof ReferenceType r) return matchType(r.referredType(), ((ReferenceType)actual).referredType(), parameters, bindings, profile);
        if (pattern instanceof QualifiedType q) { var a = (QualifiedType)actual; return q.isConst() == a.isConst() && q.isVolatile() == a.isVolatile() && matchType(q.baseType(), a.baseType(), parameters, bindings, profile); }
        if (pattern instanceof ArrayType a) {
            var b = (ArrayType)actual;
            return matchType(a.elementType(), b.elementType(), parameters, bindings, profile) && (a.bound().isEmpty() ? b.bound().isEmpty() : b.bound().isPresent() && matchArgument(TemplateArgument.create().setExpression((NodeBuilder<? extends Expression>)a.bound().get().edit()).build(), TemplateArgument.create().setExpression((NodeBuilder<? extends Expression>)b.bound().get().edit()).build(), parameters, bindings, profile));
        }
        if (pattern instanceof NamedType n && n.hasTemplateArguments()) {
            var a = (NamedType)actual;
            var parameter = parameter(n, parameters);
            if (parameter != null && parameter.kind() == TemplateParameterDeclaration.Kind.TEMPLATE) {
                var head = a.edit(); head.setTemplateArguments(List.of());
                if (!bind(parameter, TemplateArgument.create().setType(head).setKind(TemplateArgument.Kind.TEMPLATE).build(), bindings, profile)) return false;
            } else if (!headIdentity(n).equals(headIdentity(a))) return false;
            if (n.templateArguments().size() != a.templateArguments().size()) return false;
            for (int i = 0; i < n.templateArguments().size(); i++) if (!matchArgument(n.templateArguments().get(i), a.templateArguments().get(i), parameters, bindings, profile)) return false;
            return true;
        }
        if (pattern instanceof FunctionType f) {
            var a = (FunctionType)actual;
            if (f.parameters().size() != a.parameters().size() || f.variadic() != a.variadic() || f.isConst() != a.isConst() || f.isVolatile() != a.isVolatile() || !matchType(f.returnType(), a.returnType(), parameters, bindings, profile)) return false;
            for (int i = 0; i < f.parameters().size(); i++) if (!matchType(f.parameters().get(i).type().orElseThrow(), a.parameters().get(i).type().orElseThrow(), parameters, bindings, profile)) return false;
            return true;
        }
        if (pattern instanceof MemberPointerType m) { var a = (MemberPointerType)actual; return matchType(m.ownerType(), a.ownerType(), parameters, bindings, profile) && matchType(m.pointee(), a.pointee(), parameters, bindings, profile); }
        return pattern.canonicalIdentity().equals(actual.canonicalIdentity());
    }
    private static String headIdentity(NamedType n) {
        String identity = n.declaration().flatMap(Declaration::typeIdentity).orElse(n.name());
        if (n.declaration().orElse(null) instanceof ClassDeclaration c && c.templateKind() == TemplateKind.NONE && c.hasTemplateArguments()) {
            String arguments = c.templateArguments().stream().map(a -> argumentIdentity(a, PlatformProfile.defaults())).reduce((a,b) -> a + "," + b).orElse("");
            int marker = identity.lastIndexOf("<" + arguments + ">@");
            if (marker >= 0) return identity.substring(0, marker);
        }
        return identity;
    }
    private static String patternIdentity(ClassDeclaration c) {
        var names = new IdentityHashMap<Declaration, String>();
        for (int i = 0; i < c.templateParameters().size(); i++) names.put(c.templateParameters().get(i), "$" + i);
        return TypeIdentity.canonicalIdentity(NamedType.create().setName(c.name()).setTemplateArguments(c.templateArguments().stream().map(TemplateArgument::edit).toList()).build(), names);
    }
    private static String argumentIdentity(TemplateArgument argument, PlatformProfile profile) {
        if (argument.type().isPresent()) return argument.kind() + ":" + argument.type().get().canonicalIdentity();
        var value = ConstantEvaluator.evaluate(argument.expression().orElseThrow(), profile);
        if (value.isPresent()) return "VALUE:" + value.get().number();
        return TypeIdentity.canonicalIdentity(NamedType.create().setName("$").setTemplateArguments(List.of(argument.edit())).build());
    }
    private static Map<Node, Node> substitutions(ClassDeclaration selected, Map<TemplateParameterDeclaration, TemplateArgument> bindings) {
        var result = new IdentityHashMap<Node, Node>();
        var parameters = List.copyOf(bindings.keySet());
        var typeBindings = new LinkedHashMap<TemplateParameterDeclaration, TypeNode>();
        bindings.forEach((p, a) -> a.type().ifPresent(t -> typeBindings.put(p, t)));
        selected.walk(new NodeVisitor() {
            @Override public void visit(NamedType n) {
                var p = parameter(n, parameters);
                if (p != null && bindings.get(p).type().isPresent()) {
                    TypeNode replacement = bindings.get(p).type().orElseThrow();
                    if (n.hasTemplateArguments() && replacement instanceof NamedType name)
                        replacement = name.edit().setTemplateArguments(n.templateArguments().stream().map(a -> substitute(a, bindings).edit()).toList()).build();
                    result.put(n, replacement);
                } else if (n.qualifier().isPresent()) {
                    TypeNode replacement = TemplateDeduction.dependentType(n, typeBindings);
                    if (replacement != null) result.put(n, replacement);
                }
            }
            @Override public void visit(NameExpression n) {
                var p = parameter(n, parameters);
                if (p != null && bindings.get(p).expression().isPresent()) result.put(n, bindings.get(p).expression().orElseThrow());
                else if (p != null && bindings.get(p).type().orElse(null) instanceof NamedType type) {
                    var name = NameExpression.create().setName(type.name());
                    if (type.declaration().isPresent()) name.setTarget((NodeBuilder<? extends Declaration>)type.declaration().get().edit());
                    result.put(n, name.build());
                }
            }
        });
        return result;
    }
    private static TemplateArgument substitute(TemplateArgument argument, Map<TemplateParameterDeclaration, TemplateArgument> bindings) {
        if (argument.type().isPresent()) return TemplateArgument.create().setType(substituteType(argument.type().get(), bindings).edit()).setKind(argument.kind()).build();
        Expression expression = argument.expression().orElseThrow();
        var p = parameter(expression, List.copyOf(bindings.keySet()));
        if (p != null) return bindings.get(p);
        var replacements = new IdentityHashMap<Node, Node>();
        expression.walk(new NodeVisitor() { @Override public void visit(NameExpression name) {
            var parameter = parameter(name, List.copyOf(bindings.keySet()));
            if (parameter != null) bindings.get(parameter).expression().ifPresent(value -> replacements.put(name, value));
        }});
        return replacements.isEmpty() ? argument : ClassTemplateInstances.substituteArgument(argument, replacements);
    }
    static TypeNode substituteType(TypeNode type, Map<TemplateParameterDeclaration, TemplateArgument> bindings) {
        if (type instanceof NamedType n) {
            var p = parameter(n, List.copyOf(bindings.keySet()));
            if (p != null && !n.hasTemplateArguments()) return bindings.get(p).type().orElse(type);
            if(n.qualifier().isPresent()) {
                var types=new LinkedHashMap<TemplateParameterDeclaration,TypeNode>();
                bindings.forEach((parameter,argument)->argument.type().ifPresent(value->types.put(parameter,value)));
                TypeNode member=TemplateDeduction.dependentType(n,types);
                if(member!=null)return member;
            }
            if (n.hasTemplateArguments()) {
                var arguments=n.templateArguments().stream().map(a -> substitute(a,bindings)).toList();
                return n.edit().setTemplateArguments(arguments.stream().map(TemplateArgument::edit).toList())
                        .setDependent(arguments.stream().anyMatch(TypeSemantics::dependent) || n.qualifier().map(TypeSemantics::dependent).orElse(false)).build();
            }
        }
        if (type instanceof PointerType p) return p.edit().setPointee(substituteType(p.pointee(), bindings).edit()).build();
        if (type instanceof ReferenceType r) return r.edit().setReferredType(substituteType(r.referredType(), bindings).edit()).build();
        if (type instanceof QualifiedType q) return q.edit().setBaseType(substituteType(q.baseType(), bindings).edit()).build();
        if (type instanceof ArrayType a) return a.edit().setElementType(substituteType(a.elementType(), bindings).edit()).build();
        return type;
    }
}
