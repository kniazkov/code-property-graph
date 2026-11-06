/* Public custom extensions compiled against the delivered artifact. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.json.Json;
import java.io.File;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;

/** The only extension implementation lives outside the library package and test classpath. */
final class CustomPassAcceptance {
    private CustomPassAcceptance() { }

    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(work).directory(fixture.resolve("sources"));
        Path jar = Path.of(Objects.requireNonNull(System.getProperty("cpg.e2e.jar"), "Run via mvn verify")).toAbsolutePath();
        assertTrue(Files.isRegularFile(jar), "Packaged public artifact is required");
        Path client = Files.createDirectories(work.resolve("external client"));
        Path classes = Files.createDirectory(client.resolve("classes"));
        Path source = Files.copy(Path.of("examples/cpp03-custom-passes/RunPasses.java"), client.resolve("RunPasses.java"));
        String java = Path.of(System.getProperty("java.home"), "bin",
                System.getProperty("os.name").startsWith("Windows") ? "java.exe" : "java").toString();
        // Use the JDK compiler module so the check also works when javac is not on PATH.
        // No target/classes or target/test-classes may appear in either child classpath.
        command(List.of(java, "-m", "jdk.compiler/com.sun.tools.javac.Main", "-Xlint:all", "-Werror",
                "-classpath", jar.toString(), "-d", classes.toAbsolutePath().toString(), source.toAbsolutePath().toString()), client);
        Path exported = client.resolve("exported").toAbsolutePath();
        command(List.of(java, "-Dfile.encoding=UTF-8", "-classpath", jar + File.pathSeparator + classes.toAbsolutePath(),
                "RunPasses", cli.input.toAbsolutePath().toString(), exported.toString()), client);
        assertEquals(List.of("typed-values", "dependency-order", "producer-isolation", "missing-optional", "scoped-replacement",
                "atomic-analysis", "atomic-transformation", "immutable-history"), Files.readAllLines(exported.resolve("checks.txt")));
        assertEquals(Files.readString(exported.resolve("transformed.json")), Files.readString(exported.resolve("transformed-repeat.json")),
                "Repeat export must be byte-for-byte deterministic");

        Map<?, ?> original = read(exported.resolve("original.json"));
        Map<?, ?> analyzed = read(exported.resolve("analyzed.json"));
        Map<?, ?> rerun = read(exported.resolve("rerun.json"));
        Map<?, ?> transformed = read(exported.resolve("transformed.json"));
        Map<?, ?> staleRerun = read(exported.resolve("stale-rerun.json"));
        assertTrue(results(original).isEmpty());
        verifyValues(analyzed);
        verifyScopedRerun(analyzed, rerun);
        verifyTransformation(transformed);
        assertEquals("stale", result(staleRerun, nodes(staleRerun), "example.ranges", "range", "helper").get("status"));
        assertRange(result(staleRerun, nodes(staleRerun), "example.ranges", "range", "helper").get("value"), 1, 10);
        assertFalse(results(staleRerun).stream().anyMatch(value -> matches(value, nodes(staleRerun), "example.ranges", "range", "untouched")));
        assertEquals(result(transformed, nodes(transformed), "example.other-ranges", "range", "helper").get("status"),
                result(staleRerun, nodes(staleRerun), "example.other-ranges", "range", "helper").get("status"));
        for (Map<?, ?> root : List.of(original, analyzed, rerun, transformed, staleRerun)) {
            assertTrue(entries(root, "analysisDiagnostics").isEmpty(), "Custom values do not masquerade as diagnostics");
            assertTrue(entries(root, "passRuns").stream().noneMatch(run -> String.valueOf(run.get("passId")).startsWith("example.failed-")),
                    "Failed invocations cannot publish completion history");
            verifyReferences(root);
        }
        assertEquals(Files.readString(cli.input.resolve("main.cpp")), Files.readString(exported.resolve("original/main.cpp")));
        String rewritten = Files.readString(exported.resolve("transformed/main.cpp"));
        assertTrue(rewritten.contains("return 3;"));
        assertTrue(rewritten.contains("return 4 + 5;"), "Unselected function remains written as before");
        assertTrue(rewritten.contains("// A separate function"), "Source comments survive transformation");

        // The ordinary CLI knows nothing about custom classes. It can reparse the generated
        // program and emit normal JSON/sources, but must not fabricate custom pass products.
        var reparse = new CliSandbox(Files.createDirectory(work.resolve("reparse"))).directory(exported.resolve("transformed"));
        var run = reparse.run("cpp", "--stage", "resolved", "--cpp-standard", "c++03").expectStatus(0);
        assertTrue(results(read(run.work().resolve("snapshot.json"))).isEmpty());
        assertEquals(rewritten, Files.readString(run.work().resolve("generated/main.cpp")));
        return List.of(cli.input, exported.resolve("original"), exported.resolve("transformed"), run.work().resolve("generated"));
    }

    private static void verifyValues(Map<?, ?> root) {
        Map<Integer, Map<?, ?>> nodes = nodes(root);
        assertEquals(18, results(root).size());
        for (String function : List.of("helper", "untouched", "main")) {
            Map<?, ?> range = result(root, nodes, "example.ranges", "range", function);
            assertEquals("RunPasses$IntRange", range.get("type"));
            assertEquals("current", range.get("status"));
            assertRange(range.get("value"), 1, 10);
            assertRange(result(root, nodes, "example.other-ranges", "range", function).get("value"), 20, 30);
            assertEquals("initial", result(root, nodes, "example.ranges", "label", function).get("value"));
            assertEquals(Boolean.TRUE, result(root, nodes, "example.ranges", "checked", function).get("value"));
            assertEquals(0.5, ((Number) result(root, nodes, "example.ranges", "confidence", function).get("value")).doubleValue());
            assertEquals(10, ((Number) result(root, nodes, "example.range-consumer", "maximum", function).get("value")).intValue());
        }
        assertEquals(List.of("example.ranges", "example.range-consumer", "example.other-ranges"), entries(root, "passRuns").stream()
                .map(run -> String.valueOf(run.get("passId"))).filter(id -> id.startsWith("example.")).toList());
    }

    private static void verifyScopedRerun(Map<?, ?> before, Map<?, ?> rerun) {
        Map<Integer, Map<?, ?>> nodes = nodes(rerun);
        assertFalse(results(rerun).stream().anyMatch(value -> matches(value, nodes, "example.ranges", "range", "helper")),
                "An omitted key must disappear completely, rather than expose a previous current or stale payload");
        assertEquals("rerun", result(rerun, nodes, "example.ranges", "label", "helper").get("value"));
        for (String function : List.of("untouched", "main")) {
            Map<?, ?> prior = result(before, nodes(before), "example.ranges", "range", function);
            Map<?, ?> retained = result(rerun, nodes, "example.ranges", "range", function);
            assertEquals(prior.get("value"), retained.get("value"));
            assertEquals(prior.get("status"), retained.get("status"));
        }
        assertRange(result(rerun, nodes, "example.other-ranges", "range", "helper").get("value"), 20, 30);
        Map<?, ?> last = entries(rerun, "passRuns").getLast();
        assertEquals("example.ranges", last.get("passId"));
        assertEquals("function", last.get("scope"));
        assertEquals("current", last.get("status"));
        assertEquals(List.of("helper"), entries(last, "results").stream().map(value -> value.get("name")).toList());
    }

    private static void verifyTransformation(Map<?, ?> root) {
        Map<Integer, Map<?, ?>> nodes = nodes(root);
        assertEquals("stale", result(root, nodes, "example.ranges", "range", "helper").get("status"));
        assertRange(result(root, nodes, "example.ranges", "range", "helper").get("value"), 1, 10);
        // Unrelated products remain exported with their actual status, not silently removed.
        assertRange(result(root, nodes, "example.other-ranges", "range", "untouched").get("value"), 20, 30);
        List<Map<?, ?>> transformations = entries(root, "passRuns").stream().filter(run -> "example.fold-addition".equals(run.get("passId"))).toList();
        assertEquals(1, transformations.size());
        assertEquals("current", transformations.getFirst().get("status"));
        assertEquals("function", transformations.getFirst().get("scope"));
        assertTrue(nodes.values().stream().anyMatch(node -> "IntegerLiteral".equals(node.get("kind"))
                && entries(node, "transformationProvenance").stream().anyMatch(edge -> "example.fold-addition".equals(edge.get("passId"))
                && "AddExpression".equals(nodes.get(((Number) edge.get("sourceId")).intValue()).get("kind")))),
                "Replacement must link to the historical source expression with the custom pass ID");
        assertFalse(entries(root, "history").isEmpty());
    }

    private static void verifyReferences(Map<?, ?> root) {
        Map<Integer, Map<?, ?>> nodes = nodes(root);
        for (Map<?, ?> value : results(root)) assertTrue(nodes.containsKey(((Number) value.get("nodeId")).intValue()), "Result node is defined");
        long sequence = -1;
        for (Map<?, ?> run : entries(root, "passRuns")) {
            long next = ((Number) run.get("sequence")).longValue();
            assertTrue(next > sequence, "Pass runs retain chronological identity");
            sequence = next;
            for (Map<?, ?> result : entries(run, "results")) if (result.get("functionId") instanceof Number id)
                assertEquals(result.get("name"), nodes.get(id.intValue()).get("name"));
        }
    }
    private static void assertRange(Object value, int min, int max) {
        assertInstanceOf(Map.class, value, "Custom encoder must produce an object, never toString text");
        Map<?, ?> range = (Map<?, ?>) value;
        assertEquals(Set.of("min", "max"), range.keySet());
        assertEquals(min, ((Number) range.get("min")).intValue());
        assertEquals(max, ((Number) range.get("max")).intValue());
    }
    private static Map<?, ?> result(Map<?, ?> root, Map<Integer, Map<?, ?>> nodes, String producer, String name, String function) {
        List<Map<?, ?>> matches = results(root).stream().filter(value -> matches(value, nodes, producer, name, function)).toList();
        assertEquals(1, matches.size(), producer + ":" + name + " on " + function);
        return matches.getFirst();
    }
    private static boolean matches(Map<?, ?> value, Map<Integer, Map<?, ?>> nodes, String producer, String name, String function) {
        return producer.equals(value.get("producer")) && name.equals(value.get("name"))
                && function.equals(nodes.get(((Number) value.get("nodeId")).intValue()).get("name"));
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
            assertNull(nodes.put(id.intValue(), map), "Every JSON node must have one definition");
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
