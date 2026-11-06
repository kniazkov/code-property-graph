/* Standalone lattice and interval analysis client. Packaged JAR only. MIT License. */
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.analysis.IntegerRanges;
import com.kniazkov.cpg.lattice.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.passes.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import com.kniazkov.json.JsonElement;
import java.nio.file.*;
import java.util.*;

/** External client: no library package access, custom codec, reflection, or test classes. */
public final class RunRanges {
    private static final IntegerDomain INTEGERS = IntegerDomain.INSTANCE;
    private static final PassId CHECKER = new PassId("example.range-checker");
    private static final ResultKey<Boolean> POSSIBLE = new ResultKey<>(CHECKER, "five-is-possible", Boolean.class);
    private static final ResultKey<LatticeElement> SIGN = key("sign");
    private static final ResultKey<LatticeElement> SIGN_BOTTOM = key("sign-bottom");
    private static final ResultKey<LatticeElement> SIGN_TOP = key("sign-top");
    private static final ResultKey<LatticeElement> INTEGER_BOTTOM = key("integer-bottom");
    private static final ResultKey<LatticeElement> INTEGER_TOP = key("integer-top");
    private static final ResultKey<LatticeElement> INTEGER_CONSTANT = key("integer-constant");
    private static final ResultKey<LatticeElement> INTEGER_SINGLETON = key("integer-singleton");
    private static final ResultKey<LatticeElement> INTEGER_INTERVAL = key("integer-interval");
    private static final ResultKey<LatticeElement> ABSENT = key("absent");

    /** The eight elements of a powerset lattice over negative, zero and positive. */
    public enum SignValue implements LatticeElement {
        BOTTOM(0), NEGATIVE(1), ZERO(2), NONPOSITIVE(3), POSITIVE(4), NONZERO(5), NONNEGATIVE(6), TOP(7);
        private final int mask;
        SignValue(int mask) { this.mask = mask; }
        @Override public JsonElement toJson() {
            var json = Json.object().put("domain", "example.sign")
                    .put("kind", mask == 0 ? "bottom" : mask == 7 ? "top" : "set");
            return json.array("signs", signs -> {
                if ((mask & 1) != 0) signs.add("negative");
                if ((mask & 2) != 0) signs.add("zero");
                if ((mask & 4) != 0) signs.add("positive");
            }).build();
        }
    }

    /** A user-defined domain, independent of all built-in domain implementations. */
    public static final class SignDomain implements Lattice<SignValue> {
        @Override public SignValue bottom() { return SignValue.BOTTOM; }
        @Override public SignValue top() { return SignValue.TOP; }
        @Override public boolean lessOrEqual(SignValue left, SignValue right) {
            return (left.mask & right.mask) == left.mask;
        }
        @Override public SignValue join(SignValue left, SignValue right) {
            return SignValue.values()[left.mask | right.mask];
        }
        @Override public SignValue meet(SignValue left, SignValue right) {
            return SignValue.values()[left.mask & right.mask];
        }
        public SignValue abstractRange(IntegerValue range) {
            if (range.isBottom()) return bottom();
            SignValue result = bottom();
            if (range.lowerBound().isEmpty() || range.lowerBound().orElseThrow().signum() < 0)
                result = join(result, SignValue.NEGATIVE);
            if (range.contains(0)) result = join(result, SignValue.ZERO);
            if (range.upperBound().isEmpty() || range.upperBound().orElseThrow().signum() > 0)
                result = join(result, SignValue.POSITIVE);
            return result;
        }
    }

    /** Requesting this checker is enough to prepare its explicit range dependency. */
    private static final class RangeChecker implements AnalysisPass {
        @Override public PassId id() { return CHECKER; }
        @Override public Set<PassId> requires() { return Set.of(IntegerRanges.ID); }
        @Override public void analyze(PassContext context, AnalysisResults results) {
            require(context.dependencies().contains(IntegerRanges.ID), "Declared range dependency");
            CallableDeclaration function = context.nodes().stream().filter(CallableDeclaration.class::isInstance)
                    .map(CallableDeclaration.class::cast).filter(node -> node.name().equals("joined")).findFirst().orElseThrow();
            EqualExpression equality = equality(function);
            IntegerValue joined = context.resultOf(equality.left(), IntegerRanges.RANGE).orElseThrow();
            IntegerValue possible = context.resultOf(equality, IntegerRanges.RANGE).orElseThrow();
            require(joined.equals(INTEGERS.interval(1, 10)), "Branches must join to an interval, not a two-element set");
            require(joined.contains(5) && possible.contains(1), "Interval analysis must keep value == 5 possible");
            SignDomain signs = new SignDomain();
            SignValue positive = signs.abstractRange(joined);
            require(positive == SignValue.POSITIVE, "The custom domain consumes a real range result");
            require(signs.lessOrEqual(positive, signs.top()), "Custom partial order");
            require(signs.join(positive, SignValue.NEGATIVE) == SignValue.NONZERO, "Custom join");
            require(signs.meet(positive, SignValue.NEGATIVE) == signs.bottom(), "Custom meet");
            require(context.resultOf(function, ABSENT).isEmpty(), "Absent is not bottom or top");
            results.put(function, POSSIBLE, possible.contains(1));
            // Every domain result below is published with the same interface type. There
            // is no class switch, per-domain encoder registration, or toString fallback.
            results.put(function, SIGN, positive);
            results.put(function, SIGN_BOTTOM, signs.bottom());
            results.put(function, SIGN_TOP, signs.top());
            results.put(function, INTEGER_BOTTOM, INTEGERS.bottom());
            results.put(function, INTEGER_TOP, INTEGERS.top());
            results.put(function, INTEGER_CONSTANT, INTEGERS.constant(7));
            results.put(function, INTEGER_SINGLETON, INTEGERS.interval(7, 7));
            results.put(function, INTEGER_INTERVAL, joined);
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException("RunRanges CPP_SOURCE_DIRECTORY NEW_OUTPUT_DIRECTORY");
        Path input = Path.of(args[0]), output = Path.of(args[1]);
        if (Files.exists(output)) throw new IllegalArgumentException("Output already exists: " + output);
        verifyIntegerDomain();
        Project ordinary = Cpg.read(input, new Cpg.Options(Language.CPP, Cpg.Stage.RESOLVED, "c++03"));
        require(!ordinary.hasErrors(), "Fixture must parse and resolve");
        String before = json(ordinary);
        require(ordinary.passStatus(IntegerRanges.ID.value()).isEmpty(), "Normal build must not run ranges");
        require(ordinary.resultOf(equality(function(ordinary)), IntegerRanges.RANGE).isEmpty(), "Normal build has no range value");

        // Both runs start from the ordinary snapshot. The checker cannot accidentally
        // reuse a direct run's products and pretend its dependency was prepared.
        Project direct = new PassRunner().register(new IntegerRanges()).run(ordinary, IntegerRanges.ID);
        verifyRanges(direct);
        require(direct.passStatus(CHECKER.value()).isEmpty(), "Direct request does not execute the checker");
        Project dependent = new PassRunner().register(new IntegerRanges()).register(new RangeChecker()).run(ordinary, CHECKER);
        verifyRanges(dependent);
        require(dependent.passStatus(CHECKER.value()).orElseThrow() == PassRun.Status.CURRENT, "Checker completed");
        List<String> requested = dependent.passRuns().stream().map(PassRun::passId)
                .filter(id -> id.equals(IntegerRanges.ID.value()) || id.equals(CHECKER.value())).toList();
        require(requested.equals(List.of(IntegerRanges.ID.value(), CHECKER.value())), "Range dependency runs exactly once before checker");
        CallableDeclaration checked = function(dependent);
        require(dependent.resultOf(checked, POSSIBLE).orElseThrow(), "Checker publishes a usable result");
        require(dependent.resultOf(checked, SIGN).orElseThrow().equals(SignValue.POSITIVE), "Generic interface retrieval");
        require(dependent.resultOf(checked, SIGN_BOTTOM).isPresent(), "Published custom bottom is present");
        require(dependent.resultOf(checked, SIGN_TOP).isPresent(), "Published custom top is present");
        require(dependent.resultOf(checked, INTEGER_BOTTOM).orElseThrow().equals(INTEGERS.bottom()), "Published integer bottom is present");
        require(dependent.resultOf(checked, INTEGER_TOP).orElseThrow().equals(INTEGERS.top()), "Published integer top is present");
        require(dependent.resultOf(checked, ABSENT).isEmpty() && dependent.resultStatus(checked, ABSENT).isEmpty(), "Unpublished result stays absent");
        require(before.equals(json(ordinary)), "Range and custom analyses preserve their input snapshot");
        require(direct.analysisDiagnostics().isEmpty() && dependent.analysisDiagnostics().isEmpty(), "Results are not warnings");
        Map<String, String> sources = Cpg.sources(ordinary, Language.CPP);
        require(sources.equals(Cpg.sources(direct, Language.CPP)), "Direct analysis preserves source behavior");
        require(sources.equals(Cpg.sources(dependent, Language.CPP)), "Dependent analysis preserves source behavior");
        Files.createDirectories(output);
        emit(sources, output.resolve("ordinary"));
        emit(Cpg.sources(direct, Language.CPP), output.resolve("direct"));
        emit(Cpg.sources(dependent, Language.CPP), output.resolve("dependent"));
        ProjectJson.write(ordinary, output.resolve("ordinary.json"));
        ProjectJson.write(direct, output.resolve("direct.json"));
        ProjectJson.write(dependent, output.resolve("dependent.json"));
        ProjectJson.write(dependent, output.resolve("dependent-repeat.json"));
        Files.writeString(output.resolve("checks.txt"), "integer-lattice\nsingleton-normalization\nordinary-is-opt-in\ndirect-ranges\nbranch-interval-join\nequality-five-possible\ndependency-order\ncustom-domain\ngeneric-json\nabsent-bottom-top\nimmutable-sources\n");
        System.out.println("Lattice and opt-in integer range analysis verified in " + output);
    }

    private static void verifyIntegerDomain() {
        Lattice<IntegerValue> lattice = INTEGERS;
        IntegerValue seven = INTEGERS.constant(7), singleton = INTEGERS.interval(7, 7);
        require(seven.equals(singleton) && seven.hashCode() == singleton.hashCode(), "Singleton semantic equality and hash code");
        require(singleton.isConstant() && singleton.constant().orElseThrow().intValueExact() == 7, "Singleton normalizes to constant");
        require(seven.toJson().toText("  ").equals(singleton.toJson().toText("  ")), "Equivalent values have identical structured JSON");
        IntegerValue joined = lattice.join(INTEGERS.constant(1), INTEGERS.constant(10));
        require(joined.equals(INTEGERS.interval(1, 10)), "Integer join is interval hull");
        require(lattice.lessOrEqual(seven, joined), "Integer partial order");
        require(lattice.meet(INTEGERS.interval(1, 7), INTEGERS.interval(7, 10)).equals(seven), "Integer meet normalizes singleton");
        require(lattice.meet(INTEGERS.constant(1), INTEGERS.constant(10)).equals(lattice.bottom()), "Disjoint meet is bottom");
        require(lattice.join(lattice.bottom(), seven).equals(seven), "Bottom is join identity");
        require(lattice.meet(lattice.top(), seven).equals(seven), "Top is meet identity");
        require(lattice.join(lattice.top(), seven).equals(lattice.top()), "Top is join absorbing");
        require(lattice.lessOrEqual(lattice.bottom(), seven) && lattice.lessOrEqual(seven, lattice.top()), "Bounds of the order");
    }
    private static void verifyRanges(Project project) {
        EqualExpression comparison = equality(function(project));
        require(project.resultOf(comparison.left(), IntegerRanges.RANGE).orElseThrow().equals(INTEGERS.interval(1, 10)), "Read after join is [1,10]");
        require(project.resultOf(comparison, IntegerRanges.RANGE).orElseThrow().equals(INTEGERS.interval(0, 1)), "Equality is possible, not guaranteed");
        require(project.passStatus(IntegerRanges.ID.value()).orElseThrow() == PassRun.Status.CURRENT, "Range pass completion");
    }
    private static ResultKey<LatticeElement> key(String name) { return new ResultKey<>(CHECKER, name, LatticeElement.class); }
    private static String json(Project project) { return ProjectJson.encode(project).toText("  "); }
    private static CallableDeclaration function(Project project) {
        return nodes(project, CallableDeclaration.class).stream().filter(node -> node.name().equals("joined")).findFirst().orElseThrow();
    }
    private static EqualExpression equality(Node function) { return nodes(function, EqualExpression.class).getFirst(); }
    private static <T extends Node> List<T> nodes(Node root, Class<T> type) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node node) { if (type.isInstance(node)) result.add(type.cast(node)); } });
        return result;
    }
    private static void require(boolean condition, String message) { if (!condition) throw new AssertionError(message); }
    private static void emit(Map<String, String> sources, Path output) throws Exception {
        for (var entry : sources.entrySet()) {
            Path destination = output.resolve(entry.getKey());
            Files.createDirectories(destination.getParent());
            Files.writeString(destination, entry.getValue(), StandardOpenOption.CREATE_NEW);
        }
    }
}
