/* Cross-header non-template class redeclarations. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LibraryForwardClassResolutionTest {
    private static Project resolve(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("locale.cpp", source, Language.CPP)).build());
    }
    private static List<CallExpression> calls(Project project) {
        var result = new ArrayList<CallExpression>();
        project.files().getFirst().walk(new NodeVisitor() {
            @Override public void visit(CallExpression call) { result.add(call); }
        });
        return result;
    }
    @Test void localeDefinitionJoinsIosForwardInEitherIncludeOrder() {
        for (String headers : List.of("#include <ios>\n#include <locale>\n", "#include <locale>\n#include <ios>\n")) {
            var project = resolve(headers + "void f(const std::locale& l){l.name();std::locale::classic();}");
            assertTrue(project.semanticDiagnostics().isEmpty(), () -> project.semanticDiagnostics().toString());
            assertEquals(2, calls(project).size());
            calls(project).forEach(call -> assertTrue(call.target().isPresent(), () -> call.range().toString()));
        }
    }
    @Test void laterLocaleIncludeDoesNotExposeMembersAtEarlierUse() {
        var project = resolve("#include <ios>\nvoid early(const std::locale& l){l.name();}\n#include <locale>\nvoid late(const std::locale& l){l.name();}");
        assertEquals(2, calls(project).size());
        assertTrue(calls(project).getFirst().target().isEmpty());
        assertTrue(calls(project).getLast().target().isPresent());
    }
    @Test void unrelatedImportedClassesRemainAmbiguous() {
        var project = resolve("namespace first {struct locale {}; } namespace other {struct locale {}; } using namespace first; using namespace other; void f(locale* p){}");
        assertTrue(project.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("ambiguous-name")));
    }
    @Test void libraryAndUserNamespaceTypesRemainAmbiguous() {
        for (String imports : List.of("using namespace std;", "namespace wrapper {using namespace std;} using namespace wrapper;")) {
            var project=resolve("#include <locale>\nstd::locale* demand; namespace other {struct locale {}; } "
                    + imports + " using namespace other; void f(locale* p){}");
            assertTrue(project.semanticDiagnostics().stream().anyMatch(d -> d.code().equals("ambiguous-name")),
                    () -> project.semanticDiagnostics().toString());
            project.files().getFirst().walk(new NodeVisitor() {
                @Override public void visit(NamedType type) {
                    if(type.name().equals("locale")) assertTrue(type.declaration().isEmpty());
                }
            });
        }
    }

}
