/* Conservative C++03 built-in expression conversions. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.util.*;
import java.util.function.Function;

/** Applies only built-in conversions, after unique name/call resolution. No overload ranking or user conversions. */
public final class StandardConversions {
    private StandardConversions() {}

    /** Discards derived implicit wrappers before re-resolving edited declarations or operands. */
    public static Project written(Project project) {
        var editor = new ProjectEditor(project).setPassId("StandardConversions").withoutRevalidation();
        boolean[] changed = {false};
        project.walk(new NodeVisitor() {
            @Override public void visit(ConversionExpression conversion) {
                if (conversion.isExplicit()) return;
                Expression operand = conversion.operand();
                while (operand instanceof ConversionExpression inner && !inner.isExplicit()) operand = inner.operand();
                editor.replace(conversion, operand); changed[0] = true;
            }
        });
        return (changed[0] ? editor.build() : project).recordPass("StandardConversions.written");
    }

    public static Project analyze(Project project) {
        var editor = new ProjectEditor(project).setPassId("StandardConversions").withoutRevalidation();
        var constantContexts = new HashMap<PlatformProfile, IntegralConstants>();
        for (var root : SourceContexts.roots(project)) {
            var file = root.source();
            if (file.language() != Language.CPP) continue;
            var profile = TypeSemantics.profile(file);
            var constants = constantContexts.computeIfAbsent(profile, value -> new IntegralConstants(project, value));
            root.node().walk(new NodeVisitor() {
                @Override public void visit(Expression expression) {
                    if (OperatorSemantics.mayInvokeUserCode(expression)) convertOperatorArguments(editor, expression, profile);
                }
                @Override public void visit(CallExpression call) {
                    if (OperatorSemantics.mayInvokeUserCode(call)) { convertOperatorArguments(editor, call, profile); return; }
                    TypeSemantics.callType(call, profile).ifPresent(target -> {
                        // Direct declaration types retain nominal bindings needed by converting constructors.
                        var parameters = call.target().map(CallableDeclaration::parameters).orElse(target.parameters());
                        for (int i = 0; i < Math.min(call.arguments().size(), parameters.size()); i++) {
                            var argument = call.arguments().get(i);
                            parameters.get(i).type().map(TypeSemantics::decay)
                                    .ifPresent(type -> convert(editor, argument, type, profile, constants));
                        }
                        // Default argument promotions only apply to arguments after an ellipsis.
                        if (target.variadic()) for (int i = parameters.size(); i < call.arguments().size(); i++) {
                            var argument = call.arguments().get(i);
                            TypeSemantics.expressionType(argument, profile, constants).ifPresent(type -> {
                                if (TypeSemantics.floatingRank(type) == 1) convert(editor, argument, named("double"), profile, constants);
                                else TypeSemantics.promoted(argument, profile, constants).ifPresent(promoted -> convert(editor, argument, promoted, profile, constants));
                            });
                        }
                    });
                }
                @Override public void visit(ParameterDeclaration parameter) {
                    parameter.type().map(TypeSemantics::decay).ifPresent(type ->
                            parameter.defaultValue().ifPresent(value -> convert(editor, value, type, profile, constants)));
                }
                @Override public void visit(CallableDeclaration callable) {
                    callable.returnType().ifPresent(type -> callable.body().ifPresent(body -> body.walk(new NodeVisitor() {
                        @Override public void visit(ReturnStatement statement) {
                            statement.value().ifPresent(value -> convert(editor, value, type, profile, constants));
                        }
                    })));
                }
                @Override public void visit(VariableDeclaration variable) {
                    if (variable.type().isPresent()) variable.initializer().ifPresent(value -> convert(editor, value, variable.type().get(), profile, constants));
                }
                @Override public void visit(AssignmentExpression assignment) {
                    if (OperatorSemantics.mayInvokeUserCode(assignment)) { convertOperatorArguments(editor, assignment, profile); return; }
                    TypeSemantics.expressionType(assignment.target(), profile, constants).ifPresent(type -> convert(editor, assignment.value(), type, profile, constants));
                }
                @Override public void visit(UnaryExpression unary) {
                    if (OperatorSemantics.mayInvokeUserCode(unary)) { convertOperatorArguments(editor, unary, profile); return; }
                    if (unary instanceof UnaryPlusExpression || unary instanceof NegateExpression || unary instanceof BitwiseNotExpression)
                        OperatorSemantics.builtinPromotedOperandType(unary.operand(), profile, constants)
                                .ifPresent(type -> convert(editor, unary.operand(), type, profile, constants));
                    if (unary instanceof LogicalNotExpression) convert(editor, unary.operand(), named("bool"), profile, constants);
                }
                @Override public void visit(BinaryExpression binary) {
                    if (OperatorSemantics.mayInvokeUserCode(binary)) { convertOperatorArguments(editor, binary, profile); return; }
                    if (binary instanceof CommaExpression || binary instanceof IndexExpression
                            || binary instanceof DotMemberPointerExpression || binary instanceof ArrowMemberPointerExpression) return;
                    if (binary instanceof LogicalAndExpression || binary instanceof LogicalOrExpression) {
                        convert(editor, binary.left(), named("bool"), profile, constants); convert(editor, binary.right(), named("bool"), profile, constants); return;
                    }
                    var a = OperatorSemantics.builtinPromotedOperandType(binary.left(), profile, constants); var b = OperatorSemantics.builtinPromotedOperandType(binary.right(), profile, constants);
                    if (a.isEmpty() || b.isEmpty()) return;
                    if (binary instanceof ShiftLeftExpression || binary instanceof ShiftRightExpression) {
                        if (TypeSemantics.integerType(a.get(), profile).isEmpty() || TypeSemantics.integerType(b.get(), profile).isEmpty()) return;
                        TypeSemantics.promoted(a.get(), profile, constants).ifPresent(type -> convert(editor, binary.left(), type, profile, constants));
                        TypeSemantics.promoted(b.get(), profile, constants).ifPresent(type -> convert(editor, binary.right(), type, profile, constants));
                    } else TypeSemantics.commonArithmeticType(a.get(), b.get(), profile).ifPresent(type -> {
                        convert(editor, binary.left(), type, profile, constants); convert(editor, binary.right(), type, profile, constants);
                    });
                }
                @Override public void visit(ConditionalExpression conditional) {
                    convert(editor, conditional.condition(), named("bool"), profile, constants);
                    if (TypeSemantics.valueCategory(conditional, profile) != ValueCategory.LVALUE)
                        TypeSemantics.expressionType(conditional, profile, constants).ifPresent(type -> {
                            convert(editor, conditional.thenExpression(), type, profile, constants);
                            convert(editor, conditional.elseExpression(), type, profile, constants);
                        });
                }
            });
        }
        preserveDiagnostics(project, editor);
        Project converted = editor.build();
        var facts = new ProjectEditor(converted);
        var convertedContexts = new HashMap<PlatformProfile, IntegralConstants>();
        for (var root : SourceContexts.roots(converted)) {
            var file = root.source();
            if (file.language() != Language.CPP) continue;
            var profile = TypeSemantics.profile(file);
            var constants = convertedContexts.computeIfAbsent(profile, value -> new IntegralConstants(converted, value));
            boolean[] green = {false};
            root.node().walk(new NodeVisitor() {
                @Override public void visit(TypeNode type) { green[0] |= type.semanticType().isPresent(); }
            });
            root.node().walk(new NodeVisitor() {
                @Override public void visit(TypeNode type) {
                    if (green[0]) facts.describe(type, TypeSemantics.describe(type, profile));
                }
                @Override public void visit(Expression expression) {
                    facts.describe(expression, new ExpressionInfo(TypeSemantics.expressionType(expression, profile, constants)
                            .map(TypeNode::canonicalIdentity), TypeSemantics.valueCategory(expression, profile)));
                    if (expression instanceof ConversionExpression conversion)
                        TypeSemantics.expressionType(conversion.operand(), profile).map(TypeNode::canonicalIdentity)
                                .ifPresent(type -> facts.describeSource(conversion, type));
                }
            });
        }
        preserveDiagnostics(converted, facts);
        return facts.build().recordPass("StandardConversions");
    }
    private static void preserveDiagnostics(Project source, ProjectEditor editor) {
        source.walk(new NodeVisitor() {
            @Override public void visit(Node node) { node.intrinsicDiagnostics().forEach(diagnostic -> editor.diagnose(node, diagnostic)); }
        });
    }
    private static void convertOperatorArguments(ProjectEditor editor, Expression expression, PlatformProfile profile) {
        // These are function-parameter conversions, not built-in operator promotions.
        if (OperatorSemantics.operatorName(expression) == null || expression.operatorTarget().isEmpty()) return;
        var target = expression.operatorTarget().orElseThrow();
        var operands = OperatorSemantics.operands(expression);
        int offset = target instanceof MethodDeclaration && !target.isFriend() ? 1 : 0;
        for (int i = 0; i < target.parameters().size() && i + offset < operands.size(); i++) {
            var operand = operands.get(i + offset);
            target.parameters().get(i).type().ifPresent(type -> convert(editor, operand, type, profile));
        }
    }
    private static void convert(ProjectEditor editor, Expression expression, TypeNode target, PlatformProfile profile) {
        List<ProjectEditor.ConversionStep> steps = sequence(expression, target, profile);
        if (!steps.isEmpty()) editor.convert(expression, steps);
    }

    private static void convert(ProjectEditor editor, Expression expression, TypeNode target, PlatformProfile profile,
            Function<Declaration, Optional<ConstantEvaluator.Value>> constants) {
        List<ProjectEditor.ConversionStep> steps = sequence(expression, target, profile, constants);
        if (!steps.isEmpty()) editor.convert(expression, steps);
    }

    /** Returns a complete supported conversion sequence, or no steps if unknown/incompatible/identity. */
    public static List<ProjectEditor.ConversionStep> sequence(Expression expression, TypeNode target, PlatformProfile profile) {
        return sequence(expression, target, profile, ignored -> Optional.empty());
    }
    private static List<ProjectEditor.ConversionStep> sequence(Expression expression, TypeNode target, PlatformProfile profile,
            Function<Declaration, Optional<ConstantEvaluator.Value>> constants) {
        if (expression instanceof ConversionExpression conversion
                && conversion.targetType().canonicalIdentity().equals(target.canonicalIdentity())) return List.of();
        var source = TypeSemantics.expressionType(expression, profile, constants);
        if (source.isEmpty()) return List.of();
        TypeNode from = source.get();
        var userConversion = OverloadResolution.conversion(expression, target, profile, true)
                .filter(sequence -> sequence.rank() == OverloadResolution.Rank.USER_DEFINED && sequence.userDefined() != null);
        if (userConversion.isPresent()) return List.of(new ProjectEditor.ConversionStep(from.canonicalIdentity(), target,
                ConversionExpression.Kind.USER_DEFINED, userConversion.orElseThrow().userDefined()));
        var cleanTarget = detached(target);
        if (cleanTarget.isEmpty()) return List.of();
        target = cleanTarget.get();
        var result = new ArrayList<ProjectEditor.ConversionStep>();
        var plainTarget = TypeSemantics.unqualified(target);
        if (plainTarget instanceof ReferenceType reference) {
            TypeNode referred = reference.referredType();
            boolean lvalue = TypeSemantics.valueCategory(expression, profile) == ValueCategory.LVALUE;
            boolean compatible = sameUnqualified(from, referred) && preservesCv(from, referred);
            if (compatible && (lvalue || temporaryReference(referred))) {
                add(result, from, target, ConversionExpression.Kind.REFERENCE_BINDING); return List.copyOf(result);
            }
            if (temporaryReference(referred) && arithmetic(from, profile) && arithmetic(referred, profile)) {
                var value = sequence(expression, TypeSemantics.unqualified(referred), profile, constants);
                result.addAll(value);
                add(result, TypeSemantics.unqualified(referred), target, ConversionExpression.Kind.REFERENCE_BINDING);
                return List.copyOf(result);
            }
            return List.of();
        }
        TypeNode plain = TypeSemantics.unqualified(from);
        if (plain instanceof ArrayType || plain instanceof FunctionType) {
            TypeNode decayed = TypeSemantics.decay(from);
            if (detached(decayed).isEmpty()) return List.of();
            add(result, from, decayed, plain instanceof ArrayType ? ConversionExpression.Kind.ARRAY_TO_POINTER : ConversionExpression.Kind.FUNCTION_TO_POINTER);
            from = decayed;
        } else if (TypeSemantics.valueCategory(expression, profile) == ValueCategory.LVALUE) {
            if (detached(plain).isEmpty()) return List.of();
            add(result, from, plain, ConversionExpression.Kind.LVALUE_TO_RVALUE); from = plain;
        }
        target = TypeSemantics.unqualified(target);
        if (from.canonicalIdentity().equals(target.canonicalIdentity())) return List.copyOf(result);
        if (arithmetic(from, profile) && arithmetic(target, profile)) {
            var promoted = TypeSemantics.promoted(expression, profile, constants);
            String targetIdentity = target.canonicalIdentity();
            var kind = promoted.filter(type -> type.canonicalIdentity().equals(targetIdentity))
                    .filter(type -> TypeSemantics.integerType(fromType(source.get()), profile).isPresent()).isPresent()
                    ? ConversionExpression.Kind.INTEGRAL_PROMOTION
                    : TypeSemantics.floatingRank(from) == 1 && TypeSemantics.floatingRank(target) == 2
                    ? ConversionExpression.Kind.FLOATING_PROMOTION : ConversionExpression.Kind.ARITHMETIC_CONVERSION;
            add(result, from, target, kind); return List.copyOf(result);
        }
        if (target instanceof NamedType name && name.name().equals("bool") && TypeSemantics.scalar(from, profile)) {
            add(result, from, target, ConversionExpression.Kind.ARITHMETIC_CONVERSION); return List.copyOf(result);
        }
        if (target instanceof PointerType pointer) {
            if (TypeSemantics.unqualified(from) instanceof PointerType original
                    && sameUnqualified(original.pointee(), pointer.pointee()) && preservesCv(original.pointee(), pointer.pointee())) {
                add(result, from, target, ConversionExpression.Kind.QUALIFICATION); return List.copyOf(result);
            }
            if (TypeSemantics.integerType(from, profile).isPresent()
                    && ConstantEvaluator.evaluate(expression, profile).map(value -> value.number().signum() == 0).orElse(false)) {
                add(result, from, target, ConversionExpression.Kind.NULL_POINTER); return List.copyOf(result);
            }
        }
        return List.of();
    }
    private static TypeNode fromType(TypeNode type) { return TypeSemantics.unqualified(type); }
    private static boolean arithmetic(TypeNode type, PlatformProfile profile) {
        return TypeSemantics.integerType(type, profile).isPresent() || TypeSemantics.floatingRank(type) > 0;
    }
    private static boolean sameUnqualified(TypeNode a, TypeNode b) {
        return TypeSemantics.unqualified(a).canonicalIdentity().equals(TypeSemantics.unqualified(b).canonicalIdentity());
    }
    private static boolean temporaryReference(TypeNode type) {
        return TypeSemantics.qualifiers(type) == 1;
    }
    private static boolean preservesCv(TypeNode from, TypeNode to) {
        int original = TypeSemantics.qualifiers(from), target = TypeSemantics.qualifiers(to);
        return (original & target) == original;
    }
    private static void add(List<ProjectEditor.ConversionStep> steps, TypeNode from, TypeNode to, ConversionExpression.Kind kind) {
        detached(to).ifPresent(type -> steps.add(new ProjectEditor.ConversionStep(from.canonicalIdentity(), type, kind)));
    }
    /** Builds semantic type syntax without copying references into an unrelated snapshot. */
    static Optional<TypeNode> detached(TypeNode type) {
        type = type.canonicalType();
        if (type instanceof NamedType name) return name.fundamentalKind().map(kind -> named(kind.spelling()));
        if (type instanceof QualifiedType q) return detached(q.baseType()).map(base -> QualifiedType.create()
                .setBaseType(base.edit()).setConst(q.isConst()).setVolatile(q.isVolatile()).build());
        if (type instanceof PointerType p) return detached(p.pointee()).map(base -> PointerType.create().setPointee(base.edit()).build());
        if (type instanceof ReferenceType r) return detached(r.referredType()).map(base -> ReferenceType.create().setReferredType(base.edit()).build());
        if (type instanceof FunctionType f) {
            var returnType = detached(f.returnType()); if (returnType.isEmpty()) return Optional.empty();
            var function = FunctionType.create().setReturnType(returnType.get().edit()).setVariadic(f.variadic())
                    .setConst(f.isConst()).setVolatile(f.isVolatile());
            for (var parameter : f.parameters()) {
                var parameterType = parameter.type().flatMap(StandardConversions::detached);
                if (parameterType.isEmpty()) return Optional.empty();
                function.addParameter(ParameterDeclaration.create().setName(parameter.name()).setType(parameterType.get().edit()));
            }
            return Optional.of(function.build());
        }
        return Optional.empty();
    }
    private static TypeNode named(String spelling) { return NamedType.create().setName(spelling).build(); }
}
