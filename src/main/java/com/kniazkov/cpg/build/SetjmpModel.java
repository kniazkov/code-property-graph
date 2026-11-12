/* Explicit glibc nonlocal-jump runtime contract. MIT License. */
package com.kniazkov.cpg.build;
import java.util.Map;
/** Selects glibc's _setjmp runtime entry, not a guessed jmp_buf representation. */
public record SetjmpModel(String emission) {
    public SetjmpModel {
        if (!"gcc-clang-glibc".equals(emission))
            throw new IllegalArgumentException("Invalid setjmp: requires gcc-clang-glibc emission");
    }
    public Map<String,String> settings() { return Map.of("emission", emission); }
    void validate(PlatformProfile profile) {
        if (!profile.cRuntime().equals("glibc"))
            throw new IllegalArgumentException("Invalid setjmp: requires cRuntime=glibc");
    }
    public String replacement() { return "::_setjmp(env)"; }
}
