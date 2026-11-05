/* Conservative C++03 built-in expression conversions. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.util.*;

/** Applies only built-in conversions, after unique name/call resolution. No overload ranking or user conversions. */
public final class StandardConversions {
    private StandardConversions() {}

    /** Discards derived implicit wrappers before re-resolving edited declarations or operands. */
    public static Project written(Project project) {
        var editor = new ProjectEditor(project);
        boolean[] changed = {false};
        project.walk(new NodeVisitor() {
            @Override public void visit(ConversionExpression conversion) {
                if (conversion.isExplicit()) return;
                Expression operand = conversion.operand();
                while (operand instanceof ConversionExpression inner && !inner.isExplicit()) operand = inner.operand();
                editor.replace(conversion, operand); changed[0] = true;
            }
        });
        return changed[0] ? editor.build() : project;
    }

    public static Project analyze(Project project) {
        var editor = new ProjectEditor(project);
        for (var file : project.files()) {
            if (file.language() != Language.CPP) continue;
            var profile = TypeSemantics.profile(file);
            file.walk(new NodeVisitor() {
                @Override public void visit(CallExpression call) {
                    call.target().ifPresent(target -> {
                        for (int i = 0; i < Math.min(call.arguments().size(), target.parameters().size()); i++) {
                            var argument = call.arguments().get(i);
                            target.parameters().get(i).type().map(TypeSemantics::decay)
                                    .ifPresent(type -> convert(editor, argument, type, profile));
                        }
                        // Default argument promotions only apply to arguments after an ellipsis.
                        if (target.variadic()) for (int i = target.parameters().size(); i < call.arguments().size(); i++) {
                            var argument = call.arguments().get(i);
                            TypeSemantics.expressionType(argument, profile).ifPresent(type -> {
                                if (TypeSemantics.floatingRank(type) == 1) convert(editor, argument, named("double"), profile);
                                else TypeSemantics.promoted(type, profile).ifPresent(promoted -> convert(editor, argument, promoted, profile));
                            });
                        }
                    });
                }
                @Override public void visit(VariableDeclaration variable) {
                    if (variable.type().isPresent()) variable.initializer().ifPresent(value -> convert(editor, value, variable.type().get(), profile));
                }
                @Override public void visit(AssignmentExpression assignment) {
                    TypeSemantics.expressionType(assignment.target(), profile).ifPresent(type -> convert(editor, assignment.value(), type, profile));
                }
                @Override public void visit(UnaryExpression unary) {
                    if (unary instanceof UnaryPlusExpression || unary instanceof NegateExpression || unary instanceof BitwiseNotExpression)
                        TypeSemantics.expressionType(unary.operand(), profile).flatMap(type -> TypeSemantics.promoted(type, profile))
                                .ifPresent(type -> convert(editor, unary.operand(), type, profile));
                    if (unary instanceof LogicalNotExpression) convert(editor, unary.operand(), named("bool"), profile);
                }
                @Override public void visit(BinaryExpression binary) {
                    if (binary instanceof CommaExpression || binary instanceof IndexExpression
                            || binary instanceof DotMemberPointerExpression || binary instanceof ArrowMemberPointerExpression) return;
                    if (binary instanceof LogicalAndExpression || binary instanceof LogicalOrExpression) {
                        convert(editor, binary.left(), named("bool"), profile); convert(editor, binary.right(), named("bool"), profile); return;
                    }
                    var a = TypeSemantics.expressionType(binary.left(), profile); var b = TypeSemantics.expressionType(binary.right(), profile);
                    if (a.isEmpty() || b.isEmpty()) return;
                    if (binary instanceof ShiftLeftExpression || binary instanceof ShiftRightExpression) {
                        if (TypeSemantics.integerType(a.get(), profile).isEmpty() || TypeSemantics.integerType(b.get(), profile).isEmpty()) return;
                        TypeSemantics.promoted(a.get(), profile).ifPresent(type -> convert(editor, binary.left(), type, profile));
                        TypeSemantics.promoted(b.get(), profile).ifPresent(type -> convert(editor, binary.right(), type, profile));
                    } else TypeSemantics.commonArithmeticType(a.get(), b.get(), profile).ifPresent(type -> {
                        convert(editor, binary.left(), type, profile); convert(editor, binary.right(), type, profile);
                    });
                }
                @Override public void visit(ConditionalExpression conditional) {
                    convert(editor, conditional.condition(), named("bool"), profile);
                    if (TypeSemantics.valueCategory(conditional, profile) != ValueCategory.LVALUE)
                        TypeSemantics.expressionType(conditional, profile).ifPresent(type -> {
                            convert(editor, conditional.thenExpression(), type, profile);
                            convert(editor, conditional.elseExpression(), type, profile);
                        });
                }
            });
        }
        preserveDiagnostics(project, editor);
        Project converted = editor.build();
        var facts = new ProjectEditor(converted);
        for (var file : converted.files()) if (file.language() == Language.CPP) {
            var profile = TypeSemantics.profile(file);
            boolean[] green = {false};
            file.walk(new NodeVisitor() {
                @Override public void visit(TypeNode type) { green[0] |= type.semanticType().isPresent(); }
            });
            file.walk(new NodeVisitor() {
                @Override public void visit(TypeNode type) {
                    if (green[0]) facts.describe(type, TypeSemantics.describe(type, profile));
                }
                @Override public void visit(Expression expression) {
                    facts.describe(expression, new ExpressionInfo(TypeSemantics.expressionType(expression, profile)
                            .map(TypeNode::canonicalIdentity), TypeSemantics.valueCategory(expression, profile)));
                    if (expression instanceof ConversionExpression conversion)
                        TypeSemantics.expressionType(conversion.operand(), profile).map(TypeNode::canonicalIdentity)
                                .ifPresent(type -> facts.describeSource(conversion, type));
                }
            });
        }
        preserveDiagnostics(converted, facts);
        return facts.build();
    }
    private static void preserveDiagnostics(Project source, ProjectEditor editor) {
        source.walk(new NodeVisitor() {
            @Override public void visit(Node node) { node.diagnostics().forEach(diagnostic -> editor.diagnose(node, diagnostic)); }
        });
    }
    private static void convert(ProjectEditor editor, Expression expression, TypeNode target, PlatformProfile profile) {
        List<ProjectEditor.ConversionStep> steps = sequence(expression, target, profile);
        if (!steps.isEmpty()) editor.convert(expression, steps);
    }

    /** Returns a complete supported conversion sequence, or no steps if unknown/incompatible/identity. */
    public static List<ProjectEditor.ConversionStep> sequence(Expression expression, TypeNode target, PlatformProfile profile) {
        if (expression instanceof ConversionExpression conversion
                && conversion.targetType().canonicalIdentity().equals(target.canonicalIdentity())) return List.of();
        var source = TypeSemantics.expressionType(expression, profile);
        if (source.isEmpty()) return List.of();
        TypeNode from = source.get();
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
                var value = sequence(expression, TypeSemantics.unqualified(referred), profile);
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
            var promoted = TypeSemantics.promoted(from, profile);
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
