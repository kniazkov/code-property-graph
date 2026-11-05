/* C++03 expression categories. MIT License. */
package com.kniazkov.cpg.model;

/** C++03 has lvalues and rvalues, not the later xvalue/prvalue split. */
public enum ValueCategory { LVALUE, RVALUE, UNKNOWN }
