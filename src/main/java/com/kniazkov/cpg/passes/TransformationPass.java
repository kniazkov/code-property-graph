/* Extension point for batched transformations. MIT License. */
package com.kniazkov.cpg.passes;

import com.kniazkov.cpg.model.ProjectEditor;
import java.util.Set;

/** Stages edits against the prepared snapshot. The runner builds exactly one final batch
 * after the callback returns, then records successful execution. Never mutate an input node. */
public interface TransformationPass {
    PassId id();
    /** Declare every producer whose derived values or diagnostics this pass reads. */
    default Set<PassId> requires() { return Set.of(); }
    void transform(PassContext context, ProjectEditor editor);
}
