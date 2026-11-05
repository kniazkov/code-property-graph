/* A non-owning edge in lookup of a written type name. MIT License. */
package com.kniazkov.cpg.model;
import java.util.Objects;
/** One lookup step; shared using directives remain one declaration node. */
public record TypeResolutionStep(String kind, Declaration via, Declaration target) {
    public TypeResolutionStep { Objects.requireNonNull(kind); Objects.requireNonNull(via); Objects.requireNonNull(target); }
}
