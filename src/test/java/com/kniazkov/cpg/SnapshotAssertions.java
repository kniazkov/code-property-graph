/* Code Property Graph: compare semantic snapshots while retaining explicit input attribution tests. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.json.JsonElement;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** API and CLI intentionally have different owned configuration origins, but identical syntax and analyses. */
public final class SnapshotAssertions {
    private SnapshotAssertions() {}
    public static void assertEquivalent(JsonElement expected, JsonElement actual) {
        assertEquals(semantic(expected.toJavaObject()), semantic(actual.toJavaObject()));
    }
    /** Compare graph products when the caller separately checks intentionally different executions.
     * Repeat exports and identical pipelines must still compare the complete snapshot. */
    public static void assertGraphEquals(JsonElement expected, JsonElement actual) {
        assertEquals(withoutPassRuns(expected), withoutPassRuns(actual));
    }
    /** The same graph comparison with the usual explicit API/CLI attribution allowance. */
    public static void assertEquivalentGraph(JsonElement expected, JsonElement actual) {
        assertEquals(semantic(withoutPassRuns(expected)), semantic(withoutPassRuns(actual)));
    }
    private static Map<?,?> withoutPassRuns(JsonElement value) {
        var root = new LinkedHashMap<>((Map<?,?>) value.toJavaObject());
        root.remove("passRuns");
        return root;
    }
    private static Object semantic(Object value) {
        if (value instanceof List<?> list) return list.stream().map(SnapshotAssertions::semantic).toList();
        if (!(value instanceof Map<?, ?> map)) return value;
        var result = new LinkedHashMap<Object, Object>();
        for (var entry : map.entrySet()) {
            Object key = entry.getKey();
            if (Set.of("externalInputs", "configurationSource", "originalArgument", "originalArguments").contains(key)) continue;
            if ("command-line".equals(map.get("originKind")) && Set.of("sourceKey", "sourceNodeId").contains(key)) continue;
            result.put(key, semantic(entry.getValue()));
        }
        return result;
    }
}
