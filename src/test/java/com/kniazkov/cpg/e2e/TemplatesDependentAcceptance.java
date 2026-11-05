/* Written templates and concrete dependent-member lookup through public paths. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class TemplatesDependentAcceptance {
    private TemplatesDependentAcceptance() {}
    record Output(List<Path> sources,Path invalid) {}
    static Output verify(Path fixture,Path work)throws Exception{
        var cli=new CliSandbox(work).directory(fixture.resolve("sources"));
        var options=new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03");
        Project project=Cpg.read(cli.input,options);verifyGraph(project);verifyGraph(project.edit().build());verifyGraph(project.withDiagnostics("copy-check",Map.of()));
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--svg","snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json=Files.readString(run.work().resolve("snapshot.json"));assertTrue(json.contains("templateInstances"));assertTrue(json.contains("librarySources"));assertTrue(json.contains("dependent"));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        var source=Cpg.sources(project,Language.CPP);assertEquals(Set.of("main.cpp"),source.keySet(),"Library declarations and instances are not physical output files");
        assertEquals(source.get("main.cpp"),Files.readString(run.work().resolve("generated/main.cpp")));
        assertTrue(source.get("main.cpp").contains("#include <vector>"));assertFalse(source.get("main.cpp").contains("__cpg_library"));
        verifyGraph(Cpg.read(run.work().resolve("generated"),options));
        var folded=cli.run("cpp","--stage","folded","--cpp-standard","c++03").expectStatus(0);verifyGraph(Cpg.read(folded.work().resolve("generated"),options));
        assertThrows(IllegalArgumentException.class,()->Cpg.sources(project,Language.JAVA));
        var invalid=new CliSandbox(Files.createDirectory(work.resolve("invalid"))).directory(fixture.resolve("invalid"));
        var rejected=invalid.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(1);
        assertTrue(Files.isRegularFile(rejected.work().resolve("snapshot.json")));assertFalse(Files.exists(rejected.work().resolve("generated")));
        return new Output(List.of(cli.input,run.work().resolve("generated"),folded.work().resolve("generated")),invalid.input);
    }
    private static void verifyGraph(Project p){
        assertFalse(p.hasErrors());var all=nodes(p,Node.class);
        assertTrue(all.stream().flatMap(n->n.diagnostics().stream()).noneMatch(d->d.severity()==Diagnostic.Severity.ERROR),()->all.stream().flatMap(n->n.diagnostics().stream()).toList().toString());
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());owned.addAll(all);
        for(Node node:all)for(Node ref:node.references())assertTrue(owned.contains(ref),()->node.getClass().getSimpleName()+" references an unowned "+ref.getClass().getSimpleName());
        SourceFile file=p.files().getFirst();var count=function(file,"count");var unused=function(file,"neverInstantiated");
        for(var generic:List.of(count,unused)){
            assertEquals(1,generic.templateParameters().size());assertEquals(1,nodes(generic,ReturnStatement.class).size());
            var call=nodes(generic,CallExpression.class).getFirst();assertTrue(call.dependent());assertTrue(call.target().isEmpty());
            var member=assertInstanceOf(MemberExpression.class,call.callee());assertTrue(member.dependent());
            var receiver=assertInstanceOf(NameExpression.class,member.receiver());assertEquals("x",receiver.name());assertSame(generic.parameters().getFirst(),receiver.target().orElseThrow());
        }
        var countInstances=p.templateInstances().stream().filter(i->i.origin()==count).toList();assertEquals(1,countInstances.size());
        assertTrue(p.templateInstances().stream().noneMatch(i->i.origin()==unused));var instance=countInstances.getFirst();
        assertSame(file,instance.source());assertNotSame(count,instance.declaration());assertNotSame(count.body().orElseThrow(),instance.declaration().body().orElseThrow());
        assertTrue(instance.declaration().templateParameters().isEmpty());assertSame(instance.declaration(),instance.cause().target().orElseThrow());
        assertEquals(1,instance.arguments().size());assertTrue(instance.arguments().getFirst().source(Language.CPP).replace(" ", "").contains("vector<int>"));
        var specialized=nodes(instance.declaration(),CallExpression.class).getFirst();assertFalse(specialized.dependent());
        var size=specialized.target().orElseThrow();assertEquals("size",size.name());assertTrue(size.isConst());
        var library=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());for(var header:p.librarySources())library.addAll(nodes(header,Node.class));
        assertTrue(library.contains(size.templateOrigin().orElseThrow()));assertTrue(p.classTemplateInstances().stream().anyMatch(i->nodes(i.declaration(),CallableDeclaration.class).contains(size)));assertTrue(size.body().isEmpty(),"Catalogue provides a declaration, not an invented implementation");
        var receiver=assertInstanceOf(NameExpression.class,assertInstanceOf(MemberExpression.class,specialized.callee()).receiver());
        assertSame(instance.declaration().parameters().getFirst(),receiver.target().orElseThrow());
        var buffer=nodes(file,ClassDeclaration.class).stream().filter(c->c.name().equals("Buffer")).findFirst().orElseThrow();
        assertEquals(List.of(TemplateParameterDeclaration.Kind.TYPE,TemplateParameterDeclaration.Kind.VALUE),buffer.templateParameters().stream().map(TemplateParameterDeclaration::kind).toList());
        assertTrue(buffer.templateParameters().stream().allMatch(t->t.defaultArgument().isPresent()));
        var envelope=nodes(file,ClassDeclaration.class).stream().filter(c->c.name().equals("Envelope")).findFirst().orElseThrow();
        assertEquals(TemplateParameterDeclaration.Kind.TEMPLATE,envelope.templateParameters().getFirst().kind());
        assertEquals(1,envelope.templateParameters().getFirst().templateParameters().size());
        assertTrue(nodes(function(file,"dependentValue"),NamedType.class).stream().anyMatch(NamedType::typenameKeyword));
        var dependentCall=nodes(function(file,"dependentTemplate"),CallExpression.class).getFirst();assertTrue(dependentCall.dependent());assertTrue(dependentCall.target().isEmpty());
        var dependentMember=assertInstanceOf(MemberExpression.class,dependentCall.callee());assertTrue(dependentMember.templateKeyword());assertEquals(1,dependentMember.templateArguments().size());
        assertTrue(nodes(function(file,"nestedArguments"),NamedType.class).stream().anyMatch(t->t.templateArguments().stream().anyMatch(a->a.type().map(x->x instanceof NamedType n&&n.hasTemplateArguments()).orElse(false))));
    }
    private static CallableDeclaration function(Node root,String name){return nodes(root,CallableDeclaration.class).stream().filter(f->f.name().equals(name)).findFirst().orElseThrow();}
    private static <T extends Node>List<T> nodes(Node root,Class<T> type){var list=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))list.add(type.cast(n));}});return list;}
}
