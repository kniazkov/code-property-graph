/* Conservative C++03 object-operator validity checks. MIT License. */
package com.kniazkov.cpg;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import java.nio.file.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class ObjectMemoryValidationTest {
    @TempDir Path temp;
    private Project read(String source, PlatformProfile profile) throws Exception {
        Files.writeString(temp.resolve("objects.cpp"),source);
        return Cpg.read(temp,new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED).withPlatform(profile));
    }
    private List<Diagnostic> errors(Project project) {
        var result=new ArrayList<Diagnostic>();
        project.walk(new NodeVisitor() { @Override public void visit(Node node) {
            node.diagnostics().stream().filter(d->d.severity()==Diagnostic.Severity.ERROR).forEach(result::add);
        }});
        return result;
    }
    private void rejects(String source,String code) throws Exception {
        var errors=errors(read(source,PlatformProfile.defaults()));
        assertTrue(errors.stream().anyMatch(d->d.code().equals(code)),source+": "+errors);
    }
    @Test void sizeofRejectsKnownInvalidOperands() throws Exception {
        rejects("int f(){ return sizeof(void); }","invalid-sizeof");
        rejects("int g(); int f(){ return sizeof(g); }","invalid-sizeof");
        rejects("struct S; int f(){ return sizeof(S); }","incomplete-type");
        rejects("struct S { unsigned b:3; }; int f(){ S s; return sizeof(s.b); }","invalid-sizeof");
    }
    @Test void explicitBitfieldConversionAndCompleteTypesAreValid() throws Exception {
        var project=read("struct S { unsigned b:3; }; int f(){ S s; return sizeof(static_cast<unsigned>(s.b)) + sizeof((unsigned)s.b) + sizeof(S*); }",PlatformProfile.defaults());
        assertTrue(errors(project).isEmpty(),errors(project).toString());
    }
    @Test void typeidRequiresCompleteClassButAllowsPointersAndVoid() throws Exception {
        rejects("#include <typeinfo>\nstruct S; int f(){ typeid(S); return 0; }","invalid-typeid");
        rejects("#include <typeinfo>\nstruct S; int f(S& s){ typeid(s); return 0; }","invalid-typeid");
        var project=read("#include <typeinfo>\nstruct S; int f(){ typeid(S*); typeid(void); return 0; }",PlatformProfile.defaults());
        assertTrue(errors(project).isEmpty(),errors(project).toString());
    }
    @Test void newRejectsKnownInvalidObjectTypesAndInitializers() throws Exception {
        for(String expression:List.of("new void", "new int&", "new int(1,2)", "new int[3](1)", "new int[]", "new int[-1]", "new int[2][0]", "new (int[0])", "new const int", "new const int[3]"))
            rejects("int f(){ "+expression+"; return 0; }","invalid-new");
        rejects("struct S; int f(){ new S; return 0; }","incomplete-type");
    }
    @Test void constFundamentalAllocationAcceptsExplicitInitializers() throws Exception {
        var project=read("int f(){ new const int(); new const int(1); new const int[3](); new int[0]; return 0; }",PlatformProfile.defaults());
        assertTrue(errors(project).isEmpty(),errors(project).toString());
    }
    @Test void onlyFirstUnparenthesizedNewBoundMayBeRuntime() throws Exception {
        for(String expression:List.of("new int[2][n]", "new (int[n])", "new int[2][n+1]"))
            rejects("int f(int n){ "+expression+"; return 0; }","invalid-new");
        var project=read("int f(int n){ new int[n][3]; new int[0]; return 0; }",PlatformProfile.defaults());
        assertTrue(errors(project).isEmpty(),errors(project).toString());
    }
    @Test void newBoundsRejectKnownNonintegralTypesWithoutRequiringKnownValues() throws Exception {
        rejects("int f(){ new int[2.0]; return 0; }","invalid-new");
        rejects("int f(int* p){ new int[p]; return 0; }","invalid-new");
        rejects("int f(float n){ new int[n]; return 0; }","invalid-new");
        var project=read("enum E { N=3 }; int f(int n){ new int[n]; new int[N]; new int[true]; return 0; }",PlatformProfile.defaults());
        assertTrue(errors(project).isEmpty(),errors(project).toString());
    }
    @Test void unavailableLayoutDoesNotTurnSymbolicConstantBoundsIntoRuntimeBounds() throws Exception {
        var unknown=PlatformProfile.defaults().toBuilder().aggregateLayout("unknown").build();
        var project=read("struct S { int x; }; int f(int n){ const int k=sizeof(S)+1; new int[n][sizeof(S)+1]; new (int[sizeof(S)]); new int[n][k]; new int[n][sizeof(n++)]; return 0; }",unknown);
        assertTrue(errors(project).isEmpty(),errors(project).toString());
        var invalid=read("struct S { int x; }; int f(int n){ new int[2][sizeof(S)+n]; return 0; }",unknown);
        assertTrue(errors(invalid).stream().anyMatch(d->d.code().equals("invalid-new")));
    }
    @Test void deleteRejectsKnownNonObjectPointersAndNonPointers() throws Exception {
        rejects("int f(){ delete 1; return 0; }","invalid-delete");
        rejects("int f(void* p){ delete p; return 0; }","invalid-delete");
        rejects("int g(); int f(){ delete &g; return 0; }","invalid-delete");
        var project=read("int f(int* p){ delete p; delete[] p; return 0; }",PlatformProfile.defaults());
        assertTrue(errors(project).isEmpty(),errors(project).toString());
    }
}
