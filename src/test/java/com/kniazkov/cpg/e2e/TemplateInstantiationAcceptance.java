/* Cross-unit template identity, substitution and nested error traces. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class TemplateInstantiationAcceptance {
    private TemplateInstantiationAcceptance() {}
    record Output(List<Path> sources,List<Path> invalid) {}
    static Output verify(Path fixture,Path work)throws Exception {
        var cli=new CliSandbox(work).directory(fixture.resolve("sources"));
        var options=new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03");
        var project=Cpg.read(cli.input,options);verifyGraph(project);verifyGraph(project.edit().build());
        var concreteInner=instances(project,"inner").getFirst().declaration();
        assertTrue(project.flows().stream().anyMatch(f->f.entry()==concreteInner&&f.complete()&&!f.data().isEmpty()));
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--svg","snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json=Files.readString(run.work().resolve("snapshot.json"));
        for(String key:List.of("CALL_TARGET","INSTANTIATION_CAUSE","INSTANTIATION_REQUEST","requestIds","classTemplateInstances"))assertTrue(json.contains(key),key);
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        var source=Cpg.sources(project,Language.CPP);assertEquals(Set.of("main.cpp","other.cpp","api.h"),source.keySet());
        for(var entry:source.entrySet())assertEquals(entry.getValue(),Files.readString(run.work().resolve("generated").resolve(entry.getKey())));
        verifyGraph(Cpg.read(run.work().resolve("generated"),options));
        var folded=cli.run("cpp","--stage","folded","--cpp-standard","c++03").expectStatus(0);
        verifyGraph(Cpg.read(folded.work().resolve("generated"),options));
        var invalid=new ArrayList<Path>();
        for(String name:List.of("invalid-body","invalid-partial-function","invalid-ambiguous")) {
            var bad=new CliSandbox(Files.createDirectory(work.resolve(name))).directory(fixture.resolve(name));invalid.add(bad.input);
            var rejected=bad.run("cpp","--stage","resolved","--cpp-standard","c++03").expectStatus(1);
            assertTrue(Files.isRegularFile(rejected.work().resolve("snapshot.json")));assertFalse(Files.exists(rejected.work().resolve("generated")));
            var diagnosed=Cpg.readRecovering(bad.input,options);assertTrue(diagnosed.hasErrors(),name);
            if(name.equals("invalid-body")) {
                var finding=diagnosed.semanticDiagnostics().stream().filter(d->d.severity()==Diagnostic.Severity.ERROR&&d.message().contains("size")&&d.message().contains("int")).findFirst().orElseThrow();
                assertNotNull(finding.primaryRange());assertEquals(1,finding.primaryRange().start().line());
                assertEquals(List.of(2,3),finding.relatedRanges().stream().map(r->r.range().start().line()).toList());
                String errorJson=Files.readString(rejected.work().resolve("snapshot.json"));assertTrue(errorJson.contains("relatedRanges"));assertTrue(errorJson.contains("size"));
            }
        }
        return new Output(List.of(cli.input,run.work().resolve("generated"),folded.work().resolve("generated")),invalid);
    }
    private static void verifyGraph(Project p) {
        assertFalse(p.hasErrors(),()->p.semanticDiagnostics()+" / "+p.parseProblems());
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());owned.addAll(nodes(p,Node.class));
        for(Node node:owned)for(Node ref:node.references())assertTrue(owned.contains(ref),()->node.getClass().getSimpleName()+" has unowned reference "+ref.getClass().getSimpleName());
        var outers=instances(p,"outer");var inners=instances(p,"inner");assertEquals(1,outers.size());assertEquals(1,inners.size());
        var outer=outers.getFirst();var inner=inners.getFirst();assertEquals(2,outer.causes().size());assertEquals(1,inner.causes().size());
        assertTrue(nodes(outer.declaration(),CallExpression.class).contains(inner.causes().getFirst()));
        for(var cause:outer.causes())assertSame(outer.declaration(),cause.target().orElseThrow());
        assertSame(inner.declaration(),inner.causes().getFirst().target().orElseThrow());
        assertTrue(outer.causes().stream().map(c->c.range().orElseThrow().file()).distinct().count()==2);
        var size=nodes(inner.declaration(),CallExpression.class).stream().filter(c->c.callee() instanceof MemberExpression).findFirst().orElseThrow();
        assertFalse(size.dependent());assertEquals("size",size.target().orElseThrow().name());
        for(var file:p.files())for(var generic:nodes(file,CallableDeclaration.class))if(generic.name().equals("inner")&&!generic.templateParameters().isEmpty()) {
            var call=nodes(generic,CallExpression.class).getFirst();assertTrue(call.dependent());assertTrue(call.target().isEmpty());
        }
        var identities=instances(p,"identity");assertEquals(1,identities.size());
        assertTrue(identities.getFirst().requests().stream().anyMatch(CallableDeclaration.class::isInstance));
        assertTrue(identities.getFirst().requests().stream().anyMatch(CallExpression.class::isInstance));
        var main=p.files().stream().flatMap(f->nodes(f,CallableDeclaration.class).stream()).filter(f->f.name().equals("main")).findFirst().orElseThrow();
        var choose=nodes(main,CallExpression.class).stream().filter(c->c.callee() instanceof NameExpression n&&n.name().equals("choose")).toList();assertEquals(2,choose.size());
        assertTrue(choose.stream().allMatch(c->c.target().isPresent()));assertNotSame(choose.get(0).target().orElseThrow(),choose.get(1).target().orElseThrow());
        assertEquals(1,instances(p,"choose").size(),"The int candidate fails substitution, not its selected body");
        var specialization=nodes(main,CallExpression.class).stream().filter(c->c.callee() instanceof NameExpression n&&n.name().equals("specialized")).findFirst().orElseThrow();
        assertTrue(nodes(specialization.target().orElseThrow(),IntegerLiteral.class).stream().anyMatch(n->n.bigValue().intValue()==9),"Full function specialization supplies the selected body");
        for(String name:List.of("length","fixedValue")) {
            var valueInstance=instances(p,name).getFirst();
            assertEquals(TemplateArgument.Kind.VALUE,valueInstance.structuredArguments().getFirst().kind());
            int expected=name.equals("length")?3:4;
            assertTrue(nodes(valueInstance.declaration(),IntegerLiteral.class).stream().anyMatch(n->n.bigValue().intValue()==expected));
        }
        var templateArgumentInstance=instances(p,"readBox").getFirst();
        assertEquals(List.of(TemplateArgument.Kind.TEMPLATE,TemplateArgument.Kind.TYPE),templateArgumentInstance.structuredArguments().stream().map(TemplateArgument::kind).toList());
        var selected=p.classTemplateInstances().stream().filter(i->i.origin().name().equals("Select")).toList();assertEquals(4,selected.size());
        assertEquals(4,selected.stream().map(ClassTemplateInstance::identity).distinct().count());
        assertEquals(Set.of(TemplateKind.PRIMARY,TemplateKind.PARTIAL_SPECIALIZATION,TemplateKind.FULL_SPECIALIZATION),selected.stream().map(i->i.selected().templateKind()).collect(java.util.stream.Collectors.toSet()));
        for(var instance:selected)assertTrue(instance.declaration().templateParameters().isEmpty());
    }
    private static List<TemplateInstance> instances(Project p,String name){return p.templateInstances().stream().filter(i->i.origin().name().equals(name)).toList();}
    private static <T extends Node>List<T> nodes(Node root,Class<T> type){var list=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))list.add(type.cast(n));}});return list;}
}
