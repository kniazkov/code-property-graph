/* Conservative C++03 variable-argument operand validation. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.util.*;

/** The selected subset models opaque local state, not argument count/type matching
 * or whole-program lifecycle correctness. Unsupported valid forms stay explicit. */
public final class VarargsSemantics {
    private VarargsSemantics() {}

    public static Project analyze(Project project) {
        boolean[] present = {false};
        project.walk(new NodeVisitor() { @Override public void visit(VaListExpression node) { present[0] = true; } });
        if (!present[0]) return project;
        var editor = new ProjectEditor(project).retainDiagnostics().withoutRevalidation();
        var members = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        project.walk(new NodeVisitor() { @Override public void visit(Node node) { members.add(node); } });
        var fields = Collections.newSetFromMap(new IdentityHashMap<VariableDeclaration, Boolean>());
        project.walk(new NodeVisitor() { @Override public void visit(ClassDeclaration owner) {
            owner.members().stream().filter(VariableDeclaration.class::isInstance).map(VariableDeclaration.class::cast).forEach(fields::add);
        } });
        for (var root : SourceContexts.roots(project)) {
            if (root.source().language() != Language.CPP) continue;
            var profile = TypeSemantics.profile(root.source());
            var localMembers = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
            root.node().walk(new NodeVisitor() { @Override public void visit(Node node) { localMembers.add(node); } });
            Map<CallableDeclaration, Set<Node>> admitted = new IdentityHashMap<>();
            if (borrowed(profile)) root.node().walk(new NodeVisitor() { @Override public void visit(CallableDeclaration callable) {
                if (callable.body().isPresent()) admitted.put(callable, borrowedArguments(callable, profile, members, localMembers));
            } });
            inspect(root.node(), null, profile, members, localMembers, admitted, fields, new IntegralConstants(project, profile), editor, false);
        }
        return editor.build();
    }

    private static void inspect(Node node, CallableDeclaration owner, PlatformProfile profile,
            Set<Node> members, Set<Node> localMembers, Map<CallableDeclaration, Set<Node>> admitted, Set<VariableDeclaration> fields, IntegralConstants constants, ProjectEditor editor, boolean unevaluated) {
        if (node instanceof CallableDeclaration callable) { owner = callable; unevaluated = false; }
        unevaluated |= unevaluated(node, profile);
        // The initializer is separate evaluated syntax, even when the only list
        // use is beneath sizeof. Opaque va_list has no certified initializer model.
        if (node instanceof VariableDeclaration list && list.initializer().isPresent()
                && list.type().filter(type -> vaList(type, members)).isPresent())
            editor.diagnose(list, unsupported("list-initialization", "Initialization or copying of opaque va_list storage is outside this subset, including when its later use is unevaluated").orElseThrow());
        // Inspect calls in all syntax, including global/default initializers.
        // Only names explicitly admitted in the owner's body may cross a helper boundary.
        if (!unevaluated && borrowed(profile) && node instanceof CallExpression call) {
            var allowed = admitted.getOrDefault(owner, Set.of());
            var target = call.target().orElse(null);
            for (int i = 0; i < call.arguments().size(); i++) {
                var argument = call.arguments().get(i);
                TypeNode formal = target != null && i < target.parameters().size() ? target.parameters().get(i).type().orElse(null) : null;
                TypeNode actual = TypeSemantics.expressionType(argument, profile).orElse(null);
                if ((borrowedType(formal, profile) || borrowedType(actual, profile)) && !allowed.contains(argument))
                    editor.diagnose(call, unsupported("borrowed-call", "A borrowed list call requires its own adjacent start/call/end, direct automatic argument and supported same-TU helper").orElseThrow());
            }
        }
        if (node instanceof VaListExpression operation) {
            var problem = violation(operation, owner, profile, members, fields, constants, unevaluated);
            problem.ifPresent(value -> editor.diagnose(operation, value));
        }
        if (node instanceof CallableDeclaration callable && callable.body().isPresent()
                && (borrowed(profile) || containsOperation(callable.body().orElseThrow())))
            validateLocalUses(callable, profile, members, admitted.getOrDefault(callable, Set.of()), editor);
        for (Node child : node.children()) inspect(child, owner, profile, members, localMembers, admitted, fields, constants, editor, unevaluated);
    }

    private static Optional<Diagnostic> violation(VaListExpression operation, CallableDeclaration owner,
            PlatformProfile profile, Set<Node> members, Set<VariableDeclaration> fields, IntegralConstants constants, boolean unevaluated) {
        if (profile.varargs().isEmpty()) return invalid("capability", "Variable-argument operations require the explicit varargs compiler/ABI capability");
        // An unevaluated va_arg checks types and bindings, not an accessed cursor's
        // owner, storage duration, initialization or default argument promotions.
        boolean typeOnly = unevaluated && operation.kind() == VaListExpression.Kind.ARG;
        boolean borrowed = borrowed(profile) && owner != null && owner.body().isPresent()
                && operation.list() instanceof NameExpression operand
                && operand.target().filter(owner.parameters()::contains).isPresent();
        if (!typeOnly && !borrowed && (owner == null || owner.body().isEmpty() || !owner.variadic()))
            return operation.kind() == VaListExpression.Kind.ARG
                    ? unsupported("function", "This subset requires a variadic function definition containing the local va_list")
                    : invalid("function", "C++03 va_start and va_end must occur in the function accepting the varying arguments");
        if (!(operation.list() instanceof NameExpression name))
            return unsupported("list-expression", "Only a direct name bound to va_list state is supported");
        Declaration target = name.target().filter(members::contains).orElse(null);
        TypeNode listType;
        if (target instanceof ParameterDeclaration parameter) {
            listType = parameter.type().orElse(null);
            if (!typeOnly && !borrowed && listType != null && vaList(listType, members))
                return unsupported("list-storage", "Interprocedural va_list parameters are outside this local-state subset");
            if (!typeOnly && !borrowed) return invalid("list-binding", "The list operand must bind a variable in this snapshot");
        } else if (target instanceof VariableDeclaration list) {
            if (fields.contains(list)) return unsupported("list-expression", "Class-member va_list storage is outside this direct-list subset, including unevaluated uses");
            listType = list.type().orElse(null);
            if (!typeOnly && (!ownedLocal(owner.body().orElseThrow(), list)
                    || list.modifiers().contains("static") || list.modifiers().contains("extern")))
                return unsupported("list-storage", "Only automatic va_list storage in the current function is supported");
            if (!typeOnly && list.initializer().isPresent())
                return unsupported("list-copy", "Copied or initialized va_list objects are not supported");
        } else return invalid("list-binding", "The list operand must bind a variable in this snapshot");
        if (listType != null && TypeSemantics.unqualified(listType) instanceof ReferenceType)
            return unsupported("list-expression", "Reference-bound va_list operands are outside this direct-list subset");
        if (listType == null || !vaList(listType, members))
            return invalid("list-type", "The list operand must bind the standard-library va_list type, not a spelling-compatible user type");
        if (TypeSemantics.qualifiers(listType) != 0)
            return unsupported("qualified-list", "Const or volatile va_list state is outside the selected GCC/Clang contract");
        if (!typeOnly && borrowed && (operation.kind() != VaListExpression.Kind.ARG || owner.variadic() || !(owner instanceof FunctionDeclaration)
                || owner.parameters().stream().filter(p -> p.type().filter(t -> vaList(t, members)).isPresent()).count() != 1
                || owner.templateKind() != TemplateKind.NONE || !owner.templateParameters().isEmpty() || owner.templateOrigin().isPresent()))
            return unsupported("borrowed-operation", "A borrowed nonvariadic helper may only retrieve from the caller-owned list; start/end remain in its variadic owner");
        if (operation.kind() == VaListExpression.Kind.START) {
            Expression last = operation.lastParameter().orElseThrow();
            if (!(last instanceof NameExpression parameter) || owner.parameters().isEmpty()
                    || parameter.target().orElse(null) != owner.parameters().getLast())
                return invalid("last-parameter", "va_start must name the actual rightmost named parameter of this variadic definition");
            TypeNode type = owner.parameters().getLast().type().orElse(null);
            if (type == null || !boundType(type, members)) return invalid("last-parameter-type", "The last parameter requires a bound type");
            TypeNode plain = TypeSemantics.unqualified(type);
            if (plain instanceof ReferenceType || plain instanceof ArrayType || plain instanceof FunctionType || promotedType(plain)
                    || EnumSemantics.declaration(plain).isPresent())
                return invalid("last-parameter-type", "C++03 va_start excludes reference, array, function and default-promoted last-parameter types");
            if (!retrievalType(type, members, profile, constants, false)) return unsupported("last-parameter-type", "The last parameter type is outside the selected scalar/pointer subset");
        }
        if (operation.kind() == VaListExpression.Kind.ARG) {
            TypeNode type = operation.requestedType().orElseThrow();
            if (dependentType(type, Collections.newSetFromMap(new IdentityHashMap<>()))) return unsupported("argument-type", "Dependent va_arg type operands are outside this subset");
            if (!boundType(type, members)) return invalid("argument-type", "va_arg requires a bound type name, not an expression or foreign binding");
            if (!typeOnly && (promotedType(TypeSemantics.unqualified(type)) || EnumSemantics.declaration(type).isPresent()))
                return invalid("promoted-argument-type", "va_arg cannot retrieve an unpromoted float, bool, char, short, wchar_t or enumeration; request the actual promoted type");
            if (!retrievalType(type, members, profile, constants, typeOnly))
                return unsupported("argument-type", "va_arg supports selected scalar types and pointer types; complex pointers require a written bound typedef name");
        }
        return Optional.empty();
    }

    private static boolean containsOperation(Node node) {
        if (node instanceof VaListExpression) return true;
        if (node instanceof CallableDeclaration || node instanceof ClassDeclaration) return false;
        return node.children().stream().anyMatch(VarargsSemantics::containsOperation);
    }

    private static boolean ownedLocal(Node root, Node target) {
        if (root == target) return true;
        if (root instanceof CallableDeclaration || root instanceof ClassDeclaration) return false;
        return root.children().stream().anyMatch(child -> ownedLocal(child, target));
    }

    static boolean vaList(TypeNode type, Set<Node> members) {
        var seen = Collections.newSetFromMap(new IdentityHashMap<TypeNode, Boolean>());
        while (seen.add(type)) {
            if (type instanceof QualifiedType qualified) type = qualified.baseType();
            else if (type instanceof NamedType name && name.alias().filter(members::contains).isPresent()) type = name.alias().orElseThrow().type();
            else return type instanceof OpaqueLibraryType opaque && members.contains(opaque)
                    && opaque.category() == OpaqueLibraryType.Category.OBJECT && opaque.identity().equals("library::va_list")
                    && LibraryDeclarations.isLibrary(opaque);
        }
        return false;
    }

    private static boolean boundType(TypeNode type, Set<Node> members) {
        return boundType(type, members, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    private static boolean boundType(TypeNode type, Set<Node> members, Set<TypeNode> active) {
        if (!active.add(type)) return false;
        try {
            if (type instanceof NamedType name) {
                if (name.dependent() || name.hasTemplateArguments()) return false;
                if (name.alias().isPresent()) return members.contains(name.alias().orElseThrow()) && boundType(name.alias().orElseThrow().type(), members, active);
                if (name.fundamentalKind().isPresent()) return true;
                return name.declaration().filter(members::contains).filter(d -> d instanceof ClassDeclaration || d instanceof EnumDeclaration).isPresent();
            }
            if (type instanceof QualifiedType qualified) return boundType(qualified.baseType(), members, active);
            if (type instanceof PointerType pointer) return boundType(pointer.pointee(), members, active);
            if (type instanceof ReferenceType reference) return boundType(reference.referredType(), members, active);
            if (type instanceof ArrayType array) return boundType(array.elementType(), members, active);
            if (type instanceof MemberPointerType pointer) return boundType(pointer.ownerType(), members, active) && boundType(pointer.pointee(), members, active);
            if (type instanceof FunctionType function) return boundType(function.returnType(), members, active)
                    && function.parameters().stream().allMatch(p -> p.type().filter(t -> boundType(t, members, active)).isPresent())
                    && function.exceptionTypes().stream().allMatch(t -> boundType(t, members, active));
            return type instanceof OpaqueLibraryType && members.contains(type) && LibraryDeclarations.isLibrary(type);
        } finally { active.remove(type); }
    }

    private static boolean dependentType(TypeNode type, Set<TypeNode> seen) {
        if (!seen.add(type)) return false;
        if (TypeSemantics.dependent(type)) return true;
        if (type instanceof NamedType name && name.alias().isPresent())
            return dependentType(name.alias().orElseThrow().type(), seen);
        for (Node child : type.children()) {
            if (child instanceof TypeNode nested && dependentType(nested, seen)) return true;
            if (child instanceof ParameterDeclaration parameter && parameter.type().filter(value -> dependentType(value, seen)).isPresent()) return true;
        }
        return false;
    }

    private static boolean promotedType(TypeNode type) {
        return type instanceof NamedType name && name.fundamentalKind().map(kind -> Set.of("bool", "char", "signed char", "unsigned char", "short", "unsigned short", "wchar_t", "float").contains(kind.spelling())).orElse(false);
    }

    private static boolean retrievalType(TypeNode type, Set<Node> members, PlatformProfile profile, IntegralConstants constants, boolean typeOnly) {
        if (!boundType(type, members)) return false;
        TypeNode plain = TypeSemantics.unqualified(type);
        if (plain instanceof NamedType name) return name.fundamentalKind().map(kind ->
                Set.of("int", "unsigned int", "long", "unsigned long", "double", "long double").contains(kind.spelling())
                        || typeOnly && promotedType(name)).orElse(typeOnly && EnumSemantics.declaration(plain).isPresent());
        if (!(plain instanceof PointerType pointer) || !appendStarSpelling(type)) return false;
        TypeNode pointee = TypeSemantics.unqualified(pointer.pointee());
        if (pointee instanceof ReferenceType || pointee instanceof MemberPointerType) return false;
        if (pointee instanceof FunctionType || pointee instanceof ArrayType) {
            // C90 7.8.1.2 requires pointer-to-requested-object syntax obtainable by
            // simply appending '*'. Keep the written alias instead of accepting a
            // raw abstract declarator merely because a compiler builtin accepts it.
            TypeNode written = type;
            while (written instanceof QualifiedType qualified) written = qualified.baseType();
            if (!(written instanceof NamedType name) || name.alias().isEmpty()) return false;
        }
        return supportedPointerShape(plain, constants, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private static boolean appendStarSpelling(TypeNode written) {
        if (written instanceof NamedType) return true;
        if (written instanceof QualifiedType qualified) return appendStarSpelling(qualified.baseType());
        return written instanceof PointerType pointer && appendStarSpelling(pointer.pointee());
    }

    /** Bound aliases have already been checked; this pass limits signature/array
     * shapes without deriving layout or a concrete target for an indirect call. */
    private static boolean supportedPointerShape(TypeNode type, IntegralConstants constants, Set<TypeNode> active) {
        if (!active.add(type)) return false;
        try {
            if (type instanceof QualifiedType qualified) return (!(qualified.isConst() || qualified.isVolatile())
                    || !(TypeSemantics.unqualified(qualified.baseType()) instanceof FunctionType))
                    && supportedPointerShape(qualified.baseType(), constants, active);
            if (type instanceof NamedType name) return name.alias().isEmpty()
                    || supportedPointerShape(name.alias().orElseThrow().type(), constants, active);
            if (type instanceof PointerType pointer) return !(TypeSemantics.unqualified(pointer.pointee()) instanceof ReferenceType)
                    && supportedPointerShape(pointer.pointee(), constants, active);
            if (type instanceof ReferenceType reference) return !voidType(reference.referredType())
                    && !(TypeSemantics.unqualified(reference.referredType()) instanceof ReferenceType)
                    && supportedPointerShape(reference.referredType(), constants, active);
            if (type instanceof ArrayType array) return !voidType(array.elementType())
                    && !(TypeSemantics.unqualified(array.elementType()) instanceof ReferenceType)
                    && !(TypeSemantics.unqualified(array.elementType()) instanceof FunctionType)
                    && array.bound()
                    .flatMap(constants::evaluate)
                    .filter(value -> value.number().signum() > 0).isPresent()
                    && supportedPointerShape(array.elementType(), constants, active);
            if (type instanceof FunctionType function) return !function.isConst() && !function.isVolatile()
                    && !function.hasExceptionSpecification()
                    && !(TypeSemantics.unqualified(function.returnType()) instanceof ArrayType)
                    && !(TypeSemantics.unqualified(function.returnType()) instanceof FunctionType)
                    && supportedPointerShape(function.returnType(), constants, active)
                    && function.parameters().stream().allMatch(parameter -> parameter.type()
                            .filter(value -> (!voidType(value) || function.parameters().size() == 1
                                    && parameter.name().isEmpty() && !function.variadic() && TypeSemantics.qualifiers(value) == 0)
                                    && parameter.defaultValue().isEmpty()
                                    && supportedPointerShape(value, constants, active)).isPresent());
            return type instanceof OpaqueLibraryType;
        } finally { active.remove(type); }
    }

    private static boolean voidType(TypeNode type) {
        return TypeSemantics.unqualified(type) instanceof NamedType name
                && name.fundamentalKind().orElse(null) == FundamentalKind.VOID;
    }

    private static boolean unevaluated(Node node, PlatformProfile profile) {
        return node instanceof SizeofExpression || node instanceof OffsetofExpression
                || node instanceof TypeidExpression typeid && TypeSemantics.typeidOperandUnevaluated(typeid, profile);
    }

    private static void validateLocalUses(CallableDeclaration owner, PlatformProfile profile, Set<Node> members, Set<Node> allowed, ProjectEditor editor) {
        var localLists = Collections.newSetFromMap(new IdentityHashMap<Declaration, Boolean>());
        if (borrowed(profile)) owner.parameters().stream().filter(p -> p.type().filter(t -> vaList(t, members)).isPresent()).forEach(localLists::add);
        owner.body().orElseThrow().walk(new NodeVisitor() {
            @Override public void visit(VariableDeclaration variable) {
                if (ownedLocal(owner.body().orElseThrow(), variable) && variable.type().filter(t -> vaList(t, members)).isPresent()) localLists.add(variable);
            }
        });
        if (borrowed(profile)) owner.body().orElseThrow().walk(new NodeVisitor() {
            @Override public void visit(NameExpression name) {
                name.target().filter(d -> d instanceof VariableDeclaration || d instanceof ParameterDeclaration).ifPresent(d -> {
                    var type = d instanceof VariableDeclaration v ? v.type() : ((ParameterDeclaration)d).type();
                    if (type.filter(t -> vaList(t, members)).isPresent()) localLists.add(d);
                });
            }
        });
        validateUses(owner.body().orElseThrow(), localLists, profile, editor, allowed, false, false);
    }
    private static void validateUses(Node node, Set<Declaration> lists, PlatformProfile profile, ProjectEditor editor, Set<Node> allowed, boolean unevaluated, boolean directList) {
        if (node instanceof CallableDeclaration || node instanceof ClassDeclaration) return;
        unevaluated |= unevaluated(node, profile);
        if (!unevaluated && node instanceof NameExpression name && name.target().filter(lists::contains).isPresent() && !directList && !allowed.contains(name))
            editor.diagnose(name, unsupported("escaped-list", "Copying, escaping or indirectly accessing va_list is outside this local-state subset").orElseThrow());
        for (Node child : node.children()) validateUses(child, lists, profile, editor, allowed, unevaluated,
                node instanceof VaListExpression operation && child == operation.list());
    }
    static boolean borrowed(PlatformProfile profile) {
        return profile.varargs().map(com.kniazkov.cpg.build.VarargsModel::borrowedLists).orElse(false);
    }
    static boolean borrowedType(TypeNode type, PlatformProfile profile) {
        if (!borrowed(profile) || type == null) return false;
        TypeNode plain = TypeSemantics.unqualified(type);
        return plain instanceof OpaqueLibraryType opaque && opaque.identity().equals("library::va_list")
                && opaque.category() == OpaqueLibraryType.Category.OBJECT && LibraryDeclarations.isLibrary(opaque);
    }

    /** Only a direct call in a start/call/end block is admitted. This is a
     * syntactic bounded contract, not a general interprocedural lifecycle proof. */
    private static Set<Node> borrowedArguments(CallableDeclaration owner, PlatformProfile profile, Set<Node> members, Set<Node> localMembers) {
        var allowed = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        if (!owner.variadic()) return allowed;
        owner.body().orElseThrow().walk(new NodeVisitor() { @Override public void visit(BlockStatement block) {
            if (!ownedLocal(owner.body().orElseThrow(), block)) return;
            var items = block.items();
            for (int i = 1; i + 1 < items.size(); i++) {
                Expression expression = items.get(i) instanceof ExpressionStatement statement ? statement.expression()
                        : items.get(i) instanceof VariableDeclaration variable ? variable.initializer().orElse(null) : null;
                if (!(expression instanceof CallExpression call) || !(call.callee() instanceof NameExpression)
                        || call.isVirtualDispatch()) continue;
                var target = call.target().filter(localMembers::contains).orElse(null);
                if (!(target instanceof FunctionDeclaration) || target.body().isEmpty() || target.variadic()
                        || target.templateKind() != TemplateKind.NONE || !target.templateParameters().isEmpty() || target.templateOrigin().isPresent()
                        || target.parameters().stream().filter(p -> p.type().filter(t -> vaList(t, members)).isPresent()).count() != 1
                        || call.arguments().size() != target.parameters().size()) continue;
                for (int a = 0; a < call.arguments().size(); a++) {
                    var argument = call.arguments().get(a);
                    var parameter = target.parameters().get(a);
                    if (!(argument instanceof NameExpression name)
                            || !(name.target().orElse(null) instanceof VariableDeclaration list)
                            || !ownedLocal(owner.body().orElseThrow(), list)
                            || list.initializer().isPresent() || list.modifiers().contains("static") || list.modifiers().contains("extern")
                            || !list.type().filter(t -> vaList(t, members) && TypeSemantics.qualifiers(t) == 0).isPresent()
                            || !parameter.type().filter(t -> vaList(t, members) && TypeSemantics.qualifiers(t) == 0).isPresent()) continue;
                    long occurrences = call.arguments().stream().mapToLong(e -> evaluatedReferences(e, list, profile)).sum();
                    if (occurrences == 1 && listOperation(items.get(i - 1), list, VaListExpression.Kind.START)
                            && listOperation(items.get(i + 1), list, VaListExpression.Kind.END)) allowed.add(name);
                }
            }
        }});
        return allowed;
    }
    private static long evaluatedReferences(Node node, Declaration list, PlatformProfile profile) {
        if (unevaluated(node, profile)) return 0;
        long self = node instanceof NameExpression name && name.target().orElse(null) == list ? 1 : 0;
        return self + node.children().stream().mapToLong(child -> evaluatedReferences(child, list, profile)).sum();
    }
    private static boolean listOperation(Node node, Declaration list, VaListExpression.Kind kind) {
        return node instanceof ExpressionStatement statement && statement.expression() instanceof VaListExpression operation
                && operation.kind() == kind && operation.list() instanceof NameExpression name && name.target().orElse(null) == list;
    }

    private static Optional<Diagnostic> invalid(String suffix, String text) {
        return Optional.of(new Diagnostic("invalid-varargs-" + suffix, Diagnostic.Severity.ERROR, text));
    }
    private static Optional<Diagnostic> unsupported(String suffix, String text) {
        return Optional.of(new Diagnostic("unsupported-varargs-" + suffix, Diagnostic.Severity.ERROR, text));
    }
}
