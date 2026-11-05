/* C++03 canonical identities without platform-dependent conflation. MIT License. */
package com.kniazkov.cpg.model;

import java.util.*;
import java.util.stream.Collectors;

/** Structural identities separate written names and declarators from their meaning. */
public final class TypeIdentity {
    private TypeIdentity() {}
    public static TypeNode canonicalType(TypeNode type) {
        Set<TypeNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (type instanceof NamedType named && named.alias().isPresent()) {
            if (!seen.add(type)) throw new IllegalStateException("Cyclic typedef identity");
            type = named.alias().orElseThrow().type();
        }
        return type;
    }
    public static String canonicalIdentity(TypeNode type) {
        return canonicalIdentity(type, Map.of());
    }
    /** Contextual identities for bound dependent types, keyed by declaration rather than spelling. */
    public static String canonicalIdentity(TypeNode type, Map<? extends Declaration, String> dependentNames) {
        return identity(type, Collections.newSetFromMap(new IdentityHashMap<>()), dependentNames);
    }
    private static String identity(TypeNode written, Set<TypeNode> seen, Map<? extends Declaration, String> dependentNames) {
        TypeNode type = canonicalType(written);
        if (!seen.add(type)) throw new IllegalStateException("Cyclic type identity");
        try {
            return switch (type) {
                case NamedType n -> namedIdentity(n, seen, dependentNames);
                case OpaqueLibraryType o -> "opaque-library(" + o.identity() + ")";
                case QualifiedType q -> qualified(q.baseType(), q.isConst(), q.isVolatile(), seen, dependentNames);
                case PointerType p -> "pointer(" + identity(p.pointee(), seen, dependentNames) + ")";
                case ReferenceType r -> "reference(" + identity(r.referredType(), seen, dependentNames) + ")";
                case ArrayType a -> "array(" + bound(a, seen, dependentNames) + "," + identity(a.elementType(), seen, dependentNames) + ")";
                case FunctionType f -> "function(" + f.parameters().stream().map(p -> parameter(p.type().orElseThrow(), seen, dependentNames)).collect(Collectors.joining(","))
                        + (f.variadic() ? ",..." : "") + ")->" + identity(f.returnType(), seen, dependentNames)
                        + (f.isConst() ? " const" : "") + (f.isVolatile() ? " volatile" : "");
                case MemberPointerType p -> "member-pointer(" + identity(p.ownerType(), seen, dependentNames) + "," + identity(p.pointee(), seen, dependentNames) + ")";
                default -> throw new IllegalArgumentException("Unknown type identity: " + type.getClass().getName());
            };
        } finally { seen.remove(type); }
    }
    private static String qualified(TypeNode written, boolean c, boolean v, Set<TypeNode> seen, Map<? extends Declaration, String> dependentNames) {
        TypeNode type = canonicalType(written);
        var visited = Collections.newSetFromMap(new IdentityHashMap<TypeNode, Boolean>());
        while (type instanceof QualifiedType q) {
            if (!visited.add(type)) throw new IllegalStateException("Cyclic qualified type identity");
            c |= q.isConst(); v |= q.isVolatile(); type = canonicalType(q.baseType());
        }
        // Typedef-applied array cv belongs to every array element; references ignore it.
        if (type instanceof ArrayType a) {
            if (!seen.add(type)) throw new IllegalStateException("Cyclic array type identity");
            try { return "array(" + bound(a, seen, dependentNames) + "," + qualified(a.elementType(), c, v, seen, dependentNames) + ")"; }
            finally { seen.remove(type); }
        }
        if (type instanceof ReferenceType) return identity(type, seen, dependentNames);
        return (c ? "const(" : "") + (v ? "volatile(" : "") + identity(type, seen, dependentNames) + (v ? ")" : "") + (c ? ")" : "");
    }
    /** Parameter identity with array/function adjustment and top-level cv removal. */
    public static String parameterIdentity(TypeNode written) {
        return parameterIdentity(written, Map.of());
    }
    /** Parameter adjustment with contextual identities for bound dependent types. */
    public static String parameterIdentity(TypeNode written, Map<? extends Declaration, String> dependentNames) {
        return parameter(written, Collections.newSetFromMap(new IdentityHashMap<>()), dependentNames);
    }
    private static String parameter(TypeNode written, Set<TypeNode> seen, Map<? extends Declaration, String> dependentNames) {
        TypeNode type = canonicalType(written);
        boolean c = false, v = false;
        var visited = Collections.newSetFromMap(new IdentityHashMap<TypeNode, Boolean>());
        while (type instanceof QualifiedType q) {
            if (!visited.add(type)) throw new IllegalStateException("Cyclic parameter type identity");
            c |= q.isConst(); v |= q.isVolatile(); type = canonicalType(q.baseType());
        }
        // Adjust arrays before discarding top-level parameter cv: these qualifiers
        // belong to the pointed-to element, not to the resulting pointer.
        if (type instanceof ArrayType a) return "pointer(" + qualified(a.elementType(), c, v, seen, dependentNames) + ")";
        if (type instanceof FunctionType f) return "pointer(" + identity(f, seen, dependentNames) + ")";
        return identity(type, seen, dependentNames);
    }
    private static String namedIdentity(NamedType type, Set<TypeNode> seen, Map<? extends Declaration, String> dependentNames) {
        if (type.fundamentalKind().isPresent()) return type.fundamentalKind().orElseThrow().spelling();
        String name = type.declaration().map(declaration -> dependentNames.getOrDefault(declaration,
                declaration.typeIdentity().orElse(type.name()))).orElse(type.name());
        if (type.qualifier().filter(qualifier -> contextualQualifier(qualifier, dependentNames)).isPresent()) {
            name = identity(type.qualifier().orElseThrow(), seen, dependentNames) + "::" + type.name().substring(type.name().lastIndexOf("::") + 2);
        }
        // A concrete class-instance declaration already carries normalized actual arguments,
        // including defaults; the written template-id must not append a second identity.
        if (type.declaration().orElse(null) instanceof ClassDeclaration owner
                && owner.templateKind() == TemplateKind.NONE && owner.hasTemplateArguments()) return "named(" + name + ")";
        return "named(" + name + arguments(type.templateArguments(), type.hasTemplateArguments(), seen, dependentNames) + ")";
    }
    private static boolean contextualQualifier(NamedType type, Map<? extends Declaration, String> dependentNames) {
        return type.hasTemplateArguments() || type.declaration().filter(dependentNames::containsKey).isPresent()
                || type.qualifier().map(qualifier -> contextualQualifier(qualifier, dependentNames)).orElse(false);
    }
    private static String arguments(List<TemplateArgument> arguments, boolean present, Set<TypeNode> seen, Map<? extends Declaration, String> dependentNames) {
        if (!present) return "";
        return arguments.stream().map(argument -> argument.kind().name() + "(" + argument.type()
                .map(type -> identity(type, seen, dependentNames)).orElseGet(() -> expression(argument.expression().orElseThrow(), seen, dependentNames)) + ")")
                .collect(Collectors.joining(",", "<", ">"));
    }
    /** Structural value syntax normalizes bound parameter references by declaration position. */
    private static String expression(Expression value, Set<TypeNode> seen, Map<? extends Declaration, String> dependentNames) {
        if (value instanceof IntegerLiteral literal) return "integer(" + literal.cppType().orElse("int") + ":" + literal.bigValue() + ")";
        if (value instanceof BooleanLiteral literal) return "boolean(" + literal.value() + ")";
        if (value instanceof CharacterLiteral literal) return "character(" + literal.cppType() + ":" + literal.value() + ")";
        if (value instanceof FloatingLiteral literal) return "floating(" + literal.cppType() + ":" + literal.value().stripTrailingZeros() + ")";
        if (value instanceof StringLiteral literal) return "string(" + literal.wide() + ":" + literal.codeUnits() + ")";
        if (value instanceof NameExpression name) return "name(" + name.target().map(target -> dependentNames.getOrDefault(target,
                target.typeIdentity().orElse(name.name()))).orElse(name.name())
                + arguments(name.templateArguments(), name.hasTemplateArguments(), seen, dependentNames) + ")";
        if (value instanceof MemberExpression member) return value.getClass().getSimpleName() + "(" + expression(member.receiver(), seen, dependentNames)
                + "," + member.member() + arguments(member.templateArguments(), member.hasTemplateArguments(), seen, dependentNames) + ")";
        String tag = value.getClass().getSimpleName();
        if (value instanceof ConversionExpression conversion) tag += ":" + conversion.kind();
        if (value instanceof CompoundAssignmentExpression assignment) tag += ":" + assignment.operator();
        return tag + value.children().stream().map(child -> {
            if (child instanceof Expression childExpression) return expression(childExpression, seen, dependentNames);
            if (child instanceof TypeNode type) return identity(type, seen, dependentNames);
            throw new IllegalArgumentException("Unsupported value-argument child " + child.getClass().getName());
        }).collect(Collectors.joining(",", "(", ")"));
    }
    private static String bound(ArrayType type, Set<TypeNode> seen, Map<? extends Declaration, String> dependentNames) {
        return type.bound().map(e -> portableInteger(e).map(Object::toString).orElseGet(() -> expression(e, seen, dependentNames))).orElse("");
    }
    /** Only folds values representable by every C++03 signed int. Target-dependent bounds stay symbolic. */
    private static Optional<Integer> portableInteger(Expression expression) {
        long value;
        if (expression instanceof IntegerLiteral literal && literal.cppType().map(type -> type.equals("int")).orElse(true)) {
            if (literal.bigValue().bitLength() > 15) return Optional.empty();
            value = literal.bigValue().longValue();
        }
        else if (expression instanceof UnaryExpression unary) {
            var operand = portableInteger(unary.operand()); if (operand.isEmpty()) return Optional.empty();
            if (unary instanceof UnaryPlusExpression) value = operand.orElseThrow();
            else if (unary instanceof NegateExpression) value = -operand.orElseThrow();
            else return Optional.empty();
        } else if (expression instanceof BinaryExpression binary) {
            var a = portableInteger(binary.left()); var b = portableInteger(binary.right());
            if (a.isEmpty() || b.isEmpty()) return Optional.empty();
            long left = a.orElseThrow(), right = b.orElseThrow();
            if (binary instanceof AddExpression) value = left + right;
            else if (binary instanceof SubtractExpression) value = left - right;
            else if (binary instanceof MultiplyExpression) value = left * right;
            else if (binary instanceof DivideExpression && left >= 0 && right > 0) value = left / right;
            else if (binary instanceof RemainderExpression && left >= 0 && right > 0) value = left % right;
            else return Optional.empty();
        } else return Optional.empty();
        return value >= -32767 && value <= 32767 ? Optional.of((int) value) : Optional.empty();
    }
}
