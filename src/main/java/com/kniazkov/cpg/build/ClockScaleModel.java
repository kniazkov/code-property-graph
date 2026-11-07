/* Explicit target clock_t and CLOCKS_PER_SEC facts. MIT License. */
package com.kniazkov.cpg.build;

import java.math.BigInteger;
import java.util.*;

/** Bounded integral target facts; no clock runtime or timing model is implied. */
public record ClockScaleModel(String clockType, String expressionType, BigInteger value) {
    public ClockScaleModel {
        var types = Set.of("int", "unsigned int", "long", "unsigned long");
        if (!types.contains(clockType) || !types.contains(expressionType))
            throw invalid("clockType and expressionType must be int, unsigned int, long or unsigned long");
        if (value == null || value.signum() <= 0) throw invalid("value must be positive");
    }
    public static ClockScaleModel parse(String clockType, String expressionType, String decimal) {
        if (decimal == null || !decimal.matches("[1-9][0-9]*"))
            throw invalid("value must be a positive decimal integer string");
        return new ClockScaleModel(clockType, expressionType, new BigInteger(decimal));
    }
    public Map<String,String> settings() {
        var result = new LinkedHashMap<String,String>();
        result.put("clockType", clockType); result.put("expressionType", expressionType); result.put("value", value.toString());
        return Collections.unmodifiableMap(result);
    }
    void validate(PlatformProfile profile) {
        if (!profile.integerRange(expressionType).orElseThrow().contains(value))
            throw invalid("value is outside target expressionType range");
    }
    /** Exact chosen expression type, with no cast that would prevent preprocessing. */
    public String replacement() {
        return value + switch(expressionType) {
            case "unsigned int" -> "U"; case "long" -> "L"; case "unsigned long" -> "UL"; default -> "";
        };
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("Invalid clockScale: " + message); }
}
