package com.kniazkov.cpg.build;

/** Effective language edition. Only ISO C++03 is currently implemented. */
public enum CppStandard {
    CPP03;
    public static CppStandard require(String edition, String file) {
        if (edition == null || edition.equals("c++03")) return CPP03;
        throw new IllegalArgumentException(file + ": unsupported C++ standard " + edition + " (supported: c++03)");
    }
}
