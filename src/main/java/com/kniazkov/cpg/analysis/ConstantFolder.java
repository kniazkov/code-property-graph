/* Code Property Graph: typed graph analysis. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Evaluates immutable integer expressions using the owning C++ translation unit's profile.
 * Java retains its existing conservative signed-32-bit behavior. */
public final class ConstantFolder {
    private ConstantFolder() {}

    /** Visits a reversed preorder so operands are available before their parent.
     * The identity map contains only proven integer constants. A missing operand,
     * division by zero or an out-of-range intermediate prevents folding its parent.
     * All replacements are published together through the project editor. */
    public static Project fold(Project project) {
        // Replacing even an unevaluated branch can erase the syntax that explains an
        // error. Preserve the complete erroneous snapshot rather than conceal it.
        if (project.hasErrors() || ConstantEvaluator.hasSemanticErrors(project)) return project;
        var editor = new ProjectEditor(project).setPassId("ConstantFolder").withoutRevalidation();
        var order = new ArrayList<Node>();
        for (var root : SourceContexts.roots(project)) {
            SourceFile file = root.source();
            if (file.language() == Language.CPP) {
                foldCpp(root.node(), file, editor);
            } else root.node().walk(new NodeVisitor() {
                @Override public void visit(Node node) { order.add(node); }
            });
        }
        Map<Node, Integer> constants = new IdentityHashMap<>();
        for (Node node : order.reversed()) {
            if (node instanceof IntegerLiteral literal) {
                if (literal.bigValue().bitLength() < 32) constants.put(node, literal.bigValue().intValue());
                continue;
            }
            long result;
            if (node instanceof UnaryExpression unary && constants.containsKey(unary.operand())) {
                long operand = constants.get(unary.operand());
                if (unary instanceof NegateExpression) result = -operand;
                else if (unary instanceof UnaryPlusExpression) result = operand;
                else continue;
            } else if (node instanceof BinaryExpression binary
                    && constants.containsKey(binary.left()) && constants.containsKey(binary.right())) {
                long a = constants.get(binary.left()), b = constants.get(binary.right());
                if (binary instanceof AddExpression) result = a + b;
                else if (binary instanceof SubtractExpression) result = a - b;
                else if (binary instanceof MultiplyExpression) result = a * b;
                else if (binary instanceof DivideExpression && b != 0) result = a / b;
                else continue;
            } else continue;
            if (result >= Integer.MIN_VALUE && result <= Integer.MAX_VALUE) {
                constants.put(node, (int) result);
                var literal = IntegerLiteral.create().setValue((int) result);
                node.range().ifPresent(literal::setRange);
                editor.replace(node, literal.build());
            }
        }
        Project folded = editor.build();
        // Recompute green array facts if a symbolic bound was replaced by its value.
        boolean greenTypes = project.files().stream().anyMatch(file -> file.language() == Language.CPP
                && hasGreenTypes(file));
        if (greenTypes) folded = TypeNormalizer.green(folded);
        // A changed operand invalidates sequence metadata and its concrete template
        // declarations. Re-resolve before publishing fresh green sequence references.
        if (!project.dynamicArrayTypes().isEmpty() || !project.dynamicArraySemantics().isEmpty()
                || !project.containerTypes().isEmpty() || !project.containerSemantics().isEmpty()
                || !project.characterSequenceTypes().isEmpty() || !project.sequenceSemantics().isEmpty())
            folded = SequenceSemantics.green(ContainerSemantics.green(DynamicArraySemantics.green(NameResolver.resolve(folded))));
        return folded;
    }

    private static boolean hasGreenTypes(SourceFile file) {
        boolean[] result = {false};
        file.walk(new NodeVisitor() {
            @Override public void visit(TypeNode type) { result[0] |= type.semanticType().isPresent(); }
        });
        return result[0];
    }

    private static void foldCpp(Node root, SourceFile file, ProjectEditor editor) {
        var profile = TypeSemantics.profile(file);
        root.walk(new NodeVisitor() {
            @Override public void visit(Expression expression) {
                if (expression instanceof IntegerLiteral || expression instanceof BooleanLiteral) return;
                ConstantEvaluator.evaluate(expression, profile).ifPresent(value -> {
                    if (value.type().spelling().equals("bool")) {
                        var literal = BooleanLiteral.create().setValue(value.number().signum() != 0);
                        expression.range().ifPresent(literal::setRange);
                        editor.replace(expression, literal.build());
                        return;
                    }
                    // A char/short value has no equivalent ordinary integer-literal spelling.
                    if (!Set.of("int", "unsigned int", "long", "unsigned long").contains(value.type().spelling())) return;
                    var literal = IntegerLiteral.create().setValue(value.number());
                    if (!value.type().spelling().equals("int")) literal.setCppType(value.type().spelling());
                    expression.range().ifPresent(literal::setRange);
                    editor.replace(expression, literal.build());
                });
            }
        });
    }
}
