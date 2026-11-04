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
