/* Opt-in interval products, fixed points and conservative boundaries. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.analysis.IntegerRanges;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.lattice.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import com.kniazkov.cpg.passes.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class IntegerRangesTest {
    private static final IntegerDomain DOMAIN = IntegerDomain.INSTANCE;
    private static final String JOIN = "int f(int choose) { int value; if (choose) { value = 1; } else { value = 10; } if (value == 5) { return 100; } return value; }";
    @TempDir Path directory;

    private static Project parse(String source) {
        return Project.create().addFile(Parser.parse("ranges.cpp", source, Language.CPP)).build();
    }
    private static PassRunner runner() { return new PassRunner().register(new IntegerRanges()); }
    private static Project analyze(String source) { return runner().run(parse(source), IntegerRanges.ID); }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) {
            if (type.isInstance(node)) result.add(type.cast(node));
        }});
        return result;
    }
    private static CallableDeclaration function(Project project, String name) {
        return nodes(project, CallableDeclaration.class).stream().filter(fn -> fn.name().equals(name)).findFirst().orElseThrow();
    }
    private static IntegerValue range(Project project, Node node) { return project.resultOf(node, IntegerRanges.RANGE).orElseThrow(); }
    private static IntegerValue returned(Project project) { return range(project, nodes(project, ReturnStatement.class).getLast()); }
    private static List<String> history(Project project) { return project.passRuns().stream().map(PassRun::passId).toList(); }

    @Test void branchAssignmentsJoinToIntervalAndEqualityInsideGapRemainsPossible() {
        Project input = parse(JOIN);
        assertTrue(input.analysisResults().isEmpty());
        Project project = runner().run(input, IntegerRanges.ID);
        var comparison = nodes(project, EqualExpression.class).getFirst();
        assertEquals(DOMAIN.interval(1, 10), range(project, comparison.left()));
        assertEquals(DOMAIN.constant(5), range(project, comparison.right()));
        assertEquals(DOMAIN.interval(0, 1), range(project, comparison));
        assertTrue(range(project, comparison.left()).contains(5));
        assertEquals(DOMAIN.interval(1, 10), returned(project));
        assertTrue(history(project).indexOf("ControlFlow") < history(project).indexOf("IntegerRanges"));
        assertTrue(input.analysisResults().isEmpty());
        assertFalse(history(input).contains("IntegerRanges"));
        assertTrue(ProjectJson.encode(project).toString().contains("IntegerRanges"));
    }

    @Test void dependentPassSchedulesMissingRangesAndReusesCurrentProducer() {
        var check = new PassId("RangeCheck");
        var answer = new ResultKey<>(check, "possible", Boolean.class);
        var runner = runner().register(new AnalysisPass() {
            public PassId id() { return check; }
            public Set<PassId> requires() { return Set.of(IntegerRanges.ID); }
            public void analyze(PassContext context, AnalysisResults results) {
                var equality = nodes(context.project(), EqualExpression.class).getFirst();
                var value = context.resultOf(equality, IntegerRanges.RANGE).orElseThrow();
                results.put(equality, answer, value.contains(1));
            }
        });
        Project first = runner.run(parse(JOIN), check);
        assertTrue(first.resultOf(nodes(first, EqualExpression.class).getFirst(), answer).orElseThrow());
        assertTrue(history(first).indexOf("IntegerRanges") < history(first).indexOf("RangeCheck"));
        Project repeated = runner.run(first, check);
        assertEquals(1, history(repeated).stream().filter("IntegerRanges"::equals).count());
        assertEquals(2, history(repeated).stream().filter("RangeCheck"::equals).count());
    }

    @Test void ordinaryStagesNeverInvokeTheRangePass() throws Exception {
        Files.writeString(directory.resolve("main.cpp"), JOIN);
        for (var stage : Cpg.Stage.values()) {
            var project = Cpg.read(directory, new Cpg.Options(Language.CPP, stage));
            assertFalse(history(project).contains("IntegerRanges"), stage.name());
            assertTrue(project.analysisResults().stream().noneMatch(value -> value.key().producer().equals(IntegerRanges.ID)));
        }
        assertFalse(new PassRunner().registeredPasses().contains(IntegerRanges.ID));
    }

    @Test void assignmentsShadowingAndArithmeticUseDeclarationIdentity() {
        var project = analyze("int f(int choose) { int x = 2; if (choose) { int x = 100; x = 500; } x = x * 3 + 4; return x; }");
        assertEquals(DOMAIN.constant(10), returned(project));
        var variables = nodes(project, VariableDeclaration.class);
        assertEquals(DOMAIN.constant(2), range(project, variables.getFirst()));
        assertEquals(DOMAIN.constant(100), range(project, variables.getLast()));
        var assignments = nodes(project, AssignmentExpression.class);
        assertEquals(DOMAIN.constant(500), range(project, assignments.getFirst().target()));
        assertEquals(DOMAIN.constant(10), range(project, assignments.getLast()));
    }

    @Test void uninitializedOrOneSidedAssignmentIsUnknownRatherThanZero() {
        assertTrue(returned(analyze("int f() { int x; return x; }")).isTop());
        assertTrue(returned(analyze("int f(int choose) { int x; if (choose) x = 1; return x; }")).isTop());
        assertTrue(returned(analyze("int f(int x) { return x; }")).isTop());
    }

    @Test void unreachableCodeIsBottomOnlyForSupportedCompleteFunctions() {
        var project = analyze("int f() { return 1; int x = 5; return x; }");
        assertEquals(DOMAIN.constant(1), range(project, nodes(project, ReturnStatement.class).getFirst()));
        assertTrue(returned(project).isBottom());
        assertTrue(range(project, nodes(project, VariableDeclaration.class).getFirst()).isBottom());
        assertTrue(range(project, nodes(project, IntegerLiteral.class).getLast()).isBottom());
    }

    @Test void loopsConvergeAndWidenGrowingStateWithoutInventingATerminalConstant() {
        var project = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                analyze("int f(int n) { int x = 0; while (x < n) { x = x + 1; } return x; }"));
        assertTrue(returned(project).isTop());
        assertEquals(DOMAIN.constant(0), range(project, nodes(project, VariableDeclaration.class).getFirst()));
        var comparison = nodes(project, LessExpression.class).getFirst();
        assertEquals(DOMAIN.interval(0, 1), range(project, comparison));
        var stable = analyze("int f(int n) { int x = 1; while (n) { x = 10; } return x; }");
        assertEquals(DOMAIN.interval(1, 10), returned(stable));
    }

    @Test void forDoBreakContinueAndStandaloneIncrementsFollowTheCfg() {
        var incremented = analyze("int f() { int x = 1; x++; ++x; --x; x--; return x; }");
        assertEquals(DOMAIN.constant(1), returned(incremented));
        assertEquals(DOMAIN.constant(1), range(incremented, nodes(incremented, PostfixIncrementExpression.class).getFirst()));
        assertEquals(DOMAIN.constant(3), range(incremented, nodes(incremented, PrefixIncrementExpression.class).getFirst()));
        var firstIteration = analyze("int f() { int x = 1; do { x = 10; break; } while (x); return x; }");
        assertEquals(DOMAIN.constant(10), returned(firstIteration));
        var forLoop = assertTimeoutPreemptively(Duration.ofSeconds(5), () ->
                analyze("int f(int n) { int x = 1; for (int i = 0; i < n; i++) { x = 10; continue; } return x; }"));
        assertEquals(DOMAIN.interval(1, 10), returned(forLoop));
    }

    @Test void comparisonOperatorsAndPureSplitNotConditionsPublishTruthRanges() {
        var project = analyze("int f(int choose) { int x; if (choose) x = 1; else x = 10; bool a = x < 20; bool b = x <= 0; bool c = x > 0; bool d = x >= 11; bool e = x != 5; if (!choose) x = 4; return !x; }");
        assertEquals(DOMAIN.constant(1), range(project, nodes(project, LessExpression.class).getFirst()));
        assertEquals(DOMAIN.constant(0), range(project, nodes(project, LessOrEqualExpression.class).getFirst()));
        assertEquals(DOMAIN.constant(1), range(project, nodes(project, GreaterExpression.class).getFirst()));
        assertEquals(DOMAIN.constant(0), range(project, nodes(project, GreaterOrEqualExpression.class).getFirst()));
        assertEquals(DOMAIN.interval(0, 1), range(project, nodes(project, NotEqualExpression.class).getFirst()));
        assertEquals(DOMAIN.interval(0, 1), range(project, nodes(project, LogicalNotExpression.class).getFirst()));
        assertEquals(DOMAIN.constant(0), returned(project));
    }

    @Test void overflowAndSafeCastsAreConservativeForMachineIntegers() {
        assertTrue(returned(analyze("int f() { int x = 2147483647; return x + 1; }")).isTop());
        assertTrue(returned(analyze("int f() { int x = 50000; return x * x; }")).isTop());
        assertTrue(returned(analyze("int f() { int x = -2147483647 - 1; return -x; }")).isTop());
        assertTrue(returned(analyze("int f() { int x = 2147483647; ++x; return x; }")).isTop());
        assertEquals(DOMAIN.constant(1), returned(analyze("int f() { int x = 5; bool b = static_cast<bool>(x); return static_cast<int>(b); }")));
        assertEquals(DOMAIN.constant(-6), returned(analyze("int f() { int x = 2; return -(x * 3); }")));
    }

    @Test void targetProfileControlsIntOverflowRatherThanTheJavaHost() throws Exception {
        Files.writeString(directory.resolve("main.cpp"), "int f() { int x = 32767; return x + 1; }");
        var profile = PlatformProfile.defaults().toBuilder().sizeBits("int", 16).alignmentBits("int", 16).sizeBits("wchar_t", 16).alignmentBits("wchar_t", 16).wideExecutionEncoding("UTF-16").build();
        var parsed = Cpg.read(directory, new Cpg.Options(Language.CPP, Cpg.Stage.PARSED).withPlatform(profile));
        assertTrue(returned(runner().run(parsed, IntegerRanges.ID)).isTop());
    }

    @Test void unsupportedEffectsAndTypesMakeTheWholeFunctionTop() {
        var examples = List.of(
                "int unknown(); int f() { int x = 1; unknown(); return x; }",
                "int f() { int x = 1; int *p = &x; *p = 10; return x; }",
                "int f() { int x = 1; int &r = x; r = 10; return x; }",
                "int f() { static int x = 1; return x; }",
                "int x = 1; int f() { return x; }",
                "int f() { volatile int x = 1; return x; }",
                "typedef volatile int V; int f() { V x = 1; return x; }",
                "int f() { unsigned int x = 1; return x; }",
                "int f() { long x = 1; return x; }",
                "struct boolean {}; int f() { boolean b; return 7; }",
                "int f(int c) { int x = c ? 1 : 10; return x; }",
                "int f(int c) { int x = 1; if (c && ++x) x = 3; return x; }",
                "int f() { int x = 1; int y = x++; return x; }",
                "int f() { int x = 1; x += 1; return x; }",
                "int f() { int x = 5; return x / 2; }",
                "int f() { int x = 5; return static_cast<short>(x); }"
        );
        for (String source : examples) {
            var project = analyze(source);
            assertTrue(returned(project).isTop(), source);
            for (Expression expression : nodes(function(project, "f").body().orElseThrow(), Expression.class))
                assertTrue(range(project, expression).isTop(), source + " " + expression.getClass().getSimpleName());
        }
    }

    @Test void callableScopePublishesOnlyItsValuesAndEditsInvalidateThem() {
        var original = parse("int f() { return 1; } int g() { return 2; }");
        var runner = runner();
        var scoped = runner.run(original, IntegerRanges.ID, function(original, "f"));
        assertEquals(DOMAIN.constant(1), range(scoped, nodes(function(scoped, "f"), ReturnStatement.class).getFirst()));
        assertTrue(scoped.resultOf(nodes(function(scoped, "g"), ReturnStatement.class).getFirst(), IntegerRanges.RANGE).isEmpty());
        var literal = nodes(function(scoped, "f"), IntegerLiteral.class).getFirst();
        var editor = new ProjectEditor(scoped).withoutRevalidation();
        editor.replace(literal, IntegerLiteral.create().setValue(8).build());
        var changed = editor.build();
        assertTrue(changed.resultOf(literal, IntegerRanges.RANGE).isEmpty());
        var refreshed = runner.run(changed, IntegerRanges.ID, function(changed, "f"));
        assertEquals(DOMAIN.constant(8), range(refreshed, nodes(function(refreshed, "f"), ReturnStatement.class).getFirst()));
        assertEquals(DOMAIN.constant(1), range(scoped, literal));
    }

    @Test void javaIntAndBooleanSubsetUsesTheSameOptInProducts() {
        // The legacy Java parser only accepts main(String[]). Exercise the public Java
        // model directly rather than silently broadening that unrelated parser subset.
        var body = function(parse("int f(int choose) { int value; if (choose) value = 1; else value = 10; return value; }"), "f")
                .body().orElseThrow().edit();
        var method = MethodDeclaration.create().setName("f").setReturnType(NamedType.create().setName("int"))
                .addParameter(ParameterDeclaration.create().setName("choose").setType(NamedType.create().setName("boolean")))
                .setBody(body);
        var parsed = Project.create().addFile(SourceFile.create().setPath("Ranges.java").setLanguage(Language.JAVA)
                .addItem(ClassDeclaration.create().setName("Ranges").addMember(method))).build();
        assertEquals(DOMAIN.interval(1, 10), returned(runner().run(parsed, IntegerRanges.ID)));
    }
}
