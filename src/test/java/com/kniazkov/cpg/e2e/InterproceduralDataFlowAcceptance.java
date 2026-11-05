/* Contextual C++03 memory effects preserved through native source round trips. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.UnreadWrites;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.visualization.DotVisualizer;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class InterproceduralDataFlowAcceptance {
    private InterproceduralDataFlowAcceptance() { }
    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli=new CliSandbox(work).directory(fixture.resolve("sources"));
        var green=Cpg.read(cli.input,options(Cpg.Stage.GREEN));
        verifyGraph(green);
        verifyGraph(UnreadWrites.analyze(green.edit().build()));
        var run=cli.run("cpp","--stage","green","--cpp-standard","c++03").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(green),Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json=Files.readString(run.work().resolve("snapshot.json"));
        assertTrue(json.contains("callSiteId"));assertTrue(json.contains("dfg.definition"));
        assertTrue(DotVisualizer.toDot(green).contains("DFG def via call n"));
        assertEquals(Cpg.sources(green,Language.CPP).get("main.cpp"),Files.readString(run.work().resolve("generated/main.cpp")));
        verifyGraph(Cpg.read(run.work().resolve("generated"),options(Cpg.Stage.GREEN)));
        var folded=cli.run("cpp","--stage","folded","--cpp-standard","c++03").expectStatus(0);
        verifyGraph(Cpg.read(cli.input,options(Cpg.Stage.FOLDED)));
        var svgCli=new CliSandbox(Files.createDirectories(work.resolve("svg"))).directory(fixture.resolve("svg"));
        var svg=svgCli.run("cpp","--stage","green","--cpp-standard","c++03","--svg","snapshot.svg").expectStatus(0);
        assertTrue(Files.readString(svg.work().resolve("snapshot.svg")).contains("DFG def via call n"));
        return List.of(cli.input,run.work().resolve("generated"),folded.work().resolve("generated"),svgCli.input,svg.work().resolve("generated"));
    }
    private static Cpg.Options options(Cpg.Stage stage){return new Cpg.Options(Language.CPP,stage,"c++03");}
    private static void verifyGraph(Project project){
        assertFalse(project.hasErrors(),()->project.parseProblems()+" / "+project.semanticDiagnostics());
        var main=project.flows().stream().filter(f->f.entry().name().equals("main")).findFirst().orElseThrow();
        assertTrue(project.flows().stream().allMatch(FunctionFlow::complete));
        var update=project.flows().stream().filter(f->f.entry().name().equals("update")).findFirst().orElseThrow();
        var write=nodes(update.entry(),AssignmentExpression.class).getFirst();
        var updates=nodes(main.entry(),CallExpression.class).stream().filter(c->c.target().map(t->t.name().equals("update")).orElse(false)).toList();
        assertTrue(updates.size()>=2);
        var observe=nodes(main.entry(),CallExpression.class).stream().filter(c->c.target().map(t->t.name().equals("read_back")).orElse(false)).findFirst().orElseThrow();
        var read=nodes(observe.arguments().getFirst(),NameExpression.class).getFirst();
        assertTrue(main.data().stream().anyMatch(e->e.kind()==FunctionFlow.Dependency.DEFINITION&&e.from()==write&&e.to()==read&&e.callSite()==updates.getFirst()),"Body write is tied to the first invocation");
        assertFalse(main.data().stream().anyMatch(e->e.to()==read&&e.callSite()==updates.get(1)),"Later invocation cannot reach an earlier read");
        var first=nodes(main.entry(),VariableDeclaration.class).stream().filter(v->v.name().equals("first_object")).findFirst().orElseThrow();
        assertFalse(main.data().stream().anyMatch(e->e.from()==first&&e.to()==read&&e.kind()==FunctionFlow.Dependency.DEFINITION),"Proven normal write replaces prior definition");
        assertTrue(main.data().stream().anyMatch(e->e.from() instanceof ReturnStatement&&e.to()==observe&&e.callSite()==observe&&e.kind()==FunctionFlow.Dependency.VALUE),"Return value is tied to its invocation");
        Set<Node> owned=Collections.newSetFromMap(new IdentityHashMap<>());owned.addAll(nodes(project,Node.class));
        for(var flow:project.flows())for(var edge:flow.data()){
            assertTrue(owned.contains(edge.from()));assertTrue(owned.contains(edge.to()));
            if(edge.callSite()!=null)assertTrue(owned.contains(edge.callSite()));
        }
    }
    private static <T extends Node> List<T> nodes(Node root,Class<T> type){var result=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node node){if(type.isInstance(node))result.add(type.cast(node));}});return result;}
}
