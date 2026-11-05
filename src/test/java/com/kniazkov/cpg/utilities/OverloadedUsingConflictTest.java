/* Overload-import rename safety regression tests. MIT License. */
package com.kniazkov.cpg.utilities;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OverloadedUsingConflictTest {
    private static Project parse(String text) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("imports.cpp", text, Language.CPP)).build());
    }
    private static List<CallableDeclaration> functions(Project project) {
        var result = new ArrayList<CallableDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (node instanceof CallableDeclaration declaration) result.add(declaration);
        }});
        return result;
    }
    @Test void detectsUncalledPartialOverloadImport() {
        Project project = parse("namespace api { int run(int); int run(double); } using api::run;");
        assertEquals(1, OverloadedUsingConflict.find(project, List.of(functions(project).getFirst())).size());
    }
    @Test void unrelatedNamespaceOverloadsDoNotCauseRefusal() {
        Project project = parse("namespace api { int run(int); } namespace other { int run(double); } using api::run;");
        assertTrue(OverloadedUsingConflict.find(project, List.of(functions(project).getFirst())).isEmpty());
    }
    @Test void detectsAliasQualifiedAndRelativeImports() {
        Project project = parse("namespace outer { namespace api { int run(int); int run(double); } namespace alias = api; using alias::run; }");
        assertEquals(1, OverloadedUsingConflict.find(project, List.of(functions(project).getFirst())).size());
    }
    @Test void wholeOverloadSetDoesNotNeedAnImportSplit() {
        Project project = parse("namespace api { int run(int); int run(double); } using api::run;");
        assertTrue(OverloadedUsingConflict.find(project, functions(project)).isEmpty());
    }
}
