/* External lattice clients and native C++03 interval-analysis acceptance. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.json.Json;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** Compiles the checked-in example with only the delivered artifact on its classpath. */
final class IntegerRangesAcceptance {
    private IntegerRangesAcceptance() { }

    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        Path jar = Path.of(Objects.requireNonNull(System.getProperty("cpg.e2e.jar"), "Run via mvn verify")).toAbsolutePath();
        assertTrue(Files.isRegularFile(jar), "Packaged public artifact is required");
        Path client = Files.createDirectories(work.resolve("external lattice client"));
        Path classes = Files.createDirectory(client.resolve("classes"));
        Path source = Files.copy(Path.of("examples/cpp03-integer-ranges/RunRanges.java"), client.resolve("RunRanges.java"));
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        // Neither library source directories nor test classes may be on these classpaths.
        command(List.of(java, "-m", "jdk.compiler/com.sun.tools.javac.Main", "-Xlint:all", "-Werror",
                "-classpath", jar.toString(), "-d", classes.toAbsolutePath().toString(), source.toAbsolutePath().toString()), client);
        Path exported = client.resolve("exported").toAbsolutePath();
        command(List.of(java, "-Dfile.encoding=UTF-8", "-classpath", jar + File.pathSeparator + classes.toAbsolutePath(),
                "RunRanges", cli.input.toAbsolutePath().toString(), exported.toString()), client);
        assertEquals(List.of("integer-lattice", "singleton-normalization", "ordinary-is-opt-in", "direct-ranges",
                "branch-interval-join", "equality-five-possible", "dependency-order", "custom-domain", "generic-json",
                "absent-bottom-top", "immutable-sources"), Files.readAllLines(exported.resolve("checks.txt")));
        assertEquals(Files.readString(exported.resolve("dependent.json")), Files.readString(exported.resolve("dependent-repeat.json")),
                "Generic lattice serialization is deterministic");

        Map<?, ?> ordinary = read(exported.resolve("ordinary.json"));
        Map<?, ?> direct = read(exported.resolve("direct.json"));
        Map<?, ?> dependent = read(exported.resolve("dependent.json"));
        verifyNoRanges(ordinary);
        verifyRanges(direct);
        verifyRanges(dependent);
        assertEquals(List.of("IntegerRanges"), relevantHistory(direct));
        assertEquals(List.of("IntegerRanges", "example.range-checker"), relevantHistory(dependent));
        assertTrue(results(direct).stream().noneMatch(result -> "example.range-checker".equals(result.get("producer"))));
        verifyGenericResults(dependent);
        for (Map<?, ?> root : List.of(ordinary, direct, dependent)) {
            assertTrue(entries(root, "analysisDiagnostics").isEmpty(), "Published results are separate from warnings");
            verifyReferences(root);
        }
        String originalSource = Files.readString(cli.input.resolve("main.cpp"));
        for (String stage : List.of("ordinary", "direct", "dependent")) {
            assertEquals(originalSource, Files.readString(exported.resolve(stage).resolve("main.cpp")),
                    "Read-only analyses cannot rewrite written expressions or comments");
        }

        // Reparse generated sources through the ordinary CLI. It neither loads the
        // external checker nor fabricates the opt-in range pass's products/history.
        var reparsed = new CliSandbox(Files.createDirectory(work.resolve("reparse"))).directory(exported.resolve("dependent"));
        var run = reparsed.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(0);
        Map<?, ?> reparsedJson = read(run.work().resolve("snapshot.json"));
        verifyNoRanges(reparsedJson);
        assertTrue(results(reparsedJson).isEmpty());
        assertEquals(originalSource, Files.readString(run.work().resolve("generated/main.cpp")));
        return List.of(cli.input, exported.resolve("ordinary"), exported.resolve("direct"), exported.resolve("dependent"),
                run.work().resolve("generated"));
    }

    private static void verifyNoRanges(Map<?, ?> root) {
        assertTrue(relevantHistory(root).isEmpty(), "Normal build must not silently run interval analysis");
        assertTrue(results(root).isEmpty(), "An absent analysis has no published bottom/top placeholders");
    }

    private static void verifyRanges(Map<?, ?> root) {
        Map<Integer, Map<?, ?>> nodes = nodes(root);
        Map<?, ?> equality = nodes.values().stream().filter(node -> "EqualExpression".equals(node.get("kind")))
                .filter(node -> node.get("right") instanceof Map<?, ?> right && right.get("value") instanceof Number value && value.intValue() == 5)
                .findFirst().orElseThrow();
        Map<?, ?> left = (Map<?, ?>) equality.get("left");
        Map<?, ?> right = (Map<?, ?>) equality.get("right");
        Map<?, ?> joined = result(root, "IntegerRanges", "range", ((Number) left.get("id")).intValue());
        assertEquals("com.kniazkov.cpg.lattice.IntegerValue", joined.get("type"));
        assertInteger(joined.get("value"), "interval", 1, 10);
        Map<?, ?> read = left;
        while ("ConversionExpression".equals(read.get("kind"))) read = (Map<?, ?>) read.get("operand");
        assertEquals("NameExpression", read.get("kind"));
        assertEquals("value", read.get("name"));
        assertInteger(result(root, "IntegerRanges", "range", ((Number) read.get("id")).intValue()).get("value"), "interval", 1, 10);
        assertInteger(result(root, "IntegerRanges", "range", ((Number) equality.get("id")).intValue()).get("value"), "interval", 0, 1);
        assertInteger(result(root, "IntegerRanges", "range", ((Number) right.get("id")).intValue()).get("value"), "constant", 5, 5);
        assertTrue(entries(root, "passRuns").stream().filter(run -> "IntegerRanges".equals(run.get("passId")))
                .allMatch(run -> "current".equals(run.get("status"))), "Ranges finish without needing a warning");
    }

    private static void verifyGenericResults(Map<?, ?> root) {
        Map<Integer, Map<?, ?>> nodes = nodes(root);
        int function = nodes.entrySet().stream().filter(entry -> "joined".equals(entry.getValue().get("name")))
                .filter(entry -> String.valueOf(entry.getValue().get("kind")).endsWith("FunctionDeclaration"))
                .map(Map.Entry::getKey).findFirst().orElseThrow();
        List<Map<?, ?>> checker = results(root).stream().filter(value -> "example.range-checker".equals(value.get("producer"))).toList();
        assertEquals(9, checker.size());
        assertEquals(Boolean.TRUE, result(root, "example.range-checker", "five-is-possible", function).get("value"));
        for (String name : List.of("sign", "sign-bottom", "sign-top", "integer-bottom", "integer-top",
                "integer-constant", "integer-singleton", "integer-interval")) {
            Map<?, ?> result = result(root, "example.range-checker", name, function);
            assertEquals("com.kniazkov.cpg.lattice.LatticeElement", result.get("type"), "One common public export interface");
            assertInstanceOf(Map.class, result.get("value"), "Domain values must not be toString text");
        }
        assertEquals(Map.of("domain", "example.sign", "kind", "set", "signs", List.of("positive")),
                result(root, "example.range-checker", "sign", function).get("value"));
        assertEquals(Map.of("domain", "example.sign", "kind", "bottom", "signs", List.of()),
                result(root, "example.range-checker", "sign-bottom", function).get("value"));
        assertEquals(Map.of("domain", "example.sign", "kind", "top", "signs", List.of("negative", "zero", "positive")),
                result(root, "example.range-checker", "sign-top", function).get("value"));
        assertInteger(result(root, "example.range-checker", "integer-bottom", function).get("value"), "bottom", 0, 0);
        assertInteger(result(root, "example.range-checker", "integer-top", function).get("value"), "top", 0, 0);
        assertInteger(result(root, "example.range-checker", "integer-constant", function).get("value"), "constant", 7, 7);
        assertEquals(result(root, "example.range-checker", "integer-constant", function).get("value"),
                result(root, "example.range-checker", "integer-singleton", function).get("value"), "[7,7] and 7 export identically");
        assertInteger(result(root, "example.range-checker", "integer-interval", function).get("value"), "interval", 1, 10);
        assertTrue(checker.stream().noneMatch(value -> "absent".equals(value.get("name"))), "Absence is not a published lattice bound");
        Map<?, ?> checkerRun = entries(root, "passRuns").stream().filter(run -> "example.range-checker".equals(run.get("passId"))).findFirst().orElseThrow();
        assertEquals(List.of("IntegerRanges"), checkerRun.get("requires"));
        assertEquals("current", checkerRun.get("status"));
    }

    private static void assertInteger(Object value, String kind, int lower, int upper) {
        assertInstanceOf(Map.class, value);
        Map<?, ?> integer = (Map<?, ?>) value;
        assertEquals("integer", integer.get("domain"));
        assertEquals(kind, integer.get("kind"));
        if ("constant".equals(kind)) {
            assertEquals(Set.of("domain", "kind", "value"), integer.keySet());
            assertEquals(lower, ((Number) integer.get("value")).intValue());
        } else if ("interval".equals(kind)) {
            assertEquals(Set.of("domain", "kind", "lower", "upper"), integer.keySet());
            assertEquals(lower, ((Number) integer.get("lower")).intValue());
            assertEquals(upper, ((Number) integer.get("upper")).intValue());
        } else assertEquals(Set.of("domain", "kind"), integer.keySet());
    }
    private static void verifyReferences(Map<?, ?> root) {
        Map<Integer, Map<?, ?>> nodes = nodes(root);
        for (Map<?, ?> result : results(root)) {
            assertTrue(nodes.containsKey(((Number) result.get("nodeId")).intValue()), "Result node is defined");
            assertEquals("current", result.get("status"));
        }
        long sequence = -1;
        for (Map<?, ?> run : entries(root, "passRuns")) {
            long next = ((Number) run.get("sequence")).longValue();
            assertTrue(next > sequence, "History is chronological");
            sequence = next;
            for (Map<?, ?> covered : entries(run, "results")) if (covered.get("functionId") instanceof Number id)
                assertEquals(covered.get("name"), nodes.get(id.intValue()).get("name"));
        }
    }
    private static List<String> relevantHistory(Map<?, ?> root) {
        return entries(root, "passRuns").stream().map(run -> String.valueOf(run.get("passId")))
                .filter(id -> id.equals("IntegerRanges") || id.equals("example.range-checker")).toList();
    }
    private static Map<?, ?> result(Map<?, ?> root, String producer, String name, int node) {
        List<Map<?, ?>> matches = results(root).stream().filter(value -> producer.equals(value.get("producer"))
                && name.equals(value.get("name")) && ((Number) value.get("nodeId")).intValue() == node).toList();
        assertEquals(1, matches.size(), producer + ":" + name + " on node " + node);
        assertEquals("current", matches.getFirst().get("status"));
        return matches.getFirst();
    }
    private static Map<?, ?> read(Path path) throws Exception { return (Map<?, ?>) Json.parse(path.toFile()).toJavaObject(); }
    private static List<Map<?, ?>> results(Map<?, ?> root) { return entries(root, "analysisResults"); }
    private static List<Map<?, ?>> entries(Map<?, ?> map, String key) {
        if (!(map.get(key) instanceof List<?> list)) return List.of();
        var entries = new ArrayList<Map<?, ?>>();
        for (Object value : list) entries.add((Map<?, ?>) value);
        return entries;
    }
    private static Map<Integer, Map<?, ?>> nodes(Object value) {
        var nodes = new LinkedHashMap<Integer, Map<?, ?>>();
        collect(value, nodes);
        return nodes;
    }
    private static void collect(Object value, Map<Integer, Map<?, ?>> nodes) {
        if (value instanceof List<?> list) { list.forEach(item -> collect(item, nodes)); return; }
        if (!(value instanceof Map<?, ?> map)) return;
        if (map.get("id") instanceof Number id && map.get("kind") instanceof String)
            assertNull(nodes.put(id.intValue(), map), "Each JSON node has one definition");
        map.values().forEach(item -> collect(item, nodes));
    }
    private static void command(List<String> command, Path directory) throws Exception {
        Path out = Files.createTempFile(directory, "client-", ".out");
        Path err = Files.createTempFile(directory, "client-", ".err");
        Process child = new ProcessBuilder(command).directory(directory.toFile()).redirectOutput(out.toFile()).redirectError(err.toFile()).start();
        try {
            assertTrue(child.waitFor(60, TimeUnit.SECONDS), "Timed out: " + command);
            assertEquals(0, child.exitValue(), command + "\n" + Files.readString(out) + Files.readString(err));
        } finally {
            if (child.isAlive()) { child.destroyForcibly(); child.waitFor(); }
        }
    }
}
