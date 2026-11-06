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
        if (hasSemanticErrors(expression)) return Optional.empty();
        return evaluate(expression, profile, Collections.newSetFromMap(new IdentityHashMap<>()));
    }
    /** Ill-formed descendants matter even in branches that are never evaluated. */
    static boolean hasSemanticErrors(Node root) {
        boolean[] invalid = {false};
        root.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                invalid[0] |= node.diagnostics().stream().anyMatch(d -> d.severity() == Diagnostic.Severity.ERROR);
            }
        });
        return invalid[0];
    }
    private static Optional<Value> evaluate(Expression expression, PlatformProfile profile, Set<Expression> active) {
        if (OperatorSemantics.mayInvokeUserCode(expression)) return Optional.empty();
        if (!active.add(expression)) return Optional.empty();
        try {
            if (expression instanceof IntegerLiteral literal) return literal(literal, profile);
            if (expression instanceof BooleanLiteral literal) return truth(literal.value(), profile);
            if (expression instanceof CharacterLiteral literal) return integerType(literal.cppType(), profile)
                    .filter(type -> type.contains(literal.value())).map(type -> new Value(literal.value(), type));
            if (expression instanceof ConversionExpression conversion
                    && (!conversion.isExplicit() || conversion.kind() == ConversionExpression.Kind.STATIC_CAST
                        || conversion.kind() == ConversionExpression.Kind.C_STYLE_CAST)
                    && conversion.kind() != ConversionExpression.Kind.REFERENCE_BINDING
                    && !(TypeSemantics.unqualified(conversion.targetType()) instanceof ReferenceType)) {
                var type = TypeSemantics.integerType(conversion.targetType(), profile);
                if (type.isEmpty()) return Optional.empty();
                return evaluate(conversion.operand(), profile, active).flatMap(value -> cast(value.number(), type.get()));
            }
            if (expression instanceof InitializationExpression initialization
                    && initialization.form() == InitializationExpression.Form.TEMPORARY) {
                var type = initialization.type().filter(t -> !(TypeSemantics.unqualified(t) instanceof ReferenceType))
                        .flatMap(t -> TypeSemantics.integerType(t, profile));
                if (type.isEmpty() || initialization.arguments().size() > 1) return Optional.empty();
                if (initialization.arguments().isEmpty()) return cast(BigInteger.ZERO, type.get());
                return evaluate(initialization.arguments().getFirst(), profile, active)
                        .flatMap(value -> cast(value.number(), type.get()));
            }
            if (expression instanceof ConditionalExpression conditional) {
                var condition = evaluate(conditional.condition(), profile, active);
                // Both branch types participate, even when only one branch is evaluated.
                var type = TypeSemantics.expressionType(conditional, profile)
                        .flatMap(t -> TypeSemantics.integerType(t, profile));
                if (condition.isEmpty() || type.isEmpty()) return Optional.empty();
                return evaluate(condition.get().number().signum() != 0 ? conditional.thenExpression()
                        : conditional.elseExpression(), profile, active).flatMap(value -> cast(value.number(), type.get()));
            }
            if (expression instanceof SizeofExpression sizeof) {
                var type = TypeSemantics.sizeType(profile).flatMap(name -> integerType(name, profile));
                return type.flatMap(t -> TypeSemantics.sizeof(sizeof, profile).filter(t::contains).map(value -> new Value(value, t)));
            }
            if (expression instanceof UnaryExpression unary) {
                var operand = evaluate(unary.operand(), profile, active);
                if (operand.isEmpty()) return Optional.empty();
                var value = operand.orElseThrow();
                if (unary instanceof LogicalNotExpression) return truth(value.number().signum() == 0, profile);
                var type = promote(value.type(), profile);
                if (type.isEmpty()) return Optional.empty();
                BigInteger number = convert(value.number(), type.orElseThrow());
                if (unary instanceof NegateExpression) number = number.negate();
                else if (unary instanceof BitwiseNotExpression) {
                    // Signed representation is not prescribed by the target profile.
                    if (!type.get().unsigned()) return Optional.empty();
                    number = number.xor(type.get().max());
                } else if (!(unary instanceof UnaryPlusExpression)) return Optional.empty();
                return result(number, type.orElseThrow());
            }
            if (expression instanceof BinaryExpression binary) {
                var left = evaluate(binary.left(), profile, active);
                if (left.isEmpty()) return Optional.empty();
                if (binary instanceof LogicalAndExpression || binary instanceof LogicalOrExpression) {
                    // Reject potential overloaded operators, including an unknown right-hand type.
                    var rhsType = TypeSemantics.expressionType(binary.right(), profile);
                    if (rhsType.isEmpty() || TypeSemantics.integerType(rhsType.get(), profile).isEmpty()) return Optional.empty();
                    boolean a = left.get().number().signum() != 0;
                    if (binary instanceof LogicalAndExpression && !a) return truth(false, profile);
                    if (binary instanceof LogicalOrExpression && a) return truth(true, profile);
                    return evaluate(binary.right(), profile, active).flatMap(value -> truth(value.number().signum() != 0, profile));
                }
                var right = evaluate(binary.right(), profile, active);
                if (left.isEmpty() || right.isEmpty()) return Optional.empty();
                if (binary instanceof ShiftLeftExpression || binary instanceof ShiftRightExpression) {
                    var promoted = promote(left.get().type(), profile);
                    if (promoted.isEmpty()) return Optional.empty();
                    var target = promoted.get();
                    BigInteger a = convert(left.get().number(), target), count = right.get().number();
                    if (count.signum() < 0 || count.compareTo(BigInteger.valueOf(target.bits())) >= 0
                            || !target.unsigned() && a.signum() < 0) return Optional.empty();
                    return result(binary instanceof ShiftLeftExpression ? a.shiftLeft(count.intValue())
                            : a.shiftRight(count.intValue()), target);
                }
                var type = commonType(left.orElseThrow().type(), right.orElseThrow().type(), profile);
                if (type.isEmpty()) return Optional.empty();
                var target = type.orElseThrow();
                BigInteger a = convert(left.orElseThrow().number(), target), b = convert(right.orElseThrow().number(), target);
                if (TypeSemantics.isComparison(binary)) {
                    int comparison = a.compareTo(b);
                    return truth(binary instanceof EqualExpression ? comparison == 0
                            : binary instanceof NotEqualExpression ? comparison != 0
                            : binary instanceof LessExpression ? comparison < 0
                            : binary instanceof LessOrEqualExpression ? comparison <= 0
                            : binary instanceof GreaterExpression ? comparison > 0 : comparison >= 0, profile);
                }
                BigInteger number;
                if (binary instanceof AddExpression) number = a.add(b);
                else if (binary instanceof SubtractExpression) number = a.subtract(b);
                else if (binary instanceof MultiplyExpression) number = a.multiply(b);
                else if (binary instanceof DivideExpression && b.signum() != 0) {
                    if (unspecifiedDivision(a, b, target)) return Optional.empty();
                    number = a.divide(b);
                }
                else if (binary instanceof RemainderExpression && b.signum() != 0) {
                    // min / -1 is undefined for remainder too.
                    if (!target.unsigned() && a.equals(target.min()) && b.equals(BigInteger.ONE.negate())) return Optional.empty();
                    if (unspecifiedDivision(a, b, target)) return Optional.empty();
                    number = a.remainder(b);
                } else if (binary instanceof BitwiseAndExpression || binary instanceof BitwiseOrExpression
                        || binary instanceof BitwiseXorExpression) {
                    if (!target.unsigned() && (a.signum() < 0 || b.signum() < 0)) return Optional.empty();
                    number = binary instanceof BitwiseAndExpression ? a.and(b)
                            : binary instanceof BitwiseOrExpression ? a.or(b) : a.xor(b);
                } else return Optional.empty();
                return result(number, target);
            }
            return Optional.empty();
        } finally { active.remove(expression); }
    }
    /** C++03 permits implementation-defined quotient rounding for negative, non-exact division. */
    private static boolean unspecifiedDivision(BigInteger a, BigInteger b, IntegerType target) {
        return !target.unsigned() && (a.signum() < 0 || b.signum() < 0) && a.remainder(b).signum() != 0;
    }
    private static Optional<Value> truth(boolean value, PlatformProfile profile) {
        return integerType("bool", profile).map(type -> new Value(value ? BigInteger.ONE : BigInteger.ZERO, type));
    }
    /** Unsigned conversion is modulo the target width; out-of-range signed results remain unknown. */
    static Optional<Value> cast(BigInteger number, IntegerType type) {
        return result(type.spelling().equals("bool") ? (number.signum() == 0 ? BigInteger.ZERO : BigInteger.ONE) : number, type);
    }
    private static BigInteger convert(BigInteger value, IntegerType target) {
        return target.unsigned() ? value.mod(BigInteger.ONE.shiftLeft(target.bits())) : value;
    }
    private static Optional<Value> result(BigInteger number, IntegerType type) {
        number = convert(number, type);
        return type.contains(number) ? Optional.of(new Value(number, type)) : Optional.empty();
    }
}
