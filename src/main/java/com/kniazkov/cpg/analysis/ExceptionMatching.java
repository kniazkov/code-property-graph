/* Shared C++ exception handler/specification matching. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.model.*;
import java.util.*;
/** Matching by identity, pointer qualification and public unambiguous bases. */
final class ExceptionMatching {
    private ExceptionMatching() {}
    private static String plain(String type) {
        if (type == null) return null;
        while (type.startsWith("reference(") || type.startsWith("const(") || type.startsWith("volatile(")) type = type.substring(type.indexOf('(') + 1, type.length() - 1);
        return type;
    }
    static boolean catches(Project project, String thrown, String caught) {
        thrown = plain(thrown); caught = plain(caught);
        if (Objects.equals(thrown, caught)) return true;
        if (thrown == null || caught == null) return false;
        if (thrown.startsWith("pointer(") && caught.startsWith("pointer(")) {
            thrown = thrown.substring(8, thrown.length() - 1); caught = caught.substring(8, caught.length() - 1);
            if (thrown.startsWith("const(") && !caught.startsWith("const(") || thrown.contains("volatile(") && !caught.contains("volatile(")) return false;
            thrown = plain(thrown); caught = plain(caught);
            if (caught.equals("void") && !thrown.startsWith("function(") || thrown.equals(caught)) return true;
        }
        if (!thrown.startsWith("named(") || !caught.startsWith("named(")) return false;
        final String identity = thrown.substring(6, thrown.length() - 1);
        var classes = new ArrayList<ClassDeclaration>();
        project.walk(new NodeVisitor() { @Override public void visit(ClassDeclaration cls) { if (cls.isDefinition() && cls.typeIdentity().filter(identity::equals).isPresent()) classes.add(cls); }});
        if (classes.size() != 1) return false;
        var paths = new HashMap<String, Boolean>();
        basePaths(classes.getFirst(), caught, "root", true, paths, new HashSet<>());
        return paths.size() == 1 && paths.values().iterator().next();
    }
    private static void basePaths(ClassDeclaration cls, String target, String path, boolean accessible, Map<String, Boolean> paths, Set<ClassDeclaration> active) {
        if (!active.add(cls)) return;
        for (int i = 0; i < cls.bases().size(); i++) {
            var base = cls.bases().get(i); var parent = InheritanceSemantics.baseClass(base).orElse(null); if (parent == null) continue;
            String identity = base.type().canonicalIdentity();
            String key = base.isVirtual() ? "virtual:" + identity : path + "/" + i;
            boolean publicPath = accessible && base.access().equals("public");
            if (identity.equals(target)) paths.merge(key, publicPath, (a, b) -> a || b);
            else basePaths(parent, target, key, publicPath, paths, active);
        }
        active.remove(cls);
    }
}
