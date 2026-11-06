/* Built-in C++03 declaration availability and transient candidate construction. MIT License. */
package com.kniazkov.cpg.analysis;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.parser.Parser;
import java.util.*;

/** The catalogue is data outside Project; only the demanded slice is published by NameResolver. */
public final class LibraryDeclarations {
    private LibraryDeclarations() {}
    /** Immutable parsed inventory stays outside every published Project. */
    private static final Map<String,SourceFile> CATALOGUE = Collections.synchronizedMap(new LinkedHashMap<>(64,0.75f,true) {
        @Override protected boolean removeEldestEntry(Map.Entry<String,SourceFile> entry) { return size()>64; }
    });
    public static boolean isLibrary(Node node) { return node.provenance().map(p->p.unit().libraryCatalogue()).orElse(false); }
    private static PlatformProfile profile(SourceFile source) {
        return source.provenance().map(p->p.unit().configuration()).map(c->c.platform()).orElseGet(PlatformProfile::defaults);
    }
    public static String headerName(SourceFile source) {
        String path=source.path(); return path.substring(path.lastIndexOf('/')+1).replaceFirst("\\.hpp$", "");
    }
    private static Set<String> headers(SourceFile source) {
        var result=new LinkedHashSet<String>(); var visited=new HashSet<String>();
        source.walk(new NodeVisitor(){@Override public void visit(IncludeDirective include){
            if(include.system() && StandardLibraryCatalogue.headers().contains(include.header())) add(include.header());
        }
        void add(String header){if(visited.add(header)) { StandardLibraryCatalogue.dependencies(header).stream().sorted().forEach(this::add); result.add(header); }}
        });
        return result;
    }
    static Set<String> activatedHeaders(String header) {
        var result=new LinkedHashSet<String>();
        java.util.function.Consumer<String> add=new java.util.function.Consumer<>() {
            @Override public void accept(String h) { if(result.add(h)) StandardLibraryCatalogue.dependencies(h).forEach(this); }
        };
        if(StandardLibraryCatalogue.headers().contains(header)) add.accept(header);
        return result;
    }
    public static boolean visible(SourceFile source, SourceFile library) {
        return isLibrary(library) && profile(source).equals(profile(library)) && headers(source).contains(headerName(library));
    }
    /** Candidate graphs are temporary and never escape the resolver publication boundary. */
    public static Project install(Project previous) {
        Project result=LibraryGraph.materialize(candidates(previous));
        if (previous.librarySources().size()==result.librarySources().size()
                && previous.librarySources().stream().allMatch(f->f.items().isEmpty())) {
            var oldHeaders=previous.librarySources().stream().map(f->f.provenance().orElseThrow().unit().translationUnit()).toList();
            var newHeaders=result.librarySources().stream().map(f->f.provenance().orElseThrow().unit().translationUnit()).toList();
            boolean[] linked={true};
            previous.files().forEach(f->f.walk(new NodeVisitor(){@Override public void visit(IncludeDirective i){
                if(i.system() && StandardLibraryCatalogue.headers().contains(i.header()) && i.libraryHeader().isEmpty()) linked[0]=false;
            }}));
            if(oldHeaders.equals(newHeaders) && linked[0]) return previous.recordPass("LibraryDeclarations.install");
        }
        return result.recordPass("LibraryDeclarations.install");
    }
    static Project candidates(Project previous) {
        Project project=LibraryGraph.reset(previous);
        var required=new LinkedHashMap<String,SourceFile>();
        for(SourceFile source:project.files()) for(String header:headers(source))
            required.putIfAbsent(profile(source).identityHash()+"/"+header,source);
        if(required.isEmpty()) return project;
        var catalogues=new ArrayList<SourceFile.Builder>();
        for(var entry:required.entrySet()) {
            String header=entry.getKey().substring(entry.getKey().indexOf('/')+1);
            String dependencies=StandardLibraryCatalogue.dependencies(header).stream().sorted().map(h->"#include <"+h+">\n").reduce("",String::concat);
            String declarations=dependencies+StandardLibraryCatalogue.declarations(header,profile(entry.getValue()));
            var path=java.nio.file.Path.of("/__cpg_library__").resolve(entry.getKey()+".hpp");
            var unit=new com.kniazkov.cpg.build.Build.TranslationUnit(new com.kniazkov.cpg.build.Build.PhysicalSource(path),path.getParent(),List.of(),List.of(),"c++03")
                    .withPlatform(profile(entry.getValue()));
            SourceFile catalogue=CATALOGUE.computeIfAbsent(entry.getKey(),ignored->Parser.parseLibrary(unit,declarations).build());
            catalogues.add(catalogue.edit());
        }
        return LibraryGraph.linkIncludes(project.edit().setLibrarySources(catalogues).build(),LibraryDeclarations::visible,LibraryDeclarations::headerName);
    }
}
