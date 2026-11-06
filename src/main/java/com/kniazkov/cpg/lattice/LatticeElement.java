/* Public one-way export contract for immutable lattice values. MIT License. */
package com.kniazkov.cpg.lattice;

import com.kniazkov.json.JsonElement;

/** An immutable value in an analysis domain, including user-defined domains.
 * Implementations must use value equality and must not retain mutable state that
 * callers can change. A domain's factories and operations are separate from its
 * values; implementing this interface does not require an importer or decoder.
 *
 * <p>Custom analysis passes can publish these values without registering their
 * concrete classes. The result registry copies and validates the JSON value before
 * publication, so later project export never invokes this method again.</p>
 */
public interface LatticeElement {
    /** Returns a non-null, one-way JSON representation. The returned tree must not
     * expose mutable state of this value; changing it must not change this element
     * or a future representation. The representation must be deterministic. */
    JsonElement toJson();
}
