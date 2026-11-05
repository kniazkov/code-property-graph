/* Code Property Graph: C++03 literal grammar and target values. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.model.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/** Converts complete preprocessing tokens, without accepting later-language extensions. */
final class LiteralParser {
    private static final Pattern INTEGER = Pattern.compile("(0[xX][0-9a-fA-F]+|0[0-7]*|[1-9][0-9]*)([uU][lL]?|[lL][uU]?)?");
    private static final Pattern FLOAT = Pattern.compile("((?:[0-9]+\\.[0-9]*|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|[0-9]+[eE][+-]?[0-9]+)([fFlL]?)");
    private final TokenCursor tokens;
    private final Language language;
    LiteralParser(TokenCursor tokens, Language language) { this.tokens = tokens; this.language = language; }

    /** Returns null without consumption if this is not a C++ literal. */
    NodeBuilder<? extends Expression> parse() {
        if (language != Language.CPP) return null;
        String text = tokens.peek();
        var start = tokens.start();
        NodeBuilder<? extends Expression> result;
        if (text.equals("true") || text.equals("false")) {
            result = BooleanLiteral.create().setValue(text.equals("true")).setSpelling(text);
        } else if (string(text)) {
            var spellings = new ArrayList<String>();
            int checkpoint = tokens.checkpoint();
            boolean wide = false;
            while (string(tokens.peek())) {
                spellings.add(tokens.peek()); wide |= tokens.peek().startsWith("L"); tokens.advance();
            }
            var units = new ArrayList<BigInteger>();
            tokens.restore(checkpoint);
            for (String spelling : spellings) { units.addAll(decode(spelling, wide)); tokens.advance(); }
            return tokens.span(start, StringLiteral.create().setWide(wide).setCodeUnits(units).setSpelling(String.join(" ", spellings)));
        } else if (text.startsWith("'") || text.startsWith("L'")) {
            boolean wide = text.startsWith("L");
            var units = decode(text, wide);
            if (units.isEmpty()) throw tokens.error("Empty character literal");
            // Documented implementation-defined policy: ordinary multicharacter literals
            // pack execution units big-endian into int; wide literals retain the last unit.
            String type = wide ? "wchar_t" : units.size() == 1 ? "char" : "int";
            int width = tokens.platform().sizeBits(type).orElseThrow();
            BigInteger value = BigInteger.ZERO;
            for (BigInteger unit : units) value = wide ? unit : value.shiftLeft(tokens.platform().bitsPerByte()).or(unit);
            BigInteger modulus = BigInteger.ONE.shiftLeft(width);
            value = value.mod(modulus);
            if (tokens.platform().integerRange(type).orElseThrow().min().signum() < 0 && value.testBit(width - 1)) value = value.subtract(modulus);
            result = CharacterLiteral.create().setWide(wide).setWidths(wide ? width : tokens.platform().bitsPerByte(), width).setValue(value).setCppType(type).setSpelling(text);
        } else if (!text.isEmpty() && (Character.isDigit(text.charAt(0)) || text.charAt(0) == '.' && text.length() > 1 && Character.isDigit(text.charAt(1)))) {
            var integer = INTEGER.matcher(text);
            var floating = FLOAT.matcher(text);
            if (integer.matches()) {
                String digits = integer.group(1);
                int radix = digits.startsWith("0x") || digits.startsWith("0X") ? 16 : digits.startsWith("0") ? 8 : 10;
                BigInteger value = new BigInteger(radix == 16 ? digits.substring(2) : digits, radix);
                result = IntegerLiteral.create().setValue(value).setCppType(integerType(value, radix, integer.group(2))).setSpelling(text);
            } else if (floating.matches()) {
                String suffix = floating.group(2).toLowerCase(Locale.ROOT);
                try {
                    result = FloatingLiteral.create().setValue(new BigDecimal(floating.group(1)))
                            .setCppType(suffix.equals("f") ? "float" : suffix.equals("l") ? "long double" : "double").setSpelling(text);
                } catch (NumberFormatException ex) { throw tokens.error("Floating literal exponent is outside the supported decimal range"); }
            } else throw tokens.error("Malformed or unsupported C++03 numeric literal");
        } else return null;
        tokens.advance();
        return tokens.span(start, result);
    }

    private static boolean string(String text) { return text.startsWith("\"") || text.startsWith("L\""); }
    private String integerType(BigInteger value, int radix, String suffix) {
        String ending = suffix == null ? "" : suffix.toLowerCase(Locale.ROOT);
        List<String> candidates;
        if (ending.contains("u") && ending.contains("l")) candidates = List.of("unsigned long");
        else if (ending.contains("u")) candidates = List.of("unsigned int", "unsigned long");
        else if (ending.contains("l")) candidates = List.of("long", "unsigned long");
        else candidates = radix == 10 ? List.of("int", "long", "unsigned long") : List.of("int", "unsigned int", "long", "unsigned long");
        for (String candidate : candidates)
            if (tokens.platform().integerRange(candidate).orElseThrow().contains(value)) return candidate;
        throw tokens.error("Integer literal outside target C++03 type ranges");
    }

    /** Numeric escapes are code units; source characters and UCNs use execution encoding. */
    private List<BigInteger> decode(String spelling, boolean wide) {
        int cursor = spelling.startsWith("L") ? 2 : 1;
        int end = spelling.length() - 1;
        char quote = spelling.charAt(cursor - 1);
        if (end < cursor || spelling.charAt(end) != quote) throw tokens.error("Unterminated literal");
        var result = new ArrayList<BigInteger>();
        int width = wide ? tokens.platform().sizeBits("wchar_t").orElseThrow() : tokens.platform().bitsPerByte();
        BigInteger max = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
        while (cursor < end) {
            int point = spelling.codePointAt(cursor); cursor += Character.charCount(point);
            if (point == '\\') {
                if (cursor >= end) throw tokens.error("Incomplete escape");
                char escape = spelling.charAt(cursor++);
                String simple = "'\"?\\abfnrtv", replacement = "'\"?\\\u0007\b\f\n\r\t\u000b";
                int simpleIndex = simple.indexOf(escape);
                if (simpleIndex >= 0) point = replacement.charAt(simpleIndex);
                else if (escape == 'x' || escape >= '0' && escape <= '7') {
                    int begin = escape == 'x' ? cursor : cursor - 1;
                    int limit = escape == 'x' ? end : Math.min(end, begin + 3);
                    int radix = escape == 'x' ? 16 : 8;
                    while (cursor < limit && digit(spelling.charAt(cursor), radix)) cursor++;
                    if (cursor == begin) throw tokens.error("Empty hexadecimal escape");
                    BigInteger value = new BigInteger(spelling.substring(begin, cursor), radix);
                    if (value.compareTo(max) > 0) throw tokens.error("Numeric escape outside target code-unit range");
                    result.add(value); continue;
                } else if (escape == 'u' || escape == 'U') {
                    int count = escape == 'u' ? 4 : 8;
                    if (cursor + count > end) throw tokens.error("Incomplete universal character name");
                    for (int i = cursor; i < cursor + count; i++) if (!digit(spelling.charAt(i), 16)) throw tokens.error("Invalid universal character name");
                    long code = Long.parseLong(spelling.substring(cursor, cursor + count), 16); cursor += count;
                    if (code > Character.MAX_CODE_POINT || code >= 0xd800 && code <= 0xdfff || code < 0xa0 && code != 0x24 && code != 0x40 && code != 0x60)
                        throw tokens.error("Invalid universal character name");
                    point = (int) code;
                } else throw tokens.error("Unknown escape sequence");
            }
            if (wide) {
                if (tokens.platform().wideExecutionEncoding().equals("UTF-16")) {
                    for (char unit : Character.toChars(point)) result.add(BigInteger.valueOf(unit));
                } else result.add(BigInteger.valueOf(point));
            } else {
                String character = new String(Character.toChars(point));
                Charset encoding = Charset.forName(tokens.platform().executionEncoding());
                if (!encoding.newEncoder().canEncode(character)) throw tokens.error("Character is not representable in " + encoding.name());
                for (byte unit : character.getBytes(encoding)) result.add(BigInteger.valueOf(unit & 0xff));
            }
        }
        return List.copyOf(result);
    }
    private static boolean digit(char ch, int radix) {
        return ch >= '0' && ch <= (radix == 8 ? '7' : '9') || radix == 16 && (ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F');
    }
}
