/* Reaching unexpected installations across local control paths. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UnexpectedHandlerFlowTest {
    Project read(String body) {
        return DataFlow.analyze(ControlFlow.analyze(Project.create().addFile(com.kniazkov.cpg.parser.Parser.parse("a.cpp",
                "#include <exception>\nvoid h(){throw 1;}void k(){throw 2;}void opaque()throw();" + body, Language.CPP)).build()));
    }
    AuxiliaryFlowVertex event(Project project) {
        var flow = project.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow();
        assertTrue(flow.complete());
        return flow.auxiliaryVertices().stream().filter(a -> a.kind() == AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL).findFirst().orElseThrow();
    }
    void check(String body, boolean unknown, String... names) {
        var event = event(read(body));
        assertEquals(unknown, event.unknownUnexpectedHandler());
        assertEquals(Set.of(names), new HashSet<>(event.unexpectedHandlers().stream().map(CallableDeclaration::name).toList()));
    }
    @Test void directReplacementAndAddressDesignator() {
        check("void f()throw(int){std::set_unexpected(h);throw 1.0;}", false, "h");
        check("void f()throw(int){std::set_unexpected(h);std::set_unexpected(&k);throw 1.0;}", false, "k");
    }
    @Test void branchesAndLoopFixedPoint() {
        check("void f(bool b)throw(int){if(b)std::set_unexpected(h);else std::set_unexpected(k);throw 1.0;}", false, "h", "k");
        check("void f(bool b)throw(int){if(b)std::set_unexpected(h);throw 1.0;}", true, "h");
        check("void f(bool b)throw(int){std::set_unexpected(h);while(b){std::set_unexpected(k);b=false;}throw 1.0;}", false, "h", "k");
    }
    @Test void opaqueCallsInvalidateButThrowCapturesBeforeCleanup() {
        check("void f()throw(int){std::set_unexpected(h);opaque();throw 1.0;}", true);
        check("void f()throw(int){opaque();std::set_unexpected(h);throw 1.0;}", false, "h");
        check("struct G{G()throw();~G()throw();};void f()throw(int){G g;std::set_unexpected(h);throw 1.0;}", false, "h");
        check("struct G{G()throw();~G()throw();};void f()throw(int){std::set_unexpected(h);{G g;}throw 1.0;}", true);
    }
    @Test void unknownEntryIndirectRestorationAndHomonyms() {
        check("void f()throw(int){throw 1.0;}void setup(){std::set_unexpected(h);f();}", true);
        check("void f()throw(int){std::unexpected_handler old=std::set_unexpected(h);std::set_unexpected(old);throw 1.0;}", true);
        check("void set_unexpected(void (*p)())throw();void f()throw(int){set_unexpected(h);throw 1.0;}", true);
    }
    @Test void unevaluatedCallsDoNotClobberInstallation() {
        check("void f()throw(int){std::set_unexpected(h);sizeof((opaque(),1));throw 1.0;}", false, "h");
    }
    @Test void handlerInvocationHasConservativeStorageEffects() {
        var project = read("int global=0;void f()throw(int){global=1;std::set_unexpected(h);throw 1.0;}");
        var flow = project.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow();
        assertTrue(flow.data().stream().anyMatch(e -> e.to() == flow.entry()
                && e.kind() == FunctionFlow.Dependency.DEFINITION));
    }
    @Test void copyJsonAndEditRemapTargets() {
        var original = read("void f()throw(int){std::set_unexpected(h);throw 1.0;}");
        var copy = original.withDiagnostics("probe", Map.of(original, new Diagnostic("probe", Diagnostic.Severity.INFO, "copy")));
        assertThrows(UnsupportedOperationException.class, () -> event(copy).unexpectedHandlers().clear());
        assertTrue(com.kniazkov.cpg.visualization.DotVisualizer.toDot(copy, false).contains("possible unexpected handler"));
        var target = event(copy).unexpectedHandlers().getFirst();
        assertNotSame(event(original).unexpectedHandlers().getFirst(), target);
        assertTrue(copy.flows().stream().anyMatch(f -> f.entry() == target));
        var json = ProjectJson.encode(copy).toText("");
        assertTrue(json.contains("unexpectedHandlerIds"));assertTrue(json.contains("unknownUnexpectedHandler"));
        var editor = new ProjectEditor(original);
        editor.replace(event(original).owner().exceptionTypes().getFirst(), NamedType.create().setName("double").build());
        var updated = DataFlow.analyze(ControlFlow.analyze(editor.build()));
        assertTrue(updated.flows().stream().filter(f -> f.entry().name().equals("f")).findFirst().orElseThrow()
                .auxiliaryVertices().stream().noneMatch(a -> a.kind() == AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL));
    }
}
