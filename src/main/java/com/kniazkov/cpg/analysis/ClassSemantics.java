/* C++03 object initialization and lifetime facts. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Computes facts from the current immutable syntax snapshot. These plans are not runtime traces:
 * optional copy elision, conditional execution and inter-translation-unit initialization order
 * are deliberately not invented. Returned node references belong to the supplied snapshot. */
public final class ClassSemantics {
    private ClassSemantics() {}
    public enum SpecialKind { DEFAULT_CONSTRUCTOR, COPY_CONSTRUCTOR, COPY_ASSIGNMENT, DESTRUCTOR }
    public enum Origin { IMPLICIT, USER_DECLARED, SUPPRESSED }
    public enum InitializationKind { DEFAULT, ZERO, COPY, DIRECT, AGGREGATE, VALUE }
    public enum Storage { AUTOMATIC, STATIC, TEMPORARY, SUBOBJECT }
    public enum LifetimeEnd { BLOCK_EXIT, PROGRAM_EXIT, FULL_EXPRESSION, REFERENCE_LIFETIME }
    /** A special member's declaration status; implicit members have no invented source node. */
    public record SpecialMember(SpecialKind kind, Origin origin, List<CallableDeclaration> declarations) {
        public SpecialMember { declarations = List.copyOf(declarations); }
    }
    /** One base or field in language-mandated construction order. */
    public record Subobject(Node declaration, Optional<MemberInitializer> writtenInitializer) {}
    /** A variable's static initialization phase precedes its other initialization when applicable. */
    public record Initialization(VariableDeclaration declaration, Storage storage, List<InitializationKind> phases,
            Optional<ClassDeclaration> objectClass, Optional<Expression> writtenInitializer) {
        public Initialization { phases = List.copyOf(phases); }
    }
    public record Lifetime(Node object, Storage storage, LifetimeEnd end, Optional<Node> boundary) {}

    public static List<VariableDeclaration> fields(ClassDeclaration owner) {
        return owner.members().stream().filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast)
                .filter(field -> !field.modifiers().contains("static") && !field.name().isEmpty()).toList();
    }
    public static boolean aggregate(ClassDeclaration owner) {
        return owner.bases().isEmpty()
                && owner.members().stream().noneMatch(member -> member instanceof CallableDeclaration callable
                        && (callable.kind() == CallableDeclaration.Kind.CONSTRUCTOR || callable.modifiers().contains("virtual")))
                && fields(owner).stream().allMatch(field -> field.access().orElse(owner.memberAccess()).equals("public"));
    }
    public static List<SpecialMember> specialMembers(ClassDeclaration owner) {
        List<CallableDeclaration> functions = owner.members().stream().filter(CallableDeclaration.class::isInstance)
                .map(CallableDeclaration.class::cast).filter(member -> !member.isFriend()).toList();
        var result = new ArrayList<SpecialMember>();
        for (SpecialKind kind : SpecialKind.values()) {
            var declarations = functions.stream().filter(function -> matches(kind, owner, function)).toList();
            Origin origin = !declarations.isEmpty() ? Origin.USER_DECLARED
                    : kind == SpecialKind.DEFAULT_CONSTRUCTOR && functions.stream().anyMatch(function -> function.kind() == CallableDeclaration.Kind.CONSTRUCTOR)
                    ? Origin.SUPPRESSED : Origin.IMPLICIT;
            result.add(new SpecialMember(kind, origin, declarations));
        }
        return List.copyOf(result);
    }
    private static boolean matches(SpecialKind kind, ClassDeclaration owner, CallableDeclaration function) {
        return switch (kind) {
            case DEFAULT_CONSTRUCTOR -> function.kind() == CallableDeclaration.Kind.CONSTRUCTOR
                    && function.parameters().stream().allMatch(parameter -> parameter.defaultValue().isPresent());
            case COPY_CONSTRUCTOR -> function.kind() == CallableDeclaration.Kind.CONSTRUCTOR && !function.parameters().isEmpty()
                    && sameClassParameter(owner, function.parameters().getFirst(), true)
                    && function.parameters().subList(1, function.parameters().size()).stream().allMatch(parameter -> parameter.defaultValue().isPresent());
            case COPY_ASSIGNMENT -> function.name().equals("operator=") && function.parameters().size() == 1
                    && sameClassParameter(owner, function.parameters().getFirst(), false);
            case DESTRUCTOR -> function.kind() == CallableDeclaration.Kind.DESTRUCTOR;
        };
    }
    private static boolean sameClassParameter(ClassDeclaration owner, ParameterDeclaration parameter, boolean requireReference) {
        TypeNode type = parameter.type().map(TypeSemantics::unqualified).orElse(null);
        if (type instanceof ReferenceType reference) type = TypeSemantics.unqualified(reference.referredType());
        else if (requireReference) return false;
        if (!(type instanceof NamedType named)) return false;
        return named.declaration().map(declaration -> declaration == owner || declaration.definition().orElse(null) == owner
                || owner.typeIdentity().isPresent() && owner.typeIdentity().equals(declaration.typeIdentity()))
                .orElseGet(() -> named.name().equals(owner.name()));
    }
    /** Direct non-virtual bases in declaration order, then non-static fields in declaration order.
     * Virtual-base traversal is a separate hierarchy analysis, so it is rejected rather than guessed. */
    public static List<Subobject> constructionOrder(ClassDeclaration owner, CallableDeclaration constructor) {
        if (constructor.kind() != CallableDeclaration.Kind.CONSTRUCTOR) throw new IllegalArgumentException("Expected constructor");
        if (!hasCompleteNonvirtualHierarchy(owner)) throw new IllegalArgumentException("Virtual or unresolved base ordering requires hierarchy analysis");
        var result = new ArrayList<Subobject>();
        for (var base : owner.bases()) {
            ClassDeclaration baseClass = objectClass(base.type()).orElseThrow();
            result.add(new Subobject(base, constructor.initializers().stream().filter(value -> initializedBase(value)
                    .filter(target -> sameClass(baseClass, target)).isPresent()).findFirst()));
        }
        for (var field : fields(owner)) {
            var written = constructor.initializers().stream().filter(value -> value.target().map(target -> target == field).orElseGet(() -> value.name().equals(field.name()))).findFirst();
            if (!owner.keyword().equals("union") || written.isPresent()) result.add(new Subobject(field, written));
        }
        return List.copyOf(result);
    }
    /** True only when every reachable base is defined, resolved and non-virtual. */
    public static boolean hasCompleteNonvirtualHierarchy(ClassDeclaration owner) {
        return nonvirtual(owner, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    private static boolean nonvirtual(ClassDeclaration owner, Set<ClassDeclaration> active) {
        if (!owner.isDefinition() || !active.add(owner)) return false;
        try {
            for (var base : owner.bases()) {
                if (base.isVirtual()) return false;
                var target = objectClass(base.type());
                if (target.isEmpty() || !nonvirtual(target.orElseThrow(), active)) return false;
            }
            return true;
        } finally { active.remove(owner); }
    }
    public static List<Subobject> destructionOrder(ClassDeclaration owner, CallableDeclaration constructor) {
        var result = new ArrayList<>(constructionOrder(owner, constructor)); Collections.reverse(result); return List.copyOf(result);
    }
    /** Resolves the class named through a base initializer, retaining its written alias separately. */
    public static Optional<ClassDeclaration> initializedBase(MemberInitializer initializer) {
        Declaration target = initializer.target().orElse(null);
        if (target instanceof ClassDeclaration cls) return Optional.of(cls.definition().orElse(cls) instanceof ClassDeclaration definition ? definition : cls);
        if (target instanceof TypedefDeclaration alias) return objectClass(alias.type());
        return Optional.empty();
    }
    public static boolean sameClass(ClassDeclaration left, ClassDeclaration right) {
        return right != null && (left == right || left.typeIdentity().isPresent() && left.typeIdentity().equals(right.typeIdentity()));
    }
    public static Optional<ClassDeclaration> objectClass(TypeNode type) {
        type = TypeSemantics.unqualified(type);
        if (type instanceof ArrayType array) return objectClass(array.elementType());
        if (type instanceof NamedType named) return named.declaration().filter(ClassDeclaration.class::isInstance)
                .map(ClassDeclaration.class::cast).map(owner -> owner.definition().orElse(owner) instanceof ClassDeclaration definition ? definition : owner);
        return Optional.empty();
    }
    public static Initialization initialization(Project project, VariableDeclaration variable) {
        var parents = parents(project);
        if (!parents.containsKey(variable)) throw new IllegalArgumentException("Variable is outside project");
        boolean local = enclosing(variable, parents, CallableDeclaration.class).isPresent();
        Storage storage = parents.get(variable) instanceof ClassDeclaration && !variable.modifiers().contains("static") ? Storage.SUBOBJECT : !local || variable.modifiers().contains("static") || variable.modifiers().contains("extern") ? Storage.STATIC : Storage.AUTOMATIC;
        var phases = new ArrayList<InitializationKind>();
        if (storage == Storage.STATIC && variable.isDefinition()) phases.add(InitializationKind.ZERO);
        Expression written = variable.initializer().map(ClassSemantics::written).orElse(null);
        InitializationKind kind = written == null ? InitializationKind.DEFAULT
                : written instanceof InitializationExpression initializer ? switch (initializer.form()) {
                    case DIRECT -> InitializationKind.DIRECT;
                    case AGGREGATE -> InitializationKind.AGGREGATE;
                    case TEMPORARY -> InitializationKind.COPY;
                } : InitializationKind.COPY;
        if (variable.isDefinition()) phases.add(kind);
        return new Initialization(variable, storage, phases, variable.type().flatMap(ClassSemantics::objectClass), Optional.ofNullable(written));
    }
    /** Source constructor candidates with compatible arity. Ranking and access checks belong
     * to overload resolution; an empty result can denote an implicit special member. */
    public static List<CallableDeclaration> constructorCandidates(Initialization initialization) {
        if (initialization.objectClass().isEmpty()) return List.of();
        Expression value = initialization.writtenInitializer().orElse(null);
        int count = value == null ? 0 : value instanceof InitializationExpression expression
                && expression.form() == InitializationExpression.Form.DIRECT ? expression.arguments().size() : 1;
        boolean copy = initialization.phases().contains(InitializationKind.COPY);
        return initialization.objectClass().orElseThrow().members().stream().filter(CallableDeclaration.class::isInstance)
                .map(CallableDeclaration.class::cast).filter(function -> function.kind() == CallableDeclaration.Kind.CONSTRUCTOR)
                .filter(function -> !copy || !function.modifiers().contains("explicit"))
                .filter(function -> (function.variadic() || count <= function.parameters().size())
                        && function.parameters().subList(Math.min(count, function.parameters().size()), function.parameters().size())
                                .stream().allMatch(parameter -> parameter.defaultValue().isPresent())).toList();
    }
    /** Local object order is lexical; runtime branching/early exits do not turn this into an execution trace. */
    public static List<Lifetime> lifetimes(Project project) {
        var parents = parents(project);
        var result = new ArrayList<Lifetime>();
        project.walk(new NodeVisitor() {
            @Override public void visit(VariableDeclaration variable) {
                if (!variable.isDefinition() || parents.get(variable) instanceof ClassDeclaration) return;
                if (variable.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isPresent()) return;
                boolean local = enclosing(variable, parents, CallableDeclaration.class).isPresent();
                boolean stat = !local || variable.modifiers().contains("static");
                result.add(new Lifetime(variable, stat ? Storage.STATIC : Storage.AUTOMATIC,
                        stat ? LifetimeEnd.PROGRAM_EXIT : LifetimeEnd.BLOCK_EXIT,
                        stat ? Optional.empty() : enclosing(variable, parents, BlockStatement.class)));
            }
            @Override public void visit(InitializationExpression initialization) {
                if (initialization.form() != InitializationExpression.Form.TEMPORARY || initialization.type().flatMap(ClassSemantics::objectClass).isEmpty()) return;
                Node parent = parents.get(initialization);
                while (parent instanceof ConversionExpression) parent = parents.get(parent);
                if (parent instanceof VariableDeclaration variable && variable.type().map(TypeSemantics::unqualified).filter(ReferenceType.class::isInstance).isPresent()) {
                    result.add(new Lifetime(initialization, Storage.TEMPORARY, LifetimeEnd.REFERENCE_LIFETIME, Optional.of(variable)));
                } else {
                    Node boundary = initialization;
                    while (parents.get(boundary) instanceof Expression) boundary = parents.get(boundary);
                    result.add(new Lifetime(initialization, Storage.TEMPORARY, LifetimeEnd.FULL_EXPRESSION, Optional.of(boundary)));
                }
            }
        });
        return List.copyOf(result);
    }
    /** Empty functional construction is value-initialization, never default-initialization. */
    public static InitializationKind temporaryInitialization(InitializationExpression expression) {
        if (expression.form() != InitializationExpression.Form.TEMPORARY) throw new IllegalArgumentException("Expected temporary construction");
        return expression.arguments().isEmpty() ? InitializationKind.VALUE : InitializationKind.DIRECT;
    }
    private static Expression written(Expression expression) {
        while (expression instanceof ConversionExpression conversion && !conversion.isExplicit()) expression = conversion.operand();
        return expression;
    }
    private static Map<Node, Node> parents(Node root) {
        var result = new IdentityHashMap<Node, Node>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { node.children().forEach(child -> result.put(child, node)); }});
        return result;
    }
    private static Optional<Node> enclosing(Node node, Map<Node, Node> parents, Class<? extends Node> kind) {
        for (Node parent = parents.get(node); parent != null; parent = parents.get(parent)) if (kind.isInstance(parent)) return Optional.of(parent);
        return Optional.empty();
    }
}
