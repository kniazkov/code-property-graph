/* Typed CPG model: package overview. MIT License. */
/**
 * Proposed typed model alongside the original prototype.
 * Each concrete node owns a nested builder; private assembly contexts preserve
 * cycles and shared reference targets without exposing unfinished snapshots.
 * Syntax children form trees, while references may form cycles.
 * Builders are not thread-safe. Completed snapshots require safe publication
 * when passed between threads. Parsing and source emission are not wired here yet.
 */
package com.kniazkov.cpg.model;
