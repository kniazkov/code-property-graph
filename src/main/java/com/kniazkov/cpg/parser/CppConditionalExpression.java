/* Code Property Graph: C++03 conditional-inclusion expressions. MIT License. */
package com.kniazkov.cpg.parser;

import java.math.BigInteger;
import java.nio.charset.Charset;
import com.kniazkov.cpg.build.PlatformProfile;
import java.util.List;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;

/**
 * Evaluates the significant tokens of a macro-expanded C++03 #if expression.
 * The caller replaces defined expressions before invoking this helper.
 *
 * Integer arithmetic uses the profile's long width, including unsigned wrapping
 * and signed-overflow diagnostics. Ordinary character constants pack execution
 * encoding units into a nonnegative value; wide constants use the profile's
 * encoding and wchar_t signedness. Multicharacter constants retain the low long
 * bits (ordinary) or final wide code unit (wide), as implementation-defined choices.
 */
final class CppConditionalExpression {
    private final PlatformProfile profile;
    private final int width;
    private final BigInteger signedMax, signedMin, unsignedMax;
    private final List<PreprocessingToken> tokens;
    private int index;

    private record Value(BigInteger bits, boolean unsigned) {
        boolean truth() { return bits.signum() != 0; }
    }

    private CppConditionalExpression(List<PreprocessingToken> tokens, PlatformProfile profile) {
        this.tokens = tokens; this.profile = profile; width = profile.sizeBits("long").orElseThrow();
        signedMax = BigInteger.ONE.shiftLeft(width - 1).subtract(BigInteger.ONE);
        signedMin = BigInteger.ONE.shiftLeft(width - 1).negate();
        unsignedMax = BigInteger.ONE.shiftLeft(width).subtract(BigInteger.ONE);
    }

    static boolean evaluate(List<PreprocessingToken> tokens) {
        return evaluate(tokens, PlatformProfile.defaults());
    }

    static boolean evaluate(List<PreprocessingToken> tokens, PlatformProfile profile) {
        var parser = new CppConditionalExpression(tokens, profile);
        Value result = parser.conditional(true);
        if (parser.index != tokens.size())
            throw parser.error(tokens.get(parser.index), "Unexpected token in #if expression");
        return result.truth();
    }

    private Value conditional(boolean evaluate) {
        Value condition = binary(1, evaluate);
        if (!take("?")) return condition;
        Value yes = conditional(evaluate && condition.truth());
        if (!take(":")) throw error(current(), "Expected ':' in #if expression");
        Value no = conditional(evaluate && !condition.truth());
        // The unselected operand still determines the usual arithmetic conversion.
        return value(evaluate ? (condition.truth() ? yes.bits() : no.bits()) : BigInteger.ZERO,
                yes.unsigned() || no.unsigned());
    }

    private Value binary(int minimum, boolean evaluate) {
        Value left = unary(evaluate);
        while (index < tokens.size()) {
            PreprocessingToken operator = tokens.get(index);
            String op = operator.canonicalSpelling();
            int precedence = precedence(op);
            if (precedence < minimum) break;
            index++;
            boolean evaluateRight = evaluate && (!op.equals("&&") || left.truth())
                    && (!op.equals("||") || !left.truth());
            Value right = binary(precedence + 1, evaluateRight);
            left = apply(operator, left, right, evaluate);
        }
        return left;
    }

    private static int precedence(String op) {
        return switch (op) {
            case "||" -> 1;
            case "&&" -> 2;
            case "|" -> 3;
            case "^" -> 4;
            case "&" -> 5;
            case "==", "!=" -> 6;
            case "<", ">", "<=", ">=" -> 7;
            case "<<", ">>" -> 8;
            case "+", "-" -> 9;
            case "*", "/", "%" -> 10;
            default -> 0;
        };
    }

    private Value unary(boolean evaluate) {
        PreprocessingToken token = current();
        if (token == null) throw error(null, "Expected operand in #if expression");
        String spelling = token.canonicalSpelling();
        if (spelling.equals("+") || spelling.equals("-") || spelling.equals("!") || spelling.equals("~")) {
            index++;
            Value operand = unary(evaluate);
            if (spelling.equals("!")) return bool(evaluate && !operand.truth());
            if (!evaluate) return new Value(BigInteger.ZERO, operand.unsigned());
            if (spelling.equals("-") && !operand.unsigned() && operand.bits().equals(signedMin))
                throw error(token, "Signed integer overflow in #if expression");
            return value(switch (spelling) {
                case "-" -> operand.bits().negate();
                case "~" -> operand.bits().not();
                default -> operand.bits();
            }, operand.unsigned());
        }
        if (take("(")) {
            Value value = conditional(evaluate);
            if (!take(")")) throw error(current(), "Expected ')' in #if expression");
            return value;
        }
        index++;
        if (token.kind() == PP_NUMBER) return integer(token);
        if (token.kind() == CHARACTER_LITERAL) return character(token);
        // The lexer classifies new/delete as operator tokens. They are keywords,
        // unlike alternative operator tokens, and cpp.cond also replaces them.
        if (token.kind() == IDENTIFIER || spelling.equals("new") || spelling.equals("delete"))
            return bool(spelling.equals("true"));
        throw error(token, "Expected integer operand in #if expression");
    }

    private Value apply(PreprocessingToken token, Value left, Value right, boolean evaluate) {
        String op = token.canonicalSpelling();
        boolean unsigned = left.unsigned() || right.unsigned();
        boolean comparison = precedence(op) <= 2 || precedence(op) == 6 || precedence(op) == 7;
        boolean shift = op.equals("<<") || op.equals(">>");
        if (!evaluate) return new Value(BigInteger.ZERO, !comparison && (shift ? left.unsigned() : unsigned));
        BigInteger a = unsigned && !shift ? left.bits().and(unsignedMax) : left.bits();
        BigInteger b = unsigned && !shift ? right.bits().and(unsignedMax) : right.bits();
        if (shift) {
            if (b.signum() < 0 || b.compareTo(BigInteger.valueOf(width)) >= 0)
                throw error(token, "Invalid shift count in #if expression");
            return value(op.equals("<<") ? a.shiftLeft(b.intValue()) : a.shiftRight(b.intValue()), left.unsigned());
        }
        int order = a.compareTo(b);
        if (comparison) return bool(switch (op) {
            case "||" -> left.truth() || right.truth();
            case "&&" -> left.truth() && right.truth();
            case "==" -> order == 0;
            case "!=" -> order != 0;
            case "<" -> order < 0;
            case ">" -> order > 0;
            case "<=" -> order <= 0;
            case ">=" -> order >= 0;
            default -> throw new AssertionError(op);
        });
        if ((op.equals("/") || op.equals("%")) && b.signum() == 0)
            throw error(token, "Division by zero in #if expression");
        if ((op.equals("/") || op.equals("%")) && !unsigned && a.equals(signedMin) && b.equals(BigInteger.ONE.negate()))
            throw error(token, "Signed integer overflow in #if expression");
        BigInteger result = switch (op) {
            case "+" -> a.add(b);
            case "-" -> a.subtract(b);
            case "*" -> a.multiply(b);
            case "/" -> a.divide(b);
            case "%" -> a.remainder(b);
            case "&" -> a.and(b);
            case "^" -> a.xor(b);
            case "|" -> a.or(b);
            default -> throw new AssertionError(op);
        };
        if (!unsigned && (result.compareTo(signedMin) < 0 || result.compareTo(signedMax) > 0))
            throw error(token, "Signed integer overflow in #if expression");
        return value(result, unsigned);
    }

    private Value value(BigInteger bits, boolean unsigned) {
        bits = bits.and(unsignedMax);
        if (!unsigned && bits.testBit(width - 1)) bits = bits.subtract(unsignedMax.add(BigInteger.ONE));
        return new Value(bits, unsigned);
    }

    private Value integer(PreprocessingToken token) {
        String spelling = token.spelling();
        if (!spelling.matches("(?:0[xX][0-9a-fA-F]+|0[0-7]*|[1-9][0-9]*)(?:[uU][lL]?|[lL][uU]?)?"))
            throw error(token, "Invalid C++03 integer literal in #if expression");
        int end = spelling.length();
        while (end > 0 && "uUlL".indexOf(spelling.charAt(end - 1)) >= 0) end--;
        String suffix = spelling.substring(end);
        String digits = spelling.substring(0, end);
        int radix = digits.startsWith("0x") || digits.startsWith("0X") ? 16 : digits.startsWith("0") ? 8 : 10;
        if (radix == 16) digits = digits.substring(2);
        BigInteger value = new BigInteger(digits, radix);
        boolean unsigned = suffix.indexOf('u') >= 0 || suffix.indexOf('U') >= 0;
        if (value.compareTo(unsignedMax) > 0 || (!unsigned && suffix.isEmpty()
                && radix == 10 && value.compareTo(signedMax) > 0))
            throw error(token, "Integer literal out of range in #if expression");
        return value(value, unsigned || value.compareTo(signedMax) > 0);
    }

    private Value character(PreprocessingToken token) {
        String spelling = token.spelling();
        boolean wide = spelling.startsWith("L'");
        int cursor = wide ? 2 : 1;
        int end = spelling.length() - 1;
        if (cursor >= end || spelling.charAt(end) != '\'')
            throw error(token, "Invalid character literal in #if expression");
        BigInteger value = BigInteger.ZERO;
        int charBits = wide ? profile.sizeBits("wchar_t").orElseThrow() : profile.bitsPerByte();
        BigInteger charMask = BigInteger.ONE.shiftLeft(charBits).subtract(BigInteger.ONE);
        boolean wideUnsigned = profile.integerRange("wchar_t").orElseThrow().min().signum() == 0;
        while (cursor < end) {
            int codePoint = spelling.codePointAt(cursor);
            cursor += Character.charCount(codePoint);
            boolean numeric = false;
            BigInteger numericValue = null;
            if (codePoint == '\\') {
                if (cursor >= end) throw error(token, "Invalid character escape in #if expression");
                char escape = spelling.charAt(cursor++);
                String simple = "'\"?\\abfnrtv";
                String replacements = "'\"?\\\u0007\b\f\n\r\t\u000b";
                int simpleIndex = simple.indexOf(escape);
                if (simpleIndex >= 0) codePoint = replacements.charAt(simpleIndex);
                else if (escape == 'x' || escape >= '0' && escape <= '7') {
                    int start = escape == 'x' ? cursor : cursor - 1;
                    int limit = escape == 'x' ? end : Math.min(end, start + 3);
                    int radix = escape == 'x' ? 16 : 8;
                    while (cursor < limit && digit(spelling.charAt(cursor), radix)) cursor++;
                    if (cursor == start) throw error(token, "Invalid character escape in #if expression");
                    numericValue = new BigInteger(spelling.substring(start, cursor), radix);
                    codePoint = numericValue.intValue();
                    numeric = true;
                } else if (escape == 'u' || escape == 'U') {
                    int length = escape == 'u' ? 4 : 8;
                    if (cursor + length > end) throw error(token, "Invalid universal character name in #if expression");
                    for (int i = cursor; i < cursor + length; i++)
                        if (!digit(spelling.charAt(i), 16)) throw error(token, "Invalid universal character name in #if expression");
                    long decoded = Long.parseLong(spelling.substring(cursor, cursor + length), 16);
                    if (decoded > Character.MAX_CODE_POINT || decoded >= 0xD800 && decoded <= 0xDFFF
                            || decoded < 0xA0 && decoded != 0x24 && decoded != 0x40 && decoded != 0x60)
                        throw error(token, "Invalid universal character name in #if expression");
                    codePoint = (int) decoded;
                    cursor += length;
                } else throw error(token, "Invalid character escape in #if expression");
            }
            if (wide) {
                value = numeric ? numericValue.and(charMask) : BigInteger.valueOf(codePoint);
                if (!numeric && profile.wideExecutionEncoding().equals("UTF-16") && codePoint > 0xffff)
                    value = BigInteger.valueOf(Character.lowSurrogate(codePoint));
                if (!wideUnsigned && value.testBit(charBits - 1)) value = value.subtract(charMask.add(BigInteger.ONE));
            } else if (numeric) value = value.shiftLeft(charBits).or(numericValue.and(charMask));
            else {
                String character = new String(Character.toChars(codePoint));
                Charset encoding = Charset.forName(profile.executionEncoding());
                if (!encoding.newEncoder().canEncode(character))
                    throw error(token, "Character is not representable in " + profile.executionEncoding());
                for (byte octet : character.getBytes(encoding))
                    value = value.shiftLeft(charBits).or(BigInteger.valueOf(octet & 0xffL));
            }
        }
        return value(value, wide && wideUnsigned);
    }

    private static boolean digit(char ch, int radix) {
        return ch >= '0' && ch <= (radix == 8 ? '7' : '9')
                || radix == 16 && (ch >= 'a' && ch <= 'f' || ch >= 'A' && ch <= 'F');
    }

    private static Value bool(boolean value) { return new Value(value ? BigInteger.ONE : BigInteger.ZERO, false); }
    private PreprocessingToken current() { return index < tokens.size() ? tokens.get(index) : null; }
    private boolean take(String spelling) {
        if (current() == null || !current().canonicalSpelling().equals(spelling)) return false;
        index++;
        return true;
    }

    private ParseException error(PreprocessingToken token, String reason) {
        if (token == null && !tokens.isEmpty()) {
            var range = tokens.getLast().range();
            return new ParseException(range.file(), range.end().line(), range.end().column(), reason);
        }
        if (token == null) return new ParseException("<preprocessor>", 1, 1, reason);
        var range = token.range();
        return new ParseException(range.file(), range.start().line(), range.start().column(), reason);
    }
}
