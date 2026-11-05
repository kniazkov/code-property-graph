/* Common vector semantics retain real C++03 targets and observable copy effects. MIT License. */
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
final class VectorSemanticsAcceptance {
    private VectorSemanticsAcceptance() {}
    private static final Set<DynamicArrayOperation.Kind> CORE = Set.of(DynamicArrayOperation.Kind.EMPTY, DynamicArrayOperation.Kind.COUNT, DynamicArrayOperation.Kind.FILL, DynamicArrayOperation.Kind.RANGE, DynamicArrayOperation.Kind.COPY, DynamicArrayOperation.Kind.ASSIGN_COPY, DynamicArrayOperation.Kind.ASSIGN_FILL, DynamicArrayOperation.Kind.ASSIGN_RANGE, DynamicArrayOperation.Kind.APPEND);
    static List<Path> verify(Path fixture,Path work)throws Exception {
        var cli=new CliSandbox(Files.createDirectories(work.resolve("main"))).directory(fixture.resolve("sources"));
        var resolved=Cpg.read(cli.input,options(Cpg.Stage.RESOLVED));
        assertFalse(resolved.hasErrors(),()->resolved.semanticDiagnostics().toString());
        assertTrue(resolved.dynamicArrayTypes().isEmpty());assertTrue(resolved.dynamicArraySemantics().isEmpty());
        var green=Cpg.read(cli.input,options(Cpg.Stage.GREEN));verifyMain(green);verifyMain(UnreadWrites.analyze(green.edit().build()));
        var run=cli.run("cpp","--stage","green","--cpp-standard","c++03","--svg","snapshot.svg").expectStatus(0);
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(green),Json.parse(run.work().resolve("snapshot.json").toFile()));
        String json=Files.readString(run.work().resolve("snapshot.json"));
        for(String key:List.of("dynamicArrayTypes","dynamicArraySemantics","COMMON_TYPE","COMMON_OPERATION","ELEMENT_COPY_CONSTRUCTOR","elementCopyConstructorId","destinationId","sourceIds"))assertTrue(json.contains(key),key);
        assertTrue(Files.readString(run.work().resolve("snapshot.svg")).contains("DynamicArrayOperation"));
        var source=Cpg.sources(green,Language.CPP);assertEquals(Set.of("main.cpp"),source.keySet());
        assertEquals(source.get("main.cpp"),Files.readString(run.work().resolve("generated/main.cpp")));
        assertTrue(source.get("main.cpp").contains("push_back"));assertTrue(source.get("main.cpp").contains("CountingAllocator"));
        verifyMain(Cpg.read(run.work().resolve("generated"),options(Cpg.Stage.GREEN)));
        var folded=cli.run("cpp","--stage","folded","--cpp-standard","c++03").expectStatus(0);
        verifyMain(Cpg.read(folded.work().resolve("generated"),options(Cpg.Stage.GREEN)));
        verifyMain(Cpg.read(cli.input,options(Cpg.Stage.FOLDED)));
        var out=new ArrayList<Path>(List.of(cli.input,run.work().resolve("generated"),folded.work().resolve("generated")));
        var copyCli=new CliSandbox(Files.createDirectories(work.resolve("copy-overloads"))).directory(fixture.resolve("copy-overloads"));
        var copy=Cpg.read(copyCli.input,options(Cpg.Stage.GREEN));assertFalse(copy.hasErrors(),()->copy.semanticDiagnostics().toString());
        for(var operation:copy.dynamicArraySemantics())if(CORE.contains(operation.kind())&&operation.kind()!=DynamicArrayOperation.Kind.EMPTY)assertConstCopy(operation);
        assertEquals(Set.of(DynamicArrayOperation.Kind.EMPTY,DynamicArrayOperation.Kind.APPEND,DynamicArrayOperation.Kind.FILL,DynamicArrayOperation.Kind.COPY),kinds(copy));
        var copyGreen=copyCli.run("cpp","--stage","green","--cpp-standard","c++03").expectStatus(0);
        var copyFolded=copyCli.run("cpp","--stage","folded","--cpp-standard","c++03").expectStatus(0);
        out.addAll(List.of(copyCli.input,copyGreen.work().resolve("generated"),copyFolded.work().resolve("generated")));
        return List.copyOf(out);
    }
    private static Cpg.Options options(Cpg.Stage stage){return new Cpg.Options(Language.CPP,stage,"c++03");}
    private static Set<DynamicArrayOperation.Kind> kinds(Project project){var result=EnumSet.noneOf(DynamicArrayOperation.Kind.class);project.dynamicArraySemantics().stream().filter(op->CORE.contains(op.kind())).forEach(op->result.add(op.kind()));return result;}
    private static void verifyMain(Project project) {
        assertFalse(project.hasErrors(),()->project.semanticDiagnostics()+" / "+project.parseProblems());
        assertEquals(2,project.dynamicArrayTypes().size());assertEquals(13,project.dynamicArraySemantics().stream().filter(op->CORE.contains(op.kind())).count());
        assertEquals(CORE,kinds(project));
        var types=Collections.newSetFromMap(new IdentityHashMap<DynamicArrayType,Boolean>());types.addAll(project.dynamicArrayTypes());
        var operations=project.dynamicArraySemantics().stream().filter(op->CORE.contains(op.kind())).toList();
        var integer=project.dynamicArrayTypes().stream().filter(t->t.elementType().canonicalIdentity().equals("int")).findFirst().orElseThrow();
        var record=project.dynamicArrayTypes().stream().filter(t->ClassSemantics.objectClass(t.elementType()).map(c->c.name().equals("Record")).orElse(false)).findFirst().orElseThrow();
        assertNotSame(integer,record);assertFalse(integer.writtenTypes().isEmpty());assertFalse(record.writtenTypes().isEmpty());
        for(var operation:operations) {
            assertTrue(types.contains(operation.type()));
            assertTrue(LibraryDeclarations.isLibrary(operation.selectedTarget())||operation.selectedTarget().templateOrigin().filter(LibraryDeclarations::isLibrary).isPresent());
            if(operation.syntax() instanceof VariableDeclaration variable)assertSame(variable.constructorTarget().orElseThrow(),operation.selectedTarget());
            else if(operation.syntax() instanceof CallExpression call)assertSame(call.target().orElseThrow(),operation.selectedTarget());
            else if(operation.syntax() instanceof Expression expression)assertSame(expression.operatorTarget().orElseThrow(),operation.selectedTarget());
            else fail("Unexpected operation source");
            if(operation.type()==record&&operation.kind()!=DynamicArrayOperation.Kind.EMPTY)assertConstCopy(operation);
        }
        assertEquals(3,operations.stream().filter(op->op.type()==record&&op.elementCopyConstructor().isPresent()).count());
        var calls=project.files().stream().flatMap(f->nodes(f,CallExpression.class).stream()).toList();
        var userCall=calls.stream().filter(c->c.callee() instanceof MemberExpression m&&m.member().equals("push_back")&&m.receiver() instanceof NameExpression n&&n.name().equals("user")).findFirst().orElseThrow();
        assertTrue(userCall.target().isPresent());assertTrue(operations.stream().noneMatch(op->op.syntax()==userCall));
        var customCall=calls.stream().filter(c->c.callee() instanceof MemberExpression m&&m.member().equals("push_back")&&m.receiver() instanceof NameExpression n&&n.name().equals("custom")).findFirst().orElseThrow();
        assertTrue(customCall.target().isPresent());assertTrue(operations.stream().noneMatch(op->op.syntax()==customCall));
        var flags=project.files().stream().flatMap(f->nodes(f,VariableDeclaration.class).stream()).filter(v->v.name().equals("flags")).findFirst().orElseThrow();
        assertTrue(flags.constructorTarget().isPresent());assertTrue(operations.stream().noneMatch(op->op.destination()==flags));
        assertTrue(project.dynamicArrayTypes().stream().noneMatch(t->t.elementType().canonicalIdentity().equals("bool")));
        var assign=operations.stream().filter(op->op.kind()==DynamicArrayOperation.Kind.ASSIGN_COPY).findFirst().orElseThrow();
        var flow=project.flows().stream().filter(f->f.entry().name().equals("ordinary_values")).findFirst().orElseThrow();assertTrue(flow.complete());
        assertTrue(flow.data().stream().anyMatch(e->e.from()==assign&&e.to() instanceof NameExpression n&&n.name().equals("same_object")&&e.kind()==FunctionFlow.Dependency.DEFINITION),"Assignment changes the aliased container object");
        for(var operation:operations) {
            var owner=project.flows().stream().filter(f->nodes(f.entry(),Node.class).contains(operation.syntax())).findFirst().orElseThrow();
            for(var input:operation.sources())assertTrue(owner.data().stream().anyMatch(e->e.from()==input&&e.to()==operation&&e.kind()==FunctionFlow.Dependency.VALUE),operation.kind()+" input");
        }
        var owned=Collections.newSetFromMap(new IdentityHashMap<Node,Boolean>());owned.addAll(nodes(project,Node.class));
        for(Node node:owned)for(Node ref:node.references())assertTrue(owned.contains(ref),()->node.getClass().getSimpleName()+" has unowned "+ref.getClass().getSimpleName());
    }
    private static void assertConstCopy(DynamicArrayOperation operation) {
        var copy=operation.elementCopyConstructor().orElseThrow();assertEquals(CallableDeclaration.Kind.CONSTRUCTOR,copy.kind());assertEquals("Record",copy.name());
        var reference=assertInstanceOf(ReferenceType.class,copy.parameters().getFirst().type().orElseThrow());
        assertTrue(assertInstanceOf(QualifiedType.class,reference.referredType()).isConst(),"Vector copies from const element, not caller mutability");
        assertTrue(copy.body().isPresent(),"Explicit user copy effects stay in the graph");
    }
    private static <T extends Node>List<T> nodes(Node root,Class<T> type){var list=new ArrayList<T>();root.walk(new NodeVisitor(){@Override public void visit(Node n){if(type.isInstance(n))list.add(type.cast(n));}});return list;}
}
