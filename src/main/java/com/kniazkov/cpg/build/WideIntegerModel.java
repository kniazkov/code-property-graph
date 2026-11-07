/* Explicit target wint_t and WEOF facts. MIT License. */
package com.kniazkov.cpg.build;

import java.math.BigInteger;
import java.util.*;

/** Bounded nonnegative-character envelope, not a universal character-set model. */
public record WideIntegerModel(String wintType, BigInteger weof, String characterDomain) {
    public WideIntegerModel {
        if (!Set.of("int", "unsigned int", "long", "unsigned long").contains(wintType))
            throw invalid("wintType must be int, unsigned int, long or unsigned long");
        Objects.requireNonNull(weof, "weof");
        if (!"nonnegative-wchar".equals(characterDomain))
            throw invalid("only characterDomain=nonnegative-wchar is supported");
    }
    public static WideIntegerModel parse(String type, String decimal, String domain) {
        if (decimal == null || !decimal.matches("-?(0|[1-9][0-9]*)"))
            throw invalid("weof must be a decimal integer string");
        return new WideIntegerModel(type, new BigInteger(decimal), domain);
    }
    public Map<String,String> settings() {
        var result=new LinkedHashMap<String,String>();
        result.put("wintType",wintType); result.put("weof",weof.toString()); result.put("characterDomain",characterDomain);
        return Collections.unmodifiableMap(result);
    }
    void validate(PlatformProfile profile) {
        var range=profile.integerRange(wintType).orElseThrow();
        var wchar=profile.integerRange("wchar_t").orElseThrow();
        if (range.max().compareTo(wchar.max())<0)
            throw invalid("wintType cannot represent the full nonnegative wchar_t envelope");
        if (!range.contains(weof)) throw invalid("weof is outside target wintType range");
        if (weof.signum()>=0 && weof.compareTo(wchar.max())<=0)
            throw invalid("weof overlaps the selected nonnegative-wchar character envelope");
    }
    /** Exact expression type and preprocessor compatibility without casts. */
    public String replacement(PlatformProfile profile) {
        String suffix=switch(wintType) {case "unsigned int" -> "U"; case "long" -> "L"; case "unsigned long" -> "UL"; default -> "";};
        var range=profile.integerRange(wintType).orElseThrow();
        if (weof.signum()<0 && weof.equals(range.min()))
            return "(-"+range.max()+suffix+" - 1"+suffix+")";
        return weof.signum()<0 ? "(-"+weof.negate()+suffix+")" : weof+suffix;
    }
    private static IllegalArgumentException invalid(String message) {return new IllegalArgumentException("Invalid wideInteger: "+message);}
}
