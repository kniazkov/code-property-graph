/* Expanded CFG expressions execute each source effect once. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
class ExpandedExpressionDataFlowTest {
    @TempDir Path dir;
    FunctionFlow flow(String source) throws Exception {
        Files.writeString(dir.resolve("a.cpp"), source);
        return Cpg.read(dir, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED)).flows().getFirst();
    }
    <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    void assertSingleIncrementEvaluation(FunctionFlow flow) {
        assertTrue(flow.complete());
        for (UnaryExpression increment : nodes(flow.entry(), UnaryExpression.class))
            if (increment instanceof PostfixIncrementExpression || increment instanceof PrefixIncrementExpression) {
                var reads = nodes(increment.operand(), NameExpression.class);
                assertFalse(flow.data().stream().anyMatch(edge -> edge.from() == increment && reads.contains(edge.to())
                        && edge.kind() == FunctionFlow.Dependency.DEFINITION), "An acyclic increment must not read its own write");
                assertTrue(flow.data().stream().anyMatch(edge -> reads.contains(edge.to())
                        && edge.kind() == FunctionFlow.Dependency.DEFINITION), "The actual increment still reads its input");
            }
    }
    @Test void initializerDoesNotRepeatCommaOrConditionalEffects() throws Exception {
        assertSingleIncrementEvaluation(flow("int f(){ int x=0; int y=(x++,x); return y; }"));
        assertSingleIncrementEvaluation(flow("int f(bool b){ int x=0; int y=b ? ++x : 9; return y; }"));
    }
    @Test void returnDoesNotRepeatSequencedEffects() throws Exception {
        assertSingleIncrementEvaluation(flow("int f(){ int x=0; return (x++,x); }"));
        assertSingleIncrementEvaluation(flow("int f(bool b){ int x=0; return b ? ++x : 9; }"));
    }
    @Test void assignmentAndConditionPerformOnlyTheirFinalWrite() throws Exception {
        var f = flow("int f(){ int x=0; int y=0; y=(x++,x); return y; }");
        assertSingleIncrementEvaluation(f);
        var assignment = nodes(f.entry(), AssignmentExpression.class).getFirst();
        var returned = nodes(nodes(f.entry(), ReturnStatement.class).getFirst(), NameExpression.class).getFirst();
        assertTrue(f.data().stream().anyMatch(edge -> edge.from() == assignment && edge.to() == returned
                && edge.kind() == FunctionFlow.Dependency.DEFINITION));
        assertSingleIncrementEvaluation(flow("int f(){ int x=0; int y=0; if(y=(x++,x)) return y; return 0; }"));
    }
    @Test void expandedConditionalRetainsSelectorAndArmValueDependencies() throws Exception {
        var f = flow("int f(bool p,int x,int y){ return p ? x : y; }");
        var conditional = nodes(f.entry(), ConditionalExpression.class).getFirst();
        for (Expression input : List.of(conditional.condition(), conditional.thenExpression(), conditional.elseExpression()))
            assertTrue(f.data().stream().anyMatch(edge -> edge.from() == input && edge.to() == conditional
                    && edge.kind() == FunctionFlow.Dependency.VALUE));
    }
    @Test void constantPrunedArmsDoNotAcquireDataDependencies() throws Exception {
        var f = flow("int f(int x,int y){ return true ? x : y; }");
        var conditional = nodes(f.entry(), ConditionalExpression.class).getFirst();
        var excluded = nodes(conditional.elseExpression(), Node.class);
        assertFalse(f.data().stream().anyMatch(edge -> excluded.contains(edge.from()) || excluded.contains(edge.to())));
        var logical = flow("int f(int x){ return false && ++x; }");
        var increment = nodes(logical.entry(), PrefixIncrementExpression.class).getFirst();
        var skipped = nodes(increment, Node.class);
        assertFalse(logical.data().stream().anyMatch(edge -> skipped.contains(edge.from()) || skipped.contains(edge.to())));
    }
    @Test void throwBranchIsNotReplayedAtTheNormalConsumer() throws Exception {
        var f = flow("int f(bool b){ int x=0; int y=b ? (throw 1) : ++x; return y; }");
        assertSingleIncrementEvaluation(f);
        var thrown = nodes(f.entry(), ThrowExpression.class).getFirst();
        assertFalse(f.data().stream().anyMatch(edge -> edge.from() == thrown && edge.to() instanceof VariableDeclaration));
    }
}
