/* Snapshot-local demand closure for standard-library declarations. MIT License. */
package com.kniazkov.cpg.model;
import java.util.*;

/** Internal publication boundary: candidate ownership never constitutes a library demand. */
public final class LibraryGraph {
    private LibraryGraph() {}
    /** Converts trusted catalogue-only placeholders, never ordinary user-written identifiers. */
    public static SourceFile.Builder resolveOpaqueTypes(SourceFile source) {
        var replacements=new IdentityHashMap<Node,Node>();
        java.util.function.BiConsumer<Node,String> inspect=new java.util.function.BiConsumer<>() {
            @Override public void accept(Node n,String owner) {
                if(n instanceof NamespaceDeclaration namespace) owner=owner+"::"+namespace.name();
                if(n instanceof ClassDeclaration declaration) owner=owner+"::"+declaration.name();
                if(n instanceof NamedType type && type.name().startsWith("__cpg_opaque_")) {
                    String[] parts=type.name().substring("__cpg_opaque_".length()).split("_",2);
                    String identity=(owner.isEmpty() || owner.equals("::std") ? "library" : owner)+"::"+parts[1];
                    var builder=OpaqueLibraryType.create().setName(parts[1]).setIdentity(identity)
                            .setCategory(OpaqueLibraryType.Category.valueOf(parts[0].toUpperCase(java.util.Locale.ROOT)));
                    type.range().ifPresent(builder::setRange); type.provenance().ifPresent(builder::setProvenance);
                    replacements.put(type,builder.build());
                }
                for(Node child:n.children()) accept(child,owner);
            }
        };
        inspect.accept(source,"");
        var context=new EditContext(replacements,Set.of());
        var root=context.edit(source,SourceFile.Builder.class);
        // Parser lookahead can leave detached typedef probes in nested template-ids.
        // Catalogue name binding is recomputed in its complete dependency context.
        source.walk(new NodeVisitor(){@Override public void visit(NamedType type){
            context.edit(type,NamedType.Builder.class).clearAlias().clearDeclaration().clearResolutionSteps();
        }});
        return root;
    }
    /** Re-analysis starts from written syntax, not the previous snapshot's derived library slice. */
    public static Project reset(Project project) {
        if (project.librarySources().isEmpty()) return project;
        var context = new EditContext();
        var root = context.edit(project, Project.Builder.class);
        root.clearTemplateInstances().setLibrarySources(List.of());
        for (SourceFile source : project.files()) source.walk(new NodeVisitor() {
            @Override public void visit(Node n) {
                var b = context.copy(n);
                if (n instanceof Declaration) { b.clearDefinition(); b.clearPreviousDeclaration(); b.templateOrigin=null; }
                if (n instanceof Expression) { b.operatorCandidates=List.of(); b.operatorResolution=CallExpression.Resolution.UNRESOLVED; b.clearExpressionInfo(); }
                if (n instanceof VariableDeclaration v) context.edit(v,VariableDeclaration.Builder.class).setLifetimeTargets(null,null);
                if (n instanceof IncludeDirective i) context.edit(i,IncludeDirective.Builder.class).clearLibraryHeader();
                if (n instanceof NamedType t) context.edit(t,NamedType.Builder.class).clearDeclaration().clearAlias().clearResolutionSteps();
                if (n instanceof NameExpression e) context.edit(e,NameExpression.Builder.class).clearTarget();
                if (n instanceof MemberExpression e) ((MemberExpression.Builder<?,?>)b).clearTarget();
                if (n instanceof CallExpression e) context.edit(e,CallExpression.Builder.class).clearTarget().clearVirtualTargets().setDefaultArguments(List.of());
                if (n instanceof MemberInitializer e) context.edit(e,MemberInitializer.Builder.class).clearTarget();
                if (n instanceof UsingDirective e) context.edit(e,UsingDirective.Builder.class).clearTarget();
                if (n instanceof UsingDeclaration e) context.edit(e,UsingDeclaration.Builder.class).clearTarget();
                if (n instanceof NamespaceAliasDeclaration e) context.edit(e,NamespaceAliasDeclaration.Builder.class).clearTarget();
            }
        });
        return root.build();
    }
    /** Connect active include directives after candidate headers have been installed. */
    public static Project linkIncludes(Project project, java.util.function.BiPredicate<SourceFile,SourceFile> visible,
            java.util.function.Function<SourceFile,String> headerName) {
        var context=new EditContext(); var root=context.edit(project,Project.Builder.class);
        for(SourceFile source:project.files()) source.walk(new NodeVisitor(){
            @Override public void visit(IncludeDirective include) {
                var b=context.edit(include,IncludeDirective.Builder.class).clearLibraryHeader();
                if(include.system()) for(SourceFile header:project.librarySources())
                    if(include.header().equals(headerName.apply(header)) && visible.test(source,header))
                        b.setLibraryHeader(context.edge(header,SourceFile.class));
            }
        });
        return root.build();
    }
    private static Project bindLifetimes(Project project) {
        var context=new EditContext(); var root=context.edit(project,Project.Builder.class);
        java.util.function.BiConsumer<Node,SourceFile> inspect=(tree,source)->tree.walk(new NodeVisitor(){
            @Override public void visit(VariableDeclaration variable) {
                if(!variable.isDefinition() || variable.type().isEmpty()) return;
                TypeNode type=variable.type().get().canonicalType();
                while(type instanceof QualifiedType q) type=q.baseType().canonicalType();
                if(type instanceof PointerType || type instanceof ReferenceType) return;
                ClassDeclaration owner=com.kniazkov.cpg.analysis.ClassSemantics.objectClass(type).orElse(null);
                if(owner==null || !com.kniazkov.cpg.analysis.LibraryDeclarations.isLibrary(owner)) return;
                var initialization=com.kniazkov.cpg.analysis.ClassSemantics.initialization(project,variable);
                // Constructor templates participate only after deduction; unresolved
                // template parameters must not poison an exact ordinary overload.
                var candidates=com.kniazkov.cpg.analysis.ClassSemantics.constructorCandidates(initialization).stream()
                        .filter(candidate->candidate.templateParameters().isEmpty()).toList();
                Expression value=variable.initializer().orElse(null);
                List<Expression> arguments=value==null ? List.of() : value instanceof InitializationExpression i && i.form()==InitializationExpression.Form.DIRECT ? i.arguments() : List.of(value);
                var constructor=com.kniazkov.cpg.analysis.OverloadResolution.select(candidates,arguments,com.kniazkov.cpg.analysis.TypeSemantics.profile(source)).selected().orElse(null);
                for(var instance:project.templateInstances())if(instance.requests().contains(variable) && instance.origin().kind()==CallableDeclaration.Kind.CONSTRUCTOR)constructor=instance.declaration();
                var destructor=owner.members().stream().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast)
                        .filter(c->c.kind()==CallableDeclaration.Kind.DESTRUCTOR).findFirst().orElse(null);
                context.edit(variable,VariableDeclaration.Builder.class).setLifetimeTargets(constructor==null?null:context.edge(constructor,CallableDeclaration.class),
                        destructor==null?null:context.edge(destructor,CallableDeclaration.class));
            }
        });
        project.files().forEach(f->inspect.accept(f,f));
        project.templateInstances().forEach(i->inspect.accept(i,i.source()));
        project.classTemplateInstances().forEach(i->{if(!com.kniazkov.cpg.analysis.LibraryDeclarations.isLibrary(i.origin()))inspect.accept(i,i.source());});
        var build=new BuildContext(); Project result=build.build(root);
        project.walk(new NodeVisitor(){@Override public void visit(Node n){if(!n.intrinsicDiagnostics().isEmpty())build.copy(context.copy(n)).findings=n.intrinsicDiagnostics();}});
        return result;
    }
    public static Project materialize(Project project) {
        if(project.librarySources().isEmpty()) return project;
        project=bindLifetimes(project);
        var parents=new IdentityHashMap<Node,Node>();
        var all=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        var library=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        project.walk(new NodeVisitor(){@Override public void visit(Node n){all.add(n);n.children().forEach(c->parents.put(c,n));}});
        project.librarySources().forEach(f->f.walk(new NodeVisitor(){@Override public void visit(Node n){library.add(n);}}));
        for(var instance:project.classTemplateInstances()) if(com.kniazkov.cpg.analysis.LibraryDeclarations.isLibrary(instance.origin()))
            instance.walk(new NodeVisitor(){@Override public void visit(Node n){library.add(n);}});
        for(var instance:project.templateInstances()) if(com.kniazkov.cpg.analysis.LibraryDeclarations.isLibrary(instance.origin()))
            instance.walk(new NodeVisitor(){@Override public void visit(Node n){library.add(n);}});
        // An external object demand retains every compatible header occurrence,
        // including forward peers not reachable through previousDeclaration.
        var objectPeers=new IdentityHashMap<Node,List<VariableDeclaration>>();
        for (var group:com.kniazkov.cpg.analysis.LibraryExternalObjects.groups(project).values())
            for (var declaration:group) objectPeers.put(declaration,group);
        var keep=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        var pending=new ArrayDeque<Node>();
        java.util.function.Consumer<Node> demand=n->{if(n!=null && keep.add(n))pending.add(n);};
        // User-written source and non-library analysis are retained in their entirety.
        for(Node n:all) if(!library.contains(n) && !(n instanceof StandardLibrary) && n!=project) demand.accept(n);
        while(!pending.isEmpty()) {
            Node n=pending.remove();
            objectPeers.getOrDefault(n,List.of()).forEach(demand);
            Node owner=parents.get(n); if(owner!=null && owner!=project) demand.accept(owner);
            if(n instanceof ClassTemplateInstance i) { demand.accept(i.origin()); demand.accept(i.selected()); demand.accept(i.source()); }
            else if(n instanceof TemplateInstance i) { demand.accept(i.origin()); demand.accept(i.source()); }
            else n.references().forEach(demand);
            n.commentReferences().forEach(demand);
            for(Node child:n.children()) {
                // Ownership of a catalogue namespace/header/class does not use its declarations.
                if(library.contains(n) && (n instanceof SourceFile || n instanceof NamespaceDeclaration)) continue;
                if(library.contains(n) && n instanceof ClassDeclaration c && c.members().contains(child)) continue;
                if(n instanceof StandardLibrary) continue;
                demand.accept(child);
            }
        }
        var removed=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());
        for(Node n:library) if(!keep.contains(n)) removed.add(n);
        var context=new EditContext(Map.of(),removed);
        var root=context.edit(project,Project.Builder.class);
        // Catalogue token streams contain unused declarations too. Publish identity/profile
        // provenance only; virtual catalogue ranges remain useful without retaining its text.
        var provenance=new IdentityHashMap<Preprocessing,Preprocessing>();
        for(Node n:keep) if(com.kniazkov.cpg.analysis.LibraryDeclarations.isLibrary(n)) {
            Preprocessing original=n.provenance().orElseThrow().unit();
            Preprocessing compact=provenance.computeIfAbsent(original,u->new Preprocessing(u.translationUnit(),List.of(),List.of(),List.of(),Map.of(),u.configuration()).asLibraryCatalogue());
            context.copy(n).setProvenance(new TokenProvenance(compact,0,0));
        }
        for(var instance:project.classTemplateInstances()) if(keep.contains(instance))
            context.edit(instance,ClassTemplateInstance.Builder.class).requests=context.copyAll(instance.requests().stream().filter(keep::contains).toList(),Node.class);
        for(var instance:project.templateInstances()) if(keep.contains(instance))
            context.edit(instance,TemplateInstance.Builder.class).requests=context.copyAll(instance.requests().stream().filter(keep::contains).toList(),Node.class);
        var build=new BuildContext(); Project result=build.build(root);
        for(Node n:all) if(keep.contains(n) && !n.intrinsicDiagnostics().isEmpty()) build.copy(context.copy(n)).findings=n.intrinsicDiagnostics();
        return result;
    }
}
