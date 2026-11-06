/* Integer-domain normalization, lattice laws and exact one-way JSON. MIT License. */
package com.kniazkov.cpg.lattice;

import com.kniazkov.json.JsonObject;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class IntegerDomainTest {
    private final IntegerDomain integers = IntegerDomain.INSTANCE;

    @Test void singletonIntervalsNormalizeToExactIntegers() {
        IntegerValue constant = integers.constant(7);
        IntegerValue singleton = integers.interval(7, 7);
        assertEquals(IntegerValue.Kind.CONSTANT, singleton.kind());
        assertTrue(singleton.isConstant());
        assertFalse(singleton.isBottom());
        assertFalse(singleton.isTop());
        assertEquals(Optional.of(BigInteger.valueOf(7)), singleton.constant());
        assertEquals(constant, singleton);
        assertEquals(constant.hashCode(), singleton.hashCode());
        assertEquals(constant.toJson().toText(""), singleton.toJson().toText(""));
        assertEquals(1, new HashSet<>(List.of(constant, singleton, new IntegerDomain().constant(7))).size());
        assertEquals("7", singleton.toString());

        IntegerValue interval = integers.interval(7, 9);
        assertEquals(IntegerValue.Kind.INTERVAL, interval.kind());
        assertFalse(interval.isConstant());
        assertEquals(Optional.empty(), interval.constant());
        assertNotEquals(constant, interval);
        assertTrue(integers.lessOrEqual(constant, interval));
        assertFalse(integers.lessOrEqual(interval, constant));
        assertEquals("[7, 9]", interval.toString());
        assertNotEquals(constant, null);
        assertNotEquals(constant, BigInteger.valueOf(7));
    }

    @Test void bottomAndTopAreValuesWithDifferentMeaningFromMissingResults() {
        assertEquals(IntegerValue.Kind.BOTTOM, integers.bottom().kind());
        assertEquals(IntegerValue.Kind.TOP, integers.top().kind());
        assertEquals(integers.bottom(), integers.interval(9, 7));
        assertNotEquals(integers.bottom(), integers.top());
        assertTrue(integers.bottom().isBottom());
        assertTrue(integers.top().isTop());
        assertFalse(integers.bottom().contains(0));
        assertTrue(integers.top().contains(0));
        for (IntegerValue value : List.of(integers.top(), integers.bottom())) {
            assertFalse(value.isConstant());
            assertTrue(value.constant().isEmpty());
            assertTrue(value.lowerBound().isEmpty());
            assertTrue(value.upperBound().isEmpty());
        }
        assertEquals("⊥", integers.bottom().toString());
        assertEquals("⊤", integers.top().toString());
    }

    @Test void finiteIntervalsAndUnboundedEndsUseExactInclusiveBounds() {
        IntegerValue interval = integers.interval(-2, 3);
        assertTrue(interval.contains(-2));
        assertTrue(interval.contains(3));
        assertFalse(interval.contains(-3));
        assertFalse(interval.contains(4));
        IntegerValue above = integers.atLeast(BigInteger.valueOf(3));
        IntegerValue below = integers.atMost(BigInteger.valueOf(-2));
        assertEquals(Optional.of(BigInteger.valueOf(3)), above.lowerBound());
        assertTrue(above.upperBound().isEmpty());
        assertTrue(below.lowerBound().isEmpty());
        assertEquals(Optional.of(BigInteger.valueOf(-2)), below.upperBound());
        assertTrue(above.contains(3));
        assertFalse(above.contains(2));
        assertTrue(below.contains(-2));
        assertFalse(below.contains(-1));
        assertEquals(integers.top(), integers.join(above, below));
        assertEquals(integers.bottom(), integers.meet(above, below));
        assertEquals(integers.constant(3), integers.meet(above, integers.atMost(BigInteger.valueOf(3))));
        assertEquals(integers.constant(-2), integers.meet(interval, below));
        assertEquals("[3, +∞]", above.toString());
        assertEquals("[-∞, -2]", below.toString());
    }

    @Test void joinOverapproximatesTheUnionInsteadOfKeepingOnlyTheOriginalConstants() {
        IntegerValue joined = integers.join(integers.constant(1), integers.constant(10));
        assertEquals(integers.interval(1, 10), joined);
        assertTrue(joined.contains(5));
        assertEquals(integers.constant(5), integers.meet(joined, integers.constant(5)));
        assertEquals(integers.bottom(), integers.meet(integers.constant(1), integers.constant(10)));
        assertEquals(integers.interval(4, 6), integers.meet(integers.interval(1, 6), integers.interval(4, 10)));
    }

    @Test void numbersBeyondEveryMachineIntegerRemainExact() {
        BigInteger huge = BigInteger.ONE.shiftLeft(300).add(BigInteger.valueOf(12345));
        BigInteger small = huge.negate();
        IntegerValue full = integers.interval(small, huge);
        assertEquals(Optional.of(small), full.lowerBound());
        assertEquals(Optional.of(huge), full.upperBound());
        assertTrue(full.contains(huge));
        assertFalse(full.contains(huge.add(BigInteger.ONE)));
        assertTrue(integers.top().contains(huge.shiftLeft(1000)));
        assertEquals(full, integers.join(integers.constant(small), integers.constant(huge)));
        assertEquals(integers.constant(huge), integers.meet(full, integers.atLeast(huge)));
        assertEquals(integers.constant(small), integers.interval(small, small));

        JsonObject json = full.toJson();
        assertEquals(small, json.get("lower").toJavaObject());
        assertEquals(huge, json.get("upper").toJavaObject());
        assertEquals(huge, integers.constant(huge).toJson().get("value").toJavaObject());
        assertTrue(json.toText("").contains(huge.toString()));
        assertTrue(json.toText("").contains(small.toString()));
    }

    @Test void jsonDistinguishesNormalizedKindsAndDoesNotExposeMutableValueState() {
        assertEquals(Map.of("domain", "integer", "kind", "bottom"), integers.bottom().toJson().toJavaObject());
        assertEquals(Map.of("domain", "integer", "kind", "top"), integers.top().toJson().toJavaObject());
        JsonObject constant = integers.constant(7).toJson();
        assertEquals("integer", constant.get("domain").toJavaObject());
        assertEquals("constant", constant.get("kind").toJavaObject());
        assertEquals("7", constant.get("value").toText(""));
        assertEquals(3, constant.size());
        JsonObject interval = integers.interval(7, 9).toJson();
        assertEquals("interval", interval.get("kind").toJavaObject());
        assertEquals("7", interval.get("lower").toText(""));
        assertEquals("9", interval.get("upper").toText(""));
        assertEquals(4, interval.size());
        assertFalse(integers.atLeast(BigInteger.ZERO).toJson().containsKey("upper"));
        assertFalse(integers.atMost(BigInteger.ZERO).toJson().containsKey("lower"));

        IntegerValue value = integers.constant(7);
        JsonObject first = value.toJson(), second = value.toJson();
        assertNotSame(first, second);
        String before = second.toText("");
        first.addNumber("value", 99);
        first.addString("kind", "top");
        assertEquals(before, second.toText(""));
        assertEquals(before, value.toJson().toText(""));
        assertEquals(integers.constant(7), value);
        JsonObject top = integers.top().toJson();
        top.clear();
        assertEquals(2, integers.top().toJson().size());
    }

    @Test void operationsRejectNullEvenWhenTheOtherOperandWouldShortCircuit() {
        assertThrows(NullPointerException.class, () -> integers.constant((BigInteger) null));
        assertThrows(NullPointerException.class, () -> integers.interval(null, BigInteger.ONE));
        assertThrows(NullPointerException.class, () -> integers.interval(BigInteger.ONE, null));
        assertThrows(NullPointerException.class, () -> integers.atLeast(null));
        assertThrows(NullPointerException.class, () -> integers.atMost(null));
        for (IntegerValue value : List.of(integers.bottom(), integers.top(), integers.constant(7))) {
            assertThrows(NullPointerException.class, () -> integers.lessOrEqual(value, null));
            assertThrows(NullPointerException.class, () -> integers.lessOrEqual(null, value));
            assertThrows(NullPointerException.class, () -> integers.join(value, null));
            assertThrows(NullPointerException.class, () -> integers.join(null, value));
            assertThrows(NullPointerException.class, () -> integers.meet(value, null));
            assertThrows(NullPointerException.class, () -> integers.meet(null, value));
            assertThrows(NullPointerException.class, () -> value.contains(null));
        }
    }

    @Test void latticeLawsHoldForFiniteAndInfiniteIntervals() {
        Lattice<IntegerValue> lattice = integers;
        List<IntegerValue> values = samples();
        for (IntegerValue a : values) {
            assertTrue(lattice.lessOrEqual(a, a));
            assertTrue(lattice.lessOrEqual(lattice.bottom(), a));
            assertTrue(lattice.lessOrEqual(a, lattice.top()));
            assertEquals(a, lattice.join(a, a));
            assertEquals(a, lattice.meet(a, a));
            assertEquals(a, lattice.join(a, lattice.bottom()));
            assertEquals(a, lattice.meet(a, lattice.top()));
            assertEquals(lattice.top(), lattice.join(a, lattice.top()));
            assertEquals(lattice.bottom(), lattice.meet(a, lattice.bottom()));
            for (IntegerValue b : values) {
                IntegerValue join = lattice.join(a, b), meet = lattice.meet(a, b);
                assertEquals(join, lattice.join(b, a));
                assertEquals(meet, lattice.meet(b, a));
                assertTrue(lattice.lessOrEqual(a, join));
                assertTrue(lattice.lessOrEqual(b, join));
                assertTrue(lattice.lessOrEqual(meet, a));
                assertTrue(lattice.lessOrEqual(meet, b));
                assertEquals(a, lattice.meet(a, join));
                assertEquals(a, lattice.join(a, meet));
                assertEquals(lattice.lessOrEqual(a, b), lattice.join(a, b).equals(b));
                assertEquals(lattice.lessOrEqual(a, b), lattice.meet(a, b).equals(a));
                if (lattice.lessOrEqual(a, b) && lattice.lessOrEqual(b, a)) assertEquals(a, b);
                for (IntegerValue c : values) {
                    assertEquals(lattice.join(lattice.join(a, b), c), lattice.join(a, lattice.join(b, c)));
                    assertEquals(lattice.meet(lattice.meet(a, b), c), lattice.meet(a, lattice.meet(b, c)));
                    if (lattice.lessOrEqual(a, b) && lattice.lessOrEqual(b, c))
                        assertTrue(lattice.lessOrEqual(a, c));
                    if (lattice.lessOrEqual(a, c) && lattice.lessOrEqual(b, c))
                        assertTrue(lattice.lessOrEqual(join, c));
                    if (lattice.lessOrEqual(c, a) && lattice.lessOrEqual(c, b))
                        assertTrue(lattice.lessOrEqual(c, meet));
                    if (lattice.lessOrEqual(a, b)) {
                        assertTrue(lattice.lessOrEqual(lattice.join(a, c), lattice.join(b, c)));
                        assertTrue(lattice.lessOrEqual(lattice.meet(a, c), lattice.meet(b, c)));
                    }
                }
            }
        }
    }

    private List<IntegerValue> samples() {
        var result = new ArrayList<>(List.of(integers.bottom(), integers.top()));
        for (int lower = -2; lower <= 2; lower++) {
            result.add(integers.atLeast(BigInteger.valueOf(lower)));
            result.add(integers.atMost(BigInteger.valueOf(lower)));
            for (int upper = lower; upper <= 2; upper++) result.add(integers.interval(lower, upper));
        }
        result.add(integers.constant(BigInteger.ONE.shiftLeft(130)));
        result.add(integers.constant(BigInteger.ONE.shiftLeft(130).negate()));
        return result;
    }
}
