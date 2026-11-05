/* A header is shared, while concrete library members are demand-specific. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.*;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class LazyLibraryAcceptance {
    private LazyLibraryAcceptance() {}
    static List<Path> verify(Path fixture,Path work)throws Exception {
        var cli=new CliSandbox(work).directory(fixture.resolve("sources"));
        var options=new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03");
        var project=Cpg.read(cli.input,options);verifyGraph(project);verifyGraph(project.edit().build());
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--svg","snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json=Files.readString(run.work().resolve("snapshot.json"));
        for(String key:List.of("standardLibrary","LIBRARY_HEADER","TEMPLATE_ORIGIN","templateOriginId","CONSTRUCTOR_TARGET","DESTRUCTOR_TARGET","constructorTargetId","destructorTargetId"))assertTrue(json.contains(key),key);
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        assertFalse(json.contains("reserve"),"Unused declarations must not leak through catalogue token/source provenance");
        var sources=Cpg.sources(project,Language.CPP);assertEquals(Set.of("main.cpp","other.cpp"),sources.keySet());
        for(var entry:sources.entrySet())assertEquals(entry.getValue(),Files.readString(run.work().resolve("generated").resolve(entry.getKey())));
        verifyGraph(Cpg.read(run.work().resolve("generated"),options));
        var folded=cli.run("cpp","--stage","folded","--cpp-standard","c++03").expectStatus(0);verifyGraph(Cpg.read(folded.work().resolve("generated"),options));
        var main=project.files().stream().flatMap(f->nodes(f,CallableDeclaration.class).stream()).filter(f->f.name().equals("main")).findFirst().orElseThrow();
        var push=nodes(main,CallExpression.class).stream().filter(c->c.target().map(t->t.name().equals("push_back")).orElse(false)).findFirst().orElseThrow();
        var size=nodes(main,CallExpression.class).stream().filter(c->c.target().map(t->t.name().equals("size")).orElse(false)).findFirst().orElseThrow();
        var receiver=((MemberExpression)size.callee()).receiver();
        var flow=project.flows().stream().filter(f->f.entry()==main).findFirst().orElseThrow();
        assertTrue(flow.data().stream().anyMatch(e->e.from()==push&&e.to()==receiver&&e.kind()==FunctionFlow.Dependency.DEFINITION),"Opaque push_back may change the receiver observed by size");
        var removeUse=new ProjectEditor(project);removeUse.remove(main);
        var headerOnly=NameResolver.resolve(removeUse.build());assertFalse(headerOnly.hasErrors(),()->headerOnly.semanticDiagnostics().toString());
        assertTrue(headerOnly.classTemplateInstances().isEmpty());assertTrue(headerOnly.templateInstances().isEmpty());
        assertEquals(1,headerOnly.librarySources().size());assertEquals("vector",LibraryDeclarations.headerName(headerOnly.librarySources().getFirst()));
        assertTrue(nodes(headerOnly.standardLibrary().orElseThrow(),Declaration.class).isEmpty());
        var removeIncludes=new ProjectEditor(headerOnly);
        for(var file:headerOnly.files())for(var include:nodes(file,IncludeDirective.class))removeIncludes.remove(include);
        var empty=NameResolver.resolve(removeIncludes.build());assertTrue(empty.standardLibrary().isEmpty());assertTrue(empty.librarySources().isEmpty());
        assertFalse(ProjectJson.encode(empty).toText("").contains("standardLibrary"));
        return List.of(cli.input,run.work().resolve("generated"),folded.work().resolve("generated"));
    }
    private static void verifyGraph(Project project) {
        assertFalse(project.hasErrors(),()->project.semanticDiagnostics()+" / "+project.parseProblems());
        var library=project.standardLibrary().orElseThrow();assertEquals(project.librarySources(),library.headers());
        var vectorHeaders=library.headers().stream().filter(f->LibraryDeclarations.headerName(f).equals("vector")).toList();assertEquals(1,vectorHeaders.size());
        var includes=project.files().stream().flatMap(f->nodes(f,IncludeDirective.class).stream()).filter(i->i.header().equals("vector")).toList();assertEquals(2,includes.size());
        includes.forEach(i->assertSame(vectorHeaders.getFirst(),i.libraryHeader().orElseThrow()));
        var templates=nodes(library,ClassDeclaration.class).stream().filter(c->c.name().equals("vector")).toList();assertEquals(1,templates.size());
        var generic=templates.getFirst();assertEquals(2,generic.templateParameters().size());
        var genericPush=nodes(generic,CallableDeclaration.class).stream().filter(f->f.name().equals("push_back")).toList();assertEquals(1,genericPush.size());
        assertTrue(nodes(library,CallableDeclaration.class).stream().noneMatch(f->f.name().equals("reserve")));
        assertTrue(nodes(library,ClassDeclaration.class).stream().noneMatch(c->c.name().equals("map")));
        var instances=project.classTemplateInstances().stream().filter(i->i.origin()==generic).toList();assertEquals(2,instances.size());
        var integers=instances.stream().filter(i->i.arguments().getFirst().type().orElseThrow().canonicalIdentity().equals("int")).findFirst().orElseThrow();
        var doubles=instances.stream().filter(i->i.arguments().getFirst().type().orElseThrow().canonicalIdentity().equals("double")).findFirst().orElseThrow();
        var concretePush=nodes(integers.declaration(),CallableDeclaration.class).stream().filter(f->f.name().equals("push_back")).toList();assertEquals(1,concretePush.size());
        assertSame(genericPush.getFirst(),concretePush.getFirst().templateOrigin().orElseThrow());
        var reference=assertInstanceOf(ReferenceType.class,concretePush.getFirst().parameters().getFirst().type().orElseThrow());
        var qualified=assertInstanceOf(QualifiedType.class,reference.referredType());assertTrue(qualified.isConst());assertEquals("int",qualified.baseType().canonicalIdentity());
        assertTrue(nodes(doubles.declaration(),CallableDeclaration.class).stream().noneMatch(f->f.name().equals("push_back")));
        var writtenPush=project.files().stream().flatMap(f->nodes(f,CallExpression.class).stream()).filter(c->c.callee() instanceof MemberExpression m&&m.member().equals("push_back")).findFirst().orElseThrow();
        assertSame(concretePush.getFirst(),writtenPush.target().orElseThrow());
        for (var variable : project.files().stream().flatMap(f->nodes(f,VariableDeclaration.class).stream()).filter(v->Set.of("numbers","fractions").contains(v.name())).toList()) {
            assertEquals(CallableDeclaration.Kind.CONSTRUCTOR,variable.constructorTarget().orElseThrow().kind());
            assertEquals(CallableDeclaration.Kind.DESTRUCTOR,variable.destructorTarget().orElseThrow().kind());
            assertTrue(variable.constructorTarget().orElseThrow().templateOrigin().isPresent());
            assertTrue(variable.destructorTarget().orElseThrow().templateOrigin().isPresent());
        }
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());owned.addAll(nodes(project,Node.class));
        for(Node node:owned)for(Node ref:node.references())assertTrue(owned.contains(ref),()->node.getClass().getSimpleName()+" has unowned "+ref.getClass().getSimpleName());
        assertTrue(nodes(library,CallableDeclaration.class).stream().allMatch(f->f.body().isEmpty()),"Declaration catalogue has no invented implementation");
    }
    private static <T extends Node>List<T> nodes(Node root,Class<T> type){var list=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))list.add(type.cast(n));}});return list;}
}
