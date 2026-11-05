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
        var editor = new ProjectEditor(project);
        var order = new ArrayList<Node>();
        for (SourceFile file : project.files()) {
            if (file.language() == Language.CPP) {
                foldCpp(file, editor);
            } else file.walk(new NodeVisitor() {
                @Override public void visit(Node node) { order.add(node); }
            });
        }
        Map<Node, Integer> constants = new IdentityHashMap<>();
        for (Node node : order.reversed()) {
            if (node instanceof IntegerLiteral literal) {
                constants.put(node, literal.value());
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
        return greenTypes ? TypeNormalizer.green(folded) : folded;
    }

    private static boolean hasGreenTypes(SourceFile file) {
        boolean[] result = {false};
        file.walk(new NodeVisitor() {
            @Override public void visit(TypeNode type) { result[0] |= type.semanticType().isPresent(); }
        });
        return result[0];
    }

    private static void foldCpp(SourceFile file, ProjectEditor editor) {
        var profile = TypeSemantics.profile(file);
        file.walk(new NodeVisitor() {
            @Override public void visit(Expression expression) {
                if (expression instanceof IntegerLiteral) return;
                ConstantEvaluator.evaluate(expression, profile).ifPresent(value -> {
                    // The ordinary literal model remains deliberately bounded until its own milestone.
                    // A large internal value may prove a parent constant but is never truncated for publication.
                    if (value.number().compareTo(java.math.BigInteger.valueOf(Integer.MIN_VALUE)) < 0
                            || value.number().compareTo(java.math.BigInteger.valueOf(Integer.MAX_VALUE)) > 0) return;
                    var literal = IntegerLiteral.create().setValue(value.number().intValueExact());
                    if (!value.type().spelling().equals("int")) literal.setCppType(value.type().spelling());
                    expression.range().ifPresent(literal::setRange);
                    editor.replace(expression, literal.build());
                });
            }
        });
    }
}
