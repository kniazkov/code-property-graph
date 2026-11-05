/* C++03 transfers, exception paths and implicit destruction through public paths. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class StatementsExceptionsAcceptance {
    private StatementsExceptionsAcceptance() {}
    record Output(Path original,Path generated,Path folded,Path invalid) {}
    static Output verify(Path fixture,Path work) throws Exception {
        var cli=new CliSandbox(work).directory(fixture.resolve("sources"));
        var options=new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03");
        Project project=Cpg.read(cli.input,options);
        verifySyntax(project); verifySyntax(project.edit().build()); verifyFlows(project);
        verifyFlows(project.withDiagnostics("acceptance-copy",Map.of()));
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--svg","snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json=Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("IMPLICIT_DESTRUCTOR"),json);
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("<svg"));
        String generated=Files.readString(run.work().resolve("generated/main.cpp"));
        assertEquals(Cpg.sources(project,Language.CPP).get("main.cpp"),generated);
        assertFalse(generated.contains(".~Guard"),"Implicit flow cleanup must not be printed as an explicit destructor call");
        var reparsed=Cpg.read(run.work().resolve("generated"),options); verifySyntax(reparsed); verifyFlows(reparsed);
        assertThrows(IllegalArgumentException.class,()->Cpg.sources(project,Language.JAVA));
        var folded=cli.run("cpp","--stage","folded","--cpp-standard","c++03").expectStatus(0);
        var invalid=new CliSandbox(Files.createDirectory(work.resolve("invalid"))).directory(fixture.resolve("invalid"));
        var failed=invalid.run("cpp","--stage","resolved","--svg","snapshot.svg").expectStatus(1);
        assertTrue(Files.isRegularFile(failed.work().resolve("snapshot.json"))); assertFalse(Files.exists(failed.work().resolve("generated")));
        var partial=new CliSandbox(Files.createDirectory(work.resolve("partial"))).directory(fixture.resolve("partial"));
        Project recovered=Cpg.readRecovering(partial.input,options); assertTrue(recovered.hasErrors());
        var complete=flow(recovered,"complete"); var truncated=flow(recovered,"truncated");
        assertTrue(complete.complete()); assertFalse(complete.control().isEmpty()); assertFalse(complete.data().isEmpty());
        assertFalse(truncated.complete()); assertTrue(truncated.control().isEmpty()); assertTrue(truncated.data().isEmpty());
        assertFalse(truncated.entry().bodyComplete()); assertFalse(nodes(truncated.entry(),VariableDeclaration.class).isEmpty());
        var partialRun=partial.run("cpp","--stage","resolved","--svg","snapshot.svg").expectStatus(1);
        assertTrue(Files.readString(partialRun.work().resolve("snapshot.json")).contains("flowComplete"));
        assertTrue(Files.readString(partialRun.work().resolve("snapshot.svg")).contains("<svg"));
        assertFalse(Files.exists(partialRun.work().resolve("generated")));
        return new Output(cli.input,run.work().resolve("generated"),folded.work().resolve("generated"),invalid.input);
    }
    private static void verifySyntax(Project p) {
        assertFalse(p.hasErrors()); var all=nodes(p,Node.class);
        assertTrue(all.stream().flatMap(n->n.diagnostics().stream()).noneMatch(d->d.severity()==Diagnostic.Severity.ERROR),()->all.stream().flatMap(n->n.diagnostics().stream()).toList().toString());
        Set<Node> owned=Collections.newSetFromMap(new IdentityHashMap<>()); owned.addAll(all);
        for(Node n:all)for(Node ref:n.references())assertTrue(owned.contains(ref));
        for(Class<? extends Node> kind:List.of(EmptyStatement.class,DoWhileStatement.class,ForStatement.class,SwitchStatement.class,CaseStatement.class,
                BreakStatement.class,ContinueStatement.class,GotoStatement.class,LabelStatement.class,ThrowExpression.class,TryStatement.class,CatchClause.class,ConditionDeclarationExpression.class))
            assertFalse(nodes(p,kind).isEmpty(),kind.getSimpleName());
        assertTrue(nodes(p,CallableDeclaration.class).stream().anyMatch(CallableDeclaration::functionTryBlock));
        assertTrue(nodes(p,CallableDeclaration.class).stream().anyMatch(c->c.name().equals("quiet")&&c.hasExceptionSpecification()&&c.exceptionTypes().isEmpty()));
    }
    private static void verifyFlows(Project p) {
        var f=flow(p,"normalPair"); assertTrue(f.complete());
        var call=call(f,"work");
        assertEquals(List.of("b","a"),cleanup(f,normalSuccessor(f,call),f.exit()));
        var thrown=f.control().stream().filter(e->e.from()==call&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow();
        assertEquals(List.of("b","a"),cleanup(f,thrown.to(),f.exceptionalExit().orElseThrow()));
        var b=nodes(f.entry(),VariableDeclaration.class).stream().filter(v->v.name().equals("b")).findFirst().orElseThrow();
        var failed=f.control().stream().filter(e->e.from()==b&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow();
        assertEquals(List.of("a"),cleanup(f,failed.to(),f.exceptionalExit().orElseThrow()));
        var unknown=flow(p,"unknownPath"); var work=call(unknown,"work");
        var edge=unknown.control().stream().filter(e->e.from()==work&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow();
        assertEquals(List.of("g"),cleanup(unknown,edge.to(),nodes(unknown.entry(),CatchClause.class).getFirst()));
        var quiet=flow(p,"quietPath"); var noThrow=call(quiet,"quiet");
        assertFalse(quiet.control().stream().anyMatch(e->e.from()==noThrow&&e.kind()==Branch.EXCEPTION));
        assertEquals(List.of("g"),cleanup(quiet,normalSuccessor(quiet,noThrow),quiet.exit()));
        var returned=flow(p,"returning"); assertEquals(List.of("g"),cleanup(returned,nodes(returned.entry(),ReturnStatement.class).getFirst(),returned.exit()));
        var pair=flow(p,"Pair"); assertTrue(pair.complete());
        var second=pair.entry().initializers().stream().filter(i->i.name().equals("second")).findFirst().orElseThrow();
        var subobjectFailure=pair.control().stream().filter(e->e.from()==second&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow();
        assertEquals(List.of("first"),cleanup(pair,subobjectFailure.to(),pair.entry().handlers().getFirst()));
        var ownerDestructor=p.flows().stream().filter(flow -> flow.entry().kind()==CallableDeclaration.Kind.DESTRUCTOR && flow.entry().name().contains("Owner")).findFirst().orElseThrow();
        assertTrue(ownerDestructor.complete());
        var destructorWork=call(ownerDestructor,"work");
        assertEquals(List.of("second","first"),cleanup(ownerDestructor,normalSuccessor(ownerDestructor,destructorWork),ownerDestructor.exit()));
        var destructorFailure=ownerDestructor.control().stream().filter(e->e.from()==destructorWork&&e.kind()==Branch.EXCEPTION).findFirst().orElseThrow();
        assertEquals(List.of("second","first"),cleanup(ownerDestructor,destructorFailure.to(),ownerDestructor.entry().handlers().getFirst()));
        var objects=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());objects.addAll(nodes(p,Node.class));
        for(var flow:p.flows())for(var vertex:flow.auxiliaryVertices()){
            assertSame(flow.entry(),vertex.owner());
            vertex.object().ifPresent(n->assertTrue(objects.contains(n)));
            vertex.destructor().ifPresent(n->assertTrue(objects.contains(n)));
            // Implicitly declared special members have no invented AST declaration.
            // The scalar Guard cleanup paths above still require their explicit destructor target.
            if(vertex.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR && vertex.destructor().isEmpty()) {
                var variable=assertInstanceOf(VariableDeclaration.class,vertex.object().orElseThrow());
                assertEquals("Pair",com.kniazkov.cpg.analysis.ClassSemantics.objectClass(variable.type().orElseThrow()).orElseThrow().name());
            }
        }
    }
    private static FlowVertex normalSuccessor(FunctionFlow f,FlowVertex from){return f.control().stream().filter(e->e.from()==from&&e.kind()!=Branch.EXCEPTION&&e.kind()!=Branch.THROW).findFirst().orElseThrow().to();}
    private static List<String> cleanup(FunctionFlow f,FlowVertex start,FlowVertex end){
        var names=new ArrayList<String>();var seen=Collections.newSetFromMap(new IdentityHashMap<FlowVertex,Boolean>());var cursor=start;
        while(cursor!=end){assertTrue(seen.add(cursor),"Unexpected cycle in straight cleanup path");if(cursor instanceof AuxiliaryFlowVertex a&&a.kind()==AuxiliaryFlowVertex.Kind.IMPLICIT_DESTRUCTOR){
            assertTrue(a.destructor().isPresent());names.add(assertInstanceOf(VariableDeclaration.class,a.object().orElseThrow()).name());}
            cursor=normalSuccessor(f,cursor);
        }return names;
    }
    private static FunctionFlow flow(Project p,String name){return p.flows().stream().filter(f->f.entry().name().equals(name)).findFirst().orElseThrow();}
    private static CallExpression call(FunctionFlow f,String name){return nodes(f.entry(),CallExpression.class).stream().filter(c->c.callee() instanceof NameExpression n&&n.name().equals(name)).findFirst().orElseThrow();}
    private static <T extends Node> List<T> nodes(Node root,Class<T> kind){var result=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(kind.isInstance(n))result.add(kind.cast(n));}});return result;}
}
