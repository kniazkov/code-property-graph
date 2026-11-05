/* Selected overloads are independent from the conservative behavior model. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
class RemainingLibraryResolutionTest {
    @TempDir Path directory;
    private List<CallExpression> resolved(String header) throws Exception {
        try(var input=getClass().getResourceAsStream("/library-catalogue/resolved-remaining/"+header+".cpp")) {
            assertNotNull(input);Files.write(directory.resolve("main.cpp"),input.readAllBytes());
        }
        var project=Cpg.read(directory,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03"));
        assertFalse(project.hasErrors(),()->project.parseProblems()+" / "+project.semanticDiagnostics());
        var calls=new ArrayList<CallExpression>();
        project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(CallExpression c){calls.add(c);}});
        assertFalse(calls.isEmpty());
        for(var call:calls) {
            assertTrue(call.target().isPresent(),()->"Unresolved "+header+" call at "+call.range());
            assertTrue(call.target().orElseThrow().body().isEmpty(),"Catalogue target remains declaration-only");
        }
        return calls;
    }
    private void operators(String family,int expected) throws Exception {
        try(var input=getClass().getResourceAsStream("/library-catalogue/resolved-remaining/"+family+"-operators.cpp")) {
            assertNotNull(input);Files.write(directory.resolve("main.cpp"),input.readAllBytes());
        }
        var project=Cpg.read(directory,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03"));
        assertFalse(project.hasErrors(),()->project.parseProblems()+" / "+project.semanticDiagnostics());
        var expressions=new ArrayList<Expression>();
        project.files().getFirst().walk(new NodeVisitor(){@Override public void visit(Node n){
            if(n instanceof BinaryExpression || n instanceof UnaryExpression || n instanceof CompoundAssignmentExpression)expressions.add((Expression)n);
        }});
        assertEquals(expected,expressions.size());
        for(var expression:expressions)assertTrue(expression.operatorTarget().isPresent(),()->family+" operator at "+expression.range());
    }
    @Test void pairComparisonTargets() throws Exception {operators("utility",6);}
    @Test void bitsetOperatorTargets() throws Exception {operators("bitset",13);}
    @Test void complexOperatorTargets() throws Exception {operators("complex",28);}
    @Test void valarrayOperatorTargets() throws Exception {operators("valarray",54);}
    @Test void functionalAdapters() throws Exception {assertEquals(14,resolved("functional").size());}
    @Test void valarrayMathOverloads() throws Exception {assertEquals(20,resolved("valarray-math").size());}
    @Test void allocationOverloadTargets() throws Exception {
        var calls=resolved("new");assertEquals(12,calls.size());
        for(int i=0;i<calls.size();i++) {
            var parameters=calls.get(i).target().orElseThrow().parameters();
            assertEquals(i%6<2?1:2,parameters.size());
            if(i%6>=2) {
                String second=parameters.get(1).type().orElseThrow().canonicalIdentity();
                if(i%6<4)assertTrue(second.contains("nothrow_t"),second);
                else assertEquals("pointer(void)",second);
            }
        }
    }
    @Test void standardExceptionHierarchy() throws Exception {assertEquals(9,resolved("stdexcept").size());}
    @Test void utilityTemplates() throws Exception {assertEquals(1,resolved("utility").size());}
    @Test void allocatorOwnershipAndUninitializedAlgorithms() throws Exception {
        var calls=resolved("memory");assertEquals(17,calls.size());
        assertEquals("reference(int)",calls.get(0).target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
        assertEquals("reference(const(int))",calls.get(1).target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity());
    }
    @Test void exceptionHandlersAndTypeInfo() throws Exception {assertEquals(8,resolved("exception").size());}
    @Test void bitsetOverloadsAndExplicitStringTemplate() throws Exception {assertEquals(14,resolved("bitset").size());}
    @Test void complexScalarAndComplexPowerOverloads() throws Exception {
        var calls=resolved("complex");assertEquals(16,calls.size());
        assertEquals("int",calls.get(10).target().orElseThrow().parameters().get(1).type().orElseThrow().canonicalIdentity());
        assertEquals("reference(const(double))",calls.get(11).target().orElseThrow().parameters().get(1).type().orElseThrow().canonicalIdentity());
        assertTrue(calls.get(12).target().orElseThrow().parameters().get(1).type().orElseThrow().canonicalIdentity().contains("complex"));
        assertEquals("reference(const(double))",calls.get(13).target().orElseThrow().parameters().get(0).type().orElseThrow().canonicalIdentity());
    }
    @Test void valarrayApplyAndReductions() throws Exception {
        var calls=resolved("valarray");assertEquals(10,calls.size());
        assertNotEquals(calls.get(6).target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity(),
            calls.get(7).target().orElseThrow().parameters().getFirst().type().orElseThrow().canonicalIdentity(),"By-value and const-reference callback overloads differ");
    }
    @Test void localeFacetsAndCharacterOverloads() throws Exception {assertEquals(23,resolved("locale").size());}
    @Test void numericLimitsSpecializationTargets() throws Exception {var calls=resolved("limits");assertEquals(5,calls.size());
        for(int i=0;i<calls.size();i++)assertEquals(i<2?"int":"double",calls.get(i).target().orElseThrow().returnType().orElseThrow().canonicalIdentity());}
}
