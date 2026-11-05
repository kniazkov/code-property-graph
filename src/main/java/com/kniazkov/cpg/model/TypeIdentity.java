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
        return identity(type, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    private static String identity(TypeNode written, Set<TypeNode> seen) {
        TypeNode type = canonicalType(written);
        if (!seen.add(type)) throw new IllegalStateException("Cyclic type identity");
        try {
            return switch (type) {
                case NamedType n -> n.fundamentalKind().map(FundamentalKind::spelling).orElseGet(() -> "named(" + n.declaration().flatMap(Declaration::typeIdentity).orElse(n.name()) + ")");
                case QualifiedType q -> qualified(q.baseType(), q.isConst(), q.isVolatile(), seen);
                case PointerType p -> "pointer(" + identity(p.pointee(), seen) + ")";
                case ReferenceType r -> "reference(" + identity(r.referredType(), seen) + ")";
                case ArrayType a -> "array(" + bound(a) + "," + identity(a.elementType(), seen) + ")";
                case FunctionType f -> "function(" + f.parameters().stream().map(p -> parameter(p.type().orElseThrow(), seen)).collect(Collectors.joining(","))
                        + (f.variadic() ? ",..." : "") + ")->" + identity(f.returnType(), seen)
                        + (f.isConst() ? " const" : "") + (f.isVolatile() ? " volatile" : "");
                case MemberPointerType p -> "member-pointer(" + identity(p.ownerType(), seen) + "," + identity(p.pointee(), seen) + ")";
                default -> throw new IllegalArgumentException("Unknown type identity: " + type.getClass().getName());
            };
        } finally { seen.remove(type); }
    }
    private static String qualified(TypeNode written, boolean c, boolean v, Set<TypeNode> seen) {
        TypeNode type = canonicalType(written);
        var visited = Collections.newSetFromMap(new IdentityHashMap<TypeNode, Boolean>());
        while (type instanceof QualifiedType q) {
            if (!visited.add(type)) throw new IllegalStateException("Cyclic qualified type identity");
            c |= q.isConst(); v |= q.isVolatile(); type = canonicalType(q.baseType());
        }
        // Typedef-applied array cv belongs to every array element; references ignore it.
        if (type instanceof ArrayType a) {
            if (!seen.add(type)) throw new IllegalStateException("Cyclic array type identity");
            try { return "array(" + bound(a) + "," + qualified(a.elementType(), c, v, seen) + ")"; }
            finally { seen.remove(type); }
        }
        if (type instanceof ReferenceType) return identity(type, seen);
        return (c ? "const(" : "") + (v ? "volatile(" : "") + identity(type, seen) + (v ? ")" : "") + (c ? ")" : "");
    }
    /** Parameter identity with array/function adjustment and top-level cv removal. */
    public static String parameterIdentity(TypeNode written) {
        return parameter(written, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    private static String parameter(TypeNode written, Set<TypeNode> seen) {
        TypeNode type = canonicalType(written);
        boolean c = false, v = false;
        var visited = Collections.newSetFromMap(new IdentityHashMap<TypeNode, Boolean>());
        while (type instanceof QualifiedType q) {
            if (!visited.add(type)) throw new IllegalStateException("Cyclic parameter type identity");
            c |= q.isConst(); v |= q.isVolatile(); type = canonicalType(q.baseType());
        }
        // Adjust arrays before discarding top-level parameter cv: these qualifiers
        // belong to the pointed-to element, not to the resulting pointer.
        if (type instanceof ArrayType a) return "pointer(" + qualified(a.elementType(), c, v, seen) + ")";
        if (type instanceof FunctionType f) return "pointer(" + identity(f, seen) + ")";
        return identity(type, seen);
    }
    private static String bound(ArrayType type) {
        return type.bound().map(e -> portableInteger(e).map(Object::toString).orElseGet(() -> e.source(Language.CPP))).orElse("");
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
