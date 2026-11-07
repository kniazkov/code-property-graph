/* Code Property Graph: explicit C++ target model. MIT License. */
package com.kniazkov.cpg.build;

import com.kniazkov.json.Json;
import java.io.IOException;
import java.math.BigInteger;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * Immutable, host-independent C++ target choices. Sizes and alignments are in bits;
 * sizeof is measured in target bytes. The default is a documented LP64-like model,
 * not a promise of compatibility with every ABI having 32-bit int and 64-bit long.
 * Missing alignment entries and the "unknown" layout explicitly mean unknown.
 */
public final class PlatformProfile {
    private static final List<String> SCALARS = List.of("bool", "char", "short", "int", "long",
            "long long", "wchar_t", "float", "double", "long double", "pointer");
    private static final Set<String> SETTINGS = Set.of("inherits", "name", "bitsPerByte", "sizes", "alignments",
            "charSigned", "wcharType", "sizeType", "sourceEncoding", "executionEncoding", "wideExecutionEncoding", "aggregateLayout", "fieldAlignment", "aggregateAlignment", "tailPadding", "enumUnderlyingType", "signedIntegerConversion", "bitFieldSignedness", "floatingFormats", "floatingRounds", "nullPointerConstant", "maxMultibyteLength", "cRuntime", "runtimeIntegerMacros", "mbCurMaxBinding");
    private static final PlatformProfile DEFAULT = new Builder(true).build();
    private final Map<String, RuntimeIntegerMacros.Fact> runtimeIntegerMacros;
    private final Map<String, String> floatingFormats;
    private final int floatingRounds, maxMultibyteLength;
    private final String nullPointerConstant, cRuntime, mbCurMaxBinding;
    private final String name;
    private final int bitsPerByte;
    private final Map<String, Integer> sizes, alignments;
    private final boolean charSigned;
    private final String wcharType, sizeType, sourceEncoding, executionEncoding, wideExecutionEncoding, aggregateLayout;
    private final String fieldAlignment, aggregateAlignment, tailPadding;
    private final String enumUnderlyingType, signedIntegerConversion, bitFieldSignedness;

    private PlatformProfile(Builder b) {
        name = nonempty(b.name, "name"); bitsPerByte = b.bitsPerByte;
        cRuntime = policy(b.cRuntime, "cRuntime", Set.of("unknown", "glibc"));
        mbCurMaxBinding = policy(b.mbCurMaxBinding, "mbCurMaxBinding", Set.of("unavailable", "glibc-x86_64-lp64-mb16"));
        floatingRounds = b.floatingRounds;
        if (floatingRounds < -1 || floatingRounds > 3) throw invalid("floatingRounds must be -1, 0, 1, 2 or 3");
        maxMultibyteLength = b.maxMultibyteLength;
        if (maxMultibyteLength < 1) throw invalid("maxMultibyteLength must be positive");
        nullPointerConstant = policy(b.nullPointerConstant, "nullPointerConstant", Set.of("0", "0L"));
        if (bitsPerByte < 8 || bitsPerByte > 4096) throw invalid("bitsPerByte must be between 8 and 4096");
        var normalizedSizes = new LinkedHashMap<String, Integer>();
        var normalizedAlignments = new LinkedHashMap<String, Integer>();
        for (String type : SCALARS) {
            Integer size = b.sizes.get(type);
            if (size == null || size < bitsPerByte || size > 4096 || size % bitsPerByte != 0)
                throw invalid("size of " + type + " must be a positive whole target byte, at most 4096 bits");
            normalizedSizes.put(type, size);
            Integer alignment = b.alignments.get(type);
            if (alignment != null) {
                if (alignment < bitsPerByte || alignment > size || alignment % bitsPerByte != 0
                        || size % alignment != 0)
                    throw invalid("alignment of " + type + " must divide its size and be a whole target byte");
                int bytes = alignment / bitsPerByte;
                if ((bytes & (bytes - 1)) != 0) throw invalid("alignment of " + type + " must be a power of two target bytes");
                normalizedAlignments.put(type, alignment);
            }
        }
        sizes = Collections.unmodifiableMap(normalizedSizes); alignments = Collections.unmodifiableMap(normalizedAlignments);
        if (cRuntime.equals("glibc") && (bitsPerByte != 8 || sizes.get("int") != 32 || !Set.of(32, 64).contains(sizes.get("pointer"))))
            throw invalid("cRuntime glibc requires 8-bit bytes, 32-bit int and 32/64-bit pointers for the supported assertion binding");
        if (sizes.get("char") != bitsPerByte) throw invalid("char size must equal bitsPerByte");
        if (sizes.get("short") < 16 || sizes.get("int") < sizes.get("short") || sizes.get("long") < 32
                || sizes.get("long") < sizes.get("int") || sizes.get("long long") < sizes.get("long"))
            throw invalid("integer sizes must satisfy short >= 16, short <= int <= long <= long long, long >= 32");
        if (sizes.get("float") > sizes.get("double") || sizes.get("double") > sizes.get("long double"))
            throw invalid("floating sizes must satisfy float <= double <= long double");
        var formats = new LinkedHashMap<String, String>();
        int precedingPrecision = 0;
        for (String type : List.of("float", "double", "long double")) {
            String format = policy(b.floatingFormats.get(type), "floatingFormats." + type,
                    Set.of("unknown", "binary32", "binary64", "binary80", "binary128"));
            if (!type.equals("float") && format.equals("binary32"))
                throw invalid("floatingFormats." + type + " must provide at least 10 decimal digits in C++03");
            int width = format.equals("unknown") ? 0 : Integer.parseInt(format.substring(6));
            if (width > sizes.get(type)) throw invalid("floatingFormats." + type + " does not fit its storage size");
            if (width != 0 && precedingPrecision > width) throw invalid("floating formats must have nondecreasing precision");
            if (width != 0) precedingPrecision = width;
            formats.put(type, format);
        }
        floatingFormats = Collections.unmodifiableMap(formats);
        charSigned = b.charSigned; wcharType = normalizeInteger(nonempty(b.wcharType, "wcharType"));
        if (wcharType.equals("wchar_t") || wcharType.equals("bool") || storageType(wcharType).equals("long long") || !isInteger(wcharType)
                || !Objects.equals(sizes.get(storageType(wcharType)), sizes.get("wchar_t")))
            throw invalid("wcharType must name an integer type with the wchar_t size");
        sizeType = normalizeInteger(nonempty(b.sizeType, "sizeType"));
        if (!Set.of("unsigned int", "unsigned long").contains(sizeType))
            throw invalid("sizeType must be unsigned int or unsigned long");
        if (!mbCurMaxBinding.equals("unavailable") && (!cRuntime.equals("glibc") || bitsPerByte != 8
                || sizes.get("int") != 32 || sizes.get("long") != 64 || sizes.get("pointer") != 64
                || !sizeType.equals("unsigned long") || maxMultibyteLength != 16))
            throw invalid("mbCurMaxBinding glibc-x86_64-lp64-mb16 requires cRuntime=glibc, 8-bit bytes, 32-bit int, 64-bit long/pointer, sizeType=unsigned long and maxMultibyteLength=16");
        sourceEncoding = charset(b.sourceEncoding, "sourceEncoding");
        executionEncoding = charset(b.executionEncoding, "executionEncoding");
        if (!Set.of("UTF-8", "ISO-8859-1", "US-ASCII").contains(executionEncoding))
            throw invalid("executionEncoding must be UTF-8, ISO-8859-1 or US-ASCII");
        wideExecutionEncoding = nonempty(b.wideExecutionEncoding, "wideExecutionEncoding").toUpperCase(Locale.ROOT);
        if (!Set.of("UTF-16", "UTF-32").contains(wideExecutionEncoding))
            throw invalid("wideExecutionEncoding must be UTF-16 or UTF-32");
        if (sizes.get("wchar_t") < (wideExecutionEncoding.equals("UTF-32") ? 32 : 16))
            throw invalid("wchar_t is too small for " + wideExecutionEncoding);
        aggregateLayout = nonempty(b.aggregateLayout, "aggregateLayout");
        if (!Set.of("sequential", "unknown").contains(aggregateLayout))
            throw invalid("aggregateLayout must be sequential or unknown");
        fieldAlignment = policy(b.fieldAlignment, "fieldAlignment", Set.of("natural", "packed1", "unknown"));
        aggregateAlignment = policy(b.aggregateAlignment, "aggregateAlignment", Set.of("maximum-field", "unknown"));
        tailPadding = policy(b.tailPadding, "tailPadding", Set.of("alignment", "unknown"));
        bitFieldSignedness = policy(b.bitFieldSignedness, "bitFieldSignedness", Set.of("signed", "unsigned", "unknown"));
        enumUnderlyingType = policy(b.enumUnderlyingType, "enumUnderlyingType",
                Set.of("int-first", "unsigned-first", "unknown"));
        signedIntegerConversion = policy(b.signedIntegerConversion, "signedIntegerConversion",
                Set.of("unknown", "twos-complement"));
        runtimeIntegerMacros = RuntimeIntegerMacros.validate(b.runtimeIntegerMacros, this);
    }

    public static PlatformProfile defaults() { return DEFAULT; }
    /** Starts with the complete documented default, including its alignments. */
    public static Builder builder() { return new Builder(true); }
    public Builder toBuilder() { return new Builder(this); }
    public String name() { return name; }
    /** Explicit radix-two formats, independent of storage size and the Java runtime. */
    public Map<String, String> floatingFormats() { return floatingFormats; }
    /** C90 FLT_ROUNDS choice; -1 means indeterminable, not a guessed host mode. */
    public int floatingRounds() { return floatingRounds; }
    public int maxMultibyteLength() { return maxMultibyteLength; }
    public String nullPointerConstant() { return nullPointerConstant; }
    /** Explicit C runtime family selection, never inferred from the target widths or host. */
    public String cRuntime() { return cRuntime; }
    /** Explicit accessor contract only; does not detect or certify the deployment ABI. */
    public String mbCurMaxBinding() { return mbCurMaxBinding; }
    public Map<String, RuntimeIntegerMacros.Fact> runtimeIntegerMacros() { return runtimeIntegerMacros; }
    public int bitsPerByte() { return bitsPerByte; }
    public boolean charSigned() { return charSigned; }
    public String wcharType() { return wcharType; }
    public String sizeType() { return sizeType; }
    public String sourceEncoding() { return sourceEncoding; }
    public String executionEncoding() { return executionEncoding; }
    public String wideExecutionEncoding() { return wideExecutionEncoding; }
    public String aggregateLayout() { return aggregateLayout; }
    /** Natural scalar alignment or one-target-byte packing for every ordinary field. */
    public String fieldAlignment() { return fieldAlignment; }
    public String aggregateAlignment() { return aggregateAlignment; }
    public String tailPadding() { return tailPadding; }
    /**
     * Modeled enum underlying-type selection, not a host compiler fact.
     * int-first tries int, unsigned int, long, unsigned long; unsigned-first tries
     * unsigned int, int, unsigned long, long. Semantic selection also enforces
     * the standard's size restriction; unknown makes no implementation choice.
     */
    public String enumUnderlyingType() { return enumUnderlyingType; }
    /** Out-of-range signed conversion: unknown, or opt-in two's-complement wrapping. */
    public String signedIntegerConversion() { return signedIntegerConversion; }
    /**
     * Explicit model for plain char/short/int/long bit-field signedness, not an ABI fact.
     * Explicitly signed or unsigned bit-fields are unaffected.
     */
    public String bitFieldSignedness() { return bitFieldSignedness; }
    public Map<String, Integer> sizes() { return sizes; }
    public Map<String, Integer> alignments() { return alignments; }
    public OptionalInt sizeBits(String type) { return optional(sizes.get(storageType(type))); }
    public OptionalInt sizeBytes(String type) {
        var bits = sizeBits(type); return bits.isPresent() ? OptionalInt.of(bits.getAsInt() / bitsPerByte) : OptionalInt.empty();
    }
    public OptionalInt alignmentBits(String type) { return optional(alignments.get(storageType(type))); }
    private static OptionalInt optional(Integer value) { return value == null ? OptionalInt.empty() : OptionalInt.of(value); }

    public record IntegerRange(BigInteger min, BigInteger max) {
        public boolean contains(BigInteger value) { return value.compareTo(min) >= 0 && value.compareTo(max) <= 0; }
    }
    /** Two's-complement, no-padding integer model; bool has the value range 0..1. */
    public Optional<IntegerRange> integerRange(String type) {
        type = normalizeInteger(type);
        if (!isInteger(type)) return Optional.empty();
        if (type.equals("bool")) return Optional.of(new IntegerRange(BigInteger.ZERO, BigInteger.ONE));
        if (type.equals("wchar_t")) return integerRange(wcharType);
        boolean signed = !type.startsWith("unsigned ") && (!type.equals("char") || charSigned);
        int bits = sizeBits(type).orElseThrow();
        BigInteger bound = BigInteger.ONE.shiftLeft(signed ? bits - 1 : bits);
        return Optional.of(new IntegerRange(signed ? bound.negate() : BigInteger.ZERO, bound.subtract(BigInteger.ONE)));
    }

    /** Only target facts are predefined; no compiler/OS identity is invented. */
    public Map<String, String> predefinedMacros() {
        var result = new LinkedHashMap<String, String>();
        result.put("__CHAR_BIT__", Integer.toString(bitsPerByte));
        for (String type : List.of("short", "int", "long", "long long", "float", "double", "long double", "pointer", "wchar_t"))
            result.put("__SIZEOF_" + type.toUpperCase(Locale.ROOT).replace(' ', '_') + "__", Integer.toString(sizeBytes(type).orElseThrow()));
        result.put("__SCHAR_MAX__", integerRange("signed char").orElseThrow().max().toString());
        result.put("__SHRT_MAX__", integerRange("short").orElseThrow().max().toString());
        result.put("__INT_MAX__", integerRange("int").orElseThrow().max().toString());
        result.put("__LONG_MAX__", integerRange("long").orElseThrow().max() + "L");
        result.put("__WCHAR_TYPE__", wcharType);
        result.put("__SIZE_TYPE__", sizeType);
        boolean unsignedWchar = integerRange("wchar_t").orElseThrow().min().signum() == 0;
        result.put("__WCHAR_MAX__", integerRange("wchar_t").orElseThrow().max() + (unsignedWchar ? "U" : ""));
        if (!charSigned) result.put("__CHAR_UNSIGNED__", "1");
        if (unsignedWchar) result.put("__WCHAR_UNSIGNED__", "1");
        return Collections.unmodifiableMap(result);
    }

    /** Complete effective settings: null alignments are unknown, never implicit defaults. */
    public Map<String, Object> toSettings() {
        var map = new LinkedHashMap<String, Object>();
        map.put("inherits", "none"); map.put("name", name); map.put("bitsPerByte", bitsPerByte);
        map.put("sizes", sizes);
        map.put("cRuntime", cRuntime);
        map.put("mbCurMaxBinding", mbCurMaxBinding);
        var facts = new LinkedHashMap<String,Object>();
        runtimeIntegerMacros.forEach((key,value) -> facts.put(key,value.settings()));
        map.put("runtimeIntegerMacros", Collections.unmodifiableMap(facts));
        map.put("floatingFormats", floatingFormats); map.put("floatingRounds", floatingRounds);
        map.put("nullPointerConstant", nullPointerConstant); map.put("maxMultibyteLength", maxMultibyteLength);
        var allAlignments = new LinkedHashMap<String, Integer>();
        SCALARS.forEach(t -> allAlignments.put(t, alignments.get(t)));
        map.put("alignments", Collections.unmodifiableMap(allAlignments));
        map.put("charSigned", charSigned); map.put("wcharType", wcharType); map.put("sizeType", sizeType);
        map.put("sourceEncoding", sourceEncoding); map.put("executionEncoding", executionEncoding);
        map.put("wideExecutionEncoding", wideExecutionEncoding); map.put("aggregateLayout", aggregateLayout);
        map.put("fieldAlignment", fieldAlignment); map.put("aggregateAlignment", aggregateAlignment); map.put("tailPadding", tailPadding);
        map.put("bitFieldSignedness", bitFieldSignedness);
        map.put("enumUnderlyingType", enumUnderlyingType); map.put("signedIntegerConversion", signedIntegerConversion);
        return Collections.unmodifiableMap(map);
    }
    /** Stable, semantic identity; a display-name change alone does not create a new variant. */
    public String stableIdentity() {
        var map = new LinkedHashMap<>(toSettings()); map.remove("name");
        return map.toString();
    }
    /** Compact stable identifier for configuration provenance nodes. */
    public String identityHash() {
        try {
            return java.util.HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256")
                    .digest(stableIdentity().getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException ex) { throw new AssertionError(ex); }
    }
    @Override public boolean equals(Object other) { return other instanceof PlatformProfile p && toSettings().equals(p.toSettings()); }
    @Override public int hashCode() { return toSettings().hashCode(); }
    @Override public String toString() { return name + " " + stableIdentity(); }

    public static PlatformProfile read(Path path) throws IOException {
        try {
            Object value = UniqueKeyJson.parse(Files.readString(path));
            if (!(value instanceof Map<?, ?> map)) throw invalid("expected a JSON object");
            return fromSettings(map);
        } catch (com.kniazkov.json.JsonException | RuntimeException ex) { throw new IllegalArgumentException(path + ": " + ex.getMessage(), ex); }
    }
    /** A partial JSON profile must explicitly opt into inheriting the documented defaults. */
    public static PlatformProfile fromSettings(Map<?, ?> settings) {
        for (Object key : settings.keySet()) if (!SETTINGS.contains(key)) throw invalid("unknown setting " + key);
        Object inherits = settings.get("inherits");
        if (!Objects.equals(inherits, "default") && !Objects.equals(inherits, "none"))
            throw invalid("inherits must explicitly be default or none");
        if (inherits.equals("none")) for (String required : List.of("bitsPerByte", "sizes", "charSigned", "wcharType", "sizeType",
                "sourceEncoding", "executionEncoding", "wideExecutionEncoding", "aggregateLayout"))
            if (!settings.containsKey(required)) throw invalid("inherits none requires explicit " + required);
        var b = new Builder(inherits.equals("default"));
        if (settings.containsKey("name")) b.name(string(settings.get("name"), "name"));
        if (settings.containsKey("bitsPerByte")) b.bitsPerByte(integer(settings.get("bitsPerByte"), "bitsPerByte"));
        for (String field : List.of("sizes", "alignments")) if (settings.containsKey(field)) {
            if (!(settings.get(field) instanceof Map<?, ?> values)) throw invalid(field + " must be an object");
            for (var entry : values.entrySet()) {
                String key = string(entry.getKey(), field + " key");
                if (!SCALARS.contains(key)) throw invalid("unknown " + field + " type " + key);
                if (field.equals("sizes")) b.sizeBits(key, integer(entry.getValue(), field + "." + key));
                else b.alignmentBits(key, entry.getValue() == null ? null : integer(entry.getValue(), field + "." + key));
            }
        }
        if (settings.containsKey("charSigned")) {
            if (!(settings.get("charSigned") instanceof Boolean signed)) throw invalid("charSigned must be boolean");
            b.charSigned(signed);
        }
        if (settings.containsKey("sizeType")) b.sizeType(string(settings.get("sizeType"), "sizeType"));
        if (settings.containsKey("wcharType")) b.wcharType(string(settings.get("wcharType"), "wcharType"));
        if (settings.containsKey("sourceEncoding")) b.sourceEncoding(string(settings.get("sourceEncoding"), "sourceEncoding"));
        if (settings.containsKey("executionEncoding")) b.executionEncoding(string(settings.get("executionEncoding"), "executionEncoding"));
        if (settings.containsKey("wideExecutionEncoding")) b.wideExecutionEncoding(string(settings.get("wideExecutionEncoding"), "wideExecutionEncoding"));
        if (settings.containsKey("aggregateLayout")) b.aggregateLayout(string(settings.get("aggregateLayout"), "aggregateLayout"));
        if (settings.containsKey("fieldAlignment")) b.fieldAlignment(string(settings.get("fieldAlignment"), "fieldAlignment"));
        if (settings.containsKey("aggregateAlignment")) b.aggregateAlignment(string(settings.get("aggregateAlignment"), "aggregateAlignment"));
        if (settings.containsKey("tailPadding")) b.tailPadding(string(settings.get("tailPadding"), "tailPadding"));
        if (settings.containsKey("bitFieldSignedness")) b.bitFieldSignedness(string(settings.get("bitFieldSignedness"), "bitFieldSignedness"));
        if (settings.containsKey("enumUnderlyingType")) b.enumUnderlyingType(string(settings.get("enumUnderlyingType"), "enumUnderlyingType"));
        if (settings.containsKey("signedIntegerConversion")) b.signedIntegerConversion(string(settings.get("signedIntegerConversion"), "signedIntegerConversion"));
        if (settings.containsKey("floatingRounds")) b.floatingRounds(integer(settings.get("floatingRounds"), "floatingRounds"));
        if (settings.containsKey("maxMultibyteLength")) b.maxMultibyteLength(integer(settings.get("maxMultibyteLength"), "maxMultibyteLength"));
        if (settings.containsKey("nullPointerConstant")) b.nullPointerConstant(string(settings.get("nullPointerConstant"), "nullPointerConstant"));
        if (settings.containsKey("floatingFormats")) {
            if (!(settings.get("floatingFormats") instanceof Map<?, ?> formats)) throw invalid("floatingFormats must be an object");
            for (var entry : formats.entrySet()) b.floatingFormat(string(entry.getKey(), "floatingFormats key"),
                    string(entry.getValue(), "floatingFormats value"));
        }
        if (settings.containsKey("cRuntime")) b.cRuntime(string(settings.get("cRuntime"), "cRuntime"));
        if (settings.containsKey("mbCurMaxBinding")) b.mbCurMaxBinding(string(settings.get("mbCurMaxBinding"), "mbCurMaxBinding"));
        if (settings.containsKey("runtimeIntegerMacros")) {
            if (!(settings.get("runtimeIntegerMacros") instanceof Map<?,?> facts)) throw invalid("runtimeIntegerMacros must be an object");
            for (var entry : facts.entrySet()) {
                String name = string(entry.getKey(), "runtimeIntegerMacros key");
                if (!(entry.getValue() instanceof Map<?,?> fact) || !fact.keySet().equals(Set.of("type", "value")))
                    throw invalid("runtimeIntegerMacros." + name + " requires exactly type and value");
                b.runtimeIntegerMacro(name, string(fact.get("type"), name + ".type"), string(fact.get("value"), name + ".value"));
            }
        }
        return b.build();
    }
    private static String policy(String value, String field, Set<String> allowed) {
        if (!allowed.contains(nonempty(value, field))) throw invalid(field + " must be one of " + allowed);
        return value;
    }
    private static int integer(Object value, String field) {
        try {
            if (!(value instanceof Number)) throw invalid(field + " must be an integer");
            return new java.math.BigDecimal(value.toString()).intValueExact();
        } catch (NumberFormatException | ArithmeticException e) { throw invalid(field + " must be an integer"); }
    }
    private static String string(Object value, String field) {
        if (!(value instanceof String text)) throw invalid(field + " must be a string"); return nonempty(text, field);
    }
    private static String nonempty(String value, String field) {
        if (value == null || value.isBlank()) throw invalid(field + " must be nonempty"); return value;
    }
    private static String charset(String value, String field) {
        try { return Charset.forName(nonempty(value, field)).name(); }
        catch (IllegalArgumentException ex) { throw invalid("unsupported " + field + ": " + value); }
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("Invalid platform profile: " + message); }
    private static String normalizeInteger(String type) {
        if (type == null) return "";
        type = type.trim().replaceAll("\\s+", " ");
        type = type.replaceAll("\\b(const|volatile)\\s*", "").trim();
        if (type.equals("signed")) return "int";
        if (type.equals("unsigned")) return "unsigned int";
        if (type.startsWith("signed ") && !type.equals("signed char")) type = type.substring(7);
        if (!type.equals("unsigned int") && !type.equals("int") && type.endsWith(" int")) type = type.substring(0, type.length() - 4);
        return type;
    }
    private static String storageType(String type) {
        type = normalizeInteger(type);
        if (type.equals("signed char")) return "char";
        if (type.startsWith("unsigned ")) return type.substring(9);
        return type;
    }
    private static boolean isInteger(String type) {
        return Set.of("bool", "char", "short", "int", "long", "long long", "wchar_t").contains(storageType(type));
    }

    public static final class Builder {
        private String name = "custom";
        private final Map<String, RuntimeIntegerMacros.Fact> runtimeIntegerMacros = new LinkedHashMap<>();
        private final Map<String, String> floatingFormats = new LinkedHashMap<>(Map.of(
                "float", "unknown", "double", "unknown", "long double", "unknown"));
        private int floatingRounds = -1, maxMultibyteLength = 1;
        private String nullPointerConstant = "0", cRuntime = "unknown", mbCurMaxBinding = "unavailable";
        private int bitsPerByte = 8;
        private final Map<String, Integer> sizes = new LinkedHashMap<>(), alignments = new LinkedHashMap<>();
        private boolean charSigned = true;
        private String wcharType = "int", sizeType = "unsigned long", sourceEncoding = "UTF-8", executionEncoding = "UTF-8", wideExecutionEncoding = "UTF-32";
        private String aggregateLayout = "unknown";
        // Also apply to older complete profiles that predate these optional settings.
        private String enumUnderlyingType = "int-first", signedIntegerConversion = "unknown", bitFieldSignedness = "signed";
        private String fieldAlignment = "unknown", aggregateAlignment = "unknown", tailPadding = "unknown";
        private Builder(boolean defaults) {
            if (defaults) {
                name = "default-lp64"; aggregateLayout = "sequential";
                floatingFormats.put("float", "binary32"); floatingFormats.put("double", "binary64");
                floatingFormats.put("long double", "binary128"); floatingRounds = 1; maxMultibyteLength = 16;
                fieldAlignment = "natural"; aggregateAlignment = "maximum-field"; tailPadding = "alignment";
                int[] bits = {8, 8, 16, 32, 64, 64, 32, 32, 64, 128, 64};
                for (int i = 0; i < bits.length; i++) { sizes.put(SCALARS.get(i), bits[i]); alignments.put(SCALARS.get(i), bits[i]); }
            }
        }
        private Builder(PlatformProfile p) {
            runtimeIntegerMacros.putAll(p.runtimeIntegerMacros);
            cRuntime = p.cRuntime; mbCurMaxBinding = p.mbCurMaxBinding;
            floatingFormats.putAll(p.floatingFormats); floatingRounds = p.floatingRounds;
            maxMultibyteLength = p.maxMultibyteLength; nullPointerConstant = p.nullPointerConstant;
            name = p.name; bitsPerByte = p.bitsPerByte; sizes.putAll(p.sizes); alignments.putAll(p.alignments);
            charSigned = p.charSigned; wcharType = p.wcharType; sizeType = p.sizeType; sourceEncoding = p.sourceEncoding;
            executionEncoding = p.executionEncoding; wideExecutionEncoding = p.wideExecutionEncoding; aggregateLayout = p.aggregateLayout;
            bitFieldSignedness = p.bitFieldSignedness;
            enumUnderlyingType = p.enumUnderlyingType; signedIntegerConversion = p.signedIntegerConversion;
            fieldAlignment = p.fieldAlignment; aggregateAlignment = p.aggregateAlignment; tailPadding = p.tailPadding;
        }
        public Builder runtimeIntegerMacro(String name, String type, String decimal) {
            runtimeIntegerMacros.put(name, RuntimeIntegerMacros.Fact.parse(type, decimal)); return this;
        }
        public Builder cRuntime(String value) { cRuntime = value; return this; }
        public Builder mbCurMaxBinding(String value) { mbCurMaxBinding = value; return this; }
        public Builder floatingFormat(String type, String value) {
            if (!floatingFormats.containsKey(type)) throw invalid("unknown floatingFormats type " + type);
            floatingFormats.put(type, value); return this;
        }
        public Builder floatingRounds(int value) { floatingRounds = value; return this; }
        public Builder maxMultibyteLength(int value) { maxMultibyteLength = value; return this; }
        public Builder nullPointerConstant(String value) { nullPointerConstant = value; return this; }
        public Builder name(String value) { name = value; return this; }
        public Builder bitsPerByte(int value) { bitsPerByte = value; return this; }
        public Builder sizeBits(String type, int value) { sizes.put(key(type), value); return this; }
        public Builder alignmentBits(String type, Integer value) {
            if (value == null) alignments.remove(key(type)); else alignments.put(key(type), value); return this;
        }
        public Builder charSigned(boolean value) { charSigned = value; return this; }
        public Builder sizeType(String value) { sizeType = value; return this; }
        public Builder wcharType(String value) { wcharType = value; return this; }
        public Builder sourceEncoding(String value) { sourceEncoding = value; return this; }
        public Builder executionEncoding(String value) { executionEncoding = value; return this; }
        public Builder wideExecutionEncoding(String value) { wideExecutionEncoding = value; return this; }
        public Builder encodings(String source, String execution, String wide) {
            sourceEncoding = source; executionEncoding = execution; wideExecutionEncoding = wide; return this;
        }
        public Builder aggregateLayout(String value) { aggregateLayout = value; return this; }
        public Builder fieldAlignment(String value) { fieldAlignment = value; return this; }
        public Builder aggregateAlignment(String value) { aggregateAlignment = value; return this; }
        public Builder tailPadding(String value) { tailPadding = value; return this; }
        public Builder bitFieldSignedness(String value) { bitFieldSignedness = value; return this; }
        public Builder enumUnderlyingType(String value) { enumUnderlyingType = value; return this; }
        public Builder signedIntegerConversion(String value) { signedIntegerConversion = value; return this; }
        public PlatformProfile build() { return new PlatformProfile(this); }
        private static String key(String type) {
            String key = storageType(type); if (!SCALARS.contains(key)) throw invalid("unknown scalar type " + type); return key;
        }
    }
}
