/* One-way custom result conversion and publication snapshots. MIT License. */
package com.kniazkov.cpg;

import com.kniazkov.cpg.passes.*;
import com.kniazkov.json.Json;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ResultTypesTest {
    private record IntRange(int min, int max) {}

    @Test void standardNumbersStringsAndBooleansNeedNoRegistration() {
        ResultTypes types = ResultTypes.defaults();
        for (Object value : List.of((byte) 1, (short) 2, 3, 4L, 5.5F, 6.5D,
                new BigInteger("123456789012345678901234567890"), new BigDecimal("0.00000000000000000001"),
                "value", true, false)) {
            assertEquals(value, types.encode(value.getClass(), value));
            if (value instanceof Number) assertEquals(value, types.encode(Number.class, value));
        }
        assertEquals(7, types.encode(new ResultKey<>(new PassId("first"), "count", Integer.class), 7));
    }

    @Test void registryIsImmutableAndCustomConversionIsOneWay() {
        ResultTypes defaults = ResultTypes.defaults();
        ResultTypes registered = defaults.with(IntRange.class, value -> Map.of("min", value.min(), "max", value.max()));
        assertThrows(IllegalArgumentException.class, () -> defaults.encode(IntRange.class, new IntRange(1, 10)));
        assertEquals("{\"max\": 10, \"min\": 1}", Json.serialize(registered.encode(IntRange.class, new IntRange(1, 10))));
        ResultTypes replacement = registered.with(IntRange.class, value -> value.max() - value.min());
        assertEquals(9, replacement.encode(IntRange.class, new IntRange(1, 10)));
        assertEquals(Map.of("max", 10, "min", 1), registered.encode(IntRange.class, new IntRange(1, 10)));
    }

    @Test void encoderOwnedContainersAreCopiedRecursivelyAndCannotBeMutated() {
        var nested = new ArrayList<Object>(List.of(1));
        nested.add(null);
        var mutable = new LinkedHashMap<String, Object>();
        mutable.put("z", nested);
        mutable.put("a", null);
        ResultTypes types = ResultTypes.defaults().with(IntRange.class, value -> mutable);
        Object frozen = types.encode(IntRange.class, new IntRange(1, 10));
        nested.set(0, 99);
        mutable.put("new", "later");
        assertEquals("{\"a\": null, \"z\": [1, null]}", Json.serialize(frozen));
        @SuppressWarnings("unchecked") var map = (Map<String,Object>) frozen;
        @SuppressWarnings("unchecked") var list = (List<Object>) map.get("z");
        assertThrows(UnsupportedOperationException.class, () -> map.put("new", 2));
        assertThrows(UnsupportedOperationException.class, () -> list.add(2));
    }

    @Test void jsonLibraryTreesAreAlsoCopiedRatherThanRetained() {
        var json = Json.object().put("min", 1).put("max", 10).build();
        Object frozen = ResultTypes.defaults().with(IntRange.class, value -> json)
                .encode(IntRange.class, new IntRange(1, 10));
        json.addNumber("min", 99);
        assertEquals("{\"max\": 10, \"min\": 1}", Json.serialize(frozen));
    }

    @Test void invalidValuesAreRejectedInsteadOfReflectivelySerialized() {
        ResultTypes defaults = ResultTypes.defaults();
        assertThrows(IllegalArgumentException.class, () -> defaults.encode(Double.class, Double.NaN));
        assertThrows(IllegalArgumentException.class, () -> defaults.encode(Double.class, Double.POSITIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> defaults.encode(Float.class, Float.NEGATIVE_INFINITY));
        assertThrows(IllegalArgumentException.class, () -> defaults.encode(Number.class, new AtomicInteger(1)));
        assertThrows(IllegalArgumentException.class, () -> defaults.encode(Integer.class, "one"));
        assertThrows(NullPointerException.class, () -> defaults.encode(Integer.class, null));
        assertThrows(IllegalArgumentException.class, () -> defaults.with(int.class, value -> value));
        for (Object invalid : List.of(new Object(), Map.of(1, "one"), Set.of(1), new int[] {1},
                List.of(Map.of("bad", Double.NaN)))) {
            ResultTypes types = defaults.with(IntRange.class, value -> invalid);
            assertThrows(IllegalArgumentException.class, () -> types.encode(IntRange.class, new IntRange(1, 10)));
        }
    }

    @Test void cyclesAreRejectedButSharedAcyclicContainersAreAllowed() {
        var cyclic = new ArrayList<Object>();
        cyclic.add(cyclic);
        ResultTypes types = ResultTypes.defaults().with(IntRange.class, value -> cyclic);
        assertThrows(IllegalArgumentException.class, () -> types.encode(IntRange.class, new IntRange(1, 10)));
        var shared = new ArrayList<>(List.of(1));
        Object frozen = types.with(IntRange.class, value -> List.of(shared, shared)).encode(IntRange.class, new IntRange(1, 10));
        shared.set(0, 2);
        assertEquals(List.of(List.of(1), List.of(1)), frozen);
    }

    @Test void encoderExceptionsPropagateAndJsonNullIsPermitted() {
        var error = new IllegalStateException("encoder failed");
        ResultTypes types = ResultTypes.defaults().with(IntRange.class, value -> { throw error; });
        assertSame(error, assertThrows(IllegalStateException.class, () -> types.encode(IntRange.class, new IntRange(1, 10))));
        assertNull(types.with(IntRange.class, value -> null).encode(IntRange.class, new IntRange(1, 10)));
    }
}
