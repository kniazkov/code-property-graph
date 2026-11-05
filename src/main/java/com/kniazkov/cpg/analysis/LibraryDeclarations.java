/* Code Property Graph: deliberately limited C++03 library declaration catalogue. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;

/** Declaration-only prerequisite for basic vector template lookup, not a library implementation.
 * No container layout, allocator behavior, effects or full overload set is asserted. */
public final class LibraryDeclarations {
    private LibraryDeclarations() {}
    private static final String PREFIX = "__cpg_library_vector_";

    public static boolean isLibrary(Node node) {
        return node.provenance().map(p -> p.unit().libraryCatalogue()).orElse(false);
    }

    private static PlatformProfile profile(SourceFile source) {
        return source.provenance().map(p -> p.unit().configuration()).map(c -> c.platform())
                .orElseGet(PlatformProfile::defaults);
    }

    private static String path(SourceFile source) { return PREFIX + profile(source).identityHash() + ".hpp"; }

    private static boolean includesVector(SourceFile source) {
        var found = new boolean[1];
        source.walk(new NodeVisitor() {
            @Override public void visit(IncludeDirective include) {
                if (include.system() && include.header().equals("vector")) found[0] = true;
            }
        });
        return found[0];
    }

    /** Header availability is scoped to the including translation unit and target profile. */
    public static boolean visible(SourceFile source, SourceFile library) {
        return isLibrary(library) && includesVector(source) && library.path().equals(path(source));
    }

    /** Installs one catalogue per required platform profile, never as a physical source file. */
    public static Project install(Project project) {
        var required = new LinkedHashMap<String, SourceFile>();
        for (SourceFile source : project.files()) if (includesVector(source)) required.putIfAbsent(path(source), source);
        if (required.keySet().equals(new LinkedHashSet<>(project.librarySources().stream().map(SourceFile::path).toList()))) return project;
        var builder = project.edit();
        var catalogues = new ArrayList<SourceFile.Builder>();
        for (var entry : required.entrySet()) {
            String declarations = "namespace std {\n"
                    + "template<class T> class allocator;\n"
                    + "template<class T, class Allocator = allocator<T> > class vector { public:\n"
                    + "typedef " + profile(entry.getValue()).sizeType() + " size_type;\n"
                    + "explicit vector(const Allocator& alloc = Allocator());\n"
                    + "explicit vector(size_type n, const T& value = T(), const Allocator& alloc = Allocator());\n"
                    + "size_type size() const;\n};\n}\n";
            // A virtual absolute path makes all ranges, configuration identities and token
            // provenance independent of the API/CLI process working directory. It is
            // an identifier only: declaration text is supplied directly, never read here.
            var path = java.nio.file.Path.of("/__cpg_library__").resolve(entry.getKey());
            var unit = new com.kniazkov.cpg.build.Build.TranslationUnit(
                    new com.kniazkov.cpg.build.Build.PhysicalSource(path), path.getParent(), List.of(), List.of(), "c++03")
                    .withPlatform(profile(entry.getValue()));
            catalogues.add(Parser.parseLibrary(unit, declarations));
        }
        return builder.setLibrarySources(catalogues).build();
    }
}
