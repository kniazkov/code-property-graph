/* Explicit compiler support for HUGE_VAL. MIT License. */
package com.kniazkov.cpg.build;

import java.util.Map;

/** Selected positive-infinity semantics; no library function or floating evaluator. */
public record HugeValModel(String kind, String emission) {
    public HugeValModel {
        if (!"positive_infinity".equals(kind) || !"gcc-clang-builtin-huge-val".equals(emission))
            throw new IllegalArgumentException("Invalid hugeVal: requires positive_infinity and gcc-clang-builtin-huge-val");
    }
    public Map<String,String> settings() {
        var result = new java.util.LinkedHashMap<String,String>();
        result.put("kind", kind); result.put("emission", emission);
        return java.util.Collections.unmodifiableMap(result);
    }
    void validate(PlatformProfile profile) {
        if (!"binary64".equals(profile.floatingFormats().get("double")) || profile.sizeBits("double").orElseThrow() != 64)
            throw new IllegalArgumentException("Invalid hugeVal: selected compiler contract requires double binary64 with 64-bit storage");
    }
    public String replacement() { return "(__builtin_huge_val())"; }
}
