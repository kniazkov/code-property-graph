/* Code Property Graph: typed graph analysis. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import java.util.*;

/** Evaluates signed 32-bit arithmetic over immutable syntax nodes. Every accepted result fits the prototype integer model. */
public final class ConstantFolder {
    private ConstantFolder() {}

    /** Visits a reversed preorder so operands are available before their parent.
     * The identity map contains only proven integer constants. A missing operand,
     * division by zero or an out-of-range intermediate prevents folding its parent.
     * All replacements are published together through the project editor. */
    public static Project fold(Project project) {
        var editor = new ProjectEditor(project);
        var order = new ArrayList<Node>();
        project.walk(new NodeVisitor() {
            @Override public void visit(Node node) { order.add(node); }
        });
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
                editor.replace(node, IntegerLiteral.create().setValue((int) result).build());
            }
        }
        return editor.build();
    }
}
