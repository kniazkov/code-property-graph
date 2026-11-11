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
        for (var root : SourceContexts.roots(project)) {
            if (root.source().language() != Language.CPP) continue;
            var profile = TypeSemantics.profile(root.source());
            inspect(root.node(), null, profile, members, editor);
        }
        return editor.build();
    }

    private static void inspect(Node node, CallableDeclaration owner, PlatformProfile profile,
            Set<Node> members, ProjectEditor editor) {
        if (node instanceof CallableDeclaration callable) owner = callable;
        if (node instanceof VaListExpression operation) {
            var problem = violation(operation, owner, profile, members);
            problem.ifPresent(value -> editor.diagnose(operation, value));
        }
        if (node instanceof CallableDeclaration callable && callable.body().isPresent() && containsOperation(callable.body().orElseThrow()))
            validateLocalUses(callable, members, editor);
        for (Node child : node.children()) inspect(child, owner, profile, members, editor);
    }

    private static Optional<Diagnostic> violation(VaListExpression operation, CallableDeclaration owner,
            PlatformProfile profile, Set<Node> members) {
        if (profile.varargs().isEmpty()) return invalid("capability", "Variable-argument operations require the explicit varargs compiler/ABI capability");
        if (owner == null || owner.body().isEmpty() || !owner.variadic())
            return operation.kind() == VaListExpression.Kind.ARG
                    ? unsupported("function", "This subset requires a variadic function definition containing the local va_list")
                    : invalid("function", "C++03 va_start and va_end must occur in the function accepting the varying arguments");
        if (!(operation.list() instanceof NameExpression name))
            return unsupported("list-expression", "Only a direct name bound to local va_list state is supported");
        if (name.target().orElse(null) instanceof ParameterDeclaration parameter && members.contains(parameter)
                && parameter.type().filter(type -> vaList(type, members)).isPresent())
            return unsupported("list-storage", "Interprocedural va_list parameters are outside this local-state subset");
        if (!(name.target().orElse(null) instanceof VariableDeclaration list) || !members.contains(list))
            return invalid("list-binding", "The list operand must bind a variable in this snapshot");
        if (!ownedLocal(owner.body().orElseThrow(), list) || list.modifiers().contains("static") || list.modifiers().contains("extern"))
            return unsupported("list-storage", "Only automatic va_list storage in the current function is supported");
        if (list.type().isEmpty() || !vaList(list.type().orElseThrow(), members))
            return invalid("list-type", "The list operand must bind the standard-library va_list type, not a spelling-compatible user type");
        if (TypeSemantics.qualifiers(list.type().orElseThrow()) != 0)
            return unsupported("qualified-list", "Const or volatile va_list state is outside this mutable local-state subset");
        if (list.initializer().isPresent()) return unsupported("list-copy", "Copied or initialized va_list objects are not supported");
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
            if (!retrievalType(type, members)) return unsupported("last-parameter-type", "The last parameter type is outside the selected scalar/object-pointer subset");
        }
        if (operation.kind() == VaListExpression.Kind.ARG) {
            TypeNode type = operation.requestedType().orElseThrow();
            if (TypeSemantics.dependent(type)) return unsupported("argument-type", "Dependent va_arg type operands are outside this subset");
            if (!boundType(type, members)) return invalid("argument-type", "va_arg requires a bound type name, not an expression or foreign binding");
            if (promotedType(TypeSemantics.unqualified(type)))
                return invalid("promoted-argument-type", "va_arg cannot retrieve an unpromoted float, bool, char, short or wchar_t; request the actual promoted type");
            if (!retrievalType(type, members))
                return unsupported("argument-type", "va_arg supports the selected promoted scalar and object-pointer types only");
        }
        return Optional.empty();
    }

    private static boolean containsOperation(Node node) {
        if (node instanceof VaListExpression) return true;
        if (node instanceof CallableDeclaration || node instanceof ClassDeclaration) return false;
        return node.children().stream().anyMatch(VarargsSemantics::containsOperation);
    }

    private static boolean ownedLocal(Node root, VariableDeclaration target) {
        if (root == target) return true;
        if (root instanceof CallableDeclaration || root instanceof ClassDeclaration) return false;
        return root.children().stream().anyMatch(child -> ownedLocal(child, target));
    }

    private static boolean vaList(TypeNode type, Set<Node> members) {
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
                    && function.parameters().stream().allMatch(p -> p.type().filter(t -> boundType(t, members, active)).isPresent());
            return type instanceof OpaqueLibraryType && members.contains(type) && LibraryDeclarations.isLibrary(type);
        } finally { active.remove(type); }
    }

    private static boolean promotedType(TypeNode type) {
        return type instanceof NamedType name && name.fundamentalKind().map(kind -> Set.of("bool", "char", "signed char", "unsigned char", "short", "unsigned short", "wchar_t", "float").contains(kind.spelling())).orElse(false);
    }

    private static boolean retrievalType(TypeNode type, Set<Node> members) {
        if (!boundType(type, members)) return false;
        TypeNode plain = TypeSemantics.unqualified(type);
        if (plain instanceof NamedType name) return name.fundamentalKind().map(kind ->
                Set.of("int", "unsigned int", "long", "unsigned long", "double", "long double").contains(kind.spelling())).orElse(false);
        if (!(plain instanceof PointerType pointer)) return false;
        TypeNode pointee = TypeSemantics.unqualified(pointer.pointee());
        // Pointer typedefs are valid type names, but callable/member-pointer and
        // complex array declarators need a separate compiler contract.
        return !(pointee instanceof FunctionType || pointee instanceof ReferenceType || pointee instanceof ArrayType || pointee instanceof MemberPointerType);
    }

    private static void validateLocalUses(CallableDeclaration owner, Set<Node> members, ProjectEditor editor) {
        var localLists = Collections.newSetFromMap(new IdentityHashMap<VariableDeclaration, Boolean>());
        owner.body().orElseThrow().walk(new NodeVisitor() {
            @Override public void visit(VariableDeclaration variable) {
                if (ownedLocal(owner.body().orElseThrow(), variable) && variable.type().filter(t -> vaList(t, members)).isPresent()) localLists.add(variable);
            }
        });
        validateUses(owner.body().orElseThrow(), localLists, editor, false, false);
    }
    private static void validateUses(Node node, Set<VariableDeclaration> lists, ProjectEditor editor, boolean unevaluated, boolean directList) {
        if (node instanceof CallableDeclaration || node instanceof ClassDeclaration) return;
        if (node instanceof SizeofExpression || node instanceof OffsetofExpression) unevaluated = true;
        if (!unevaluated && node instanceof NameExpression name && name.target().filter(lists::contains).isPresent() && !directList)
            editor.diagnose(name, unsupported("escaped-list", "Copying, escaping or indirectly accessing va_list is outside this local-state subset").orElseThrow());
        for (Node child : node.children()) validateUses(child, lists, editor, unevaluated,
                node instanceof VaListExpression operation && child == operation.list());
    }
    private static Optional<Diagnostic> invalid(String suffix, String text) {
        return Optional.of(new Diagnostic("invalid-varargs-" + suffix, Diagnostic.Severity.ERROR, text));
    }
    private static Optional<Diagnostic> unsupported(String suffix, String text) {
        return Optional.of(new Diagnostic("unsupported-varargs-" + suffix, Diagnostic.Severity.ERROR, text));
    }
}
