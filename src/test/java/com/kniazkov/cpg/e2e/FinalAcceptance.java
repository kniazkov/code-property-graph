/* Integrated multifile C++03 acceptance through the public API and packaged CLI. MIT License. */
package com.kniazkov.cpg.e2e;

import com.kniazkov.cpg.Cpg;
import com.kniazkov.cpg.SnapshotAssertions;
import com.kniazkov.cpg.analysis.LibraryDeclarations;
import com.kniazkov.cpg.model.*;
import static com.kniazkov.cpg.e2e.ExpressionSyntax.written;
import com.kniazkov.cpg.serialization.ProjectJson;
import com.kniazkov.json.Json;
import java.nio.file.*;
import java.util.*;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.*;

/** One program composes the features; assertions follow semantic identities, not fixed node IDs. */
final class FinalAcceptance {
    private FinalAcceptance() {}
    private static final Set<String> FILES = Set.of("main.cpp", "evaluate.cpp", "include/pipeline.h");
    private static final Cpg.Options OPTIONS = new Cpg.Options(Language.CPP, Cpg.Stage.GREEN, "c++03");

    /** Original, generated, and CLI-reparsed/generated trees must all pass the same native oracle. */
    static List<Path> verify(Path fixture, Path work) throws Exception {
        var cli = new CliSandbox(Files.createDirectory(work.resolve("first"))).directory(fixture.resolve("sources"));
        Project project = Cpg.read(cli.input, OPTIONS);
        verifyApi(project);
        var run = cli.run("cpp", "--stage", "green", "--cpp-standard", "c++03").expectStatus(0);
        verifyExport(project, run);
        Path generated = run.work().resolve("generated");
        verifyApi(Cpg.read(generated, OPTIONS));
        String header = Files.readString(generated.resolve("include/pipeline.h"));
        assertTrue(header.contains("#define FINAL_TWICE(value) ((value) + (value))"));
        assertTrue(header.contains("return FINAL_TWICE(value);"));
        assertTrue(header.contains("twice<int>(value)"));
        assertTrue(Files.readString(generated.resolve("evaluate.cpp")).contains("throw FINAL_ERROR;"));

        // A fresh process reads generated physical sources, rather than reusing the first graph.
        var reparse = new CliSandbox(Files.createDirectory(work.resolve("reparse"))).directory(generated);
        Project reparsed = Cpg.read(reparse.input, OPTIONS);
        verifyApi(reparsed);
        var rerun = reparse.run("cpp", "--stage", "green", "--cpp-standard", "c++03").expectStatus(0);
        verifyExport(reparsed, rerun);
        assertEquals(Cpg.sources(project, Language.CPP), Cpg.sources(reparsed, Language.CPP),
                "The complete multifile program must reach a stable generation round trip");
        return List.of(cli.input, generated, rerun.work().resolve("generated"));
    }

    private static void verifyExport(Project project, CliRun run) throws Exception {
        var json = Json.parse(run.work().resolve("snapshot.json").toFile());
        SnapshotAssertions.assertEquivalent(ProjectJson.encode(project), json);
        run.snapshot(); // Also rejects duplicate IDs and dangling edges, including exported history.
        verifyJson(object(json.toJavaObject()));
        Map<String, String> sources = Cpg.sources(project, Language.CPP);
        assertEquals(FILES, sources.keySet());
        Path root = run.work().resolve("generated");
        try (var paths = Files.walk(root)) {
            assertEquals(FILES, new HashSet<>(paths.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/')).toList()));
        }
        for (var entry : sources.entrySet())
            assertEquals(entry.getValue(), Files.readString(root.resolve(entry.getKey())), entry.getKey());
    }

    private static void verifyApi(Project project) {
        assertFalse(project.hasErrors(), () -> project.semanticDiagnostics() + " / " + project.parseProblems());
        assertEquals(2, project.files().size(), "Headers are shared physical inputs, not extra translation units");
        var owned = Collections.newSetFromMap(new IdentityHashMap<Node, Boolean>());
        owned.addAll(nodes(project, Node.class));
        for (Node node : owned) for (Node target : node.references())
            assertTrue(owned.contains(target), () -> node.getClass().getSimpleName() + " has a foreign target");

        var main = function(project, "main");
        var evaluate = function(project, "evaluate");
        var evaluatorBinding = nodes(main, VariableDeclaration.class).stream()
                .filter(v -> v.name().equals("evaluator")).findFirst().orElseThrow();
        assertTrue(evaluatorBinding.diagnostics().stream().noneMatch(d -> d.code().equals("unread-write")),
                "The reference binding is used by both cross-unit calls");
        assertTrue(evaluate.range().orElseThrow().file().endsWith("/evaluate.cpp"));
        var calls = nodes(main, CallExpression.class).stream().filter(c -> namedCall(c, "evaluate")).toList();
        assertEquals(2, calls.size());
        for (var call : calls) assertSame(evaluate, call.target().orElseThrow());
        var declarations = nodes(project, CallableDeclaration.class).stream()
                .filter(c -> c.name().equals("evaluate") && c.body().isEmpty()).toList();
        assertEquals(2, declarations.size());
        for (var declaration : declarations) assertSame(evaluate, declaration.definition().orElseThrow());

        var dispatch = nodes(evaluate, CallExpression.class).stream()
                .filter(c -> c.callee() instanceof MemberExpression m && m.member().equals("apply")).findFirst().orElseThrow();
        var base = dispatch.target().orElseThrow();
        assertTrue(base.isPureVirtual());
        assertEquals("apply", base.name());
        assertTrue(dispatch.isVirtualDispatch());
        assertFalse(dispatch.virtualTargetsComplete(), "Do not invent a closed world for an open virtual call");
        assertEquals(1, dispatch.virtualTargets().size());
        var derived = dispatch.virtualTargets().getFirst();
        assertNotSame(base, derived);
        assertTrue(derived.body().isPresent());
        var receiver = assertInstanceOf(NameExpression.class,
                assertInstanceOf(MemberExpression.class, dispatch.callee()).receiver());
        assertSame(evaluate.parameters().getFirst(), receiver.target().orElseThrow());
        var instance = project.templateInstances().stream().filter(i -> i.origin().name().equals("twice")).findFirst().orElseThrow();
        assertEquals(1, project.templateInstances().size());
        assertEquals(2, instance.causes().size(), "Shared inline body uses one canonical concrete template across both units");
        assertEquals("int", instance.declaration().returnType().orElseThrow().canonicalIdentity());
        assertTrue(instance.declaration().templateParameters().isEmpty());
        assertTrue(instance.causes().stream().anyMatch(nodes(derived, CallExpression.class)::contains));
        for (var cause : instance.causes()) assertSame(instance.declaration(), cause.target().orElseThrow());
        var addition = nodes(instance.declaration(), AddExpression.class).getFirst();
        var reads = nodes(addition, NameExpression.class);
        assertEquals(2, reads.size());
        for (var read : reads) assertSame(instance.declaration().parameters().getFirst(), read.target().orElseThrow());
        macro(addition, "FINAL_TWICE", "include/pipeline.h", 10, 6);

        var mainFlow = flow(project, main);
        for (var call : calls) assertData(mainFlow, evaluatorBinding,
                written(call.arguments().getFirst()), FunctionFlow.Dependency.DEFINITION);
        var evaluateFlow = flow(project, evaluate);
        var values = nodes(main, VariableDeclaration.class).stream().filter(v -> v.name().equals("values")).findFirst().orElseThrow();
        assertEquals(1, project.dynamicArrayTypes().size());
        var type = project.dynamicArrayTypes().getFirst();
        assertEquals("int", type.elementType().canonicalIdentity());
        assertEquals(8, project.dynamicArraySemantics().size());
        var append = project.dynamicArraySemantics().stream().filter(op -> op.kind() == DynamicArrayOperation.Kind.APPEND).toList();
        assertEquals(2, append.size());
        assertEquals(List.of(3, 4), append.stream().map(op -> assertInstanceOf(IntegerLiteral.class, written(assertInstanceOf(Expression.class, op.sources().getFirst()))).value()).toList());
        for (var op : project.dynamicArraySemantics()) {
            assertSame(type, op.type());
            assertTrue(LibraryDeclarations.isLibrary(op.selectedTarget())
                    || op.selectedTarget().templateOrigin().filter(LibraryDeclarations::isLibrary).isPresent());
            if (op.syntax() instanceof CallExpression call) assertSame(call.target().orElseThrow(), op.selectedTarget());
            else if (op.syntax() instanceof VariableDeclaration variable) assertSame(variable.constructorTarget().orElseThrow(), op.selectedTarget());
            else assertSame(assertInstanceOf(Expression.class, op.syntax()).operatorTarget().orElseThrow(), op.selectedTarget());
            var owner = nodes(main, Node.class).contains(op.syntax()) ? mainFlow : evaluateFlow;
            for (var source : op.sources()) assertData(owner, source, op, FunctionFlow.Dependency.VALUE);
        }
        for (var op : append) {
            assertSame(values, assertInstanceOf(NameExpression.class, op.destination()).target().orElseThrow());
            assertEquals(List.of(op.destination()), op.mutatedObjects());
            assertData(mainFlow, op, values, FunctionFlow.Dependency.DEFINITION);
        }
        assertData(mainFlow, append.getFirst(), append.getLast(), FunctionFlow.Dependency.DEFINITION);
        var write = nodes(main, AssignmentExpression.class).stream().filter(a -> a.target() instanceof IndexExpression).findFirst().orElseThrow();
        var access = project.dynamicArraySemantics().stream().filter(op -> op.syntax() == write.target()).findFirst().orElseThrow();
        assertTrue(access.writable());
        assertSame(values, access.handle().orElseThrow().sequence());
        assertEquals(0, assertInstanceOf(IntegerLiteral.class, access.index().orElseThrow()).value());
        assertData(mainFlow, write, values, FunctionFlow.Dependency.DEFINITION);
        assertData(mainFlow, write, calls.getLast().arguments().get(1), FunctionFlow.Dependency.DEFINITION);
        var checked = project.dynamicArraySemantics().stream().filter(op -> op.kind() == DynamicArrayOperation.Kind.AT).findFirst().orElseThrow();
        assertTrue(checked.boundsChecked());
        assertFalse(checked.writable());
        assertEquals(DynamicArrayOperation.ResultKind.REFERENCE, checked.resultKind());
        assertSame(evaluate.parameters().get(1), checked.handle().orElseThrow().sequence());
        assertEquals(0, assertInstanceOf(IntegerLiteral.class, checked.index().orElseThrow()).value());
        for (var call : calls) for (int i = 0; i < call.arguments().size(); i++) {
            final int index = i;
            assertTrue(mainFlow.data().stream().anyMatch(edge -> edge.from() == call.arguments().get(index)
                    && edge.to() == evaluate.parameters().get(index) && edge.callSite() == call
                    && edge.kind() == FunctionFlow.Dependency.VALUE), "Call-site-specific cross-unit argument edge");
        }
        var thrown = nodes(evaluate, ThrowExpression.class).getFirst();
        assertEquals(3, assertInstanceOf(IntegerLiteral.class, thrown.value().orElseThrow()).value());
        macro(thrown.value().orElseThrow(), "FINAL_ERROR", "evaluate.cpp", 5, 7);
        assertTrue(evaluateFlow.control().stream().anyMatch(e -> e.from() == thrown
                && e.to() == evaluateFlow.exceptionalExit().orElseThrow() && e.kind() == FunctionFlow.Branch.THROW));
        var caught = nodes(main, CatchClause.class).getFirst();
        assertTrue(mainFlow.control().stream().anyMatch(e -> e.from() == calls.getLast()
                && e.to() == caught && e.kind() == FunctionFlow.Branch.EXCEPTION));
        var errorRead = nodes(caught, NameExpression.class).stream().filter(n -> n.name().equals("error")).findFirst().orElseThrow();
        assertSame(caught.parameter().orElseThrow(), errorRead.target().orElseThrow());
        assertData(mainFlow, caught.parameter().orElseThrow(), errorRead, FunctionFlow.Dependency.DEFINITION);
    }

    private static void macro(Node node, String name, String invocationFile, int invocationLine, int definitionLine) {
        var provenance = node.provenance().orElseThrow();
        var expansion = provenance.expansions().stream().filter(e -> e.name().equals(name)).findFirst().orElseThrow();
        var definition = provenance.unit().definitions().stream().filter(d -> d.id() == expansion.definitionId()).findFirst().orElseThrow();
        assertEquals(name, definition.name());
        assertTrue(definition.range().file().endsWith("/include/pipeline.h"));
        assertEquals(definitionLine, definition.range().start().line());
        assertEquals(definition.range(), expansion.definition());
        assertTrue(expansion.invocation().file().endsWith("/" + invocationFile));
        assertEquals(invocationLine, expansion.invocation().start().line());
        assertTrue(provenance.tokens().stream().anyMatch(token -> token.expansionIds().contains(expansion.id())
                && token.spellingRange().file().endsWith("/include/pipeline.h")
                && token.spellingRange().start().line() == definitionLine));
        if (name.equals("FINAL_TWICE")) {
            assertEquals(1, expansion.arguments().size());
            assertEquals("value", expansion.arguments().getFirst().stream().map(Preprocessing.Fragment::spelling).reduce("", String::concat));
        }
    }

    /** Independently follow the exported IDs without access to the in-memory Project. */
    private static void verifyJson(Map<?, ?> root) {
        var index = new LinkedHashMap<Object, Map<?, ?>>();
        jsonNodes(root).forEach(n -> assertNull(index.put(n.get("id"), n), "Duplicate current node ID"));
        var main = one(jsonNodes(root), n -> named(n, "FunctionDeclaration", "main"));
        var evaluate = one(jsonNodes(root), n -> named(n, "FunctionDeclaration", "evaluate") && n.containsKey("body"));
        var evaluatorBinding = one(jsonNodes(main), n -> named(n, "VariableDeclaration", "evaluator"));
        assertTrue(!evaluatorBinding.containsKey("diagnostics") || objects(evaluatorBinding.get("diagnostics")).stream()
                .noneMatch(d -> "unread-write".equals(d.get("code"))), "Used reference must not acquire an exported unread-write warning");
        var callList = jsonNodes(main).stream().filter(n -> "CallExpression".equals(n.get("kind"))
                && "evaluate".equals(object(n.get("callee")).get("name"))).toList();
        assertEquals(2, callList.size());
        for (var call : callList) {
            assertEquals(evaluate.get("id"), call.get("staticTargetId"));
            edge(call, "CALL_TARGET", evaluate);
            edge(evaluatorBinding, "dfg.definition", jsonWritten(objects(call.get("arguments")).getFirst()));
            var arguments = objects(call.get("arguments"));
            var parameters = objects(evaluate.get("parameters"));
            for (int i = 0; i < arguments.size(); i++)
                edgeAtCall(arguments.get(i), "dfg.value", parameters.get(i), call);
        }
        var headers = jsonNodes(root).stream().filter(n -> named(n, "FunctionDeclaration", "evaluate") && !n.containsKey("body")).toList();
        assertEquals(2, headers.size());
        for (var declaration : headers) {
            assertEquals(evaluate.get("id"), declaration.get("definitionId"));
            edge(declaration, "ref", evaluate);
        }
        var dispatch = one(jsonNodes(evaluate), n -> "CallExpression".equals(n.get("kind"))
                && "apply".equals(object(n.get("callee")).get("member")));
        var base = index.get(dispatch.get("staticTargetId"));
        assertEquals(true, base.get("pureVirtual"));
        assertEquals(true, dispatch.get("virtualDispatch"));
        assertEquals(false, dispatch.get("virtualTargetsComplete"));
        var targets = list(dispatch.get("virtualTargetIds"));
        assertEquals(1, targets.size());
        var derived = index.get(targets.getFirst());
        assertTrue(derived.containsKey("body"));
        assertEquals(List.of(base.get("id")), list(derived.get("overridesIds")));
        edge(dispatch, "CALL_TARGET", base);
        var concrete = objects(root.get("templateInstances"));
        assertEquals(1, concrete.size());
        var instance = concrete.getFirst();
        var template = object(instance.get("declaration"));
        assertEquals("twice", index.get(instance.get("originId")).get("name"));
        assertEquals("int", object(template.get("returnType")).get("name"));
        assertEquals(true, instance.get("complete"));
        assertEquals(2, list(instance.get("causeIds")).size());
        for (Object causeId : list(instance.get("causeIds"))) {
            var cause = index.get(causeId);
            assertEquals(template.get("id"), cause.get("staticTargetId"));
            edge(cause, "CALL_TARGET", template);
            edge(cause, "INSTANTIATION_CAUSE", instance);
        }
        assertTrue(jsonNodes(derived).stream().anyMatch(n -> list(instance.get("causeIds")).contains(n.get("id"))));
        var addition = one(jsonNodes(template), n -> "AddExpression".equals(n.get("kind")));
        jsonMacro(addition, index, "FINAL_TWICE", "include/pipeline.h", 10, 6);

        var values = one(jsonNodes(main), n -> named(n, "VariableDeclaration", "values"));
        var operations = objects(root.get("dynamicArraySemantics"));
        assertEquals(8, operations.size());
        var append = operations.stream().filter(n -> "append".equals(n.get("operation"))).toList();
        assertEquals(2, append.size());
        for (int i = 0; i < append.size(); i++) {
            var op = append.get(i);
            var target = index.get(op.get("selectedTargetId"));
            assertEquals("push_back", target.get("name"));
            var syntax = index.get(op.get("syntaxId"));
            assertEquals(target.get("id"), syntax.get("staticTargetId"));
            edge(syntax, "COMMON_OPERATION", op);
            edge(op, "CPP_TARGET", target);
            var destination = index.get(op.get("destinationId"));
            edge(destination, "ref", values);
            assertEquals(List.of(destination.get("id")), list(op.get("mutatedObjectIds")));
            edge(op, "MUTATES", destination);
            edge(op, "dfg.definition", values);
            var source = index.get(list(op.get("sourceIds")).getFirst());
            assertEquals(i + 3, number(jsonWritten(source).get("value")));
            edge(source, "dfg.value", op);
        }
        edge(append.getFirst(), "dfg.definition", append.getLast());
        var write = one(jsonNodes(main), n -> "AssignmentExpression".equals(n.get("kind"))
                && "IndexExpression".equals(object(n.get("target")).get("kind")));
        var access = one(operations, n -> Objects.equals(n.get("syntaxId"), object(write.get("target")).get("id")));
        assertEquals(true, access.get("writable"));
        assertEquals(values.get("id"), object(access.get("handle")).get("sequenceId"));
        edge(write, "dfg.definition", values);
        edge(write, "dfg.definition", objects(callList.getLast().get("arguments")).get(1));
        var checked = one(operations, n -> "at".equals(n.get("operation")));
        assertEquals(true, checked.get("boundsChecked"));
        assertEquals(false, checked.get("writable"));
        assertEquals(objects(evaluate.get("parameters")).get(1).get("id"), object(checked.get("handle")).get("sequenceId"));
        var thrown = one(jsonNodes(evaluate), n -> "ThrowExpression".equals(n.get("kind")));
        var literal = object(thrown.get("value"));
        assertEquals(3, number(literal.get("value")));
        jsonMacro(literal, index, "FINAL_ERROR", "evaluate.cpp", 5, 7);
        var exceptional = one(objects(evaluate.get("flowVertices")), n -> "EXCEPTIONAL_EXIT".equals(n.get("kind")));
        edge(thrown, "cfg.throw", exceptional);
        var caught = one(jsonNodes(main), n -> "CatchClause".equals(n.get("kind")));
        edge(callList.getLast(), "cfg.exception", caught);
        var errorRead = one(jsonNodes(caught), n -> named(n, "NameExpression", "error"));
        edge(errorRead, "ref", object(caught.get("parameter")));
        edge(object(caught.get("parameter")), "dfg.definition", errorRead);
    }

    private static void jsonMacro(Map<?, ?> node, Map<Object, Map<?, ?>> index, String name,
            String invocationFile, int invocationLine, int definitionLine) {
        var provenance = object(node.get("provenance"));
        var source = index.get(provenance.get("sourceFileId"));
        var preprocessing = object(source.get("preprocessing"));
        var expansion = one(objects(preprocessing.get("expansions")), n -> name.equals(n.get("name"))
                && list(provenance.get("expansionIds")).contains(n.get("id")));
        var definition = one(objects(preprocessing.get("definitions")), n -> Objects.equals(n.get("id"), expansion.get("definitionId")));
        assertEquals(name, definition.get("name"));
        assertTrue(list(definition.get("expansionIds")).contains(expansion.get("id")));
        assertEquals(definition.get("range"), expansion.get("definition"));
        jsonRange(object(definition.get("range")), "include/pipeline.h", definitionLine);
        jsonRange(object(expansion.get("invocation")), invocationFile, invocationLine);
        assertTrue(objects(preprocessing.get("tokens")).stream().anyMatch(t -> list(t.get("expansionIds")).contains(expansion.get("id"))
                && String.valueOf(object(t.get("spellingRange")).get("file")).endsWith("/include/pipeline.h")
                && number(object(object(t.get("spellingRange")).get("start")).get("line")) == definitionLine));
        if (name.equals("FINAL_TWICE")) {
            assertEquals(1, list(expansion.get("arguments")).size());
            assertEquals("value", objects(list(expansion.get("arguments")).getFirst()).stream()
                    .map(fragment -> String.valueOf(fragment.get("spelling"))).reduce("", String::concat));
        }
    }

    private static void jsonRange(Map<?, ?> range, String path, int line) {
        assertTrue(String.valueOf(range.get("file")).endsWith("/" + path));
        assertEquals(line, number(object(range.get("start")).get("line")));
    }
    private static boolean namedCall(CallExpression call, String name) {
        return call.callee() instanceof NameExpression n && n.name().equals(name);
    }
    private static CallableDeclaration function(Project project, String name) {
        return nodes(project, CallableDeclaration.class).stream().filter(c -> c.name().equals(name) && c.body().isPresent()).findFirst().orElseThrow();
    }
    private static FunctionFlow flow(Project project, CallableDeclaration entry) {
        var result = project.flows().stream().filter(f -> f.entry() == entry).findFirst().orElseThrow();
        assertTrue(result.complete());
        return result;
    }
    private static void assertData(FunctionFlow flow, Node from, Node to, FunctionFlow.Dependency kind) {
        assertTrue(flow.data().stream().anyMatch(e -> e.from() == from && e.to() == to && e.kind() == kind),
                () -> "Missing " + kind + ": " + from.getClass().getSimpleName() + " -> " + to.getClass().getSimpleName());
    }
    private static <T extends Node> List<T> nodes(Node root, Class<T> kind) {
        var result = new ArrayList<T>();
        root.walk(new NodeVisitor() { @Override public void visit(Node n) { if (kind.isInstance(n)) result.add(kind.cast(n)); } });
        return result;
    }
    private static Map<?, ?> jsonWritten(Map<?, ?> node) {
        while ("ConversionExpression".equals(node.get("kind"))) node = object(node.get("operand"));
        return node;
    }
    private static Map<?, ?> object(Object value) { return assertInstanceOf(Map.class, value); }
    private static List<?> list(Object value) { return assertInstanceOf(List.class, value); }
    private static List<Map<?, ?>> objects(Object value) { return list(value).stream().<Map<?, ?>>map(FinalAcceptance::object).toList(); }
    private static int number(Object value) { return assertInstanceOf(Number.class, value).intValue(); }
    private static boolean named(Map<?, ?> value, String kind, String name) { return kind.equals(value.get("kind")) && name.equals(value.get("name")); }
    private static Map<?, ?> one(List<Map<?, ?>> nodes, Predicate<Map<?, ?>> predicate) {
        var selected = nodes.stream().filter(predicate).toList();
        assertEquals(1, selected.size(), () -> "Expected one semantic node, got " + selected.stream().map(n -> n.get("kind") + ":" + n.get("name")).toList());
        return selected.getFirst();
    }
    private static List<Map<?, ?>> jsonNodes(Object root) {
        var result = new ArrayList<Map<?, ?>>();
        collectJson(root, result);
        return result;
    }
    private static void collectJson(Object value, List<Map<?, ?>> result) {
        if (value instanceof Map<?, ?> map) {
            if (map.containsKey("kind") && map.containsKey("id")) result.add(map);
            map.forEach((key, child) -> { if (!Set.of("history", "sourceContexts", "preprocessing").contains(key)) collectJson(child, result); });
        } else if (value instanceof List<?> list) list.forEach(child -> collectJson(child, result));
    }
    private static void edge(Map<?, ?> from, String kind, Map<?, ?> to) {
        assertTrue(objects(from.get("edges")).stream().anyMatch(e -> kind.equals(e.get("kind")) && Objects.equals(to.get("id"), e.get("to"))),
                () -> "Missing JSON " + from.get("id") + " --" + kind + "--> " + to.get("id"));
    }
    private static void edgeAtCall(Map<?, ?> from, String kind, Map<?, ?> to, Map<?, ?> call) {
        assertTrue(objects(from.get("edges")).stream().anyMatch(e -> kind.equals(e.get("kind"))
                && Objects.equals(to.get("id"), e.get("to")) && Objects.equals(call.get("id"), e.get("callSiteId"))));
    }
}
