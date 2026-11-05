/* Code Property Graph: immutable control and data dependencies of one callable. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;

/** Per-function analysis over existing snapshot nodes. Its terminal is the exit owned by the entry declaration. */
public record FunctionFlow(CallableDeclaration entry, Exit exit, List<ControlEdge> control, List<DataEdge> data, boolean complete) {

    /** Compatibility constructor for a complete analysis. */
    public FunctionFlow(CallableDeclaration entry, Exit exit, List<ControlEdge> control, List<DataEdge> data) { this(entry, exit, control, data, true); }

    public enum Branch { ENTRY, NEXT, TRUE, FALSE, RETURN, BACK, EXCEPTION, THROW, CLEANUP, BREAK, CONTINUE, GOTO, CASE, DEFAULT }
    public enum Dependency { VALUE, DEFINITION }
    /** Directed transfer between instruction, condition, entry or exit vertices. */
    public record ControlEdge(FlowVertex from, FlowVertex to, Branch kind) {
        public ControlEdge { Objects.requireNonNull(from); Objects.requireNonNull(to); Objects.requireNonNull(kind); }
    }
    /** Direction is from the producer of a value or reaching definition to its consumer. */
    public record DataEdge(Node from, Node to, Dependency kind, CallExpression callSite) {
        /** Intraprocedural compatibility constructor; no invocation context is attached. */
        public DataEdge(Node from, Node to, Dependency kind) { this(from, to, kind, null); }
        public DataEdge { Objects.requireNonNull(from); Objects.requireNonNull(to); Objects.requireNonNull(kind); }
    }

    /** Flow-owned operations, ordered deterministically within their callable. */
    public List<AuxiliaryFlowVertex> auxiliaryVertices() {
        return control.stream().flatMap(e -> java.util.stream.Stream.of(e.from(), e.to()))
                .filter(AuxiliaryFlowVertex.class::isInstance).map(AuxiliaryFlowVertex.class::cast)
                .distinct().sorted(Comparator.comparingInt(AuxiliaryFlowVertex::index)).toList();
    }
    public Optional<AuxiliaryFlowVertex> exceptionalExit() {
        return auxiliaryVertices().stream().filter(v -> v.kind() == AuxiliaryFlowVertex.Kind.EXCEPTIONAL_EXIT).findFirst();
    }

    private static boolean dataEndpoint(Node node, Set<Node> evaluated) {
        return evaluated.contains(node) || node instanceof DynamicArrayOperation operation
                && evaluated.contains(operation.syntax())
                || node instanceof ContainerOperation container && evaluated.contains(container.syntax())
                || node instanceof SequenceOperation sequence && evaluated.contains(sequence.syntax());
    }

    public FunctionFlow {
        Objects.requireNonNull(entry);
        if (exit != entry.exit()) throw new IllegalArgumentException("Exit belongs to another callable");
        control = List.copyOf(control);
        data = List.copyOf(data);
        if (!complete && (!control.isEmpty() || !data.isEmpty())) throw new IllegalArgumentException("Incomplete flow must not publish partial edges");
        Set<FlowVertex> members = Collections.newSetFromMap(new IdentityHashMap<>());
        entry.walk(new NodeVisitor() {
            @Override public void visit(Node node) { members.add(node); }
        });
        var indices = new HashMap<Integer, AuxiliaryFlowVertex>();
        for (ControlEdge edge : control) for (FlowVertex vertex : List.of(edge.from(), edge.to())) {
            if (vertex instanceof AuxiliaryFlowVertex auxiliary) {
                if (auxiliary.owner() != entry)
                    throw new IllegalArgumentException("Foreign auxiliary flow operation");
                var previous = indices.putIfAbsent(auxiliary.index(), auxiliary);
                if (previous != null && previous != auxiliary) throw new IllegalArgumentException("Duplicate auxiliary flow index");
            }
        }
        members.addAll(indices.values());
        for (ControlEdge edge : control) {
            if (!members.contains(edge.from()) || !members.contains(edge.to()) || edge.from() == exit || edge.from() instanceof AuxiliaryFlowVertex a && (a.kind() == AuxiliaryFlowVertex.Kind.EXCEPTIONAL_EXIT || a.kind() == AuxiliaryFlowVertex.Kind.TERMINATE_EXIT))
                throw new IllegalArgumentException("Invalid control-flow endpoint");
        }
        // Defaults remain owned by their declaration. Only expressions actually
        // referenced by calls in this function may be external data-flow endpoints.
        Set<Node> evaluated = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> storage = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Node>();
        pending.add(entry);
        while (!pending.isEmpty()) {
            Node node = pending.remove();
            if (!evaluated.add(node)) continue;
            pending.addAll(node.children());
            referencedStorage(node).ifPresent(storage::add);
            if (node instanceof CallExpression call)
                call.defaultArguments().forEach(argument -> pending.add(argument.expression()));
        }
        evaluated.addAll(storage);
        evaluated.addAll(invokedStorage(entry, indices.values()));
        // Implicit subobject lifetime operations evaluate their referenced object,
        // which remains owned by its class rather than by this callable.
        // Project.withFlows separately verifies every auxiliary object is project-owned.
        for (var auxiliary : indices.values()) auxiliary.object().ifPresent(evaluated::add);
        var contexts = new IdentityHashMap<CallExpression, Set<Node>>();
        for (DataEdge edge : data) {
            Set<Node> allowed = evaluated;
            if (edge.callSite() != null) {
                if (!evaluated.contains(edge.callSite()))
                    throw new IllegalArgumentException("Foreign data-flow call site");
                allowed = contexts.computeIfAbsent(edge.callSite(), site -> calledNodes(site, evaluated));
            }
            if (!dataEndpoint(edge.from(), allowed) || !dataEndpoint(edge.to(), allowed))
                throw new IllegalArgumentException("Foreign data-flow endpoint in " + entry.name() + ": "
                        + edge.from().getClass().getSimpleName() + " " + edge.from().range() + " -> "
                        + edge.to().getClass().getSimpleName() + " " + edge.to().range()
                        + " via " + (edge.callSite() == null ? "no call" : edge.callSite().range()));
        }
    }
    /** Only bodies selected by this invocation can contribute contextual data. */
    private static Set<Node> calledNodes(CallExpression site, Set<Node> local) {
        Set<Node> result = Collections.newSetFromMap(new IdentityHashMap<>());
        result.addAll(local);
        Set<Node> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Node>(); pending.add(site);
        while (!pending.isEmpty()) {
            Node node = pending.remove();
            if (!visited.add(node)) continue;
            result.add(node);
            referencedStorage(node).ifPresent(result::add);
            pending.addAll(node.children());
            if (node instanceof CallExpression call) {
                call.target().ifPresent(pending::add);
                pending.addAll(call.virtualTargets());
                call.defaultArguments().forEach(argument -> pending.add(argument.expression()));
            }
        }
        return result;
    }

    /** Global/static initializers can reach reads bound to that exact declaration. */
    private static Optional<VariableDeclaration> referencedStorage(Node node) {
        Optional<Declaration> target = node instanceof NameExpression name ? name.target()
                : node instanceof MemberExpression member ? member.target() : Optional.empty();
        return target.filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast);
    }

    /** Reachable opaque invocations may read bound storage, but not publish body nodes. */
    private static Set<Node> invokedStorage(CallableDeclaration entry, Collection<AuxiliaryFlowVertex> auxiliaries) {
        Set<Node> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> storage = Collections.newSetFromMap(new IdentityHashMap<>());
        Set<Node> privateLocals = Collections.newSetFromMap(new IdentityHashMap<>());
        var pending = new ArrayDeque<Node>(); pending.add(entry);
        for (var auxiliary : auxiliaries) auxiliary.destructor().or(auxiliary::constructor).ifPresent(pending::add);
        while (!pending.isEmpty()) {
            Node node = pending.remove();
            if (!seen.add(node)) continue;
            pending.addAll(node.children());
            referencedStorage(node).ifPresent(storage::add);
            if (node instanceof CallableDeclaration callable) {
                callable.definition().ifPresent(pending::add);
                if (callable != entry) callable.walk(new NodeVisitor() {
                    @Override public void visit(VariableDeclaration variable) {
                        if (!variable.modifiers().contains("static") && !variable.modifiers().contains("extern"))
                            privateLocals.add(variable);
                    }
                });
            }
            if (node instanceof CallExpression call) {
                call.target().ifPresent(pending::add); pending.addAll(call.virtualTargets());
                call.defaultArguments().forEach(argument -> pending.add(argument.expression()));
            }
            if (node instanceof Expression expression) expression.operatorTarget().ifPresent(pending::add);
            if (node instanceof VariableDeclaration variable) {
                variable.destructorTarget().ifPresent(pending::add);
                if (variable.constructorTarget().isPresent()) pending.add(variable.constructorTarget().orElseThrow());
                else variable.type().flatMap(type -> objectClass(type, Collections.newSetFromMap(new IdentityHashMap<>())))
                        .ifPresent(owner -> owner.members().stream().filter(CallableDeclaration.class::isInstance)
                            .map(CallableDeclaration.class::cast).filter(c -> c.kind() == CallableDeclaration.Kind.CONSTRUCTOR)
                            .forEach(pending::add));
            }
        }
        storage.removeAll(privateLocals);
        return storage;
    }

    /** Follow owned type wrappers and typedef links without importing analysis state. */
    private static Optional<ClassDeclaration> objectClass(TypeNode type, Set<TypeNode> seen) {
        if (!seen.add(type)) return Optional.empty();
        if (type instanceof QualifiedType qualified) return objectClass(qualified.baseType(), seen);
        if (type instanceof ArrayType array) return objectClass(array.elementType(), seen);
        if (type instanceof NamedType named) {
            Declaration declaration = named.declaration().orElse(null);
            if (declaration instanceof TypedefDeclaration alias) return objectClass(alias.type(), seen);
            if (declaration instanceof ClassDeclaration owner)
                return Optional.of(owner.definition().orElse(owner) instanceof ClassDeclaration definition ? definition : owner);
        }
        return Optional.empty();
    }

}
