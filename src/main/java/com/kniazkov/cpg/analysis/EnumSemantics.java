/* Printed ISO C++03 4.5/2 and 7.2: target enum choices. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;
import java.util.function.BiFunction;

/** Underlying representation and integral promotion are deliberately separate. */
final class EnumSemantics {
    private EnumSemantics() {}

    static Optional<Map<EnumeratorDeclaration, ConstantEvaluator.Value>> values(EnumDeclaration enumeration,
            PlatformProfile profile, Function<Declaration, Optional<ConstantEvaluator.Value>> outer) {
        return valuesBefore(enumeration, null, profile, outer);
    }
    /** Earlier enumerators have their initializer types inside the same enum definition. */
    static Optional<Map<EnumeratorDeclaration, ConstantEvaluator.Value>> valuesBefore(EnumDeclaration enumeration,
            EnumeratorDeclaration stop, PlatformProfile profile, Function<Declaration, Optional<ConstantEvaluator.Value>> outer) {
        return valuesBefore(enumeration, stop, profile, outer,
                (expression, values) -> ConstantEvaluator.integralConstant(expression, profile, values));
    }
    /** Bounded clients supply their shared-context admission without changing lexical typing. */
    static Optional<Map<EnumeratorDeclaration, ConstantEvaluator.Value>> valuesBefore(EnumDeclaration enumeration,
            EnumeratorDeclaration stop, PlatformProfile profile,
            Function<Declaration, Optional<ConstantEvaluator.Value>> outer,
            BiFunction<Expression, Function<Declaration, Optional<ConstantEvaluator.Value>>, Optional<ConstantEvaluator.Value>> evaluate) {
        var values = new IdentityHashMap<EnumeratorDeclaration, ConstantEvaluator.Value>();
        BigInteger next = BigInteger.ZERO;
        ConstantEvaluator.IntegerType previousType = null;
        for (EnumeratorDeclaration enumerator : enumeration.enumerators()) {
            if (enumerator == stop) break;
            Optional<ConstantEvaluator.Value> value;
            if (enumerator.value().isPresent()) value = evaluate.apply(enumerator.value().get(),
                    ConstantEvaluator.overlay(outer, declaration -> Optional.ofNullable(values.get(declaration)), profile));
            else {
                value = previousType != null && previousType.contains(next)
                        ? Optional.of(new ConstantEvaluator.Value(next, previousType)) : Optional.empty();
                // 7.2/4 retains the preceding initializer type when the next value fits.
                // Only the first or overflowing implicit initializer needs a model choice.
                for (String spelling : List.of("int", "unsigned int", "long", "unsigned long")) {
                    var type = ConstantEvaluator.integerType(spelling, profile);
                    if (value.isPresent()) break;
                    if (type.isPresent() && type.get().contains(next)) { value = Optional.of(new ConstantEvaluator.Value(next, type.get())); break; }
                }
            }
            if (value.isEmpty()) return Optional.empty();
            values.put(enumerator, value.get()); previousType = value.get().type(); next = value.get().number().add(BigInteger.ONE);
        }
        return Optional.of(Collections.unmodifiableMap(values));
    }

    static Optional<ConstantEvaluator.IntegerType> underlying(EnumDeclaration enumeration, PlatformProfile profile) {
        return underlying(enumeration, profile, ignored -> Optional.empty());
    }
    static Optional<ConstantEvaluator.IntegerType> underlying(EnumDeclaration enumeration, PlatformProfile profile,
            Function<Declaration, Optional<ConstantEvaluator.Value>> outer) {
        if (profile.enumUnderlyingType().equals("unknown")) return Optional.empty();
        var values = values(enumeration, profile, outer);
        if (values.isEmpty()) return Optional.empty();
        return underlyingValues(values.get().values().stream().map(ConstantEvaluator.Value::number).toList(), profile);
    }
    /** Original 7.2 representation choice from already certified initializer values. */
    static Optional<ConstantEvaluator.IntegerType> underlyingValues(List<BigInteger> numbers, PlatformProfile profile) {
        if (profile.enumUnderlyingType().equals("unknown")) return Optional.empty();
        if (numbers.isEmpty()) numbers = List.of(BigInteger.ZERO);
        var signed = ConstantEvaluator.integerType("int", profile).orElseThrow();
        var unsigned = ConstantEvaluator.integerType("unsigned int", profile).orElseThrow();
        boolean wider = numbers.stream().anyMatch(value -> !signed.contains(value) && !unsigned.contains(value));
        var order = profile.enumUnderlyingType().equals("unsigned-first")
                ? List.of("unsigned int", "int", "unsigned long", "long")
                : List.of("int", "unsigned int", "long", "unsigned long");
        for (String spelling : order) {
            var type = ConstantEvaluator.integerType(spelling, profile).orElseThrow();
            if ((!wider && type.bits() > signed.bits()) || !numbers.stream().allMatch(type::contains)) continue;
            return Optional.of(type);
        }
        return Optional.empty();
    }
    static Optional<ConstantEvaluator.IntegerType> promoted(EnumDeclaration enumeration, PlatformProfile profile) {
        // 4.5/2 uses every underlying-type value, not just the enumerator range.
        return underlying(enumeration, profile).flatMap(type -> promoteUnderlying(type, profile));
    }
    static Optional<ConstantEvaluator.IntegerType> promoteUnderlying(ConstantEvaluator.IntegerType type, PlatformProfile profile) {
        for (String spelling : List.of("int", "unsigned int", "long", "unsigned long")) {
            var candidate = ConstantEvaluator.integerType(spelling, profile).orElseThrow();
            if (candidate.contains(type.min()) && candidate.contains(type.max())) return Optional.of(candidate);
        }
        return Optional.empty();
    }
    /** The closed interval between the smallest and largest enumerators is
     * always within 7.2/6's value range, independent of the remaining range edges.
     * Other explicitly cast values remain unknown, not fabricated or rejected. */
    static boolean containsKnownValue(EnumDeclaration enumeration, BigInteger number, PlatformProfile profile,
            Function<Declaration, Optional<ConstantEvaluator.Value>> outer) {
        var values = values(enumeration, profile, outer);
        if (values.isEmpty()) return false;
        var numbers = values.get().values().stream().map(ConstantEvaluator.Value::number).toList();
        if (numbers.isEmpty()) return number.signum() == 0;
        BigInteger min = Collections.min(numbers), max = Collections.max(numbers);
        return min.compareTo(number) <= 0 && max.compareTo(number) >= 0;
    }
    /** Exact syntax-owned object types for enum dependency tracking. This is not
     * overload resolution or general expression-type inference, and creates no copies. */
    static Optional<TypeNode> objectType(Expression expression) {
        if (expression.operatorTarget().isPresent()) return expression.operatorTarget().orElseThrow().returnType();
        Declaration target = expression instanceof NameExpression name ? name.target().orElse(null)
                : expression instanceof MemberExpression member ? member.target().orElse(null) : null;
        if (target instanceof VariableDeclaration variable) return variable.type();
        if (target instanceof ParameterDeclaration parameter) return parameter.type();
        if (expression instanceof CallExpression call) return call.target().flatMap(CallableDeclaration::returnType)
                .or(() -> objectType(call.callee()).map(type -> TypeSemantics.unqualified(TypeSemantics.withoutReference(type)))
                        .flatMap(type -> {
                            if (type instanceof PointerType pointer) type = TypeSemantics.unqualified(pointer.pointee());
                            return type instanceof FunctionType function ? Optional.of(function.returnType()) : Optional.empty();
                        }));
        if (expression instanceof DotMemberPointerExpression || expression instanceof ArrowMemberPointerExpression)
            return objectType(((BinaryExpression) expression).right()).map(type -> TypeSemantics.unqualified(TypeSemantics.withoutReference(type)))
                    .flatMap(type -> type instanceof MemberPointerType member ? Optional.of(member.pointee()) : Optional.empty());
        if (expression instanceof ConversionExpression conversion) return Optional.of(conversion.targetType());
        if (expression instanceof InitializationExpression initialization) return initialization.type();
        Expression pointer = expression instanceof IndexExpression index ? index.left()
                : expression instanceof DereferenceExpression dereference ? dereference.operand() : null;
        if (pointer != null) return objectType(pointer).map(type -> TypeSemantics.unqualified(TypeSemantics.withoutReference(type)))
                .flatMap(type -> type instanceof ArrayType array ? Optional.of(array.elementType())
                        : type instanceof PointerType value ? Optional.of(value.pointee()) : Optional.empty());
        return Optional.empty();
    }
    static Optional<EnumDeclaration> declaration(TypeNode type) {
        type = TypeSemantics.unqualified(TypeSemantics.withoutReference(type));
        return type instanceof NamedType named && named.declaration().orElse(null) instanceof EnumDeclaration enumeration
                ? Optional.of(enumeration) : Optional.empty();
    }
}
