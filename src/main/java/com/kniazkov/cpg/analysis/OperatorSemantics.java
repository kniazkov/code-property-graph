/* C++03 operator calls retain their written syntax. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.util.*;
import java.util.function.Function;

/** Selects operator functions using the same conversion ordering as ordinary calls.
 * No purity, exception specification or complete dynamic call graph is inferred. */
public final class OperatorSemantics {
    private OperatorSemantics() {}

    /** Potential calls must not inherit built-in folding or short-circuit behavior. */
    public static boolean mayInvokeUserCode(Expression expression) {
        return expression.operatorTarget().isPresent() || !expression.operatorCandidates().isEmpty()
                || expression.operatorResolution() == CallExpression.Resolution.AMBIGUOUS;
    }

    /** Unknown callees can mutate reachable state and throw, including conversion functions. */
    public static boolean mayMutate(Expression expression) { return mayInvokeUserCode(expression); }
    public static boolean mayThrow(Expression expression) { return mayInvokeUserCode(expression); }

    /** Owned written operands only; the postfix dummy argument is not source syntax. */
    public static List<Expression> operands(Expression expression) {
        if (expression instanceof CallExpression call) {
            var result = new ArrayList<Expression>(); result.add(call.callee()); result.addAll(call.arguments());
            return List.copyOf(result);
        }
        if (expression instanceof AssignmentExpression a) return List.of(a.target(), a.value());
        if (expression instanceof CompoundAssignmentExpression a) return List.of(a.target(), a.value());
        if (expression instanceof BinaryExpression b) return List.of(b.left(), b.right());
        if (expression instanceof UnaryExpression u) return List.of(u.operand());
        if (expression instanceof ConversionExpression c) return List.of(c.operand());
        return List.of();
    }

    /** A bounded built-in fallback: one visible scalar conversion function, never a
     * guessed choice among competing conversions or a chained user conversion. */
    public static Optional<TypeNode> builtinOperandType(Expression expression, PlatformProfile profile) {
        var original = TypeSemantics.expressionType(expression, profile);
        if (original.isEmpty() || TypeSemantics.scalar(TypeSemantics.decay(original.get()), profile)) return original;
        var owner = ClassSemantics.objectClass(original.get());
        if (owner.isEmpty()) return original;
        var conversions = owner.get().members().stream().filter(CallableDeclaration.class::isInstance)
                .map(CallableDeclaration.class::cast).filter(c -> c.conversionType().isPresent())
                .filter(c -> (TypeSemantics.qualifiers(original.get()) & 1) == 0 || c.isConst())
                .filter(c -> (TypeSemantics.qualifiers(original.get()) & 2) == 0 || c.isVolatile())
                .map(c -> TypeSemantics.withoutReference(c.conversionType().orElseThrow()))
                .filter(t -> TypeSemantics.scalar(TypeSemantics.decay(t), profile)).toList();
        return conversions.size() == 1 ? Optional.of(conversions.getFirst()) : original;
    }

    public static String operatorName(Expression expression) {
        String token = switch (expression) {
            case AddExpression ignored -> "+";
            case SubtractExpression ignored -> "-";
            case MultiplyExpression ignored -> "*";
            case DivideExpression ignored -> "/";
            case RemainderExpression ignored -> "%";
            case BitwiseAndExpression ignored -> "&";
            case BitwiseOrExpression ignored -> "|";
            case BitwiseXorExpression ignored -> "^";
            case ShiftLeftExpression ignored -> "<<";
            case ShiftRightExpression ignored -> ">>";
            case EqualExpression ignored -> "==";
            case NotEqualExpression ignored -> "!=";
            case LessExpression ignored -> "<";
            case LessOrEqualExpression ignored -> "<=";
            case GreaterExpression ignored -> ">";
            case GreaterOrEqualExpression ignored -> ">=";
            case LogicalAndExpression ignored -> "&&";
            case LogicalOrExpression ignored -> "||";
            case LogicalNotExpression ignored -> "!";
            case BitwiseNotExpression ignored -> "~";
            case UnaryPlusExpression ignored -> "+";
            case NegateExpression ignored -> "-";
            case AddressOfExpression ignored -> "&";
            case DereferenceExpression ignored -> "*";
            case PrefixIncrementExpression ignored -> "++";
            case PostfixIncrementExpression ignored -> "++";
            case PrefixDecrementExpression ignored -> "--";
            case PostfixDecrementExpression ignored -> "--";
            case AssignmentExpression ignored -> "=";
            case CompoundAssignmentExpression assignment -> assignment.operator();
            case IndexExpression ignored -> "[]";
            case CommaExpression ignored -> ",";
            case ArrowMemberPointerExpression ignored -> "->*";
            case CallExpression ignored -> "()";
            default -> null;
        };
        return token == null ? null : "operator" + token;
    }

    /** Candidates are point-aware lexical, associated-namespace and member lookup results. */
    public static OverloadResolution.Result resolve(Expression expression, List<CallableDeclaration> declarations,
            PlatformProfile profile, ProjectEditor editor) {
        return resolve(expression, declarations, profile, editor, operand -> TypeSemantics.expressionType(operand, profile));
    }
    /** Contextual types include enumerator names whose owning enum is known by lookup. */
    public static OverloadResolution.Result resolve(Expression expression, List<CallableDeclaration> declarations,
            PlatformProfile profile, ProjectEditor editor, Function<Expression, Optional<TypeNode>> argumentTypes) {
        var written = operands(expression);
        if (operatorName(expression) == null || written.isEmpty()) return OverloadResolution.selectCandidates(List.of());
        var arguments = new ArrayList<>(written);
        if (expression instanceof PostfixIncrementExpression || expression instanceof PostfixDecrementExpression)
            arguments.add(IntegerLiteral.create().setValue(0).build());
        var viable = new ArrayList<OverloadResolution.Candidate>();
        for (var declaration : declarations) {
            boolean member = declaration instanceof MethodDeclaration && !declaration.isFriend();
            List<Expression> explicit = member ? arguments.subList(1, arguments.size()) : arguments;
            var candidate = OverloadResolution.candidate(declaration, explicit, profile, declaration.parameters().size(),
                    operand -> written.contains(operand) ? argumentTypes.apply(operand) : TypeSemantics.expressionType(operand, profile));
            if (candidate.isEmpty()) continue;
            if (member) {
                var objectType = argumentTypes.apply(arguments.getFirst());
                if (objectType.isEmpty()) continue;
                int qualifiers = TypeSemantics.qualifiers(objectType.get());
                if ((qualifiers & 1) != 0 && !declaration.isConst()
                        || (qualifiers & 2) != 0 && !declaration.isVolatile()) continue;
                int target = (declaration.isConst() ? 1 : 0) | (declaration.isVolatile() ? 2 : 0);
                var sequences = new ArrayList<OverloadResolution.Sequence>();
                sequences.add(new OverloadResolution.Sequence(OverloadResolution.Rank.EXACT,
                        Integer.bitCount(target & ~qualifiers), null));
                sequences.addAll(candidate.get().sequences());
                viable.add(new OverloadResolution.Candidate(declaration, sequences));
            } else viable.add(candidate.get());
        }
        // Built-ins participate in the same pairwise comparison, not a fallback after
        // a user function has already won. Synthetic declarations never enter the graph.
        var builtins = builtinCandidates(expression, written, profile, argumentTypes);
        var combined = new ArrayList<>(viable); combined.addAll(builtins);
        var selected = OverloadResolution.selectCandidates(combined);
        Set<CallableDeclaration> builtinDeclarations = Collections.newSetFromMap(new IdentityHashMap<>());
        builtins.forEach(candidate -> builtinDeclarations.add(candidate.declaration()));
        boolean builtinWins = selected.selected().filter(builtinDeclarations::contains).isPresent();
        var result = new OverloadResolution.Result(builtinWins ? CallExpression.Resolution.UNRESOLVED : selected.status(),
                selected.selected().filter(target -> !builtinDeclarations.contains(target)),
                builtinWins ? List.of() : selected.viable().stream().filter(target -> !builtinDeclarations.contains(target)).toList());
        editor.resolveOperator(expression, result.viable(), result.status());
        result.selected().ifPresent(target -> editor.bindOperator(expression, target));
        return result;
    }

    private static List<OverloadResolution.Candidate> builtinCandidates(Expression expression,
            List<Expression> operands, PlatformProfile profile, Function<Expression, Optional<TypeNode>> argumentTypes) {
        boolean logical = expression instanceof LogicalAndExpression || expression instanceof LogicalOrExpression
                || expression instanceof LogicalNotExpression;
        boolean integral = expression instanceof RemainderExpression || expression instanceof BitwiseAndExpression
                || expression instanceof BitwiseOrExpression || expression instanceof BitwiseXorExpression
                || expression instanceof ShiftLeftExpression || expression instanceof ShiftRightExpression
                || expression instanceof BitwiseNotExpression;
        boolean arithmetic = expression instanceof AddExpression || expression instanceof SubtractExpression
                || expression instanceof MultiplyExpression || expression instanceof DivideExpression
                || expression instanceof UnaryPlusExpression || expression instanceof NegateExpression
                || expression instanceof BinaryExpression binary && TypeSemantics.isComparison(binary);
        if (!logical && !integral && !arithmetic) return List.of();
        List<String> names = logical ? List.of("bool") : integral ? List.of("int", "unsigned int", "long", "unsigned long")
                : List.of("int", "unsigned int", "long", "unsigned long", "float", "double", "long double");
        var result = new ArrayList<OverloadResolution.Candidate>();
        for (String first : names) {
            if (operands.size() == 1) builtinCandidate(operands, List.of(first), profile, argumentTypes).ifPresent(result::add);
            else if (operands.size() == 2) for (String second : names)
                builtinCandidate(operands, List.of(first, second), profile, argumentTypes).ifPresent(result::add);
        }
        return List.copyOf(result);
    }
    private static Optional<OverloadResolution.Candidate> builtinCandidate(List<Expression> operands,
            List<String> parameters, PlatformProfile profile, Function<Expression, Optional<TypeNode>> argumentTypes) {
        var declaration = FunctionDeclaration.create().setName("<builtin>")
                .setReturnType(NamedType.create().setName("void"));
        for (int i = 0; i < parameters.size(); i++) declaration.addParameter(ParameterDeclaration.create()
                .setName("operand" + i).setType(NamedType.create().setName(parameters.get(i))));
        return OverloadResolution.candidate(declaration.build(), operands, profile, parameters.size(), argumentTypes);
    }
}
