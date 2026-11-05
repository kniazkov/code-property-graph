/* Integer evaluation for the deliberately small ordinary-expression subset. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.math.BigInteger;
import java.util.*;

/** Uses arbitrary precision internally, but never invents a value for signed overflow. */
final class ConstantEvaluator {
    private ConstantEvaluator() {}
    record IntegerType(String spelling, int bits, int rank, boolean unsigned, BigInteger min, BigInteger max) {
        boolean contains(BigInteger value) { return value.compareTo(min) >= 0 && value.compareTo(max) <= 0; }
    }
    record Value(BigInteger number, IntegerType type) {}

    static Optional<IntegerType> integerType(String spelling, PlatformProfile profile) {
        var range = profile.integerRange(spelling);
        var width = profile.sizeBits(spelling);
        if (range.isEmpty() || width.isEmpty()) return Optional.empty();
        int rank = switch (spelling) {
            case "bool" -> 0;
            case "char", "signed char", "unsigned char" -> 1;
            case "short", "unsigned short" -> 2;
            case "int", "unsigned int" -> 3;
            case "long", "unsigned long" -> 4;
            case "wchar_t" -> integerType(profile.wcharType(), profile).map(IntegerType::rank).orElse(3);
            default -> -1;
        };
        return rank < 0 ? Optional.empty() : Optional.of(new IntegerType(spelling, width.getAsInt(), rank,
                range.orElseThrow().min().signum() == 0, range.orElseThrow().min(), range.orElseThrow().max()));
    }

    static Optional<Value> literal(IntegerLiteral literal, PlatformProfile profile) {
        BigInteger number = literal.bigValue();
        if (literal.cppType().isPresent()) return integerType(literal.cppType().orElseThrow(), profile)
                .filter(type -> type.contains(number)).map(type -> new Value(number, type));
        // Decimal literal candidate types are target-dependent, not host Java int types.
        for (String spelling : List.of("int", "long")) {
            var type = integerType(spelling, profile);
            if (type.isPresent() && type.orElseThrow().contains(number)) return Optional.of(new Value(number, type.orElseThrow()));
        }
        return Optional.empty();
    }

    static Optional<IntegerType> promote(IntegerType type, PlatformProfile profile) {
        if (type.rank() >= 3 && !type.spelling().equals("wchar_t")) return Optional.of(type);
        for (String spelling : List.of("int", "unsigned int", "long", "unsigned long")) {
            var candidate = integerType(spelling, profile);
            if (candidate.isPresent() && candidate.orElseThrow().min().compareTo(type.min()) <= 0
                    && candidate.orElseThrow().max().compareTo(type.max()) >= 0) return candidate;
        }
        return Optional.empty();
    }

    static Optional<IntegerType> commonType(IntegerType a, IntegerType b, PlatformProfile profile) {
        var pa = promote(a, profile); var pb = promote(b, profile);
        if (pa.isEmpty() || pb.isEmpty()) return Optional.empty();
        a = pa.orElseThrow(); b = pb.orElseThrow();
        if (a.unsigned() == b.unsigned()) return Optional.of(a.rank() >= b.rank() ? a : b);
        IntegerType unsigned = a.unsigned() ? a : b;
        IntegerType signed = a.unsigned() ? b : a;
        if (unsigned.rank() >= signed.rank()) return Optional.of(unsigned);
        if (signed.max().compareTo(unsigned.max()) >= 0) return Optional.of(signed);
        return integerType("unsigned " + signed.spelling(), profile);
    }

    static Optional<Value> evaluate(Expression expression, PlatformProfile profile) {
        return evaluate(expression, profile, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    private static Optional<Value> evaluate(Expression expression, PlatformProfile profile, Set<Expression> active) {
        if (!active.add(expression)) return Optional.empty();
        try {
            if (expression instanceof IntegerLiteral literal) return literal(literal, profile);
            if (expression instanceof ConversionExpression conversion && !conversion.isExplicit()
                    && conversion.kind() != ConversionExpression.Kind.REFERENCE_BINDING) {
                var type = TypeSemantics.integerType(conversion.targetType(), profile);
                if (type.isEmpty()) return Optional.empty();
                return evaluate(conversion.operand(), profile, active).flatMap(value -> {
                    BigInteger number = type.get().spelling().equals("bool")
                            ? (value.number().signum() == 0 ? BigInteger.ZERO : BigInteger.ONE) : value.number();
                    return result(number, type.get());
                });
            }
            if (expression instanceof SizeofExpression sizeof) {
                var type = TypeSemantics.sizeType(profile).flatMap(name -> integerType(name, profile));
                return type.flatMap(t -> TypeSemantics.sizeof(sizeof, profile).filter(t::contains).map(value -> new Value(value, t)));
            }
            if (expression instanceof UnaryExpression unary) {
                var operand = evaluate(unary.operand(), profile, active);
                if (operand.isEmpty()) return Optional.empty();
                var value = operand.orElseThrow();
                var type = promote(value.type(), profile);
                if (type.isEmpty()) return Optional.empty();
                BigInteger number = convert(value.number(), type.orElseThrow());
                if (unary instanceof NegateExpression) number = number.negate();
                else if (!(unary instanceof UnaryPlusExpression)) return Optional.empty();
                return result(number, type.orElseThrow());
            }
            if (expression instanceof BinaryExpression binary) {
                var left = evaluate(binary.left(), profile, active); var right = evaluate(binary.right(), profile, active);
                if (left.isEmpty() || right.isEmpty()) return Optional.empty();
                var type = commonType(left.orElseThrow().type(), right.orElseThrow().type(), profile);
                if (type.isEmpty()) return Optional.empty();
                var target = type.orElseThrow();
                BigInteger a = convert(left.orElseThrow().number(), target), b = convert(right.orElseThrow().number(), target);
                BigInteger number;
                if (binary instanceof AddExpression) number = a.add(b);
                else if (binary instanceof SubtractExpression) number = a.subtract(b);
                else if (binary instanceof MultiplyExpression) number = a.multiply(b);
                else if (binary instanceof DivideExpression && b.signum() != 0) number = a.divide(b);
                else if (binary instanceof RemainderExpression && b.signum() != 0) {
                    // min / -1 is undefined for remainder too.
                    if (!target.unsigned() && a.equals(target.min()) && b.equals(BigInteger.ONE.negate())) return Optional.empty();
                    number = a.remainder(b);
                } else return Optional.empty();
                return result(number, target);
            }
            return Optional.empty();
        } finally { active.remove(expression); }
    }
    private static BigInteger convert(BigInteger value, IntegerType target) {
        return target.unsigned() ? value.mod(BigInteger.ONE.shiftLeft(target.bits())) : value;
    }
    private static Optional<Value> result(BigInteger number, IntegerType type) {
        number = convert(number, type);
        return type.contains(number) ? Optional.of(new Value(number, type)) : Optional.empty();
    }
}
