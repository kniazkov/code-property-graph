/* Nonlocal C++ control is an explicit analysis boundary, not exception unwinding. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class NonlocalControlFlowTest {
    @TempDir Path temp;
    private Project resolved(String source) {
        var profile = PlatformProfile.builder().cRuntime("glibc").setjmp("gcc-clang-glibc").build();
        var unit = new Build.TranslationUnit(new Build.PhysicalSource(temp.resolve("main.cpp")), temp,
                List.of(), List.of(), "c++03").withPlatform(profile);
        var result = NameResolver.resolve(Project.create().addFile(Parser.parse(unit, source)).build());
        assertFalse(result.hasErrors(), () -> result.parseProblems() + " / " + result.semanticDiagnostics());
        return result;
    }
    private static FunctionFlow flow(Project project, String name) {
        return project.flows().stream().filter(f -> f.entry().name().equals(name)).findFirst().orElseThrow();
    }
    private static void incomplete(Project project, String... names) {
        for (String name : names) {
            var f = flow(project, name);
            assertFalse(f.complete(), name);
            assertTrue(f.control().isEmpty(), name);
            assertTrue(f.data().isEmpty(), name);
            assertTrue(f.auxiliaryVertices().isEmpty(), name);
            assertTrue(f.entry().diagnostics().stream().anyMatch(d -> d.code().equals("unsupported-nonlocal-control-flow")
                    && d.severity() == Diagnostic.Severity.WARNING), name);
        }
    }
    private static void complete(Project project, String... names) {
        for (String name : names) {
            var f = flow(project, name);
            assertTrue(f.complete(), name + ": " + f.entry().diagnostics());
            assertFalse(f.control().isEmpty(), name);
            assertTrue(f.entry().diagnostics().stream().noneMatch(d -> d.code().equals("unsupported-nonlocal-control-flow")), name);
        }
    }

    @Test void saveAndRestoreAreNotOrdinaryReturnsOrExceptionUnwinds() {
        var graph = resolved("#include <csetjmp>\n"
                + "int saved(){std::jmp_buf state; if(setjmp(state)==0) std::longjmp(state,1); return 7;}"
                + "int ordinary(){int value=3;return value;} int wrapper(){return saved();}");
        for (var copy : List.of(graph, graph.edit().build(), NameResolver.resolve(graph.edit().build()))) {
            var analyzed = DataFlow.analyze(copy);
            incomplete(analyzed, "saved", "wrapper"); complete(analyzed, "ordinary");
            var repeated = DataFlow.analyze(ControlFlow.analyze(analyzed));
            incomplete(repeated, "saved", "wrapper"); complete(repeated, "ordinary");
        }
    }

    @Test void restoreBoundaryPropagatesThroughPrototypesRecursionAndKnownPointerInitializers() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;"
                + "void jump(); void first(){jump();} void second(){first();}"
                + "void jump(){std::longjmp(state,1);}"
                + "void indirect(){void (*target)()=jump; target();}"
                + "void addressOnly(){void (*target)()=jump;}"
                + "void recursive(int n){if(n)recursive(n-1);else second();}"
                + "int clean(){return 9;}");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump", "first", "second", "indirect", "recursive");
        complete(analyzed, "addressOnly", "clean");
    }

    @Test void unevaluatedCallsAndUserLongjmpDoNotAcquireTheLibraryEffect() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;"
                + "int jump(){std::longjmp(state,1);return 0;}"
                + "int sizeOnly(){return sizeof(jump());}"
                + "namespace user{int longjmp(int x){return x;}}"
                + "int userCall(){return user::longjmp(4);} int unrelated(){return 2;}");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump"); complete(analyzed, "sizeOnly", "longjmp", "userCall", "unrelated");
    }

    @Test void evaluatedDefaultArgumentsPropagateWithoutEvaluatingTheirDeclaration() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;"
                + "int jump(){std::longjmp(state,1);return 0;}"
                + "int consume(int x=jump()){return x;}"
                + "int omitted(){return consume();} int explicitArgument(){return consume(3);}");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump", "omitted"); complete(analyzed, "consume", "explicitArgument");
    }

    @Test void editingAwayTheKnownTransferClearsDerivedBoundary() {
        var graph = DataFlow.analyze(resolved("#include <csetjmp>\nstd::jmp_buf state;"
                + "int jump(){std::longjmp(state,1);return 0;} int caller(){return jump();}"));
        incomplete(graph, "jump", "caller");
        var editor = new ProjectEditor(graph);
        var body = flow(graph, "jump").entry().body().orElseThrow();
        editor.replace(body, BlockStatement.create().addItem(ReturnStatement.create()
                .setValue(IntegerLiteral.create().setValue(4))).build());
        var edited = DataFlow.analyze(NameResolver.resolve(editor.build()));
        complete(edited, "jump", "caller");
        incomplete(graph, "jump", "caller");
    }
    @Test void namespaceQualifiedPointersAndImplicitLifetimeCallsRetainTheBoundary() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;"
                + "namespace helper{void jump(){std::longjmp(state,1);}}"
                + "void indirect(){void (*target)()=helper::jump;target();}"
                + "struct Cleanup{~Cleanup(){helper::jump();}};"
                + "struct Start{Start(){helper::jump();}};"
                + "struct Nested{Cleanup value;};"
                + "void cleanup(){Cleanup item;} void nested(){Nested item;}"
                + "void start(){Start item;} void referenceOnly(Cleanup &item){}"
                + "void addressOnly(){void (*target)()=helper::jump;}");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump", "indirect", "~Cleanup", "Start", "cleanup", "nested", "start");
        complete(analyzed, "referenceOnly", "addressOnly");
    }

    @Test void allocationsDeletesTemporariesAndSubobjectSpecialMembersPropagate() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;void jump(){std::longjmp(state,1);}"
                + "struct Start{Start(){jump();}}; struct Argument{Argument(int){jump();}};"
                + "struct Cleanup{~Cleanup(){jump();}};"
                + "struct Derived:Start{Derived(){}};"
                + "struct Nested{Cleanup field;~Nested(){}};"
                + "Start *make(){return new Start;} Start *makeArray(){return new Start[2];}"
                + "Argument *makeArgument(){return new Argument(1);}"
                + "Start *caller(){return make();} void drop(Cleanup*p){delete p;}"
                + "void dropArray(Cleanup*p){delete [] p;} void dropCaller(Cleanup*p){drop(p);}"
                + "void temporary(){Argument(2);} int unequal(){return sizeof(new Start);}"
                + "void scalar(){int *p=new int;delete p;}");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump", "Start", "Argument", "~Cleanup", "Derived", "~Nested", "make", "makeArray",
                "makeArgument", "caller", "drop", "dropArray", "dropCaller", "temporary");
        complete(analyzed, "unequal", "scalar");
    }

    @Test void deleteThroughKnownVirtualBaseIncludesDerivedDestructors() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;void jump(){std::longjmp(state,1);}"
                + "struct Base{virtual ~Base(){}};struct Derived:Base{~Derived(){jump();}};"
                + "void drop(Base *p){delete p;} void caller(Base*p){drop(p);}");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump", "~Derived", "drop", "caller");
        complete(analyzed, "~Base");
    }

    @Test void omittedConstructorDefaultsExecuteAtTheirConstructionSite() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;int jump(){std::longjmp(state,1);return 0;}"
                + "struct Value{Value(int n=jump()){}};"
                + "struct Derived:Value{Derived(){}};"
                + "Value *allocated(){return new Value;} Value *supplied(){return new Value(3);}"
                + "void local(){Value value;} void temporary(){Value();}"
                + "void explicitLocal(){Value value(3);} int unevaluated(){return sizeof(new Value);}");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump", "Derived", "allocated", "local", "temporary");
        complete(analyzed, "Value", "supplied", "explicitLocal", "unevaluated");
    }

    @Test void writtenBaseAndMemberInitializersIncludeTheirOmittedDefaults() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;int jump(){std::longjmp(state,1);return 0;}"
                + "struct Value{Value(int a,int b=jump()){}};"
                + "struct BaseDefault:Value{BaseDefault():Value(3){}};"
                + "struct MemberDefault{Value field;MemberDefault():field(3){}};"
                + "struct Explicit:Value{Explicit():Value(3,4){}};");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump", "BaseDefault", "MemberDefault");
        complete(analyzed, "Value", "Explicit");
    }

    @Test void inheritedImplicitVirtualDestructorRetainsKnownDerivedTransfers() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;void jump(){std::longjmp(state,1);}"
                + "struct A{virtual ~A(){}};struct B:A{};struct C:B{~C(){jump();}};"
                + "void drop(B*p){delete p;}void caller(B*p){drop(p);}"
                + "void addressOnly(B*p){}int unevaluated(B*p){return sizeof(p);}");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump", "~C", "drop", "caller");
        complete(analyzed, "~A", "addressOnly", "unevaluated");
    }

    @Test void implicitDynamicDestructorsIncludeNestedMemberCleanup() {
        var graph = resolved("#include <csetjmp>\nstd::jmp_buf state;void jump(){std::longjmp(state,1);}"
                + "struct Member{~Member(){jump();}};struct Nested{Member item;};"
                + "struct A{virtual ~A(){}};struct B:A{};struct C:B{Nested value;};"
                + "struct Unrelated{Member item;};"
                + "void drop(B*p){delete p;}void caller(B*p){drop(p);}"
                + "struct Plain{~Plain(){}};void ordinary(Plain*p){delete p;}");
        var analyzed = DataFlow.analyze(graph);
        incomplete(analyzed, "jump", "~Member", "drop", "caller");
        complete(analyzed, "~A", "~Plain", "ordinary");
    }

}
