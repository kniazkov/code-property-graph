/* Standalone public extension client. Compile against the packaged JAR only. MIT License. */
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.passes.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import java.nio.file.*;
import java.util.*;

/** No library-package access, reflection, test dependencies, or CLI plugin loading. */
public final class RunPasses {
    public record IntRange(int min, int max) { }
    private static final PassId RANGES = new PassId("example.ranges");
    private static final PassId CONSUMER = new PassId("example.range-consumer");
    private static final PassId OTHER = new PassId("example.other-ranges");
    private static final PassId FOLD = new PassId("example.fold-addition");
    private static final PassId FAIL_ANALYSIS = new PassId("example.failed-analysis");
    private static final PassId FAIL_TRANSFORM = new PassId("example.failed-transform");
    private static final ResultKey<IntRange> RANGE = new ResultKey<>(RANGES, "range", IntRange.class);
    private static final ResultKey<IntRange> OTHER_RANGE = new ResultKey<>(OTHER, "range", IntRange.class);
    private static final ResultKey<String> LABEL = new ResultKey<>(RANGES, "label", String.class);
    private static final ResultKey<Boolean> CHECKED = new ResultKey<>(RANGES, "checked", Boolean.class);
    private static final ResultKey<Double> CONFIDENCE = new ResultKey<>(RANGES, "confidence", Double.class);
    private static final ResultKey<Integer> MAXIMUM = new ResultKey<>(CONSUMER, "maximum", Integer.class);
    private static final ResultKey<String> MISSING = new ResultKey<>(RANGES, "missing", String.class);
    private static final ResultTypes TYPES = ResultTypes.defaults().with(IntRange.class,
            range -> Map.of("min", range.min(), "max", range.max()));

    /** The analysis returns values even though it produces no warnings. */
    private static final class RangePass implements AnalysisPass {
        private final boolean publishRange;
        RangePass(boolean publishRange) { this.publishRange = publishRange; }
        public PassId id() { return RANGES; }
        public void analyze(PassContext context, AnalysisResults results) {
            for (Node node : context.nodes()) if (node instanceof CallableDeclaration function && function.body().isPresent()) {
                if (publishRange) results.put(node, RANGE, new IntRange(1, 10));
                results.put(node, LABEL, publishRange ? "initial" : "rerun");
                results.put(node, CHECKED, true);
                results.put(node, CONFIDENCE, 0.5);
            }
        }
    }

    private static final class RangeConsumer implements AnalysisPass {
        public PassId id() { return CONSUMER; }
        public Set<PassId> requires() { return Set.of(RANGES); }
        public void analyze(PassContext context, AnalysisResults results) {
            require(context.dependencies().contains(RANGES), "Declared dependency missing from context");
            for (Node node : context.nodes()) if (node instanceof CallableDeclaration function && function.body().isPresent()) {
                IntRange value = context.resultOf(node, RANGE).orElseThrow(); // No unchecked cast.
                require(value.equals(new IntRange(1, 10)), "Typed dependency value");
                require(context.resultOf(node, MISSING).isEmpty(), "Missing result is Optional.empty");
                results.put(node, MAXIMUM, value.max());
            }
        }
    }

    private static final class OtherRanges implements AnalysisPass {
        public PassId id() { return OTHER; }
        public void analyze(PassContext context, AnalysisResults results) {
            for (Node node : context.nodes()) if (node instanceof CallableDeclaration function && function.body().isPresent())
                results.put(node, OTHER_RANGE, new IntRange(20, 30));
        }
    }

    /** All replacements are queued in the runner's one editor, which is built after return. */
    private static final class FoldAddition implements TransformationPass {
        public PassId id() { return FOLD; }
        public Set<PassId> requires() { return Set.of(RANGES); }
        public void transform(PassContext context, ProjectEditor editor) {
            var function = context.function().orElseThrow();
            require(context.resultOf(function, RANGE).orElseThrow().equals(new IntRange(1, 10)), "Transformation dependency");
            var expressions = context.nodes().stream().filter(AddExpression.class::isInstance).toList();
            require(expressions.size() == 1, "Expected helper's one addition");
            editor.replace(expressions.getFirst(), IntegerLiteral.create().setValue(3).build());
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("RunPasses CPP_SOURCE_DIRECTORY NEW_OUTPUT_DIRECTORY");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output already exists: " + output);
        Project original = Cpg.read(input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        require(!original.hasErrors(), "Input has errors");
        String originalJson = json(original);
        long warningCount = original.semanticDiagnostics().stream().filter(d -> d.severity() == Diagnostic.Severity.WARNING).count();
        require(warningCount == 0, "Fixture must not need warnings to prove an analysis ran");
        require(original.resultOf(function(original, "helper"), RANGE).isEmpty(), "No implicit custom analysis");
        var runner = new PassRunner(TYPES).register(new RangePass(true)).register(new RangeConsumer())
                .register(new OtherRanges()).register(new FoldAddition());
        Project dependent = runner.run(original, CONSUMER);
        Project analyzed = runner.run(dependent, OTHER);
        var helper = function(analyzed, "helper");
        var untouched = function(analyzed, "untouched");
        require(analyzed.resultOf(helper, RANGE).orElseThrow().equals(new IntRange(1, 10)), "Typed range retrieval");
        require(analyzed.resultOf(helper, OTHER_RANGE).orElseThrow().equals(new IntRange(20, 30)), "Producer-qualified key isolation");
        require(analyzed.resultOf(helper, MAXIMUM).orElseThrow() == 10, "Consumer value");
        require(analyzed.resultOf(helper, MISSING).isEmpty(), "Missing key");
        require(analyzed.semanticDiagnostics().stream().noneMatch(d -> d.severity() == Diagnostic.Severity.WARNING), "No warning required");
        require(analyzed.passStatus(RANGES.value(), helper).orElseThrow() == PassRun.Status.CURRENT, "Range pass current without warnings");
        require(analyzed.passStatus(CONSUMER.value(), helper).orElseThrow() == PassRun.Status.CURRENT, "Consumer pass current");
        List<String> customOrder = analyzed.passRuns().stream().map(PassRun::passId).filter(id -> id.startsWith("example.")).toList();
        require(customOrder.equals(List.of(RANGES.value(), CONSUMER.value(), OTHER.value())), "Dependency must execute before consumer exactly once");
        String analyzedJson = json(analyzed);

        // Publishing a smaller set is a replacement, not an overlay of last run's keys.
        Project rerun = new PassRunner(TYPES).register(new RangePass(false)).run(analyzed, RANGES, helper);
        var rerunHelper = function(rerun, "helper");
        var rerunUntouched = function(rerun, "untouched");
        require(rerun.resultOf(rerunHelper, RANGE).isEmpty(), "Omitted scoped result must disappear");
        require(rerun.resultStatus(rerunHelper, RANGE).isEmpty(), "Omitted result must not linger as stale metadata");
        require(rerun.resultOf(rerunHelper, LABEL).orElseThrow().equals("rerun"), "New result replaces old value");
        require(rerun.resultOf(rerunUntouched, RANGE).orElseThrow().equals(new IntRange(1, 10)), "Out-of-scope result survives");
        require(rerun.resultOf(rerunHelper, OTHER_RANGE).orElseThrow().equals(new IntRange(20, 30)), "Another producer survives");
        require(rerun.passStatus(RANGES.value(), rerunUntouched).equals(analyzed.passStatus(RANGES.value(), untouched)), "No invented out-of-scope freshness");
        require(analyzedJson.equals(json(analyzed)), "Rerun must not change ancestor");
        verifyFailures(analyzed);

        // Transform the pre-rerun branch: it still has the range required by FoldAddition.
        Project transformed = runner.run(analyzed, FOLD, helper);
        var transformedHelper = function(transformed, "helper");
        require(nodes(transformedHelper, AddExpression.class).isEmpty(), "Addition was replaced");
        require(nodes(helper, AddExpression.class).size() == 1, "Ancestor still owns the addition");
        require(transformed.resultOf(transformedHelper, RANGE).isEmpty(), "Edited analysis value cannot be exposed as current");
        require(transformed.passStatus(RANGES.value(), transformedHelper).orElseThrow() == PassRun.Status.STALE, "Edited analysis must be stale");
        require(transformed.passStatus(FOLD.value(), transformedHelper).orElseThrow() == PassRun.Status.CURRENT, "Transformation completion");
        var replacement = nodes(transformedHelper, IntegerLiteral.class).stream().filter(n -> n.value() == 3).findFirst().orElseThrow();
        require(replacement.transformationProvenance().stream().anyMatch(edge -> edge.passId().equals(FOLD.value())
                && edge.source() instanceof AddExpression), "Pass-tagged predecessor provenance");
        require(analyzedJson.equals(json(analyzed)), "Transformation must not alter ancestor");
        require(originalJson.equals(json(original)), "All operations preserve original snapshot");
        require(json(transformed).equals(json(transformed)), "Deterministic repeated JSON export");

        // A rerun elsewhere must not make an already-stale result appear current.
        String transformedJson = json(transformed);
        Project staleRerun = new PassRunner(TYPES).register(new RangePass(false))
                .run(transformed, RANGES, function(transformed, "untouched"));
        var staleHelper = function(staleRerun, "helper");
        require(staleRerun.resultStatus(staleHelper, RANGE).orElseThrow() == PassRun.Status.STALE, "Out-of-scope stale payload survives honestly");
        require(staleRerun.resultOf(staleHelper, RANGE).isEmpty(), "Stale payload is not a current typed value");
        require(staleRerun.resultStatus(function(staleRerun, "untouched"), RANGE).isEmpty(), "Selected omitted range is removed");
        require(staleRerun.resultStatus(staleHelper, OTHER_RANGE).equals(transformed.resultStatus(transformedHelper, OTHER_RANGE)), "Another producer retains actual freshness");
        require(transformedJson.equals(json(transformed)), "Rerunning a branch preserves transformed ancestor");

        // Validate source generation before writing any deliverable.
        var originalSources = Cpg.sources(original, Language.CPP);
        var transformedSources = Cpg.sources(transformed, Language.CPP);
        require(originalSources.get("main.cpp").contains("return 1 + 2;"), "Original written expression");
        require(transformedSources.get("main.cpp").contains("return 3;"), "Generated constant");
        Files.createDirectories(output);
        emit(originalSources, output.resolve("original"));
        emit(transformedSources, output.resolve("transformed"));
        ProjectJson.write(original, output.resolve("original.json"));
        ProjectJson.write(analyzed, output.resolve("analyzed.json"));
        ProjectJson.write(rerun, output.resolve("rerun.json"));
        ProjectJson.write(transformed, output.resolve("transformed.json"));
        ProjectJson.write(transformed, output.resolve("transformed-repeat.json"));
        ProjectJson.write(staleRerun, output.resolve("stale-rerun.json"));
        Files.writeString(output.resolve("checks.txt"), "typed-values\ndependency-order\nproducer-isolation\nmissing-optional\nscoped-replacement\natomic-analysis\natomic-transformation\nimmutable-history\n");
        System.out.println("Custom passes verified; generated original and transformed C++03 sources in " + output);
    }

    private static void verifyFailures(Project input) {
        String before = json(input);
        AnalysisPass failedAnalysis = new AnalysisPass() {
            public PassId id() { return FAIL_ANALYSIS; }
            public void analyze(PassContext context, AnalysisResults results) {
                Node node = context.function().orElseThrow();
                results.put(node, new ResultKey<>(FAIL_ANALYSIS, "partial", Integer.class), 99);
                results.diagnose(node, new Diagnostic("example.partial", Diagnostic.Severity.WARNING, "Must not escape a failed invocation"));
                throw new IllegalStateException("analysis failed deliberately");
            }
        };
        expectFailure(() -> new PassRunner(TYPES).register(failedAnalysis).run(input, FAIL_ANALYSIS, function(input, "helper")), "analysis failed deliberately");
        require(before.equals(json(input)), "Failed analysis published partial values, warnings, or history");
        TransformationPass failedTransformation = new TransformationPass() {
            public PassId id() { return FAIL_TRANSFORM; }
            public void transform(PassContext context, ProjectEditor editor) {
                Node node = context.nodes().stream().filter(AddExpression.class::isInstance).findFirst().orElseThrow();
                editor.replace(node, IntegerLiteral.create().setValue(99).build());
                throw new IllegalStateException("transformation failed deliberately");
            }
        };
        expectFailure(() -> new PassRunner(TYPES).register(failedTransformation).run(input, FAIL_TRANSFORM, function(input, "helper")), "transformation failed deliberately");
        require(before.equals(json(input)), "Failed transformation published edits or history");
        require(input.passRuns().stream().noneMatch(run -> run.passId().startsWith("example.failed-")), "Failure must not claim completion");
    }

    private static void expectFailure(Runnable action, String message) {
        try { action.run(); throw new AssertionError("Expected failure: " + message); }
        catch (IllegalStateException failure) { require(message.equals(failure.getMessage()), "Unexpected failure: " + failure); }
    }
    private static String json(Project project) { return ProjectJson.encode(project).toText("  "); }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static CallableDeclaration function(Project project, String name) {
        return nodes(project, CallableDeclaration.class).stream().filter(f -> f.name().equals(name)).findFirst().orElseThrow();
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    private static void emit(Map<String, String> sources, Path output) throws Exception {
        for (var entry : sources.entrySet()) {
            Path destination = output.resolve(entry.getKey());
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, entry.getValue(), StandardOpenOption.CREATE_NEW);
        }
    }
}
