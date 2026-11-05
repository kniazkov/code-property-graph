/* Immutable target-dependent type facts, separate from written syntax. MIT License. */
package com.kniazkov.cpg.model;

import com.kniazkov.cpg.build.PlatformProfile;
import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/** Green type facts. Sizes use target bytes, never an assumed eight-bit host byte.
 * Empty facts are deliberately unknown (incomplete, unsupported, or ABI-dependent).
 * Canonical identity retains language type distinctions even when widths coincide. */
public record TypeRepresentation(String canonicalIdentity, String profileIdentity,
        Optional<BigInteger> sizeBits, Optional<BigInteger> sizeBytes,
        OptionalInt alignmentBits, Optional<PlatformProfile.IntegerRange> integerRange) {
    public TypeRepresentation {
        Objects.requireNonNull(canonicalIdentity);
        Objects.requireNonNull(profileIdentity);
        Objects.requireNonNull(sizeBits);
        Objects.requireNonNull(sizeBytes);
        Objects.requireNonNull(alignmentBits);
        Objects.requireNonNull(integerRange);
    }
}
