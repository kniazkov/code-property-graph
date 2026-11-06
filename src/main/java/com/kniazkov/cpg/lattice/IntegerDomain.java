/* Integer interval lattice and normalizing factories. MIT License. */
package com.kniazkov.cpg.lattice;

import java.math.BigInteger;
import java.util.Objects;

/** Language-independent intervals of mathematical integers, ordered by inclusion.
 * The empty set is bottom and all integers are top. Join computes the convex hull,
 * not an exact union: joining 1 and 10 produces [1, 10], which also contains 5.
 * Meet computes intersection. Every factory and operation normalizes empty and
 * singleton intervals; no machine-width limit or implicit overflow is imposed.
 *
 * <p>This stateless, thread-safe class is both the concrete factory and the lattice.
 * All instances produce equal values; {@link #INSTANCE} is supplied for convenience.
 * Language-specific analyses are responsible for conversions and overflow rules.</p>
 */
public final class IntegerDomain implements Lattice<IntegerValue> {
    public static final IntegerDomain INSTANCE = new IntegerDomain();
    private static final IntegerValue BOTTOM = new IntegerValue(true, null, null);
    private static final IntegerValue TOP = new IntegerValue(false, null, null);

    public IntegerDomain() {}

    /** An exact mathematical integer. */
    public IntegerValue constant(BigInteger value) {
        Objects.requireNonNull(value, "Integer constant");
        return new IntegerValue(false, value, value);
    }

    public IntegerValue constant(long value) { return constant(BigInteger.valueOf(value)); }

    /** Closed interval. Reversed bounds produce bottom; equal bounds produce a
     * constant with the same equality, hash and JSON representation as {@link #constant(BigInteger)}. */
    public IntegerValue interval(BigInteger lower, BigInteger upper) {
        return normalized(Objects.requireNonNull(lower, "Lower bound"),
                Objects.requireNonNull(upper, "Upper bound"));
    }

    public IntegerValue interval(long lower, long upper) {
        return interval(BigInteger.valueOf(lower), BigInteger.valueOf(upper));
    }

    /** All integers greater than or equal to the inclusive lower bound. */
    public IntegerValue atLeast(BigInteger lower) {
        return new IntegerValue(false, Objects.requireNonNull(lower, "Lower bound"), null);
    }

    /** All integers less than or equal to the inclusive upper bound. */
    public IntegerValue atMost(BigInteger upper) {
        return new IntegerValue(false, null, Objects.requireNonNull(upper, "Upper bound"));
    }

    @Override public IntegerValue bottom() { return BOTTOM; }
    @Override public IntegerValue top() { return TOP; }

    @Override public boolean lessOrEqual(IntegerValue left, IntegerValue right) {
        Objects.requireNonNull(left, "Left element");
        Objects.requireNonNull(right, "Right element");
        if (left.isBottom()) return true;
        if (right.isBottom()) return false;
        BigInteger leftLower = left.lowerBound().orElse(null), rightLower = right.lowerBound().orElse(null);
        BigInteger leftUpper = left.upperBound().orElse(null), rightUpper = right.upperBound().orElse(null);
        return (rightLower == null || leftLower != null && rightLower.compareTo(leftLower) <= 0)
                && (rightUpper == null || leftUpper != null && rightUpper.compareTo(leftUpper) >= 0);
    }

    @Override public IntegerValue join(IntegerValue left, IntegerValue right) {
        Objects.requireNonNull(left, "Left element");
        Objects.requireNonNull(right, "Right element");
        if (left.isBottom()) return right;
        if (right.isBottom()) return left;
        BigInteger leftLower = left.lowerBound().orElse(null), rightLower = right.lowerBound().orElse(null);
        BigInteger leftUpper = left.upperBound().orElse(null), rightUpper = right.upperBound().orElse(null);
        return normalized(leftLower == null || rightLower == null ? null : leftLower.min(rightLower),
                leftUpper == null || rightUpper == null ? null : leftUpper.max(rightUpper));
    }

    @Override public IntegerValue meet(IntegerValue left, IntegerValue right) {
        Objects.requireNonNull(left, "Left element");
        Objects.requireNonNull(right, "Right element");
        if (left.isBottom() || right.isBottom()) return bottom();
        BigInteger leftLower = left.lowerBound().orElse(null), rightLower = right.lowerBound().orElse(null);
        BigInteger leftUpper = left.upperBound().orElse(null), rightUpper = right.upperBound().orElse(null);
        return normalized(leftLower == null ? rightLower : rightLower == null ? leftLower : leftLower.max(rightLower),
                leftUpper == null ? rightUpper : rightUpper == null ? leftUpper : leftUpper.min(rightUpper));
    }

    private IntegerValue normalized(BigInteger lower, BigInteger upper) {
        if (lower == null && upper == null) return top();
        if (lower != null && upper != null) {
            int order = lower.compareTo(upper);
            if (order > 0) return bottom();
            if (order == 0) return constant(lower);
        }
        return new IntegerValue(false, lower, upper);
    }
}
