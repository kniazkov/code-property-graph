/* Code Property Graph: typed graph analysis. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;

/** Replaces recognized Java and C++ output statements with a shared PrintStatement and preserves their argument expression. */
public final class ConsoleNormalizer {
    private ConsoleNormalizer() {}

    /** Accepts an unresolved external name while protecting local declarations that shadow it. */
    private static boolean external(Expression expression, String name) {
        return expression instanceof NameExpression n && n.name().equals(name) && n.target().isEmpty();
    }

    /** Matches external or catalogue-owned streams without accepting user shadows. */
    private static boolean stream(Expression expression, String member) {
        return expression instanceof ScopeMemberExpression access && access.member().equals(member)
                && (external(access.receiver(), "std") || access.receiver() instanceof NameExpression qualifier
                    && qualifier.name().equals("std")
                    && qualifier.target().filter(LibraryDeclarations::isLibrary).isPresent())
                && (access.target().isEmpty() || access.target().filter(LibraryDeclarations::isLibrary).isPresent());
    }

    /** Recognizes the supported console forms after name resolution, respecting local shadowing. */
    public static Project green(Project project) {
        var editor = new ProjectEditor(project);
        for (SourceFile file : project.files()) file.walk(new NodeVisitor() {
            @Override public void visit(ExpressionStatement statement) {
                Expression expr = statement.expression();
                if (file.language() == Language.JAVA && expr instanceof CallExpression call && call.arguments().size() == 1
                        && call.callee() instanceof DotMemberExpression println && println.member().equals("println")
                        && println.receiver() instanceof DotMemberExpression out && out.member().equals("out")
                        && external(out.receiver(), "System")) {
                    editor.replace(statement, PrintStatement.from(statement, call.arguments().getFirst()));
                }
                if (file.language() == Language.CPP && expr instanceof ShiftLeftExpression tail && stream(tail.right(), "endl")
                        && tail.left() instanceof ShiftLeftExpression head && stream(head.left(), "cout")) {
                    editor.replace(statement, PrintStatement.from(statement, head.right()));
                }
            }
        });
        return editor.build();
    }
}
