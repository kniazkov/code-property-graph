/* Explicit compiler/layout subset for offsetof. MIT License. */
package com.kniazkov.cpg.build;

import java.math.BigInteger;
import java.util.*;

/** Caller-selected layout contract, not host detection or a compiler allocation guarantee. */
public record OffsetofModel(String emission, String layout, BigInteger supportedObjectBytes,
        String memberPointerStorage, String enumStorage, String bitFieldStorage) {
    private static final BigInteger MAXIMUM = BigInteger.valueOf(2147483647L);
    /** Existing callers do not opt in to any member-pointer representation. */
    public OffsetofModel(String emission, String layout, BigInteger supportedObjectBytes) {
        this(emission, layout, supportedObjectBytes, null);
    }
    /** Existing member-pointer callers do not select enum storage. */
    public OffsetofModel(String emission, String layout, BigInteger supportedObjectBytes, String memberPointerStorage) {
        this(emission, layout, supportedObjectBytes, memberPointerStorage, null);
    }
    /** Existing enum callers do not select bit-field allocation. */
    public OffsetofModel(String emission, String layout, BigInteger supportedObjectBytes, String memberPointerStorage, String enumStorage) {
        this(emission, layout, supportedObjectBytes, memberPointerStorage, enumStorage, null);
    }
    public OffsetofModel {
        if (!"gcc-clang-builtin".equals(emission) || !"x86_64-sysv-lp64-v1".equals(layout))
            throw invalid("requires gcc-clang-builtin emission and x86_64-sysv-lp64-v1 layout");
        if (supportedObjectBytes == null || supportedObjectBytes.signum() <= 0 || supportedObjectBytes.compareTo(MAXIMUM) > 0)
            throw invalid("supportedObjectBytes must be between 1 and 2147483647; this is a supported-subset ceiling, not a compiler maximum");
        if (memberPointerStorage != null && !memberPointerStorage.equals("itanium-x86_64-lp64-v1"))
            throw invalid("memberPointerStorage must be itanium-x86_64-lp64-v1 when supplied");
        if (enumStorage != null && !enumStorage.equals("gcc-clang-x86_64-default-enum-v1"))
            throw invalid("enumStorage must be gcc-clang-x86_64-default-enum-v1 when supplied");
        if (bitFieldStorage != null && !Set.of("x86_64-sysv-unsigned-v1", "x86_64-sysv-unsigned-v2", "x86_64-sysv-unsigned-v3", "x86_64-sysv-integral-v4", "x86_64-sysv-oversized-v5", "x86_64-sysv-bool-v6").contains(bitFieldStorage))
            throw invalid("bitFieldStorage must be x86_64-sysv-unsigned-v1, x86_64-sysv-unsigned-v2, x86_64-sysv-unsigned-v3, x86_64-sysv-integral-v4, x86_64-sysv-oversized-v5 or x86_64-sysv-bool-v6 when supplied");
    }
    public static OffsetofModel parse(String emission, String layout, String decimal) {
        return parse(emission, layout, decimal, null);
    }
    public static OffsetofModel parse(String emission, String layout, String decimal, String memberPointerStorage) {
        return parse(emission, layout, decimal, memberPointerStorage, null);
    }
    public static OffsetofModel parse(String emission, String layout, String decimal, String memberPointerStorage, String enumStorage) {
        return parse(emission, layout, decimal, memberPointerStorage, enumStorage, null);
    }
    public static OffsetofModel parse(String emission, String layout, String decimal, String memberPointerStorage, String enumStorage, String bitFieldStorage) {
        if (decimal == null || !decimal.matches("[1-9][0-9]*"))
            throw invalid("supportedObjectBytes must be a positive canonical decimal string");
        return new OffsetofModel(emission, layout, new BigInteger(decimal), memberPointerStorage, enumStorage, bitFieldStorage);
    }
    public Map<String,String> settings() {
        var result = new LinkedHashMap<String,String>();
        result.put("emission", emission); result.put("layout", layout);
        result.put("supportedObjectBytes", supportedObjectBytes.toString());
        // Absence preserves canonical settings and identity of existing profiles.
        if (memberPointerStorage != null) result.put("memberPointerStorage", memberPointerStorage);
        if (enumStorage != null) result.put("enumStorage", enumStorage);
        if (bitFieldStorage != null) result.put("bitFieldStorage", bitFieldStorage);
        return Collections.unmodifiableMap(result);
    }
    void validate(PlatformProfile profile) {
        if (profile.bitsPerByte() != 8 || !profile.sizeType().equals("unsigned long")
                || !profile.aggregateLayout().equals("sequential") || !profile.fieldAlignment().equals("natural")
                || !profile.aggregateAlignment().equals("maximum-field") || !profile.tailPadding().equals("alignment"))
            throw invalid("requires 8-bit bytes, sizeType=unsigned long and sequential/natural/maximum-field/alignment layout policies");
        var widths = Map.ofEntries(Map.entry("bool",8), Map.entry("char",8), Map.entry("short",16),
                Map.entry("int",32), Map.entry("long",64), Map.entry("wchar_t",32), Map.entry("float",32),
                Map.entry("double",64), Map.entry("long double",128), Map.entry("pointer",64));
        for (var entry : widths.entrySet())
            if (profile.sizeBits(entry.getKey()).orElse(-1) != entry.getValue()
                    || profile.alignmentBits(entry.getKey()).orElse(-1) != entry.getValue())
                throw invalid("requires " + entry.getKey() + " storage and alignment of " + entry.getValue() + " bits");
    }
    public String replacement() { return "(__builtin_offsetof(type, member))"; }
    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Invalid offsetof: " + detail);
    }
}
