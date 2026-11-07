/* Explicit target integer macro facts, never discovered from the host. MIT License. */
package com.kniazkov.cpg.build;

import java.math.BigInteger;
import java.util.*;

/** Bounded int-expression facts for 30 C++03 runtime-library macros. */
public final class RuntimeIntegerMacros {
    private RuntimeIntegerMacros() {}
    private static final Map<String, List<String>> HEADERS = Map.of(
            "cerrno", List.of("EDOM", "ERANGE", "EILSEQ"),
            "clocale", List.of("LC_ALL", "LC_COLLATE", "LC_CTYPE", "LC_MONETARY", "LC_NUMERIC", "LC_TIME"),
            "csignal", List.of("SIGABRT", "SIGFPE", "SIGILL", "SIGINT", "SIGSEGV", "SIGTERM"),
            "cstdio", List.of("_IOFBF", "_IOLBF", "_IONBF", "BUFSIZ", "EOF", "FOPEN_MAX", "FILENAME_MAX", "L_tmpnam", "SEEK_CUR", "SEEK_END", "SEEK_SET", "TMP_MAX"),
            "cstdlib", List.of("EXIT_FAILURE", "EXIT_SUCCESS", "RAND_MAX"));
    public static List<String> namesForHeader(String canonicalHeader) { return HEADERS.getOrDefault(canonicalHeader, List.of()); }
    public static Set<String> names() { var names = new TreeSet<String>(); HEADERS.values().forEach(names::addAll); return Collections.unmodifiableSet(names); }
    /** Only int is supported in this packet; this is not a universal ISO type claim. */
    public record Fact(String type, BigInteger value) {
        public Fact {
            if (!"int".equals(type)) throw invalid("only explicitly typed int facts are supported");
            Objects.requireNonNull(value, "runtime integer value");
        }
        public static Fact parse(String type, String decimal) {
            if (decimal == null || !decimal.matches("-?(0|[1-9][0-9]*)")) throw invalid("value must be a decimal integer string");
            return new Fact(type, new BigInteger(decimal));
        }
        public Map<String, String> settings() { var m = new LinkedHashMap<String,String>(); m.put("type", type); m.put("value", value.toString()); return Collections.unmodifiableMap(m); }
        public String replacement(PlatformProfile profile) {
            if (value.equals(profile.integerRange("int").orElseThrow().min()))
                return "(-" + profile.integerRange("int").orElseThrow().max() + " - 1)";
            return value.signum() < 0 ? "(" + value + ")" : value.toString();
        }
    }
    static Map<String, Fact> validate(Map<String, Fact> input, PlatformProfile profile) {
        var facts = new TreeMap<String,Fact>();
        for (var entry : input.entrySet()) {
            String name = entry.getKey(); Fact fact = Objects.requireNonNull(entry.getValue());
            if (!names().contains(name)) throw invalid("unknown macro " + name);
            if (!profile.integerRange("int").orElseThrow().contains(fact.value())) throw invalid(name + " is outside target int range");
            facts.put(name, fact);
        }
        distinct(facts, namesForHeader("cerrno"));
        for (String name : namesForHeader("cerrno")) if (facts.containsKey(name) && facts.get(name).value().signum() == 0) throw invalid(name + " must be nonzero");
        distinct(facts, namesForHeader("clocale")); distinct(facts, namesForHeader("csignal"));
        for (String name : namesForHeader("csignal")) minimum(facts, name, 1);
        distinct(facts, List.of("_IOFBF", "_IOLBF", "_IONBF")); distinct(facts, List.of("SEEK_CUR", "SEEK_END", "SEEK_SET"));
        if (facts.containsKey("EOF") && facts.get("EOF").value().signum() >= 0) throw invalid("EOF must be negative");
        minimum(facts, "BUFSIZ", 256); minimum(facts, "FOPEN_MAX", 8); minimum(facts, "TMP_MAX", 25); minimum(facts, "RAND_MAX", 32767);
        // Positive capacities are a supported target-fact policy, not proof that
        // the supplied sizes are adequate for a particular filesystem/runtime.
        minimum(facts, "FILENAME_MAX", 1); minimum(facts, "L_tmpnam", 1);
        return Collections.unmodifiableMap(facts);
    }
    private static void minimum(Map<String,Fact> facts, String name, int bound) {
        if (facts.containsKey(name) && facts.get(name).value().compareTo(BigInteger.valueOf(bound)) < 0) throw invalid(name + " must be at least " + bound);
    }
    private static void distinct(Map<String,Fact> facts, List<String> names) {
        var seen = new HashSet<BigInteger>();
        for (String name : names) if (facts.containsKey(name) && !seen.add(facts.get(name).value())) throw invalid("values must be distinct within " + names);
    }
    private static IllegalArgumentException invalid(String message) { return new IllegalArgumentException("Invalid runtimeIntegerMacros: " + message); }
}
