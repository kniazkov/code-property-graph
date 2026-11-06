/* Printed ISO C++03 4.5/3 and 9.6, before later expression-provenance DRs. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.math.BigInteger;
import java.util.*;
import java.util.function.Function;

final class BitFieldSemantics {
    private BitFieldSemantics() {}
    static Optional<VariableDeclaration> field(Expression expression) {
        while (expression instanceof ConversionExpression conversion && !conversion.isExplicit()
                && conversion.kind() == ConversionExpression.Kind.LVALUE_TO_RVALUE) expression = conversion.operand();
        if (OperatorSemantics.mayInvokeUserCode(expression)) return Optional.empty();
        Declaration target = expression instanceof NameExpression name ? name.target().orElse(null)
                : expression instanceof MemberExpression member ? member.target().orElse(null) : null;
        return target instanceof VariableDeclaration variable && variable.bitWidth().isPresent()
                ? Optional.of(variable) : Optional.empty();
    }
    /** The printed edition predates CWG324. Do not assume either preservation or loss. */
    static boolean uncertainResult(Expression expression) {
        while (expression instanceof ConversionExpression conversion && !conversion.isExplicit()
                && conversion.kind() == ConversionExpression.Kind.LVALUE_TO_RVALUE) expression = conversion.operand();
        if (OperatorSemantics.mayInvokeUserCode(expression) || expression instanceof ConversionExpression
                || expression instanceof PostfixIncrementExpression || expression instanceof PostfixDecrementExpression) return false;
        if (expression instanceof AssignmentExpression assignment) return mayBeField(assignment.target());
        if (expression instanceof CompoundAssignmentExpression assignment) return mayBeField(assignment.target());
        if (expression instanceof PrefixIncrementExpression || expression instanceof PrefixDecrementExpression)
            return mayBeField(((UnaryExpression) expression).operand());
        if (expression instanceof CommaExpression comma) return mayBeField(comma.right());
        if (expression instanceof ConditionalExpression conditional)
            return mayBeField(conditional.thenExpression()) || mayBeField(conditional.elseExpression());
        return false;
    }
    private static boolean mayBeField(Expression expression) { return field(expression).isPresent() || uncertainResult(expression); }
    static Optional<ConstantEvaluator.IntegerType> promoted(VariableDeclaration field, PlatformProfile profile,
            Function<Expression, Optional<ConstantEvaluator.Value>> constants) {
        TypeNode written = field.type().map(TypeSemantics::unqualified).orElse(null);
        if (written == null) return Optional.empty();
        // Enumeration bit-fields ignore the width for promotion (4.5/3).
        var enumeration = EnumSemantics.declaration(written);
        if (enumeration.isPresent()) return EnumSemantics.promoted(enumeration.get(), profile);
        if (!(written instanceof NamedType named) || named.fundamentalKind().isEmpty()) return Optional.empty();
        String spelling = named.fundamentalKind().get().spelling();
        boolean plain = Set.of("char", "short", "int", "long").contains(spelling)
                && !Arrays.asList(named.name().split("\\s+")).contains("signed");
        if (plain) {
            if (profile.bitFieldSignedness().equals("unknown")) return Optional.empty();
            spelling = profile.bitFieldSignedness().equals("unsigned") ? "unsigned " + spelling
                    : spelling.equals("char") ? "signed char" : spelling;
        }
        var declared = ConstantEvaluator.integerType(spelling, profile);
        var width = field.bitWidth().flatMap(constants).map(ConstantEvaluator.Value::number);
        if (declared.isEmpty() || width.isEmpty() || width.get().signum() <= 0) return Optional.empty();
        var type = declared.get();
        int bits = width.get().min(BigInteger.valueOf(type.bits())).intValue();
        BigInteger min, max;
        if (spelling.equals("bool")) { min = BigInteger.ZERO; max = BigInteger.ONE; }
        else {
            BigInteger bound = BigInteger.ONE.shiftLeft(type.unsigned() ? bits : bits - 1);
            min = type.unsigned() ? BigInteger.ZERO : bound.negate(); max = bound.subtract(BigInteger.ONE);
        }
        for (String candidate : List.of("int", "unsigned int")) {
            var promoted = ConstantEvaluator.integerType(candidate, profile).orElseThrow();
            if (promoted.contains(min) && promoted.contains(max)) return Optional.of(promoted);
        }
        return declared; // No integral promotion applies to a larger bit-field.
    }
}
