/* C++03 type facts evaluated against the translation unit's target. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.math.BigInteger;
import java.util.*;

/** Conservative scalar, pointer and array layout. No host ABI is consulted.
 * Function, void, incomplete, member-pointer and unmodelled class layouts remain unknown. */
public final class TypeSemantics {
    private TypeSemantics() {}

    /** The effective profile belongs to the TU occurrence, not to the physical header path. */
    public static PlatformProfile profile(SourceFile file) {
        return file.provenance().map(TokenProvenance::unit).map(Preprocessing::configuration)
                .map(com.kniazkov.cpg.build.Build.TranslationUnit::platform).orElseGet(PlatformProfile::defaults);
    }

    public static TypeRepresentation describe(TypeNode type, PlatformProfile profile) {
        Objects.requireNonNull(type); Objects.requireNonNull(profile);
        var facts = facts(type, profile, Collections.newSetFromMap(new IdentityHashMap<>()));
        return new TypeRepresentation(type.canonicalIdentity(), profile.stableIdentity(),
                facts.size.map(value -> value.multiply(BigInteger.valueOf(profile.bitsPerByte()))),
                facts.size, facts.alignment, facts.range);
    }

    /** Result is measured in target bytes. Empty means no justified sizeof value. */
    public static Optional<BigInteger> sizeof(TypeNode type, PlatformProfile profile) {
        return describe(type, profile).sizeBytes();
    }

    public static Optional<BigInteger> sizeof(SizeofExpression expression, PlatformProfile profile) {
        return expression.type().or(() -> expression.operand().flatMap(value -> expressionType(value, profile)))
                .flatMap(type -> sizeof(type, profile));
    }

    private record Facts(Optional<BigInteger> size, OptionalInt alignment,
            Optional<PlatformProfile.IntegerRange> range) {}
    private static final Facts UNKNOWN = new Facts(Optional.empty(), OptionalInt.empty(), Optional.empty());

    private static Facts facts(TypeNode type, PlatformProfile profile, Set<TypeNode> active) {
        if (!active.add(type)) return UNKNOWN;
        try {
            if (type instanceof QualifiedType qualified) return facts(qualified.baseType(), profile, active);
            if (type instanceof ReferenceType reference) return facts(reference.referredType(), profile, active);
            if (type instanceof NamedType name) {
                if (name.alias().isPresent()) return facts(name.alias().orElseThrow().type(), profile, active);
                if (name.fundamentalKind().isEmpty() || name.fundamentalKind().orElseThrow() == FundamentalKind.VOID)
                    return UNKNOWN;
                String spelling = name.fundamentalKind().orElseThrow().spelling();
                return scalar(spelling, profile);
            }
            if (type instanceof PointerType) return scalar("pointer", profile);
            if (type instanceof ArrayType array) {
                var element = facts(array.elementType(), profile, active);
                var count = array.bound().flatMap(bound -> ConstantEvaluator.evaluate(bound, profile))
                        .map(ConstantEvaluator.Value::number).filter(n -> n.signum() > 0);
                return new Facts(element.size.flatMap(size -> count.map(size::multiply)), element.alignment, Optional.empty());
            }
            return UNKNOWN;
        } finally { active.remove(type); }
    }

    private static Facts scalar(String type, PlatformProfile profile) {
        OptionalInt size = profile.sizeBytes(type);
        return new Facts(size.isPresent() ? Optional.of(BigInteger.valueOf(size.getAsInt())) : Optional.empty(),
                profile.alignmentBits(type), profile.integerRange(type));
    }

    /** Explicit sequential plain-aggregate rule. This is not a C++ class/vtable/base-class ABI.
     * Callers must supply only ordinary non-static object fields, in declaration order. */
    public record AggregateLayout(List<BigInteger> offsets, BigInteger sizeBytes, int alignmentBits) {
        public AggregateLayout { offsets = List.copyOf(offsets); }
    }
    public static Optional<AggregateLayout> layout(List<? extends TypeNode> fields, PlatformProfile profile) {
        if (!profile.aggregateLayout().equals("sequential")) return Optional.empty();
        BigInteger offset = BigInteger.ZERO;
        int maximumAlignment = profile.bitsPerByte();
        var offsets = new ArrayList<BigInteger>();
        for (TypeNode field : fields) {
            // References require an ABI representation, unlike sizeof(reference).
            if (unqualified(field) instanceof ReferenceType) return Optional.empty();
            var facts = describe(field, profile);
            if (facts.sizeBytes().isEmpty() || facts.alignmentBits().isEmpty()) return Optional.empty();
            int alignment = facts.alignmentBits().getAsInt();
            if (alignment % profile.bitsPerByte() != 0) return Optional.empty();
            offset = align(offset, BigInteger.valueOf(alignment / profile.bitsPerByte()));
            offsets.add(offset);
            offset = offset.add(facts.sizeBytes().orElseThrow());
            maximumAlignment = Math.max(maximumAlignment, alignment);
        }
        return Optional.of(new AggregateLayout(offsets,
                align(offset.max(BigInteger.ONE), BigInteger.valueOf(maximumAlignment / profile.bitsPerByte())), maximumAlignment));
    }
    private static BigInteger align(BigInteger size, BigInteger alignment) {
        return size.add(alignment).subtract(BigInteger.ONE).divide(alignment).multiply(alignment);
    }

    /** Explicit unsigned size_t policy; pointer widths do not imply an integer rank. */
    public static Optional<String> sizeType(PlatformProfile profile) {
        return profile.integerRange(profile.sizeType()).isPresent() ? Optional.of(profile.sizeType()) : Optional.empty();
    }

    static TypeNode unqualified(TypeNode type) {
        Set<TypeNode> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        while (seen.add(type)) {
            if (type instanceof QualifiedType qualifier) type = qualifier.baseType();
            else if (type instanceof NamedType name && name.alias().isPresent()) type = name.alias().orElseThrow().type();
            else return type;
        }
        return type;
    }

    /** Restricted expression typing needed for unevaluated sizeof, with no array-to-pointer decay
     * on the direct operand. Unsupported expressions deliberately remain unknown. */
    public static Optional<TypeNode> expressionType(Expression expression, PlatformProfile profile) {
        if (expression instanceof IntegerLiteral literal)
            return ConstantEvaluator.literal(literal, profile).map(value -> named(value.type().spelling()));
        if (expression instanceof SizeofExpression) return sizeType(profile).map(TypeSemantics::named);
        if (expression instanceof NameExpression name) return name.target().flatMap(target -> {
            if (target instanceof VariableDeclaration variable) return variable.type();
            if (target instanceof ParameterDeclaration parameter) {
                return parameter.type().map(type -> {
                    var plain = unqualified(type);
                    if (plain instanceof ArrayType array) return PointerType.create().setPointee(array.elementType().edit()).build();
                    if (plain instanceof FunctionType) return PointerType.create().setPointee(type.edit()).build();
                    return type;
                });
            }
            return Optional.empty();
        });
        if (expression instanceof CallExpression call) return call.target().flatMap(CallableDeclaration::returnType);
        if (expression instanceof AssignmentExpression assignment) return expressionType(assignment.target(), profile);
        if (expression instanceof AddressOfExpression address)
            return expressionType(address.operand(), profile).map(type -> PointerType.create().setPointee(type.edit()).build());
        if (expression instanceof DereferenceExpression dereference)
            return expressionType(dereference.operand(), profile).flatMap(type -> {
                type = unqualified(type);
                return type instanceof PointerType pointer ? Optional.of(pointer.pointee()) : Optional.empty();
            });
        if (expression instanceof UnaryPlusExpression || expression instanceof NegateExpression) {
            var operand = ((UnaryExpression)expression).operand();
            return expressionType(operand, profile).flatMap(type -> integerType(type, profile))
                    .flatMap(type -> ConstantEvaluator.promote(type, profile)).map(type -> named(type.spelling()));
        }
        if (expression instanceof BinaryExpression binary) {
            var left = expressionType(binary.left(), profile).flatMap(type -> integerType(type, profile));
            var right = expressionType(binary.right(), profile).flatMap(type -> integerType(type, profile));
            if (left.isPresent() && right.isPresent()) {
                if (binary instanceof EqualExpression || binary instanceof NotEqualExpression || binary instanceof LessExpression
                        || binary instanceof LessOrEqualExpression || binary instanceof GreaterExpression || binary instanceof GreaterOrEqualExpression)
                    return Optional.of(named("bool"));
                if (binary instanceof AddExpression || binary instanceof SubtractExpression || binary instanceof MultiplyExpression
                        || binary instanceof DivideExpression || binary instanceof RemainderExpression)
                    return ConstantEvaluator.commonType(left.orElseThrow(), right.orElseThrow(), profile)
                            .map(type -> named(type.spelling()));
            }
        }
        return Optional.empty();
    }

    static Optional<ConstantEvaluator.IntegerType> integerType(TypeNode type, PlatformProfile profile) {
        type = unqualified(type);
        if (type instanceof ReferenceType reference) return integerType(reference.referredType(), profile);
        return type instanceof NamedType name ? name.fundamentalKind()
                .flatMap(kind -> ConstantEvaluator.integerType(kind.spelling(), profile)) : Optional.empty();
    }
    private static TypeNode named(String name) { return NamedType.create().setName(name).build(); }
}
