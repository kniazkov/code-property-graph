/* Safe C++03 vector normalization with preserved written syntax. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import java.util.*;

/** Publishes sequence semantics only for the trusted ordinary vector specialization.
 * Allocation, invalidation and exact internal copy counts are deliberately unspecified.
 * Raw calls remain available for source generation and conservative effect analysis. */
public final class DynamicArraySemantics {
    private DynamicArraySemantics() {}

    public static Project green(Project project) {
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        var types = new LinkedHashMap<ClassDeclaration, DynamicArrayType.Builder>();
        var elements = new IdentityHashMap<ClassDeclaration, TypeNode>();
        var sites = new IdentityHashMap<ClassDeclaration, List<NodeBuilder<? extends TypeNode>>>();
        for (var instance : project.classTemplateInstances()) {
            if (!trusted(instance.origin(), "vector") || !trusted(instance.selected(), "vector")
                    || instance.origin() != instance.selected() || instance.arguments().size() != 2) continue;
            TypeNode element = instance.arguments().getFirst().type().orElse(null);
            TypeNode allocator = instance.arguments().get(1).type().orElse(null);
            if (element == null || allocator == null || bool(element) || !standardAllocator(project, allocator, element)) continue;
            var cls = instance.declaration();
            types.put(cls, DynamicArrayType.create().setConcreteClass(context.copy(cls)).setElementType(context.copy(element)));
            elements.put(cls, element); sites.put(cls, new ArrayList<>());
        }
        var operations = new ArrayList<NodeBuilder<? extends DynamicArrayOperation>>();
        for (var source : SourceContexts.roots(project)) {
            if (source.source().language() != Language.CPP || LibraryDeclarations.isLibrary(source.node())) continue;
            var profile = TypeSemantics.profile(source.source());
            source.node().walk(new NodeVisitor() {
                private ClassDeclaration owner(TypeNode type) {
                    var cls = ClassSemantics.objectClass(TypeSemantics.withoutReference(type)).orElse(null);
                    return types.containsKey(cls) ? cls : null;
                }
                private ClassDeclaration owner(Expression expression) {
                    return TypeSemantics.expressionType(expression, profile).map(this::owner).orElse(null);
                }
                @Override public void visit(TypeNode type) {
                    var cls = owner(type); if (cls != null) sites.get(cls).add(context.copy(type));
                }
                private void add(DynamicArrayOperation.Kind kind, Node syntax, Node destination,
                        ClassDeclaration cls, CallableDeclaration target, List<? extends Expression> arguments) {
                    if (cls == null || target == null || !libraryTarget(target)) return;
                    boolean record = ClassSemantics.objectClass(elements.get(cls)).isPresent();
                    if (record && (kind == DynamicArrayOperation.Kind.COUNT || kind == DynamicArrayOperation.Kind.ASSIGN_COPY || kind == DynamicArrayOperation.Kind.ASSIGN_FILL
                            || kind == DynamicArrayOperation.Kind.ASSIGN_RANGE)) return;
                    if ((kind == DynamicArrayOperation.Kind.RANGE || kind == DynamicArrayOperation.Kind.ASSIGN_RANGE)
                            && (record || !pointerRange(arguments, profile))) return;
                    var operation = DynamicArrayOperation.create().setKind(kind).setSyntax(context.copy(syntax))
                            .setDestination(context.copy(destination)).setType(types.get(cls)).setSelectedTarget(context.copy(target));
                    var inputs = new ArrayList<NodeBuilder<? extends Node>>();
                    arguments.forEach(argument -> inputs.add(context.copy(argument))); operation.setSources(inputs);
                    // vector and the standard allocator copy through const T& even
                    // when the caller supplies a mutable lvalue to push_back/fill.
                    if (kind != DynamicArrayOperation.Kind.EMPTY) {
                        var selected = ClassSemantics.selectedElementCopyConstructor(elements.get(cls), null, profile);
                        if (record && selected.isEmpty() && ClassSemantics.objectClass(elements.get(cls)).stream()
                                .flatMap(owner -> ClassSemantics.specialMembers(owner).stream())
                                .anyMatch(member -> member.kind() == ClassSemantics.SpecialKind.COPY_CONSTRUCTOR
                                        && member.origin() != ClassSemantics.Origin.IMPLICIT)) return;
                        if (selected.isPresent() && !selected.get().access().orElse("public").equals("public")) return;
                        selected.ifPresent(copy -> operation.setElementCopyConstructor(context.copy(copy)));
                    }
                    operations.add(operation);
                }
                @Override public void visit(VariableDeclaration variable) {
                    if (!variable.isDefinition() || variable.type().isEmpty()) return;
                    TypeNode written = TypeSemantics.unqualified(variable.type().orElseThrow());
                    if (written instanceof ReferenceType || written instanceof ArrayType) return;
                    var cls = owner(written); if (cls == null) return;
                    var value = variable.initializer().map(DynamicArraySemantics::written).orElse(null);
                    List<Expression> arguments = value == null ? List.of()
                            : value instanceof InitializationExpression init && init.form() == InitializationExpression.Form.DIRECT
                            ? init.arguments() : List.of(value);
                    var target = variable.constructorTarget().orElse(null);
                    var kind = creation(cls, arguments, profile);
                    if (kind != null) add(kind, variable, variable, cls, target, arguments);
                }
                @Override public void visit(CallExpression call) {
                    if (!(call.callee() instanceof MemberExpression member)) return;
                    var cls = owner(member.receiver()); var target = call.target().orElse(null);
                    if (cls == null || target == null) return;
                    var args = call.arguments();
                    if (target.name().equals("push_back") && args.size() == 1)
                        add(DynamicArrayOperation.Kind.APPEND, call, member.receiver(), cls, target, args);
                    else if (target.name().equals("assign") && args.size() == 2) {
                        var kind = count(args.getFirst(), profile) ? DynamicArrayOperation.Kind.ASSIGN_FILL : DynamicArrayOperation.Kind.ASSIGN_RANGE;
                        add(kind, call, member.receiver(), cls, target, args);
                    } else if (target.name().equals("operator=") && args.size() == 1 && cls == owner(args.getFirst()))
                        add(DynamicArrayOperation.Kind.ASSIGN_COPY, call, member.receiver(), cls, target, args);
                }
                @Override public void visit(AssignmentExpression assignment) {
                    var cls = owner(assignment.target());
                    if (cls != null && cls == owner(assignment.value())) add(DynamicArrayOperation.Kind.ASSIGN_COPY,
                            assignment, assignment.target(), cls, assignment.operatorTarget().orElse(null), List.of(assignment.value()));
                }
            });
        }
        types.forEach((cls, type) -> type.setWrittenTypes(sites.get(cls)));
        root.setDynamicArrayTypes(new ArrayList<>(types.values())); root.setDynamicArraySemantics(operations);
        return root.build();
    }
    private static Expression written(Expression expression) {
        while (expression instanceof ConversionExpression conversion && !conversion.isExplicit()) expression = conversion.operand();
        return expression;
    }
    private static DynamicArrayOperation.Kind creation(ClassDeclaration cls, List<Expression> args, PlatformProfile profile) {
        if (args.isEmpty()) return DynamicArrayOperation.Kind.EMPTY;
        if (args.size() == 1) {
            if (TypeSemantics.expressionType(args.getFirst(), profile).flatMap(ClassSemantics::objectClass)
                    .filter(other -> ClassSemantics.sameClass(cls, other)).isPresent()) return DynamicArrayOperation.Kind.COPY;
            return count(args.getFirst(), profile) ? DynamicArrayOperation.Kind.COUNT : null;
        }
        // Explicit allocator arguments remain raw until their evaluation is modeled.
        if (args.size() != 2) return null;
        return count(args.getFirst(), profile) ? DynamicArrayOperation.Kind.FILL : DynamicArrayOperation.Kind.RANGE;
    }
    private static boolean pointerRange(List<? extends Expression> arguments, PlatformProfile profile) {
        return arguments.size() == 2 && arguments.stream().allMatch(argument -> TypeSemantics.expressionType(argument, profile)
                .map(TypeSemantics::decay).map(TypeSemantics::unqualified).filter(PointerType.class::isInstance).isPresent());
    }
    private static boolean count(Expression expression, PlatformProfile profile) {
        return TypeSemantics.expressionType(expression, profile).flatMap(type -> TypeSemantics.integerType(type, profile)).isPresent();
    }
    private static boolean bool(TypeNode type) {
        return TypeSemantics.unqualified(type) instanceof NamedType named && named.fundamentalKind().orElse(null) == FundamentalKind.BOOL;
    }
    private static boolean trusted(ClassDeclaration declaration, String name) {
        return LibraryDeclarations.isLibrary(declaration) && declaration.name().equals(name);
    }
    private static boolean libraryTarget(CallableDeclaration target) {
        return LibraryDeclarations.isLibrary(target) || target.templateOrigin().filter(LibraryDeclarations::isLibrary).isPresent();
    }
    private static boolean standardAllocator(Project project, TypeNode allocator, TypeNode element) {
        var cls = ClassSemantics.objectClass(allocator).orElse(null);
        if (cls == null || !trusted(cls, "allocator")) return false;
        for (var instance : project.classTemplateInstances()) if (instance.declaration() == cls)
            return trusted(instance.origin(), "allocator") && instance.selected() == instance.origin()
                    && instance.arguments().size() == 1 && instance.arguments().getFirst().type()
                    .filter(type -> type.canonicalIdentity().equals(element.canonicalIdentity())).isPresent();
        // Default template arguments may retain the trusted primary declaration rather
        // than a concrete allocator specialization; validate their written type argument.
        TypeNode plain = TypeSemantics.unqualified(allocator);
        return plain instanceof NamedType named && named.templateArguments().size() == 1
                && named.templateArguments().getFirst().type()
                        .filter(type -> type.canonicalIdentity().equals(element.canonicalIdentity())).isPresent()
                && project.classTemplateInstances().stream().anyMatch(instance -> instance.origin() == cls
                        && instance.selected() == cls && trusted(instance.origin(), "allocator")
                        && instance.arguments().size() == 1 && instance.arguments().getFirst().type()
                                .filter(type -> type.canonicalIdentity().equals(element.canonicalIdentity())).isPresent());
    }
}
