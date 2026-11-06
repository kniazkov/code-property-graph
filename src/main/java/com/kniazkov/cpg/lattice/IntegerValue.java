/* Immutable mathematical integer interval values. MIT License. */
package com.kniazkov.cpg.lattice;

import com.kniazkov.json.Json;
import com.kniazkov.json.JsonObject;
import java.math.BigInteger;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/** An immutable, normalized element of {@link IntegerDomain}.
 * Bounds are inclusive mathematical integers, independent of source language,
 * machine width, overflow rules and platform profile. Create values through
 * {@link IntegerDomain}; equal represented intervals have equal values and hashes.
 */
public final class IntegerValue implements LatticeElement {
    /** The four normalized representations of an integer-domain value. */
    public enum Kind { BOTTOM, CONSTANT, INTERVAL, TOP }

    private final boolean empty;
    private final BigInteger lower;
    private final BigInteger upper;

    IntegerValue(boolean empty, BigInteger lower, BigInteger upper) {
        this.empty = empty;
        this.lower = lower;
        this.upper = upper;
    }

    /** The normalized representation, with singleton intervals as constants. */
    public Kind kind() {
        if (empty) return Kind.BOTTOM;
        if (lower == null && upper == null) return Kind.TOP;
        return lower != null && lower.equals(upper) ? Kind.CONSTANT : Kind.INTERVAL;
    }

    public boolean isBottom() { return empty; }
    public boolean isTop() { return !empty && lower == null && upper == null; }
    public boolean isConstant() { return !empty && lower != null && lower.equals(upper); }

    /** Inclusive finite lower bound. Empty means negative infinity for a nonempty
     * value; bottom has no bounds either, so inspect {@link #isBottom} first. */
    public Optional<BigInteger> lowerBound() { return Optional.ofNullable(lower); }

    /** Inclusive finite upper bound. Empty means positive infinity for a nonempty
     * value; bottom has no bounds either, so inspect {@link #isBottom} first. */
    public Optional<BigInteger> upperBound() { return Optional.ofNullable(upper); }

    /** The exact integer if this value is a singleton, otherwise empty. */
    public Optional<BigInteger> constant() { return isConstant() ? Optional.of(lower) : Optional.empty(); }

    /** Whether the represented set contains this exact mathematical integer. */
    public boolean contains(BigInteger value) {
        Objects.requireNonNull(value, "Integer value");
        return !empty && (lower == null || lower.compareTo(value) <= 0)
                && (upper == null || upper.compareTo(value) >= 0);
    }

    public boolean contains(long value) { return contains(BigInteger.valueOf(value)); }

    /** Fresh JSON tree. Finite bounds and constants are exact JSON numbers.
     * Top and bottom contain only {@code domain} and {@code kind}; intervals omit
     * an infinite bound and singleton intervals use {@code kind: constant} and
     * {@code value}. No source-language or node identity is encoded. */
    @Override public JsonObject toJson() {
        var json = Json.object().put("domain", "integer")
                .put("kind", kind().name().toLowerCase(Locale.ROOT));
        if (isConstant()) json.put("value", lower);
        else if (!isBottom() && !isTop()) {
            if (lower != null) json.put("lower", lower);
            if (upper != null) json.put("upper", upper);
        }
        return json.build();
    }

    @Override public boolean equals(Object other) {
        return this == other || other instanceof IntegerValue value
                && empty == value.empty && Objects.equals(lower, value.lower)
                && Objects.equals(upper, value.upper);
    }

    @Override public int hashCode() { return Objects.hash(empty, lower, upper); }

    @Override public String toString() {
        if (isBottom()) return "⊥";
        if (isTop()) return "⊤";
        if (isConstant()) return lower.toString();
        return "[" + (lower == null ? "-∞" : lower) + ", " + (upper == null ? "+∞" : upper) + "]";
    }
}
