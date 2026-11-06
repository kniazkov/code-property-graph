/* Immutable language-independent abstract analysis domains. MIT License. */
/** Public lattice operations and immutable, one-way-serializable lattice values.
 * {@link com.kniazkov.cpg.lattice.Lattice} defines order and bounds, while
 * {@link com.kniazkov.cpg.lattice.LatticeElement} lets built-in and user-defined
 * domains participate in typed analysis result export without a concrete-class
 * switch or a JSON importer. {@link com.kniazkov.cpg.lattice.IntegerDomain} is the
 * normalizing factory and lattice for mathematical integer intervals; it contains
 * no source-language semantics. Bottom and top are actual published values, distinct
 * from an absent result.
 */
package com.kniazkov.cpg.lattice;
