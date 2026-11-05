/* Doxygen identity and source-preserving parameter edits through public paths. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.Documentation;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.utilities.Comments;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class DoxygenParametersAcceptance {
    private DoxygenParametersAcceptance() {}
    record Output(List<Path> sources) {}
    static Output verify(Path fixture,Path work) throws Exception {
        var cli=new CliSandbox(work).directory(fixture.resolve("sources"));
        var options=new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03");
        Project project=Cpg.read(cli.input,options); verify(project); verify(project.edit().build());
        var originalJson=ProjectJson.encode(project).toString(); var original=Cpg.sources(project,Language.CPP);
        for(var source:original.entrySet())assertEquals(Files.readString(cli.input.resolve(source.getKey())),source.getValue());
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--svg","snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("DocumentationEntry"));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("DocumentationEntry"));
        for(var source:original.entrySet())assertEquals(source.getValue(),Files.readString(run.work().resolve("generated").resolve(source.getKey())));
        Project cleaned=Comments.remove(project);
        assertTrue(nodes(cleaned,Comment.class).isEmpty());assertTrue(nodes(cleaned,DocumentationEntry.class).isEmpty());
        for(CallableDeclaration fn:nodes(cleaned,CallableDeclaration.class))assertTrue(Documentation.forDeclaration(cleaned,fn).isEmpty());
        Path clean=write(work.resolve("cleaned"),Cpg.sources(cleaned,Language.CPP));
        var prototype=nodes(project,CallableDeclaration.class).stream().filter(f->f.name().equals("process")&&f.body().isEmpty()).findFirst().orElseThrow();
        var definition=processDefinition(project);
        var headerRename=com.kniazkov.cpg.utilities.Documentation.renameParameter(project,prototype.parameters().getFirst(),"amount");
        var headerSources=Cpg.sources(headerRename,Language.CPP);
        assertEquals(original.get("include/api.h").replace("@param count","@param amount").replace("process(int count)","process(int amount)"),headerSources.get("include/api.h"));
        for(String name:original.keySet())if(!name.equals("include/api.h"))assertEquals(original.get(name),headerSources.get(name));
        assertEquals(List.of("amount"),Documentation.forParameter(headerRename,processDefinition(headerRename).parameters().getFirst()).stream().map(DocumentationEntry::name).toList());
        var bodyRename=com.kniazkov.cpg.utilities.Documentation.renameParameter(project,definition.parameters().getFirst(),"limit");
        var bodySources=Cpg.sources(bodyRename,Language.CPP);
        assertEquals(original.get("impl.cpp").replace("process(int n)","process(int limit)").replace("return n + ZERO;","return limit + ZERO;"),bodySources.get("impl.cpp"));
        for(String name:original.keySet())if(!name.equals("impl.cpp"))assertEquals(original.get(name),bodySources.get(name));
        assertEquals(List.of("count"),Documentation.forParameter(bodyRename,processDefinition(bodyRename).parameters().getFirst()).stream().map(DocumentationEntry::name).toList());
        Path headerOutput=write(work.resolve("header-renamed"),headerSources),bodyOutput=write(work.resolve("body-renamed"),bodySources);
        for(Path path:List.of(run.work().resolve("generated"),clean,headerOutput,bodyOutput)){
            Project reparsed=Cpg.read(path,options);assertFalse(reparsed.hasErrors());
            assertTrue(nodes(reparsed,Node.class).stream().flatMap(n->n.diagnostics().stream()).noneMatch(d->d.severity()==Diagnostic.Severity.ERROR));
        }
        assertEquals(originalJson,ProjectJson.encode(project).toString());assertEquals(original,Cpg.sources(project,Language.CPP));
        return new Output(List.of(cli.input,run.work().resolve("generated"),clean,headerOutput,bodyOutput));
    }
    private static void verify(Project project){
        assertFalse(project.hasErrors());var all=nodes(project,Node.class);
        assertTrue(all.stream().flatMap(n->n.diagnostics().stream()).noneMatch(d->d.severity()==Diagnostic.Severity.ERROR),()->all.stream().flatMap(n->n.diagnostics().stream()).toList().toString());
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());owned.addAll(all);
        for(Node n:all)for(Node ref:n.references())assertTrue(owned.contains(ref));
        var definition=processDefinition(project);var comments=Documentation.commentsForDeclaration(project,definition);
        assertEquals(2,comments.size(),"Header repeated in two TUs must appear once alongside definition comment");
        assertTrue(comments.stream().anyMatch(c->c.text().contains("Header process brief")));assertTrue(comments.stream().anyMatch(c->c.text().contains("Definition process brief")));
        var docs=Documentation.forParameter(project,definition.parameters().getFirst());assertEquals(1,docs.size());
        assertEquals("count",docs.getFirst().name());assertTrue(docs.getFirst().description().contains("word count stays here"));
        assertTrue(docs.getFirst().bindings().stream().allMatch(b->b.target() instanceof ParameterDeclaration p&&p.name().equals("count")&&b.declaration().parameters().contains(p)));
        for(var identity:nodes(project,CallableDeclaration.class).stream().filter(f->f.name().equals("identity")).toList()){
            assertEquals(List.of("T","U"),identity.templateParameters().stream().map(TemplateParameterDeclaration::name).toList());
            var entries=Documentation.forDeclaration(project,identity);
            assertEquals(List.of(DocumentationEntry.Kind.BRIEF,DocumentationEntry.Kind.TPARAM,DocumentationEntry.Kind.TPARAM,DocumentationEntry.Kind.PARAM,DocumentationEntry.Kind.RETURN),entries.stream().map(DocumentationEntry::kind).toList());
            for(var entry:entries)for(var binding:entry.bindings())if(entry.kind()==DocumentationEntry.Kind.TPARAM)
                assertTrue(binding.target() instanceof TemplateParameterDeclaration p&&p.name().equals(entry.name())&&binding.declaration().templateParameters().contains(p));
            assertTrue(Documentation.commentsForDeclaration(project,identity).getFirst().text().contains("@custom Keep this unknown command and count text exactly."));
        }
        for(var entry:nodes(project,DocumentationEntry.class).stream().filter(e->e.name().equals("size")).toList()){
            assertFalse(entry.bindings().isEmpty());assertTrue(entry.bindings().stream().allMatch(b->b.target()==null));
            assertTrue(entry.diagnostics().stream().anyMatch(d->d.severity()==Diagnostic.Severity.WARNING));
            var range=entry.range().orElseThrow();assertEquals(range,entry.nameRange().orElseThrow());
            String physical=project.files().stream().map(f->f.provenance().orElseThrow().unit().sources().get(range.file())).filter(Objects::nonNull).findFirst().orElseThrow();
            assertEquals("size",physical.substring(range.start().offset(),range.end().offset()));
        }
        var lineFunction=nodes(project,CallableDeclaration.class).stream().filter(f->f.name().equals("lineDocs")).findFirst().orElseThrow();
        assertEquals(6,Documentation.commentsForDeclaration(project,lineFunction).size());
        var lineEntries=Documentation.forDeclaration(project,lineFunction);
        assertEquals(List.of(DocumentationEntry.Kind.BRIEF,DocumentationEntry.Kind.PARAM,DocumentationEntry.Kind.RETURN),lineEntries.stream().map(DocumentationEntry::kind).toList());
        assertTrue(lineEntries.get(0).description().contains("Continued brief text."));
        assertTrue(lineEntries.get(1).description().contains("Continued parameter text."));
        var context=nodes(project,Comment.class).stream().filter(c->c.text().contains("A physical comment with two include contexts")).toList();assertEquals(1,context.size());
        var entry=context.getFirst().documentationEntries().stream().filter(e->e.kind()==DocumentationEntry.Kind.PARAM).findFirst().orElseThrow();
        assertEquals(2,entry.bindings().size());var includeIds=new HashSet<Long>();var types=new HashSet<String>();
        for(var binding:entry.bindings()){
            var parameter=assertInstanceOf(ParameterDeclaration.class,binding.target());assertTrue(binding.declaration().parameters().contains(parameter));
            types.add(assertInstanceOf(NamedType.class,parameter.type().orElseThrow()).name());
            var p=binding.declaration().provenance().orElseThrow();includeIds.add(p.unit().tokens().get(p.startToken()).includeId());
        }
        assertEquals(Set.of("int","long"),types);assertEquals(2,includeIds.size());
    }
    private static CallableDeclaration processDefinition(Project p){return nodes(p,CallableDeclaration.class).stream().filter(f->f.name().equals("process")&&f.body().isPresent()).findFirst().orElseThrow();}
    private static Path write(Path root,Map<String,String> sources)throws Exception{Files.createDirectories(root);for(var entry:sources.entrySet()){Path file=root.resolve(entry.getKey());Files.createDirectories(file.getParent());Files.writeString(file,entry.getValue());}return root;}
    private static <T extends Node>List<T> nodes(Node root,Class<T> type){var list=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))list.add(type.cast(n));}});return list;}
}
