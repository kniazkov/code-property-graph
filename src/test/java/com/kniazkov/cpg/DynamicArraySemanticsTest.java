package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.generation.Source;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class DynamicArraySemanticsTest {
    private Project green(String text) {
        var parsed = Project.create().addFile(Parser.parse("vector.cpp", text, Language.CPP)).build();
        return DynamicArraySemantics.green(NameResolver.resolve(parsed));
    }
    @Test void scalarConstructionAssignmentAndAppend() {
        var project = green("#include <vector>\nint main(){std::vector<int> a; std::vector<int> b(3); std::vector<int> c(3,7); int xs[2]; std::vector<int> d(xs,xs+2); std::vector<int> e=c; a=c; a.assign(2,4); a.assign(xs,xs+2); a.push_back(9);}");
        assertFalse(project.hasErrors(), project.semanticDiagnostics().toString());
        assertEquals(EnumSet.of(DynamicArrayOperation.Kind.EMPTY, DynamicArrayOperation.Kind.COUNT, DynamicArrayOperation.Kind.FILL, DynamicArrayOperation.Kind.RANGE, DynamicArrayOperation.Kind.COPY, DynamicArrayOperation.Kind.ASSIGN_COPY, DynamicArrayOperation.Kind.ASSIGN_FILL, DynamicArrayOperation.Kind.ASSIGN_RANGE, DynamicArrayOperation.Kind.APPEND), EnumSet.copyOf(project.dynamicArraySemantics().stream().map(DynamicArrayOperation::kind).toList()));
        assertEquals(1, project.dynamicArrayTypes().size());
        assertTrue(project.dynamicArraySemantics().stream().allMatch(op -> op.type() == project.dynamicArrayTypes().getFirst()));
        assertEquals(9, project.dynamicArraySemantics().size());
        assertTrue(Source.render(project.files().getFirst(), Language.CPP).contains("push_back"));
    }
    @Test void foldingRebuildsInputsAndKeepsEffects() {
        var project = green("#include <vector>\nint main(){std::vector<int> a; a.push_back(1+2);}");
        var folded = ConstantFolder.fold(project);
        var append = folded.dynamicArraySemantics().stream().filter(op -> op.kind() == DynamicArrayOperation.Kind.APPEND).findFirst().orElseThrow();
        Node input = append.sources().getFirst();
        while (input instanceof ConversionExpression conversion) input = conversion.operand();
        assertInstanceOf(IntegerLiteral.class, input);
        assertEquals(3, ((IntegerLiteral)input).value());
        assertInstanceOf(CallExpression.class, append.syntax());
    }
    @Test void aliasesAndLookalikes() {
        var project = green("#include <vector>\nusing std::vector; typedef vector<int> Numbers; struct User { void push_back(int); }; int main(){Numbers a; a.push_back(2); const Numbers b=a; User u; u.push_back(2); std::vector<bool> bits; bits.push_back(true);}");
        assertEquals(3, project.dynamicArraySemantics().size());
        assertEquals(1, project.dynamicArrayTypes().size());
        assertFalse(project.dynamicArrayTypes().getFirst().writtenTypes().isEmpty());
    }
    @Test void vectorCopiesThroughConstElementDespiteMutableCaller() {
        var project = green("#include <vector>\nstruct Record { Record(); Record(Record&); Record(const Record&); }; void f(){Record r; std::vector<Record> a; a.push_back(r); std::vector<Record> b(2,r); std::vector<Record> c=b;}");
        var copies = project.dynamicArraySemantics().stream().filter(op -> Set.of(DynamicArrayOperation.Kind.APPEND,
                DynamicArrayOperation.Kind.FILL, DynamicArrayOperation.Kind.COPY).contains(op.kind())).toList();
        assertEquals(3, copies.size());
        for (var operation : copies) {
            var parameter = operation.elementCopyConstructor().orElseThrow().parameters().getFirst().type().orElseThrow();
            var reference = assertInstanceOf(ReferenceType.class, parameter);
            assertTrue(assertInstanceOf(QualifiedType.class, reference.referredType()).isConst());
        }
    }
    @Test void recordCopyLinksAndConservativeAssignment() {
        var project = green("#include <vector>\nstruct Record { int* p; Record(int* q):p(q){} Record(const Record& r):p(r.p){} }; int main(){int x; Record r(&x); std::vector<Record> a; a.push_back(r); std::vector<Record> b=a; b=a;}");
        var copies = project.dynamicArraySemantics().stream().filter(op -> op.kind() == DynamicArrayOperation.Kind.COPY || op.kind() == DynamicArrayOperation.Kind.APPEND).toList();
        assertEquals(2, copies.size());
        assertTrue(copies.stream().allMatch(op -> op.elementCopyConstructor().isPresent()));
        assertTrue(copies.stream().allMatch(op -> op.elementCopyConstructor().orElseThrow().name().equals("Record")));
        assertFalse(project.dynamicArraySemantics().stream().anyMatch(op -> op.kind() == DynamicArrayOperation.Kind.ASSIGN_COPY));
    }
}
