package com.kniazkov.cpg.parser.cpp;

import com.kniazkov.cpg.tree.Expression;
import com.kniazkov.cpg.tree.Fragment;
import com.kniazkov.cpg.tree.Position;
import com.kniazkov.cpg.tree.Source;
import com.kniazkov.cpg.tree.cpp.CppBooleanLiteral;
import com.kniazkov.cpg.tree.cpp.CppCharacterLiteral;
import com.kniazkov.cpg.tree.cpp.CppFloatingLiteral;
import com.kniazkov.cpg.tree.cpp.CppIntegerLiteral;
import com.kniazkov.cpg.tree.cpp.CppStringLiteral;
import com.kniazkov.cpg.tree.cpp.CppType;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses a single C++03 literal: integer, floating, character, string, or
 * boolean.
 *
 * <p>The parser implements the literal subset described by statements
 * CPP03-2.13.1, CPP03-2.13.2, CPP03-2.13.3, CPP03-2.13.4, and CPP03-2.13.5.
 * It accepts one literal with optional surrounding whitespace and returns an
 * immutable expression node.</p>
 */
public final class LiteralParser {

    private static final Set<String> INTEGER_SUFFIXES = Set.of("", "u", "l", "ul", "lu");

    private static final Pattern FLOATING_BODY = Pattern.compile(
            "^(?:\\d+\\.\\d*|\\.\\d+|\\d+)(?:[eE][+-]?\\d+)?$");

    private static final Map<Character, Integer> SIMPLE_ESCAPES = Map.ofEntries(
            Map.entry('\'', 39),
            Map.entry('"', 34),
            Map.entry('?', 63),
            Map.entry('\\', 92),
            Map.entry('a', 7),
            Map.entry('b', 8),
            Map.entry('f', 12),
            Map.entry('n', 10),
            Map.entry('r', 13),
            Map.entry('t', 9),
            Map.entry('v', 11));

    private final CppDataModel model;

    /**
     * Creates a parser for the ILP32 data model.
     */
    public LiteralParser() {
        this(CppDataModel.ILP32);
    }

    /**
     * Creates a parser for the given data model.
     *
     * @param model the integer type widths
     */
    public LiteralParser(CppDataModel model) {
        this.model = Objects.requireNonNull(model, "model");
    }

    /**
     * Parses one literal.
     *
     * @param text the literal text, possibly surrounded by whitespace
     * @return the parsed expression
     * @throws NullPointerException if {@code text} is {@code null}
     * @throws ParseException if {@code text} is not a valid C++03 literal
     */
    public Expression parse(String text) {
        Objects.requireNonNull(text, "text");
        String trimmed = text.trim();
        if (trimmed.isEmpty()) {
            throw new ParseException("empty literal", 0);
        }
        if (trimmed.charAt(0) == '"' || isWideStringLiteral(trimmed)) {
            return parseString(trimmed);
        }
        if (trimmed.charAt(0) == '\'' || isWideCharacterLiteral(trimmed)) {
            return parseCharacter(trimmed);
        }
        if (trimmed.equals("true")) {
            return new CppBooleanLiteral(fragment(trimmed), true);
        }
        if (trimmed.equals("false")) {
            return new CppBooleanLiteral(fragment(trimmed), false);
        }
        if (isFloating(trimmed)) {
            return parseFloating(trimmed);
        }
        return parseInteger(trimmed);
    }

    private static boolean isWideStringLiteral(String text) {
        return text.length() >= 2 && text.charAt(0) == 'L' && text.charAt(1) == '"';
    }

    private static boolean isFloating(String text) {
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return false;
        }
        int end = text.length();
        char last = text.charAt(end - 1);
        if (last == 'f' || last == 'F' || last == 'l' || last == 'L') {
            end--;
        }
        for (int index = 0; index < end; index++) {
            char ch = text.charAt(index);
            if (ch == '.' || ch == 'e' || ch == 'E') {
                return true;
            }
        }
        return false;
    }

    private static boolean isWideCharacterLiteral(String text) {
        return text.length() >= 2 && text.charAt(0) == 'L' && text.charAt(1) == '\'';
    }

    private static Fragment fragment(String text) {
        Source source = (start, end) -> text.substring(start, end);
        return new Fragment(
                source,
                new Position(0, 1, 1),
                new Position(text.length(), 1, text.length() + 1));
    }

    private Expression parseFloating(String text) {
        char first = text.charAt(0);
        if (first == '+' || first == '-') {
            throw new ParseException("floating literal must not have a sign", 0);
        }
        int suffixStart = text.length();
        char last = text.charAt(suffixStart - 1);
        if (last == 'f' || last == 'F' || last == 'l' || last == 'L') {
            suffixStart--;
        }
        String suffix = text.substring(suffixStart).toLowerCase(Locale.ROOT);
        String body = text.substring(0, suffixStart);
        if (!FLOATING_BODY.matcher(body).matches()) {
            throw new ParseException("invalid floating literal", 0);
        }
        double value = Double.parseDouble(body);
        if (Double.isInfinite(value)) {
            throw new ParseException("floating literal out of range", 0);
        }
        CppType type;
        if (suffix.equals("f")) {
            type = CppType.FLOAT;
        } else if (suffix.equals("l")) {
            type = CppType.LONG_DOUBLE;
        } else {
            type = CppType.DOUBLE;
        }
        return new CppFloatingLiteral(fragment(text), text, value, suffix, type);
    }

    private Expression parseString(String text) {
        boolean wide = text.charAt(0) == 'L';
        int quote = wide ? 1 : 0;
        if (text.length() < quote + 2 || text.charAt(text.length() - 1) != '"') {
            throw new ParseException("malformed string literal", 0);
        }
        String body = text.substring(quote + 1, text.length() - 1);
        List<Integer> chars = parseSChars(body);
        chars.add(0);
        CppType type = wide ? CppType.WCHAR_T_ARRAY : CppType.CHAR_ARRAY;
        return new CppStringLiteral(fragment(text), text, wide, chars, type);
    }

    private static List<Integer> parseSChars(String body) {
        List<Integer> codes = new ArrayList<>();
        int index = 0;
        while (index < body.length()) {
            char ch = body.charAt(index);
            if (ch == '\\') {
                Escape escape = parseEscape(body, index + 1);
                codes.add(escape.code());
                index = escape.next();
            } else {
                if (ch == '"' || ch == '\n' || ch == '\r') {
                    throw new ParseException("invalid character in string literal", index);
                }
                codes.add((int) ch);
                index++;
            }
        }
        return codes;
    }

    private Expression parseInteger(String text) {
        char first = text.charAt(0);
        if (first == '+' || first == '-') {
            throw new ParseException("integer literal must not have a sign", 0);
        }

        int suffixStart = text.length();
        while (suffixStart > 0 && isSuffixChar(text.charAt(suffixStart - 1))) {
            suffixStart--;
        }
        String suffix = text.substring(suffixStart).toLowerCase(Locale.ROOT);
        if (!INTEGER_SUFFIXES.contains(suffix)) {
            throw new ParseException("invalid integer literal suffix", suffixStart);
        }

        String body = text.substring(0, suffixStart);
        if (body.isEmpty()) {
            throw new ParseException("missing integer literal digits", 0);
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
            throw new ParseException("missing integer literal digits", 0);
        }

        BigInteger value;
        try {
            value = new BigInteger(digits, radix);
        } catch (NumberFormatException exception) {
            throw new ParseException("invalid integer literal", 0);
        }

        CppType type = resolveIntegerType(value, radix, suffix);
        return new CppIntegerLiteral(fragment(text), text, value, radix, suffix, type);
    }

    private static boolean isSuffixChar(char ch) {
        return ch == 'u' || ch == 'U' || ch == 'l' || ch == 'L';
    }

    private CppType resolveIntegerType(BigInteger value, int radix, String suffix) {
        CppType type;
        if (suffix.isEmpty()) {
            type = radix == 10 ? resolveDecimalUnsuffixed(value) : resolveOctalHexUnsuffixed(value);
        } else if (suffix.equals("u")) {
            type = resolveUnsigned(value);
        } else if (suffix.equals("l")) {
            type = resolveLong(value);
        } else {
            type = CppType.UNSIGNED_LONG_INT;
        }
        if (type == null) {
            throw new ParseException("integer literal cannot be represented by any allowed type", 0);
        }
        return type;
    }

    private CppType resolveDecimalUnsuffixed(BigInteger value) {
        if (fitsSigned(value, model.intBits())) {
            return CppType.INT;
        }
        if (fitsSigned(value, model.longBits())) {
            return CppType.LONG_INT;
        }
        return null;
    }

    private CppType resolveOctalHexUnsuffixed(BigInteger value) {
        if (fitsSigned(value, model.intBits())) {
            return CppType.INT;
        }
        if (fitsUnsigned(value, model.intBits())) {
            return CppType.UNSIGNED_INT;
        }
        if (fitsSigned(value, model.longBits())) {
            return CppType.LONG_INT;
        }
        if (fitsUnsigned(value, model.longBits())) {
            return CppType.UNSIGNED_LONG_INT;
        }
        return null;
    }

    private CppType resolveUnsigned(BigInteger value) {
        if (fitsUnsigned(value, model.intBits())) {
            return CppType.UNSIGNED_INT;
        }
        if (fitsUnsigned(value, model.longBits())) {
            return CppType.UNSIGNED_LONG_INT;
        }
        return null;
    }

    private CppType resolveLong(BigInteger value) {
        if (fitsSigned(value, model.longBits())) {
            return CppType.LONG_INT;
        }
        if (fitsUnsigned(value, model.longBits())) {
            return CppType.UNSIGNED_LONG_INT;
        }
        return null;
    }

    private static boolean fitsSigned(BigInteger value, int bits) {
        return value.compareTo(BigInteger.ONE.shiftLeft(bits - 1)) < 0;
    }

    private static boolean fitsUnsigned(BigInteger value, int bits) {
        return value.compareTo(BigInteger.ONE.shiftLeft(bits)) < 0;
    }

    private Expression parseCharacter(String text) {
        boolean wide = text.charAt(0) == 'L';
        int quote = wide ? 1 : 0;
        if (text.length() < quote + 2 || text.charAt(text.length() - 1) != '\'') {
            throw new ParseException("malformed character literal", 0);
        }

        String body = text.substring(quote + 1, text.length() - 1);
        if (body.isEmpty()) {
            throw new ParseException("empty character literal", 0);
        }

        List<Integer> cchars = parseCChars(body);
        CppType type;
        int value;
        if (wide) {
            type = CppType.WCHAR_T;
            value = accumulate(cchars);
        } else if (cchars.size() == 1) {
            type = CppType.CHAR;
            value = cchars.get(0);
        } else {
            type = CppType.INT;
            value = accumulate(cchars);
        }
        return new CppCharacterLiteral(fragment(text), text, wide, cchars, type, value);
    }

    private static List<Integer> parseCChars(String body) {
        List<Integer> codes = new ArrayList<>();
        int index = 0;
        while (index < body.length()) {
            char ch = body.charAt(index);
            if (ch == '\\') {
                Escape escape = parseEscape(body, index + 1);
                codes.add(escape.code());
                index = escape.next();
            } else {
                if (ch == '\'' || ch == '\n' || ch == '\r') {
                    throw new ParseException("invalid character in character literal", index);
                }
                codes.add((int) ch);
                index++;
            }
        }
        return codes;
    }

    private static Escape parseEscape(String body, int index) {
        if (index >= body.length()) {
            throw new ParseException("incomplete escape sequence", index);
        }
        char ch = body.charAt(index);
        Integer simple = SIMPLE_ESCAPES.get(ch);
        if (simple != null) {
            return new Escape(simple, index + 1);
        }
        if (ch == 'x') {
            return parseHexEscape(body, index + 1);
        }
        if (ch == 'u' || ch == 'U') {
            return parseUniversalEscape(body, index + 1, ch == 'u' ? 4 : 8);
        }
        if (ch >= '0' && ch <= '7') {
            return parseOctalEscape(body, index);
        }
        throw new ParseException("unknown escape sequence", index);
    }

    private static Escape parseOctalEscape(String body, int index) {
        int value = 0;
        int count = 0;
        int cursor = index;
        while (cursor < body.length() && count < 3) {
            char ch = body.charAt(cursor);
            if (ch < '0' || ch > '7') {
                break;
            }
            value = value * 8 + (ch - '0');
            cursor++;
            count++;
        }
        return new Escape(value, cursor);
    }

    private static Escape parseHexEscape(String body, int index) {
        int value = 0;
        int cursor = index;
        while (cursor < body.length() && isHexDigit(body.charAt(cursor))) {
            value = value * 16 + hexValue(body.charAt(cursor));
            cursor++;
        }
        if (cursor == index) {
            throw new ParseException("missing hexadecimal digits in escape sequence", index);
        }
        return new Escape(value, cursor);
    }

    private static Escape parseUniversalEscape(String body, int index, int digits) {
        if (index + digits > body.length()) {
            throw new ParseException("incomplete universal-character-name", index);
        }
        int value = 0;
        for (int i = 0; i < digits; i++) {
            char ch = body.charAt(index + i);
            if (!isHexDigit(ch)) {
                throw new ParseException("invalid universal-character-name", index + i);
            }
            value = value * 16 + hexValue(ch);
        }
        return new Escape(value, index + digits);
    }

    private static boolean isHexDigit(char ch) {
        return (ch >= '0' && ch <= '9')
                || (ch >= 'a' && ch <= 'f')
                || (ch >= 'A' && ch <= 'F');
    }

    private static int hexValue(char ch) {
        if (ch <= '9') {
            return ch - '0';
        }
        if (ch >= 'a') {
            return ch - 'a' + 10;
        }
        return ch - 'A' + 10;
    }

    private static int accumulate(List<Integer> codes) {
        int value = 0;
        for (int code : codes) {
            value = (value << 8) | (code & 0xFF);
        }
        return value;
    }

    private record Escape(int code, int next) { }
}
