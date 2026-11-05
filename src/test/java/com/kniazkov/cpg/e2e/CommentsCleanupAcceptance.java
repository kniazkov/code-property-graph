/* Physical C++03 comments and explicit snapshot cleanup through public paths. MIT License. */
package com.kniazkov.cpg.e2e;
import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.CppLexer;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.cpg.utilities.Comments;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
final class CommentsCleanupAcceptance {
    private CommentsCleanupAcceptance() {}
    record Output(Path original,Path generated,Path cleaned) {}
    static Output verify(Path fixture,Path work) throws Exception {
        var cli=new CliSandbox(work).directory(fixture.resolve("sources"));
        var options=new Cpg.Options(Language.CPP,Cpg.Stage.RESOLVED,"c++03");
        Project project=Cpg.read(cli.input,options);
        verifyComments(project); verifyComments(project.edit().build());
        var before=ProjectJson.encode(project).toString();
        Map<String,String> original=Cpg.sources(project,Language.CPP);
        for(var file:original.entrySet())assertEquals(Files.readString(cli.input.resolve(file.getKey())),file.getValue());
        var run=cli.run("cpp","--stage","resolved","--cpp-standard","c++03","--svg","snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project),Json.parse(run.work().resolve("snapshot.json").toFile()));
        assertTrue(Files.readString(run.work().resolve("snapshot.json")).contains("HAS_COMMENT"));
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("HAS_COMMENT"));
        for(var file:original.entrySet())assertEquals(file.getValue(),Files.readString(run.work().resolve("generated").resolve(file.getKey())));
        Project cleaned=Comments.remove(project);
        assertNotSame(project,cleaned); assertEquals(before,ProjectJson.encode(project).toString());
        verifyComments(project); verifyClean(cleaned); verifyClean(cleaned.edit().build());
        Map<String,String> stripped=Cpg.sources(cleaned,Language.CPP);
        assertTrue(stripped.get("main.cpp").contains("\"/* this is text */ // still text\""));
        Path output=Files.createDirectory(work.resolve("cleaned"));
        for(var file:stripped.entrySet()){
            Path path=output.resolve(file.getKey()); Files.createDirectories(path.getParent()); Files.writeString(path,file.getValue());
            assertTrue(CppLexer.scan(file.getKey(),file.getValue()).comments().isEmpty());
        }
        for(boolean feature:List.of(false,true)){
            Project originalVariant=readVariant(cli.input,feature,options); verifyComments(originalVariant);
            Project cleanedVariant=Comments.remove(originalVariant); verifyClean(cleanedVariant);
            assertEquals(stripped,Cpg.sources(cleanedVariant,Language.CPP),"Cleanup must cover both active and inactive arms identically");
            Project reparsed=readVariant(output,feature,options); verifyClean(reparsed);
            assertFalse(reparsed.hasErrors()); assertTrue(nodes(reparsed,Node.class).stream().flatMap(n->n.diagnostics().stream()).noneMatch(d->d.severity()==Diagnostic.Severity.ERROR));
        }
        var cleanedCli=new CliSandbox(Files.createDirectory(work.resolve("cleaned-cli"))).directory(output);
        var cleanedRun=cleanedCli.run("cpp","--stage","resolved").expectStatus(0);
        assertFalse(Files.readString(cleanedRun.work().resolve("snapshot.json")).contains("HAS_COMMENT"));
        assertEquals(original,Cpg.sources(project,Language.CPP));
        return new Output(cli.input,run.work().resolve("generated"),output);
    }
    private static Project readVariant(Path root,boolean feature,Cpg.Options options){
        var unit=new Build.TranslationUnit(new Build.PhysicalSource(root.resolve("main.cpp")),root,List.of(),
                feature?List.of(new Build.MacroOperation(true,"FEATURE=1")):List.of(),"c++03");
        return Cpg.read(new Build(List.of(unit)),options);
    }
    private static void verifyComments(Project p){
        assertFalse(p.hasErrors()); var file=p.files().getFirst(); var comments=file.comments();
        assertFalse(comments.isEmpty()); assertEquals(comments.size(),nodes(p,Comment.class).size());
        var owned=Collections.newSetFromMap(new IdentityHashMap<Comment,Boolean>());owned.addAll(comments);assertEquals(comments.size(),owned.size());
        var sources=file.provenance().orElseThrow().unit().sources();
        assertEquals(sources.entrySet().stream().mapToInt(e->CppLexer.scan(e.getKey(),e.getValue()).comments().size()).sum(),comments.size());
        for(Comment comment:comments){
            var range=comment.range().orElseThrow();String source=sources.get(range.file());assertNotNull(source);
            assertEquals(source.substring(range.start().offset(),range.end().offset()),comment.text());
        }
        for(Node node:nodes(p,Node.class))for(Comment comment:node.commentReferences())assertTrue(owned.contains(comment));
        var value=variable(p,"value"); assertEquals(List.of("// first leading line","// second leading line"),value.commentReferences().stream().map(Comment::text).toList());
        assertTrue(variable(p,"second").commentReferences().stream().anyMatch(c->c.text().equals("// trailing last declaration")));
        assertTrue(variable(p,"first").commentReferences().isEmpty());
        var detached=comments.stream().filter(c->c.text().equals("// separated file-only comment")).findFirst().orElseThrow();
        assertTrue(nodes(p,Node.class).stream().noneMatch(n->n.commentReferences().contains(detached)));
        var call=nodes(p,CallExpression.class).stream().filter(c->c.callee() instanceof NameExpression n&&n.name().equals("add")).findFirst().orElseThrow();
        assertTrue(ExpressionSyntax.written(call.arguments().get(1)).commentReferences().stream().anyMatch(c->c.text().equals("/* second argument */")));
        assertTrue(comments.stream().anyMatch(c->c.text().contains("enabled branch comment")));
        assertTrue(comments.stream().anyMatch(c->c.text().contains("disabled branch comment")));
        assertTrue(comments.stream().anyMatch(c->c.range().orElseThrow().file().endsWith("value.h")));
    }
    private static void verifyClean(Project p){
        assertTrue(nodes(p,Comment.class).isEmpty());
        for(Node node:nodes(p,Node.class))assertTrue(node.commentReferences().isEmpty());
        for(SourceFile file:p.files()){
            assertTrue(file.comments().isEmpty());var unit=file.provenance().orElseThrow().unit();
            for(var source:unit.sources().entrySet())assertTrue(CppLexer.scan(source.getKey(),source.getValue()).comments().isEmpty());
            for(var branch:unit.branches())assertFalse(branch.rawSource().contains("branch comment"));
        }
    }
    private static VariableDeclaration variable(Project p,String name){return nodes(p,VariableDeclaration.class).stream().filter(v->v.name().equals(name)).findFirst().orElseThrow();}
    private static <T extends Node> List<T> nodes(Node root,Class<T> type){var result=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))result.add(type.cast(n));}});return result;}
}
