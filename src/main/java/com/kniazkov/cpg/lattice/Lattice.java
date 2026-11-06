/* Public language-independent lattice operations. MIT License. */
package com.kniazkov.cpg.lattice;

/** A partially ordered domain of immutable analysis values.
 *
 * <p>{@link #lessOrEqual} is reflexive, antisymmetric and transitive. {@link #join}
 * returns the least upper bound and {@link #meet} the greatest lower bound.
 * {@link #bottom} is below every element; {@link #top} is above every element.
 * Operations must not mutate their arguments. Implementations define the domain's
 * meaning and reject null arguments; clients need no common factory interface.</p>
 *
 * @param <E> the immutable element type of this domain
 */
public interface Lattice<E extends LatticeElement> {
    /** Whether {@code left} is below or equal to {@code right} in this domain. */
    boolean lessOrEqual(E left, E right);

    /** The least value greater than or equal to both arguments. */
    E join(E left, E right);

    /** The greatest value less than or equal to both arguments. */
    E meet(E left, E right);

    /** The least element; publishing this value is not an absent analysis result. */
    E bottom();

    /** The greatest element; publishing this value is not an absent analysis result. */
    E top();
}
