package com.kniazkov.cpg.tree;

import java.math.BigInteger;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * An integer literal expression.
 *
 * <p>The original spelling is preserved by {@link #data()}. The numeric value
 * is exposed as a {@link BigInteger} and through convenience accessors for
 * {@code int} and {@code long}. Decimal, octal, hexadecimal, and the usual
 * {@code u}/{@code l}/{@code ll} suffixes are supported.</p>
 */
public final class IntegerLiteral implements Expression {

    private static final Set<String> SUFFIXES = Set.of(
            "", "u", "l", "ul", "lu", "ll", "ull", "llu");

    private final Fragment fragment;
    private final String data;
    private final BigInteger value;
    private final int radix;
    private final String suffix;

    /**
     * Creates an integer literal.
     *
     * @param fragment the source fragment covered by the literal
     * @param data the literal text as written in the source
     * @throws NullPointerException if any argument is {@code null}
     * @throws IllegalArgumentException if {@code data} is not a valid integer
     *         literal
     */
    public IntegerLiteral(Fragment fragment, String data) {
        this.fragment = Objects.requireNonNull(fragment, "fragment");
        this.data = Objects.requireNonNull(data, "data");

        Parsed parsed = parse(data);
        this.value = parsed.value;
        this.radix = parsed.radix;
        this.suffix = parsed.suffix;
    }

    @Override
    public Fragment fragment() {
        return fragment;
    }

    @Override
    public String data() {
        return data;
    }

    @Override
    public List<Node> children() {
        return List.of();
    }

    /**
     * Returns the numeric value.
     *
     * @return the value, never {@code null}
     */
    public BigInteger value() {
        return value;
    }

    /**
     * Returns the value converted to {@code int} with Java truncation semantics.
     *
     * @return the truncated value
     */
    public int intValue() {
        return value.intValue();
    }

    /**
     * Returns the value converted to {@code long} with Java truncation semantics.
     *
     * @return the truncated value
     */
    public long longValue() {
        return value.longValue();
    }

    /**
     * Returns the value as an exact {@code int}.
     *
     * @return the exact value
     * @throws ArithmeticException if the value does not fit into an {@code int}
     */
    public int intValueExact() {
        return value.intValueExact();
    }

    /**
     * Returns the value as an exact {@code long}.
     *
     * @return the exact value
     * @throws ArithmeticException if the value does not fit into a {@code long}
     */
    public long longValueExact() {
        return value.longValueExact();
    }

    /**
     * Returns whether the value fits into a signed {@code int}.
     *
     * @return {@code true} if the value fits
     */
    public boolean fitsInt() {
        return value.bitLength() <= 31;
    }

    /**
     * Returns whether the value fits into a signed {@code long}.
     *
     * @return {@code true} if the value fits
     */
    public boolean fitsLong() {
        return value.bitLength() <= 63;
    }

    /**
     * Returns whether the value fits into an unsigned 32-bit integer.
     *
     * @return {@code true} if the value fits
     */
    public boolean fitsUnsignedInt() {
        return value.bitLength() <= 32;
    }

    /**
     * Returns whether the value fits into an unsigned 64-bit integer.
     *
     * @return {@code true} if the value fits
     */
    public boolean fitsUnsignedLong() {
        return value.bitLength() <= 64;
    }

    /**
     * Returns the radix of the literal.
     *
     * @return {@code 8}, {@code 10}, or {@code 16}
     */
    public int radix() {
        return radix;
    }

    /**
     * Returns the normalized suffix.
     *
     * @return the suffix, possibly empty, never {@code null}
     */
    public String suffix() {
        return suffix;
    }

    /**
     * Returns whether the literal has an unsigned suffix.
     *
     * @return {@code true} if the suffix contains {@code u}
     */
    public boolean isUnsigned() {
        return suffix.indexOf('u') >= 0;
    }

    /**
     * Returns whether the value is zero.
     *
     * @return {@code true} if the value is zero
     */
    public boolean isZero() {
        return value.signum() == 0;
    }

    /**
     * Returns the sign of the value.
     *
     * @return {@code -1}, {@code 0}, or {@code 1}
     */
    public int signum() {
        return value.signum();
    }

    @Override
    public <R> R accept(Visitor<R> visitor) {
        return visitor.visit(this);
    }

    private static Parsed parse(String text) {
        if (text.isEmpty()) {
            throw new IllegalArgumentException("empty integer literal");
        }
        if (text.charAt(0) == '+' || text.charAt(0) == '-') {
            throw new IllegalArgumentException("integer literal must not have a sign: " + text);
        }

        int suffixStart = text.length();
        while (suffixStart > 0 && isSuffixChar(text.charAt(suffixStart - 1))) {
            suffixStart--;
        }
        String suffix = text.substring(suffixStart).toLowerCase(Locale.ROOT);
        if (!SUFFIXES.contains(suffix)) {
            throw new IllegalArgumentException("invalid integer literal suffix: " + text);
        }

        String body = text.substring(0, suffixStart);
        if (body.isEmpty()) {
            throw new IllegalArgumentException("missing integer literal digits: " + text);
        }

        int radix;
        String digits;
        if (body.length() >= 2
                && body.charAt(0) == '0'
                && (body.charAt(1) == 'x' || body.charAt(1) == 'X')) {
            radix = 16;
            digits = body.substring(2);
        } else if (body.charAt(0) == '0') {
            radix = 8;
            digits = body;
        } else {
            radix = 10;
            digits = body;
        }

        if (digits.isEmpty()) {
            throw new IllegalArgumentException("missing integer literal digits: " + text);
        }

        try {
            return new Parsed(new BigInteger(digits, radix), radix, suffix);
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("invalid integer literal: " + text, exception);
        }
    }

    private static boolean isSuffixChar(char ch) {
        return ch == 'u' || ch == 'U' || ch == 'l' || ch == 'L';
    }

    private record Parsed(BigInteger value, int radix, String suffix) {}
}
