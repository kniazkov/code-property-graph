/* Generic lattice results use the same frozen publication contract as custom codecs. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.lattice.LatticeElement;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.passes.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import com.kniazkov.json.JsonElement;
import com.kniazkov.json.JsonObject;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LatticeResultJsonTest {
    /** A client-owned domain value, deliberately unknown to the core registry. */
    private record LabelValue(String state, String label) implements LatticeElement {
        @Override public JsonElement toJson() {
            var json = Json.object().put("domain", "labels").put("state", state);
            if (label != null) json.put("label", label);
            return json.build();
        }
    }

    private static AnalysisPass pass(PassId id, BiConsumer<PassContext, AnalysisResults> action) {
        return new AnalysisPass() {
            @Override public PassId id() { return id; }
            @Override public void analyze(PassContext context, AnalysisResults results) {
                action.accept(context, results);
            }
        };
    }

    private static Map<?, ?> document(Project project) {
        return (Map<?, ?>) ProjectJson.encode(project).toJavaObject();
    }

    private static Map<String, Map<?, ?>> results(Project project) {
        var byName = new TreeMap<String, Map<?, ?>>();
        Object entries = document(project).get("analysisResults");
        if (entries != null) for (Object entry : (List<?>) entries) {
            var result = (Map<?, ?>) entry;
            byName.put((String) result.get("name"), result);
        }
        return byName;
    }

    @Test void arbitraryLatticeElementsNeedNoRegistrationForInterfaceOrConcreteKeys() {
        var producer = new PassId("labels");
        var interfaceKey = new ResultKey<>(producer, "interface", LatticeElement.class);
        var concreteKey = new ResultKey<>(producer, "concrete", LabelValue.class);
        var value = new LabelValue("constant", "trusted");
        var expected = Map.of("domain", "labels", "state", "constant", "label", "trusted");
        var types = ResultTypes.defaults();

        assertEquals(expected, types.encode(interfaceKey, value));
        assertEquals(expected, types.encode(concreteKey, value));
        assertEquals(expected, types.encode(Object.class, value),
                "The public value contract also works with a broader declared result type");
        assertThrows(IllegalArgumentException.class, () -> types.encode(String.class, value));
        assertThrows(IllegalArgumentException.class, () -> types.encode(Object.class, new Object()));
    }

    @Test void explicitRegistrationsOverrideOnlyTheirExactDeclaredType() {
        var value = new LabelValue("constant", "trusted");
        var defaults = ResultTypes.defaults();
        var interfaceEncoder = defaults.with(LatticeElement.class, ignored -> "interface override");
        var concreteEncoder = defaults.with(LabelValue.class, ignored -> "concrete override");

        assertEquals("interface override", interfaceEncoder.encode(LatticeElement.class, value));
        assertEquals(defaults.encode(LabelValue.class, value), interfaceEncoder.encode(LabelValue.class, value));
        assertEquals("concrete override", concreteEncoder.encode(LabelValue.class, value));
        assertEquals(defaults.encode(LatticeElement.class, value), concreteEncoder.encode(LatticeElement.class, value));
        assertEquals("concrete override", interfaceEncoder.with(LabelValue.class, ignored -> "concrete override")
                .encode(LabelValue.class, value));

        var calls = new AtomicInteger();
        LatticeElement counted = () -> {
            calls.incrementAndGet();
            return Json.object().put("state", "top").build();
        };
        assertEquals("interface override", interfaceEncoder.encode(LatticeElement.class, counted));
        assertEquals(0, calls.get(), "An explicit codec must not invoke toJson as well");
    }

    @Test void publishedBottomAndTopRemainDistinctFromMissingResultsInApiAndJson() {
        var producer = new PassId("labels");
        var bottomKey = new ResultKey<>(producer, "bottom", LabelValue.class);
        var topKey = new ResultKey<>(producer, "top", LabelValue.class);
        var constantKey = new ResultKey<>(producer, "constant", LabelValue.class);
        var interfaceKey = new ResultKey<>(producer, "interface", LatticeElement.class);
        var absentKey = new ResultKey<>(producer, "absent", LabelValue.class);
        var bottom = new LabelValue("bottom", null);
        var top = new LabelValue("top", null);
        var constant = new LabelValue("constant", "trusted");
        Project input = Project.create().build();
        assertTrue(input.resultOf(input, bottomKey).isEmpty());
        assertTrue(input.resultStatus(input, bottomKey).isEmpty());
        assertTrue(results(input).isEmpty());

        Project project = new PassRunner().register(pass(producer, (context, sink) -> {
            sink.put(context.project(), bottomKey, bottom);
            sink.put(context.project(), topKey, top);
            sink.put(context.project(), constantKey, constant);
            sink.put(context.project(), interfaceKey, bottom);
        })).run(input, producer);

        assertSame(bottom, project.resultOf(project, bottomKey).orElseThrow());
        assertSame(top, project.resultOf(project, topKey).orElseThrow());
        assertSame(constant, project.resultOf(project, constantKey).orElseThrow());
        assertSame(bottom, project.resultOf(project, interfaceKey).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, project.resultStatus(project, bottomKey).orElseThrow());
        assertEquals(PassRun.Status.CURRENT, project.resultStatus(project, topKey).orElseThrow());
        assertTrue(project.resultOf(project, absentKey).isEmpty());
        assertTrue(project.resultStatus(project, absentKey).isEmpty());
        var exported = results(project);
        assertEquals(Set.of("bottom", "top", "constant", "interface"), exported.keySet());
        assertEquals(Map.of("domain", "labels", "state", "bottom"), exported.get("bottom").get("value"));
        assertEquals(Map.of("domain", "labels", "state", "top"), exported.get("top").get("value"));
        assertEquals(Map.of("domain", "labels", "state", "constant", "label", "trusted"),
                exported.get("constant").get("value"));
        assertEquals(exported.get("bottom").get("value"), exported.get("interface").get("value"));
        assertEquals(LabelValue.class.getName(), exported.get("bottom").get("type"));
        assertEquals(LatticeElement.class.getName(), exported.get("interface").get("type"));
        for (var result : exported.values()) {
            assertEquals("labels", result.get("producer"));
            assertEquals("current", result.get("status"));
            assertEquals(0, ((Number) result.get("nodeId")).intValue());
        }
        assertTrue(results(input).isEmpty(), "Publishing must not modify the input snapshot");
    }

    @Test void mutableLatticeJsonIsDeeplyFrozenAndNeverEncodedAgainDuringExport() {
        var producer = new PassId("frozen-lattice");
        var key = new ResultKey<>(producer, "value", LatticeElement.class);
        var calls = new AtomicInteger();
        var returned = new ArrayList<JsonObject>();
        LatticeElement value = () -> {
            calls.incrementAndGet();
            var nested = Json.object().put("bounds", Json.array().add(1).add(10).build()).build();
            var root = Json.object().put("state", "range").put("details", nested).build();
            returned.add(nested);
            returned.add(root);
            return root;
        };
        Project project = new PassRunner().register(pass(producer, (context, sink) ->
                sink.put(context.project(), key, value))).run(Project.create().build(), producer);
        assertEquals(1, calls.get(), "Publication must call the public encoder exactly once");
        String before = ProjectJson.encode(project).toText("");
        returned.get(0).addNumber("extra", 99);
        returned.get(1).addString("state", "changed");

        assertEquals(before, ProjectJson.encode(project).toText(""));
        assertEquals(before, ProjectJson.encode(project).toText(""));
        assertSame(value, project.resultOf(project, key).orElseThrow());
        assertEquals(1, calls.get());
        @SuppressWarnings("unchecked") var frozen = (Map<String, Object>) project.analysisResults().getFirst().encodedValue();
        @SuppressWarnings("unchecked") var nested = (Map<String, Object>) frozen.get("details");
        @SuppressWarnings("unchecked") var bounds = (List<Object>) nested.get("bounds");
        assertThrows(UnsupportedOperationException.class, () -> frozen.put("later", 1));
        assertThrows(UnsupportedOperationException.class, () -> nested.put("later", 1));
        assertThrows(UnsupportedOperationException.class, () -> bounds.add(2));
    }

    @Test void onlyTheLastStagedValueIsEncoded() {
        var producer = new PassId("replacement");
        var key = new ResultKey<>(producer, "value", LatticeElement.class);
        var discardedCalls = new AtomicInteger();
        var publishedCalls = new AtomicInteger();
        LatticeElement discarded = () -> {
            discardedCalls.incrementAndGet();
            return Json.object().put("state", "bottom").build();
        };
        LatticeElement published = () -> {
            publishedCalls.incrementAndGet();
            return Json.object().put("state", "top").build();
        };
        Project project = new PassRunner().register(pass(producer, (context, sink) -> {
            sink.put(context.project(), key, discarded);
            sink.put(context.project(), key, published);
        })).run(Project.create().build(), producer);

        assertSame(published, project.resultOf(project, key).orElseThrow());
        assertEquals(Map.of("state", "top"), results(project).get("value").get("value"));
        assertEquals(0, discardedCalls.get());
        assertEquals(1, publishedCalls.get());
    }

    @Test void latticeEncoderFailureDoesNotPublishAnyResultsDiagnosticsOrPassRecord() {
        var error = new IllegalStateException("Lattice encoding failed");
        LatticeElement failing = () -> { throw error; };
        assertSame(error, assertAtomicFailure(failing, IllegalStateException.class));
    }

    @Test void invalidLatticeJsonDoesNotBypassValidationOrAtomicPublication() {
        LatticeElement tooDeep = () -> {
            JsonElement json = Json.object().put("state", "top").build();
            for (int index = 0; index < 260; index++) json = Json.object().put("child", json).build();
            return json;
        };
        assertTrue(assertAtomicFailure(tooDeep, IllegalArgumentException.class).getMessage().contains("nesting depth"));
    }

    @Test void missingLatticeJsonFailsAtomicallyButExplicitCodecsMayStillEncodeNull() {
        assertAtomicFailure(() -> null, NullPointerException.class);
        assertNull(ResultTypes.defaults().with(LabelValue.class, ignored -> null)
                .encode(LabelValue.class, new LabelValue("bottom", null)));
    }

    private static <T extends Throwable> T assertAtomicFailure(LatticeElement failing, Class<T> failureType) {
        var producer = new PassId("atomic-lattice");
        var firstKey = new ResultKey<>(producer, "a-first", LatticeElement.class);
        var failingKey = new ResultKey<>(producer, "z-failing", LatticeElement.class);
        var originalValue = new LabelValue("bottom", null);
        Project original = new PassRunner().register(pass(producer, (context, sink) ->
                sink.put(context.project(), firstKey, originalValue))).run(Project.create().build(), producer);
        String before = ProjectJson.encode(original).toText("");
        var firstCalls = new AtomicInteger();
        LatticeElement first = () -> {
            firstCalls.incrementAndGet();
            return Json.object().put("state", "top").build();
        };
        var runner = new PassRunner().register(pass(producer, (context, sink) -> {
            sink.put(context.project(), firstKey, first);
            sink.put(context.project(), failingKey, failing);
            sink.diagnose(context.project(), new Diagnostic("NOT-PUBLISHED", Diagnostic.Severity.INFO, "Unpublished"));
        }));

        T failure = assertThrows(failureType, () -> runner.run(original, producer));
        assertEquals(1, firstCalls.get(), "A successful earlier conversion is still not published");
        assertEquals(before, ProjectJson.encode(original).toText(""));
        assertSame(originalValue, original.resultOf(original, firstKey).orElseThrow());
        assertTrue(original.resultOf(original, failingKey).isEmpty());
        assertEquals(1, original.analysisResults().size());
        assertTrue(original.analysisDiagnostics().isEmpty());
        assertEquals(1, original.passRuns().size());
        return failure;
    }
}
