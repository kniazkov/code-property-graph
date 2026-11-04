/* Typed CPG model: package overview. MIT License. */
/**
 * Typed program representation shared by the parser, transformations and emitters.
 * Each concrete node owns a nested builder; private assembly contexts preserve
 * cycles and shared reference targets without exposing unfinished snapshots.
 * Syntax children form trees, while references may form cycles.
 * Builders are not thread-safe. Completed snapshots require safe publication
 * when passed between threads.
 */
package com.kniazkov.cpg.model;
