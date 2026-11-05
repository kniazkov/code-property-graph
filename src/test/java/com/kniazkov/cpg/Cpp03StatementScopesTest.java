/* C++03 statement scope and recovery regressions. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class Cpp03StatementScopesTest {
    @TempDir Path directory;
    Project resolve(String source) {
        return NameResolver.resolve(Project.create().addFile(Parser.parse("scope.cpp", source, Language.CPP, "c++03")).build());
    }
    List<NameExpression> names(Project p, String spelling) {
        List<NameExpression> result = new ArrayList<>();
        p.walk(new NodeVisitor() { @Override public void visit(NameExpression n) { if (n.name().equals(spelling)) result.add(n); } });
        return result;
    }
    Set<String> errors(Project p) {
        Set<String> result = new HashSet<>();
        p.walk(new NodeVisitor() { @Override public void visit(Node n) { n.diagnostics().stream()
                .filter(d -> d.severity() == Diagnostic.Severity.ERROR).forEach(d -> result.add(d.code())); } });
        return result;
    }
    @Test void loopConditionAndCatchNamesStayLocal() {
        var p = resolve("int f() { int i=9; for(int i=0; i<2; ++i) { i=i+1; } i=i+1; if(int q=1) { q=q+1; } q; try { throw i; } catch(int e) { i=e; } e; return i; }");
        var i = names(p, "i");
        assertNotSame(i.getFirst().target().orElseThrow(), i.getLast().target().orElseThrow());
        assertTrue(names(p,"q").getLast().target().isEmpty());
        assertTrue(names(p,"e").getFirst().target().isPresent());
        assertTrue(names(p,"e").getLast().target().isEmpty());
    }
    @Test void handlerAndConditionNamesCannotBeRedeclaredInOuterBody() {
        assertTrue(errors(resolve("void f() { try {} catch(int e) { int e=1; } }")).contains("conflicting-declaration"));
        assertTrue(errors(resolve("void f() { if(int n=1) { int n=2; } }")).contains("conflicting-declaration"));
        assertTrue(errors(resolve("void f() { for(int i=0; i<1; ++i) { int i=2; } }")).contains("conflicting-declaration"));
        assertTrue(errors(resolve("void f() { if(int n=1) { { int n=2; } } }")).isEmpty());
    }
    @Test void statementValidationPreservesEarlierSemanticErrors() {
        var p = resolve("int f() { sizeof(void); break; return 0; }");
        assertTrue(errors(p).containsAll(Set.of("invalid-sizeof", "break-outside-loop-or-switch")));
        assertTrue(errors(resolve("int f() { return sizeof(void); }")).contains("invalid-sizeof"));
    }
    @Test void invalidJumpsAndLabelsAreDiagnosed() {
        var p = resolve("void f() { goto L; int x=1; L: ; L: ; goto missing; break; continue; }");
        assertTrue(errors(p).containsAll(Set.of("goto-crosses-initialization", "duplicate-label", "unknown-label", "break-outside-loop-or-switch", "continue-outside-loop")));
    }
    @Test void switchLabelsHavePerSwitchIdentity() {
        var p = resolve("void f(int x) { switch(x) { case 1: ; case 1: ; default: ; default: ; } case 2: ; }");
        assertTrue(errors(p).containsAll(Set.of("duplicate-case", "duplicate-default", "case-outside-switch")));
        assertTrue(errors(resolve("void f(int x) { switch(x) { case 1: switch(x) { case 1: break; } break; } }")).isEmpty());
    }
    @Test void switchAndGotoCannotBypassInitializationOrEnterHandlers() {
        assertTrue(errors(resolve("void f(int n) { switch(n) { int x=1; case 1: ; } }")).contains("case-crosses-initialization"));
        assertTrue(errors(resolve("void f() { switch(int n=1) { case 1: break; } }")).isEmpty());
        assertTrue(errors(resolve("void f() { goto L; try { L: ; } catch(...) {} }")).contains("goto-enters-try-or-handler"));
    }
    @Test void conditionAndCatchDefinitionsReachUses() {
        var p = DataFlow.analyze(resolve("int f() { if(int n=1) { return n; } try { throw 1; } catch(int e) { return e; } return 0; }"));
        var flow = p.flows().getFirst();
        assertTrue(flow.complete());
        for (String spelling : List.of("n", "e")) {
            var use = names(p, spelling).getFirst();
            assertTrue(flow.data().stream().anyMatch(edge -> edge.to() == use && edge.from() == use.target().orElseThrow()
                    && edge.kind() == FunctionFlow.Dependency.DEFINITION));
        }
    }
    @Test void jumpsMayBypassUninitializedPodObjects() {
        assertTrue(errors(resolve("struct P { int n; }; void f() { goto L; P p; L: ; }")).isEmpty());
        assertTrue(errors(resolve("struct P { ~P(); }; void f() { goto L; P p; L: ; }")).contains("goto-crosses-initialization"));
    }
    @Test void legalRethrowIsNotACompileError() {
        assertTrue(errors(resolve("void rethrow() { throw; }")).isEmpty());
    }
    @Test void completedPrefixRetainsFlowAfterParseError() throws Exception {
        Files.writeString(directory.resolve("partial.cpp"), "int done(int x) { return x+1; } int broken() { int x =");
        var p = Cpg.readRecovering(directory, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        assertTrue(p.hasErrors());
        var completed = p.flows().stream().filter(f -> f.entry().name().equals("done")).findFirst().orElseThrow();
        assertTrue(completed.complete());
        assertFalse(completed.control().isEmpty());
        assertFalse(completed.data().isEmpty());
        assertThrows(RuntimeException.class, () -> Cpg.sources(p, Language.CPP));
    }
}
