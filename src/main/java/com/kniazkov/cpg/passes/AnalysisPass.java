/* Extension point for immutable analysis. MIT License. */
package com.kniazkov.cpg.passes;

import java.util.Set;

/** An analysis reads its prepared snapshot and stages a complete result set. A successful
 * rerun replaces this producer's values and diagnostics only in the requested scope.
 * An exception publishes neither staged products nor a completion record. */
public interface AnalysisPass {
    PassId id();
    /** Declare every producer whose derived values or diagnostics this pass reads.
     * Changes in undeclared producers or external mutable state cannot be tracked. */
    default Set<PassId> requires() { return Set.of(); }
    void analyze(PassContext context, AnalysisResults results);
}
