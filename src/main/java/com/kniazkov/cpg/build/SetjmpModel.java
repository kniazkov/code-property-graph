/* Explicit glibc nonlocal-jump runtime contract. MIT License. */
package com.kniazkov.cpg.build;
import java.util.Map;
/** Selects glibc's _setjmp runtime entry, not a guessed jmp_buf representation. */
public record SetjmpModel(String emission) {
    public SetjmpModel {
        if (!"gcc-clang-glibc".equals(emission) && !"gcc-clang-glibc-static-v2".equals(emission))
            throw new IllegalArgumentException("Invalid setjmp: requires gcc-clang-glibc or gcc-clang-glibc-static-v2 emission");
    }
    /** Opt-in direct same-translation-unit static storage; no environment lifetime proof. */
    public boolean staticBuffers() { return emission.equals("gcc-clang-glibc-static-v2"); }
    public Map<String,String> settings() { return Map.of("emission", emission); }
    void validate(PlatformProfile profile) {
        if (!profile.cRuntime().equals("glibc"))
            throw new IllegalArgumentException("Invalid setjmp: requires cRuntime=glibc");
    }
    public String replacement() { return "::_setjmp(env)"; }
}
