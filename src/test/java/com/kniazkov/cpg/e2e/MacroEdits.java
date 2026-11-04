/* Code Property Graph: reusable acceptance edit of one macro-expanded operand. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.model.*;
import java.util.ArrayList;
import static org.junit.jupiter.api.Assertions.*;

final class MacroEdits {
    private MacroEdits() {}
    static Project squareRightOperand(Project project) {
        var matches = new ArrayList<MultiplyExpression>();
        project.walk(new NodeVisitor() { @Override public void visit(MultiplyExpression n) {
            if (n.left() instanceof NameExpression name && name.name().equals("a")) matches.add(n);
        }});
        assertEquals(1, matches.size());
        var original = matches.getFirst().right();
        var editor = new ProjectEditor(project);
        editor.replace(original, NameExpression.create().setName("b").build());
        var edited = editor.build();
        var replacements = new ArrayList<NameExpression>();
        edited.walk(new NodeVisitor() { @Override public void visit(NameExpression n) {
            if (n.name().equals("b")) replacements.add(n);
        }});
        assertEquals(1, replacements.size());
        assertEquals(original.provenance(), replacements.getFirst().provenance());
        return edited;
    }
}
