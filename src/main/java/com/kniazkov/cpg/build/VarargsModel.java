/* Explicit compiler contract for bounded C++03 varargs. MIT License. */
package com.kniazkov.cpg.build;

import java.util.*;

/** Compiler-owned va_list operations. No va_list size, alignment or register layout is implied. */
public record VarargsModel(String emission, String abi) {
    public VarargsModel {
        if (!"gcc-clang-builtin".equals(emission) || !Set.of("x86_64-sysv-lp64-v1", "x86_64-sysv-lp64-borrowed-v2").contains(abi))
            throw invalid("requires gcc-clang-builtin emission and a selected x86_64-sysv-lp64 ABI");
    }
    public boolean borrowedLists() { return "x86_64-sysv-lp64-borrowed-v2".equals(abi); }
    public Map<String, String> settings() {
        var result = new LinkedHashMap<String, String>();
        result.put("emission", emission); result.put("abi", abi);
        return Collections.unmodifiableMap(result);
    }
    void validate(PlatformProfile profile) {
        if (profile.bitsPerByte() != 8 || !profile.sizeType().equals("unsigned long"))
            throw invalid("requires 8-bit bytes and sizeType=unsigned long");
        var widths = Map.ofEntries(Map.entry("bool", 8), Map.entry("char", 8), Map.entry("short", 16),
                Map.entry("int", 32), Map.entry("long", 64), Map.entry("wchar_t", 32), Map.entry("pointer", 64),
                Map.entry("float", 32), Map.entry("double", 64), Map.entry("long double", 128));
        for (var entry : widths.entrySet())
            if (profile.sizeBits(entry.getKey()).orElse(-1) != entry.getValue()
                    || profile.alignmentBits(entry.getKey()).orElse(-1) != entry.getValue())
                throw invalid("requires " + entry.getKey() + " storage and alignment of " + entry.getValue() + " bits");
        for (var entry : Map.of("float", "binary32", "double", "binary64", "long double", "binary80").entrySet())
            if (!entry.getValue().equals(profile.floatingFormats().get(entry.getKey())))
                throw invalid("requires " + entry.getKey() + " format " + entry.getValue());
    }
    public static boolean isBuiltin(String name) {
        return Set.of("__builtin_va_start", "__builtin_va_arg", "__builtin_va_end").contains(name);
    }
    public static List<String> parameters(String name) {
        return switch (name) {
            case "va_start" -> List.of("ap", "parmN");
            case "va_arg" -> List.of("ap", "type");
            case "va_end" -> List.of("ap");
            default -> throw invalid("unsupported macro " + name);
        };
    }
    public String replacement(String name) {
        return "(__builtin_" + name + "(" + String.join(", ", parameters(name)) + "))";
    }
    private static IllegalArgumentException invalid(String detail) {
        return new IllegalArgumentException("Invalid varargs: " + detail);
    }
}
