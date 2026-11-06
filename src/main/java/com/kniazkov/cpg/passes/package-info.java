/* Public extension points for project passes. MIT License. */
/** Immutable custom analysis results and batched transformations.
 * Register an {@link com.kniazkov.cpg.passes.AnalysisPass} or
 * {@link com.kniazkov.cpg.passes.TransformationPass} with a
 * {@link com.kniazkov.cpg.passes.PassRunner}, then request its stable
 * {@link com.kniazkov.cpg.passes.PassId}. Requirements are prepared in sorted dependency
 * order, and only successful callbacks receive completion history. A requested analysis
 * always reruns, replacing its complete producer-owned result set in the selected scope.
 * Other values of that producer outside the scope are retained. Rerunning a prerequisite
 * invalidates all transitive declared dependents, even outside that scope, since callbacks
 * can read the whole project. A syntax or binding edit invalidates all custom results.
 * Covering every function with scoped custom invocations does not execute or make current
 * the callback's whole-project branch; a project-level dependency runs that branch too.
 *
 * <p>Typed values must be immutable. Register custom one-way JSON converters through
 * {@link com.kniazkov.cpg.passes.ResultTypes}; conversion and validation happen before
 * publication, never during later export. The original snapshot is unchanged on failure.
 * Scoped transformation callbacks may edit their callable but cannot remove it or replace
 * it with a different lineage. Read/reference targets may be anywhere in the project.</p>
 */
package com.kniazkov.cpg.passes;
