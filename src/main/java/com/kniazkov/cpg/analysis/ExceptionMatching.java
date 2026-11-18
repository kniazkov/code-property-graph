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
            if (qualifiedPointers(thrown, caught)) return true;
            thrown = thrown.substring(8, thrown.length() - 1); caught = caught.substring(8, caught.length() - 1);
            if ((qualifiers(thrown) & ~qualifiers(caught)) != 0) return false;
            thrown = plain(thrown); caught = plain(caught);
            // Base/void conversions apply only at the first indirection level.
            if (caught.equals("void") && !thrown.startsWith("function(")) return true;

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
    private static int qualifiers(String type) {
        int result = 0;
        while (type.startsWith("const(") || type.startsWith("volatile(")) {
            result |= type.startsWith("const(") ? 1 : 2;
            type = type.substring(type.indexOf('(') + 1, type.length() - 1);
        }
        return result;
    }
    /** Added deep qualifiers require const at every intervening destination level. */
    private static boolean qualifiedPointers(String source, String destination) {
        boolean interveningConst = true;
        while (source.startsWith("pointer(") && destination.startsWith("pointer(")) {
            source = source.substring(8, source.length() - 1);
            destination = destination.substring(8, destination.length() - 1);
            int from = qualifiers(source), to = qualifiers(destination);
            if ((from & ~to) != 0 || from != to && !interveningConst) return false;
            interveningConst &= (to & 1) != 0;
            source = plain(source); destination = plain(destination);
        }
        return source.equals(destination);
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
