/* Code Property Graph: deterministic JSON snapshot export. MIT License. */
package com.kniazkov.cpg.serialization;

import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.analysis.ClassSemantics;
import com.kniazkov.cpg.analysis.InheritanceSemantics;
import com.kniazkov.json.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Writes selected syntax ownership as nested fields and non-owning edges as destination IDs.
 * Reachable historical nodes are defined once in {@code history}; their child fields
 * contain {@code nodeId} references. Reference-only historical dependencies are
 * marked {@code historyDependency} and retain identity and source range, not ownership.
 * Earlier project/file nodes are context only,
 * so provenance never implicitly imports unrelated earlier syntax. Ordered pass
 * executions are separate {@code passRuns} records, not provenance dependencies. */
public final class ProjectJson {
    private ProjectJson() {}

    /** Numbers syntax nodes in preorder before encoding any edge, including forward and cyclic references. */
    public static JsonObject encode(Project project) {
        return new Export(project).encode();
    }

    /** Creates a UTF-8 file containing the complete snapshot. Existing files are protected. */
    public static void write(Project project, Path file) throws IOException {
        String text = encode(project).toText("  ") + "\n";
        Files.writeString(file, text, StandardOpenOption.CREATE_NEW);
    }

    private record Edge(String kind, int to, Integer callSiteId) {
        int group() {
            return kind.equals("ref") ? 0 : kind.startsWith("cfg.") ? 1 : 2;
        }
    }

    private static final class Export {
        private final boolean compilationBuild;
        private final Project project;
        private final Map<FlowVertex,Integer> ids = new IdentityHashMap<>();
        private final List<FlowVertex> ordered = new ArrayList<>();
        private final List<Preprocessing> sourceContexts = new ArrayList<>();
        private final Set<FlowVertex> currentNodes = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Set<Node> historicalSyntax = Collections.newSetFromMap(new IdentityHashMap<>());
        private final ArrayDeque<Node> pending = new ArrayDeque<>();
        private int currentCount;
        private boolean discovering;
        private boolean referenceChildren;
        private final Map<Preprocessing,Integer> units = new IdentityHashMap<>();
        private final Map<FlowVertex,List<Edge>> outgoing = new IdentityHashMap<>();

        Export(Project project) {
            this.project = project;
            compilationBuild = project.build().isPresent();
            // Configuration attribution can differ between API and CLI. Number executable
            // syntax and its flow operations first so their IDs do not depend on that catalogue.
            var index = new NodeVisitor() {
                @Override public void visit(Node n) { register(n); }
            };
            index.visit(project);
            project.files().forEach(file -> file.walk(index));
            project.standardLibrary().ifPresent(library -> library.walk(index));
            project.templateInstances().forEach(instance -> instance.walk(index));
            project.classTemplateInstances().forEach(instance -> instance.walk(index));
            project.dynamicArrayTypes().forEach(type -> type.walk(index));
            project.dynamicArraySemantics().forEach(operation -> operation.walk(index));
            project.containerTypes().forEach(type -> type.walk(index));
            project.containerSemantics().forEach(operation -> operation.walk(index));
            project.characterSequenceTypes().forEach(type -> type.walk(index));
            project.sequenceSemantics().forEach(operation -> operation.walk(index));
            for (var flow : project.flows()) for (var vertex : flow.auxiliaryVertices()) register(vertex);
            project.externalInputs().forEach(input -> input.walk(index));
            for (SourceFile file : java.util.stream.Stream.concat(project.files().stream(), project.librarySources().stream()).toList()) file.provenance().ifPresent(p -> units.put(p.unit(), id(file)));
            currentCount = ordered.size();
            currentNodes.addAll(ordered);
            // Discover exactly the fields we serialize, not every node in earlier snapshots.
            // In discovery mode child objects are references, so cycles cannot recurse.
            discovering = true;
            referenceChildren = true;
            for (FlowVertex vertex : ordered) if (vertex instanceof Node n) pending.add(n);
            int contextCursor = 0;
            while (!pending.isEmpty() || contextCursor < sourceContexts.size()) {
                if (!pending.isEmpty()) {
                    Node n = pending.removeFirst();
                    if (!dependencyOnly(n)) {
                        n.references().forEach(this::id);
                        n.commentReferences().forEach(this::syntaxId);
                        fullNode(n);
                    }
                } else {
                    // Detached history can introduce a token unit without retaining its
                    // SourceFile. Discover that unit's attribution dependencies too.
                    preprocessing(sourceContexts.get(contextCursor++));
                }
            }
            discovering = false;
            referenceChildren = false;
            // Attribution trees can differ between API and CLI for identical syntax.
            // Keep historical syntax/context IDs independent of that catalogue too.
            int nextId = 0;
            for (FlowVertex vertex : ordered) if (!(vertex instanceof ExternalInput)) ids.put(vertex, nextId++);
            for (Preprocessing unit : sourceContexts) units.put(unit, nextId++);
            for (FlowVertex vertex : ordered) if (vertex instanceof ExternalInput) ids.put(vertex, nextId++);
            for (FlowVertex vertex : ids.keySet()) if (vertex instanceof Node n && !dependencyOnly(n)) for (Node target : n.references()) edge(n, target, "ref");
            for (FlowVertex vertex : ids.keySet()) if (vertex instanceof Node n && !dependencyOnly(n))
                for (Comment comment : n.commentReferences()) edge(n, comment, "HAS_COMMENT");
            for (FlowVertex vertex : ids.keySet()) {
                if (vertex instanceof Node n && dependencyOnly(n)) continue;
                if (vertex instanceof OpaqueLibraryType type) {
                    type.iteratorContainer().ifPresent(container -> edge(type, container, "ITERATOR_CONTAINER"));
                    type.iteratorDifferenceType().ifPresent(difference -> edge(type, difference, "ITERATOR_DIFFERENCE_TYPE"));
                    type.iteratorValueType().ifPresent(value -> edge(type, value, "ITERATOR_VALUE_TYPE"));
                }
                if (vertex instanceof DynamicArrayType type) {
                    if (type.concreteClass() != null) edge(type, type.concreteClass(), "DYNAMIC_ARRAY_CLASS");
                    edge(type, type.elementType(), "ELEMENT_TYPE");
                    type.writtenTypes().forEach(site -> edge(site, type, "COMMON_TYPE"));
                }
                if (vertex instanceof DynamicArrayOperation operation) {
                    edge(operation.syntax(), operation, "COMMON_OPERATION");
                    edge(operation, operation.type(), "DYNAMIC_ARRAY_TYPE");
                    edge(operation, operation.destination(), "DESTINATION");
                    operation.sources().forEach(source -> edge(operation, source, "SOURCE"));
                    edge(operation, operation.selectedTarget(), "CPP_TARGET");
                    operation.elementCopyConstructor().ifPresent(target -> edge(operation, target, "ELEMENT_COPY_CONSTRUCTOR"));
                    operation.elementDestructor().ifPresent(target -> edge(operation, target, "ELEMENT_DESTRUCTOR"));
                    operation.elementAssignment().ifPresent(target -> edge(operation, target, "ELEMENT_ASSIGNMENT"));
                    operation.index().ifPresent(operand -> edge(operation, operand, "INDEX"));
                    operation.mutatedObjects().forEach(target -> edge(operation, target, "MUTATES"));
                    operation.handle().ifPresent(handle -> {
                        edge(operation, handle.sequence(), "HANDLE_SEQUENCE");
                        handle.position().ifPresent(position -> edge(operation, position, "HANDLE_POSITION"));
                    });
                    operation.handleEffects().forEach(effect -> {
                        edge(operation, effect.producer(), "HANDLE_" + effect.validityAfter().name());
                        effect.sequenceAfter().ifPresent(sequence -> edge(operation, sequence, "SEQUENCE_AFTER"));
                        effect.positionAfter().ifPresent(position -> edge(operation, position, "POSITION_AFTER"));
                    });
                }
            if (vertex instanceof CharacterSequenceType type) {
                type.declaration().ifPresent(value -> edge(type, value, "CHARACTER_SEQUENCE_CLASS"));
                edge(type, type.characterType(), "CHARACTER_TYPE");
                type.traitsType().ifPresent(value -> edge(type, value, "TRAITS_TYPE"));
                type.allocatorType().ifPresent(value -> edge(type, value, "ALLOCATOR_TYPE"));
                type.writtenTypes().forEach(value -> edge(value, type, "COMMON_TYPE"));
            }
            if (vertex instanceof SequenceOperation operation) {
                edge(operation.syntax(), operation, "COMMON_OPERATION");
                edge(operation, operation.selectedTarget(), "CPP_TARGET");
                operation.characterType().ifPresent(value -> edge(operation, value, "CHARACTER_SEQUENCE_TYPE"));
                operation.destination().ifPresent(value -> edge(operation, value, "DESTINATION"));
                operation.inputSequence().ifPresent(value -> edge(operation, value, "INPUT_SEQUENCE"));
                operation.outputDestination().ifPresent(value -> edge(operation, value, "OUTPUT_DESTINATION"));
                operation.first().ifPresent(value -> edge(operation, value, "RANGE_FIRST"));
                operation.last().ifPresent(value -> edge(operation, value, "RANGE_LAST"));
                operation.secondFirst().ifPresent(value -> edge(operation, value, "SECOND_RANGE_FIRST"));
                operation.count().ifPresent(value -> edge(operation, value, "COUNT"));
                operation.position().ifPresent(value -> edge(operation, value, "POSITION"));
                operation.initialValue().ifPresent(value -> edge(operation, value, "INITIAL_VALUE"));
                operation.callable().ifPresent(value -> edge(operation, value, "CALLABLE"));
                operation.callableTarget().ifPresent(value -> edge(operation, value, "CALLABLE_TARGET"));
                operation.resultType().ifPresent(value -> edge(operation, value, "RESULT_TYPE"));
                operation.accumulatorType().ifPresent(value -> edge(operation, value, "ACCUMULATOR_TYPE"));
                operation.sources().forEach(value -> edge(operation, value, "SOURCE"));
                operation.mutatedObjects().forEach(value -> edge(operation, value, "MUTATES"));
                operation.callableEffects().forEach(value -> edge(operation, value, "CALLABLE_EFFECT"));
            }
                if (vertex instanceof ContainerType type) {
                    if (type.concreteClass() != null) edge(type, type.concreteClass(), "CONTAINER_CLASS");
                    edge(type, type.elementType(), "ELEMENT_TYPE");
                    type.keyType().ifPresent(value -> edge(type, value, "KEY_TYPE"));
                    type.mappedType().ifPresent(value -> edge(type, value, "MAPPED_TYPE"));
                    type.comparatorType().ifPresent(value -> edge(type, value, "COMPARATOR_TYPE"));
                    type.underlyingType().ifPresent(value -> edge(type, value, "UNDERLYING_TYPE"));
                    type.writtenTypes().forEach(site -> edge(site, type, "COMMON_TYPE"));
                }
                if (vertex instanceof ContainerOperation operation) {
                    edge(operation.syntax(), operation, "COMMON_OPERATION");
                    edge(operation, operation.type(), "CONTAINER_TYPE");
                    edge(operation, operation.destination(), "DESTINATION");
                    operation.sources().forEach(source -> edge(operation, source, "SOURCE"));
                    edge(operation, operation.selectedTarget(), "CPP_TARGET");
                    operation.comparator().ifPresent(value -> edge(operation, value, "COMPARATOR_TARGET"));
                    operation.sourceContainer().ifPresent(value -> edge(operation, value, "SOURCE_CONTAINER"));
                    operation.elementCopyConstructor().ifPresent(target -> edge(operation, target, "ELEMENT_COPY_CONSTRUCTOR"));
                    operation.elementDestructor().ifPresent(target -> edge(operation, target, "ELEMENT_DESTRUCTOR"));
                    operation.elementAssignment().ifPresent(target -> edge(operation, target, "ELEMENT_ASSIGNMENT"));
                    operation.index().ifPresent(operand -> edge(operation, operand, "INDEX"));
                    operation.mutatedObjects().forEach(target -> edge(operation, target, "MUTATES"));
                    operation.handle().ifPresent(handle -> {
                        edge(operation, handle.sequence(), "HANDLE_SEQUENCE");
                        handle.position().ifPresent(position -> edge(operation, position, "HANDLE_POSITION"));
                    });
                    operation.handleEffects().forEach(effect -> {
                        edge(operation, effect.producer(), "HANDLE_" + effect.validityAfter().name());
                        effect.sequenceAfter().ifPresent(sequence -> edge(operation, sequence, "SEQUENCE_AFTER"));
                        effect.positionAfter().ifPresent(position -> edge(operation, position, "POSITION_AFTER"));
                    });
                }
                if (vertex instanceof VariableDeclaration variable) {
                    variable.constructorTarget().ifPresent(target -> edge(variable, target, "CONSTRUCTOR_TARGET"));
                    variable.destructorTarget().ifPresent(target -> edge(variable, target, "DESTRUCTOR_TARGET"));
                }
                if (vertex instanceof IncludeDirective include) include.libraryHeader().ifPresent(header -> edge(include, header, "LIBRARY_HEADER"));
                if (vertex instanceof Declaration declaration) declaration.templateOrigin().ifPresent(origin -> edge(declaration, origin, "TEMPLATE_ORIGIN"));
            }
            for (FlowVertex vertex : ids.keySet()) if (vertex instanceof CallExpression call && !dependencyOnly(call))
                call.target().ifPresent(target -> edge(call, target, "CALL_TARGET"));
            for (TemplateInstance instance : project.templateInstances()) for (Node request : instance.requests()) {
                edge(request, instance, "INSTANTIATION_CAUSE");
                edge(instance, request, "INSTANTIATION_REQUEST");
            }
            for (ClassTemplateInstance instance : project.classTemplateInstances()) for (Node request : instance.requests()) {
                edge(request, instance, "INSTANTIATION_CAUSE");
                edge(instance, request, "INSTANTIATION_REQUEST");
            }
            for (var flow : project.flows()) {
                for (var e : flow.control()) edge(e.from(), e.to(), "cfg." + lower(e.kind()));
                for (var e : flow.data()) edge(e.from(), e.to(), "dfg." + lower(e.kind()), e.callSite());
            }
        }

        private void register(FlowVertex vertex) {
            if (!ids.containsKey(vertex)) {
                ids.put(vertex, ordered.size());
                ordered.add(vertex);
                if (discovering && vertex instanceof Node n) pending.add(n);
            }
        }

        private Integer id(FlowVertex vertex) {
            if (!ids.containsKey(vertex)) {
                if (!discovering) throw new IllegalStateException("Unindexed JSON dependency: " + vertex);
                register(vertex);
            }
            return ids.get(vertex);
        }

        private boolean dependencyOnly(Node node) {
            return !currentNodes.contains(node) && !historicalSyntax.contains(node);
        }

        /** True history and owned operands retain syntax; semantic targets are summaries. */
        private Integer syntaxId(Node node) {
            int value = id(node);
            if (!currentNodes.contains(node) && historicalSyntax.add(node) && discovering) pending.add(node);
            return value;
        }

        private JsonObject dependency(Node node) {
            var obj = Json.object().put("id", id(node)).put("kind", node.getClass().getSimpleName())
                    .put("historyDependency", true);
            String name = switch (node) {
                case ClassDeclaration n -> n.name();
                case CallableDeclaration n -> n.name();
                case VariableDeclaration n -> n.name();
                case ParameterDeclaration n -> n.name();
                case TypedefDeclaration n -> n.name();
                case NamespaceDeclaration n -> n.name();
                case NamespaceAliasDeclaration n -> n.name();
                case UsingDeclaration n -> n.qualifiedName();
                case UsingDirective n -> n.namespaceName();
                case NameExpression n -> n.name();
                case TemplateParameterDeclaration n -> n.name();
                case EnumDeclaration n -> n.name();
                case EnumeratorDeclaration n -> n.name();
                default -> null;
            };
            if (name != null) obj.put("name", name);
            node.range().ifPresent(value -> obj.put("range", range(value)));
            if (node instanceof TypeNode type) obj.put("canonicalIdentity", type.canonicalIdentity());
            if (node instanceof Declaration declaration)
                declaration.typeIdentity().ifPresent(value -> obj.put("typeIdentity", value));
            return obj.build();
        }

        private JsonObject encode() { return fullNode(project); }

        private JsonObject historyNode(Node n) {
            boolean previous = referenceChildren;
            referenceChildren = true;
            try { return dependencyOnly(n) ? dependency(n) : fullNode(n); }
            finally { referenceChildren = previous; }
        }

        private static String lower(Enum<?> kind) { return kind.name().toLowerCase(Locale.ROOT); }

        private void edge(FlowVertex from, FlowVertex to, String kind) {
            edge(from, to, kind, null);
        }

        private void edge(FlowVertex from, FlowVertex to, String kind, CallExpression callSite) {
            if (!ids.containsKey(from) || !ids.containsKey(to) || callSite != null && !ids.containsKey(callSite))
                throw new IllegalStateException("Edge outside project");
            outgoing.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new Edge(kind, id(to), callSite == null ? null : id(callSite)));
        }

        private JsonArray nodes(List<? extends Node> children) {
            var array = Json.array();
            children.forEach(child -> array.add(node(child)));
            return array.build();
        }

        private JsonObject passRun(PassRun run) {
            var object = Json.object().put("sequence", run.sequence()).put("passId", run.passId())
                    .put("scope", lower(run.scope())).put("status", lower(run.status()));
            if (run.custom()) object.put("custom", true);
            if (!run.requirements().isEmpty()) object.array("requires", values -> run.requirements().stream().sorted().forEach(values::add));
            return object.array("results", results -> run.results().forEach(result -> results.object(value -> {
                        value.put("name", result.name()).put("status", lower(result.status()));
                        result.range().ifPresent(source -> value.put("range", range(source)));
                        // The result points only to this snapshot's owned callable. Deleted
                        // callables retain their summary without reimporting their old body.
                        result.function().ifPresent(function -> value.put("functionId", id(function)));
                    }))).build();
        }

        /** Result payloads are detached values, never syntax ownership or history edges. */
        private JsonObject analysisResult(AnalysisResult<?> result) {
            return Json.object().put("producer", result.key().producer().value())
                    .put("name", result.key().name()).put("type", result.key().type().getName())
                    .put("status", lower(result.status())).put("nodeId", id(result.node()))
                    .put("value", resultValue(result.encodedValue())).build();
        }

        private JsonObject analysisDiagnostic(AnalysisDiagnostic finding) {
            Diagnostic diagnostic = finding.diagnostic();
            var object = Json.object().put("producer", finding.producer().value())
                    .put("status", lower(finding.status())).put("nodeId", id(finding.node()))
                    .put("code", diagnostic.code()).put("severity", lower(diagnostic.severity()))
                    .put("message", diagnostic.message());
            if (diagnostic.primaryRange() != null) object.put("primaryRange", range(diagnostic.primaryRange()));
            else finding.node().range().ifPresent(value -> object.put("primaryRange", range(value)));
            if (!diagnostic.relatedRanges().isEmpty()) object.array("relatedRanges", values ->
                    diagnostic.relatedRanges().forEach(value -> values.object(item -> item
                            .put("role", value.role()).put("label", value.label()).put("range", range(value.range())))));
            return object.build();
        }

        /** Builds a fresh JSON tree from the registry's already validated immutable copy.
         * No custom encoder runs here, so stateful encoders cannot change a snapshot. */
        private static JsonElement resultValue(Object value) {
            if (value == null) return JsonNull.INSTANCE;
            if (value instanceof String text) return new JsonString(text);
            if (value instanceof Boolean flag) return JsonBoolean.of(flag);
            if (value instanceof Number number) return JsonNumber.of(number);
            if (value instanceof Map<?, ?> map) {
                var object = Json.object();
                map.forEach((key, item) -> object.put((String) key, resultValue(item)));
                return object.build();
            }
            if (value instanceof List<?> list) {
                var array = Json.array();
                list.forEach(item -> array.add(resultValue(item)));
                return array.build();
            }
            throw new IllegalStateException("Unencoded analysis result value");
        }

        private JsonObject node(Node n) {
            if (referenceChildren) return Json.object().put("nodeId", syntaxId(n)).build();
            return fullNode(n);
        }

        private JsonObject fullNode(Node n) {
            boolean historical = !currentNodes.contains(n);
            var obj = Json.object().put("id", id(n)).put("kind", n.getClass().getSimpleName());
            switch (n) {
                case Project p -> {
                    obj.put("schemaVersion", 1).put("stage", lower(p.snapshotStage()));
                    if (historical) break; // Context, never ownership of an entire old snapshot.
                    if (!discovering) {
                        obj.array("passRuns", a -> p.passRuns().forEach(run -> a.add(passRun(run))));
                        if (!p.analysisResults().isEmpty()) obj.array("analysisResults", values -> p.analysisResults().stream()
                                .filter(result -> currentNodes.contains(result.node()))
                                .sorted(Comparator.comparing((AnalysisResult<?> result) -> result.key().producer().value())
                                        .thenComparing(result -> result.key().name()).thenComparing(result -> result.key().type().getName())
                                        .thenComparingInt(result -> id(result.node())))
                                .forEach(result -> values.add(analysisResult(result))));
                        if (!p.analysisDiagnostics().isEmpty()) obj.array("analysisDiagnostics", values -> p.analysisDiagnostics().stream()
                                .filter(finding -> currentNodes.contains(finding.node()))
                                .sorted(Comparator.comparing((AnalysisDiagnostic finding) -> finding.producer().value())
                                        .thenComparingInt(finding -> id(finding.node())).thenComparing(finding -> finding.diagnostic().code())
                                        .thenComparing(finding -> finding.diagnostic().severity()).thenComparing(finding -> finding.diagnostic().message()))
                                .forEach(finding -> values.add(analysisDiagnostic(finding))));
                        obj.array("history", a -> {
                            for (int i = currentCount; i < ordered.size(); i++)
                                if (ordered.get(i) instanceof Node old) a.add(historyNode(old));
                        });
                        if (!sourceContexts.isEmpty()) obj.array("sourceContexts", a -> sourceContexts.forEach(unit ->
                                a.object(context -> context.put("id", units.get(unit)).put("kind", "SourceContext")
                                        .put("preprocessing", preprocessing(unit)))));
                    }
                    obj.put("files", nodes(p.files()));
                    p.standardLibrary().ifPresent(library -> obj.put("standardLibrary", node(library)));
                    if (!p.librarySources().isEmpty()) obj.array("librarySources", a -> p.librarySources().forEach(header -> a.add(id(header))));
                    if (!p.templateInstances().isEmpty()) obj.put("templateInstances", nodes(p.templateInstances()));
                    if (!p.classTemplateInstances().isEmpty()) obj.put("classTemplateInstances", nodes(p.classTemplateInstances()));
                    if (!p.dynamicArrayTypes().isEmpty()) obj.put("dynamicArrayTypes", nodes(p.dynamicArrayTypes()));
                    if (!p.dynamicArraySemantics().isEmpty()) obj.put("dynamicArraySemantics", nodes(p.dynamicArraySemantics()));
                    if (!p.containerTypes().isEmpty()) obj.put("containerTypes", nodes(p.containerTypes()));
                    if (!p.containerSemantics().isEmpty()) obj.put("containerSemantics", nodes(p.containerSemantics()));
                    if (!p.characterSequenceTypes().isEmpty()) obj.put("characterSequenceTypes", nodes(p.characterSequenceTypes()));
                    if (!p.sequenceSemantics().isEmpty()) obj.put("sequenceSemantics", nodes(p.sequenceSemantics()));
                    if (p.hasErrors()) {
                        obj.put("hasErrors", true);
                        obj.array("diagnosticGroups", a -> p.diagnosticGroups().forEach(g -> a.add(diagnosticGroup(g))));
                    }
                    if (!p.externalInputs().isEmpty()) obj.put("externalInputs", nodes(p.externalInputs()));
                    p.build().ifPresent(b -> obj.object("build", v -> {
                        v.array("units", a -> b.units().forEach(u -> a.add(configuration(u))));
                        p.composition().ifPresent(c -> v.object("composition", o -> o.put("name", c.name())
                                .array("variantIds", a -> a.addAll(c.variantIds()))));
                    }));
                }
                case DynamicArrayType type -> {
                    obj.put("elementTypeId", id(type.elementType()))
                        .array("writtenTypeIds", a -> type.writtenTypes().forEach(site -> a.add(id(site))));
                    if (type.concreteClass() != null) obj.put("concreteClassId", id(type.concreteClass()));
                }
                case DynamicArrayOperation operation -> {
                    obj.put("operation", lower(operation.kind())).put("syntaxId", id(operation.syntax()))
                        .put("destinationId", id(operation.destination())).put("typeId", id(operation.type()))
                        .put("selectedTargetId", id(operation.selectedTarget()))
                        .array("sourceIds", a -> operation.sources().forEach(source -> a.add(id(source))));
                    operation.elementCopyConstructor().ifPresent(target -> obj.put("elementCopyConstructorId", id(target)));
                    operation.elementDestructor().ifPresent(target -> obj.put("elementDestructorId", id(target)));
                    operation.elementAssignment().ifPresent(target -> obj.put("elementAssignmentId", id(target)));
                    obj.put("resultKind", lower(operation.resultKind())).put("writable", operation.writable())
                        .put("boundsChecked", operation.boundsChecked())
                        .array("mutatedObjectIds", a -> operation.mutatedObjects().forEach(target -> a.add(id(target))));
                    operation.index().ifPresent(operand -> obj.put("indexId", id(operand)));
                    operation.handle().ifPresent(handle -> obj.object("handle", h -> {
                        h.put("kind", lower(handle.kind())).put("sequenceId", id(handle.sequence()))
                            .put("direction", lower(handle.direction())).put("positionKind", lower(handle.positionKind()))
                            .put("writable", handle.writable()).put("validity", lower(handle.validity()));
                        handle.position().ifPresent(position -> h.put("positionId", id(position)));
                    }));
                    obj.array("handleEffects", a -> operation.handleEffects().forEach(effect -> a.object(e -> {
                        e.put("producerId", id(effect.producer())).put("validityAfter", lower(effect.validityAfter()));
                        effect.sequenceAfter().ifPresent(sequence -> e.put("sequenceAfterId", id(sequence)));
                        effect.positionAfter().ifPresent(position -> e.put("positionAfterId", id(position)));
                    })));
                }
                case CharacterSequenceType type -> {
                    obj.put("characterTypeId", id(type.characterType()));
                    type.declaration().ifPresent(value -> obj.put("concreteClassId", id(value)));
                    type.traitsType().ifPresent(value -> obj.put("traitsTypeId", id(value)));
                    type.allocatorType().ifPresent(value -> obj.put("allocatorTypeId", id(value)));
                    obj.array("writtenTypeIds", a -> type.writtenTypes().forEach(value -> a.add(id(value))));
                }
                case SequenceOperation operation -> {
                    obj.put("operation", lower(operation.kind())).put("syntaxId", id(operation.syntax()))
                        .put("selectedTargetId", id(operation.selectedTarget()))
                        .put("lengthKind", lower(operation.lengthKind())).put("resultKind", lower(operation.resultKind()))
                        .put("direction", lower(operation.direction())).put("sizeEffect", lower(operation.sizeEffect()))
                        .put("writable", operation.writable()).put("callableEffect", lower(operation.callableEffect()))
                        .put("callableMayThrow", operation.callableMayThrow());
                    operation.characterType().ifPresent(value -> obj.put("characterTypeId", id(value)));
                    operation.destination().ifPresent(value -> obj.put("destinationId", id(value)));
                    operation.inputSequence().ifPresent(value -> obj.put("inputSequenceId", id(value)));
                    operation.outputDestination().ifPresent(value -> obj.put("outputDestinationId", id(value)));
                    operation.first().ifPresent(value -> obj.put("firstId", id(value)));
                    operation.last().ifPresent(value -> obj.put("lastId", id(value)));
                    operation.secondFirst().ifPresent(value -> obj.put("secondFirstId", id(value)));
                    operation.count().ifPresent(value -> obj.put("countId", id(value)));
                    operation.position().ifPresent(value -> obj.put("positionId", id(value)));
                    operation.initialValue().ifPresent(value -> obj.put("initialValueId", id(value)));
                    operation.callable().ifPresent(value -> obj.put("callableId", id(value)));
                    operation.callableTarget().ifPresent(value -> obj.put("callableTargetId", id(value)));
                    operation.resultType().ifPresent(value -> obj.put("resultTypeId", id(value)));
                    operation.accumulatorType().ifPresent(value -> obj.put("accumulatorTypeId", id(value)));
                    obj.array("sourceIds", a -> operation.sources().forEach(value -> a.add(id(value))));
                    obj.array("mutatedObjectIds", a -> operation.mutatedObjects().forEach(value -> a.add(id(value))));
                    obj.array("callableEffectIds", a -> operation.callableEffects().forEach(value -> a.add(id(value))));
                }
                case ContainerType type -> {
                    obj.put("kind", lower(type.kind())).put("iteratorCategory", lower(type.iteratorCategory()))
                        .put("order", lower(type.order())).put("referenceStability", lower(type.referenceStability()))
                        .put("uniqueKeys", type.uniqueKeys()).put("valueTypeId", id(type.valueType()));
                    type.keyType().ifPresent(value -> obj.put("keyTypeId", id(value)));
                    type.mappedType().ifPresent(value -> obj.put("mappedTypeId", id(value)));
                    type.comparatorType().ifPresent(value -> obj.put("comparatorTypeId", id(value)));
                    type.underlyingType().ifPresent(value -> obj.put("underlyingTypeId", id(value)));
                    obj.put("elementTypeId", id(type.elementType()))
                        .array("writtenTypeIds", a -> type.writtenTypes().forEach(site -> a.add(id(site))));
                    if (type.concreteClass() != null) obj.put("concreteClassId", id(type.concreteClass()));
                }
                case ContainerOperation operation -> {
                    obj.put("operation", lower(operation.kind())).put("syntaxId", id(operation.syntax()))
                        .put("destinationId", id(operation.destination())).put("typeId", id(operation.type()))
                        .put("selectedTargetId", id(operation.selectedTarget()))
                        .array("sourceIds", a -> operation.sources().forEach(source -> a.add(id(source))));
                    operation.elementCopyConstructor().ifPresent(target -> obj.put("elementCopyConstructorId", id(target)));
                    operation.elementDestructor().ifPresent(target -> obj.put("elementDestructorId", id(target)));
                    operation.elementAssignment().ifPresent(target -> obj.put("elementAssignmentId", id(target)));
                    operation.comparator().ifPresent(value -> obj.put("comparatorId", id(value)));
                    operation.sourceContainer().ifPresent(value -> obj.put("sourceContainerId", id(value)));
                    obj.put("mayInsert", operation.mayInsert()).put("preservesTransferredReferences", operation.preservesTransferredReferences());
                    if (operation.kind() == ContainerOperation.Kind.SPLICE) obj.put("transferKind", lower(operation.transferKind()));
                    obj.put("resultKind", lower(operation.resultKind())).put("writable", operation.writable())
                        .put("boundsChecked", operation.boundsChecked())
                        .array("mutatedObjectIds", a -> operation.mutatedObjects().forEach(target -> a.add(id(target))));
                    operation.index().ifPresent(operand -> obj.put("indexId", id(operand)));
                    operation.handle().ifPresent(handle -> obj.object("handle", h -> {
                        h.put("kind", lower(handle.kind())).put("sequenceId", id(handle.sequence()))
                            .put("direction", lower(handle.direction())).put("positionKind", lower(handle.positionKind()))
                            .put("writable", handle.writable()).put("validity", lower(handle.validity()));
                        handle.position().ifPresent(position -> h.put("positionId", id(position)));
                    }));
                    obj.array("handleEffects", a -> operation.handleEffects().forEach(effect -> a.object(e -> {
                        e.put("producerId", id(effect.producer())).put("validityAfter", lower(effect.validityAfter()));
                        effect.sequenceAfter().ifPresent(sequence -> e.put("sequenceAfterId", id(sequence)));
                        effect.positionAfter().ifPresent(position -> e.put("positionAfterId", id(position)));
                    })));
                }
                case StandardLibrary library -> obj.put("headers", nodes(library.headers()));
                case ClassTemplateInstance instance -> {
                    obj.put("originId", id(instance.origin())).put("selectedId", id(instance.selected()))
                        .put("sourceId", id(instance.source())).put("identity", instance.identity())
                        .array("requestIds", a -> instance.requests().forEach(request -> a.add(id(request))))
                        .put("arguments", nodes(instance.arguments())).put("declaration", node(instance.declaration()));
                }
                case TemplateInstance instance -> {
                    obj.put("originId", id(instance.origin())).put("selectedId", id(instance.selected()))
                        .array("requestIds", a -> instance.requests().forEach(request -> a.add(id(request))))
                        .array("causeIds", a -> instance.causes().forEach(cause -> a.add(id(cause))))
                        .put("sourceId", id(instance.source())).put("status", instance.status()).put("complete", instance.complete())
                        .put("structuredArguments", nodes(instance.structuredArguments()))
                        .array("structuredArgumentIds", a -> instance.structuredArguments().forEach(argument -> a.add(id(argument))))
                        .array("argumentIds", a -> instance.arguments().forEach(argument -> a.add(id(argument))))
                        .put("declaration", node(instance.declaration()));
                    if (instance.cause() != null) obj.put("causeId", id(instance.cause()));
                }
                case ExternalInput input -> obj.put("key", input.key()).put("role", input.role())
                        .put("text", input.text()).put("inputs", nodes(input.inputs()));
                case Comment comment -> {
                    obj.put("commentKind", comment.kind().name()).put("text", comment.text());
                    if (!comment.documentationEntries().isEmpty()) obj.put("documentationEntries", nodes(comment.documentationEntries()));
                }
                case DocumentationEntry entry -> {
                    obj.put("documentationKind", entry.kind().name()).put("name", entry.name()).put("description", entry.description());
                    entry.nameRange().ifPresent(r -> obj.put("nameRange", range(r)));
                    entry.tagRange().ifPresent(r -> obj.put("tagRange", range(r)));
                    obj.array("bindings", a -> entry.bindings().forEach(binding -> a.object(b -> {
                        b.put("declarationId", id(binding.declaration()));
                        if (binding.target() != null) b.put("targetId", id(binding.target()));
                        binding.declaration().provenance().ifPresent(p -> {
                            b.put("translationUnitId", units.get(p.unit()));
                            if (p.startToken() < p.unit().tokens().size()) b.put("includeId", p.unit().tokens().get(p.startToken()).includeId());
                        });
                    })));
                }
                case TemplateParameterDeclaration parameter -> {
                    obj.put("name", parameter.name()).put("keyword", parameter.keyword());
                    if (parameter.kind() != TemplateParameterDeclaration.Kind.TYPE) obj.put("parameterKind", parameter.kind().name());
                    parameter.type().ifPresent(type -> obj.put("type", node(type)));
                    parameter.defaultArgument().ifPresent(argument -> obj.put("defaultArgument", node(argument)));
                    if (!parameter.templateParameters().isEmpty()) obj.put("templateParameters", nodes(parameter.templateParameters()));
                }
                case TemplateArgument argument -> {
                    obj.put("argumentKind", argument.kind().name());
                    argument.type().ifPresent(type -> obj.put("type", node(type)));
                    argument.expression().ifPresent(expression -> obj.put("expression", node(expression)));
                }
                case SourceFile f -> {
                    if (project.librarySources().contains(f)) obj.put("libraryDeclaration", true);
                    obj.put("path", f.range().map(SourceRange::file)
                                .orElseGet(() -> Path.of(f.path()).toAbsolutePath().normalize().toString()))
                        .put("language", f.language().name());
                    if (!historical) obj.put("items", nodes(f.items()));
                    if (!historical && !f.comments().isEmpty()) obj.put("comments", nodes(f.comments()));
                    if (f.hasErrors()) obj.put("hasErrors", true);
                    if (!f.parseProblems().isEmpty()) obj.array("parseProblems", a ->
                            f.parseProblems().forEach(problem -> a.add(problem(problem))));
                    f.stoppedAt().ifPresent(position -> obj.put("stoppedAt", range(position)));
                }
                case NamespaceDeclaration namespace -> obj.put("name", namespace.name()).put("anonymous", namespace.anonymous())
                        .put("members", nodes(namespace.members()));
                case NamespaceAliasDeclaration alias -> {
                    obj.put("name", alias.name()).put("targetName", alias.targetName());
                    alias.target().ifPresent(target -> obj.put("targetId", id(target)));
                }
                case UsingDirective using -> {
                    obj.put("namespaceName", using.namespaceName());
                    using.target().ifPresent(target -> obj.put("targetId", id(target)));
                }
                case UsingDeclaration using -> {
                    obj.put("qualifiedName", using.qualifiedName());
                    using.target().ifPresent(target -> obj.put("targetId", id(target)));
                }
                case EnumDeclaration enumeration -> obj.put("name", enumeration.name()).put("enumerators", nodes(enumeration.enumerators()));
                case EnumeratorDeclaration value -> {
                    obj.put("name", value.name());
                    value.value().ifPresent(expression -> obj.put("value", node(expression)));
                }
                case ClassDeclaration c -> {
                    if (c.templateKind() != TemplateKind.NONE) obj.put("templateKind", c.templateKind().name());
                    if (c.exportedTemplate()) obj.put("exportedTemplate", true);
                    if (c.hasTemplateArguments()) obj.put("templateArguments", nodes(c.templateArguments()));
                    if (!c.templateParameters().isEmpty()) obj.put("templateParameters", nodes(c.templateParameters()));
                    if (c.isDefinition() && c.members().stream().anyMatch(member -> member.access().isPresent())) {
                        obj.put("aggregate", ClassSemantics.aggregate(c));
                        obj.array("specialMembers", values -> ClassSemantics.specialMembers(c).forEach(member -> values.add(Json.object()
                                .put("kind", member.kind().name()).put("origin", member.origin().name())
                                .array("declarationIds", declarations -> member.declarations().forEach(declaration -> declarations.add(id(declaration)))).build())));
                    }
                    if (!c.bases().isEmpty()) obj.put("bases", nodes(c.bases()));
                    if (c.implicit()) obj.put("implicit", true);
                    obj.put("name", c.name()).array("modifiers", a -> a.addAll(c.modifiers())).put("members", nodes(c.members()));
                    if (!c.keyword().equals("class")) obj.put("keyword", c.keyword());
                    if (!c.memberAccess().equals("public")) obj.put("memberAccess", c.memberAccess());
                }
                case CallableDeclaration c -> {
                    if (c.templateKind() != TemplateKind.NONE) obj.put("templateKind", c.templateKind().name());
                    if (c.exportedTemplate()) obj.put("exportedTemplate", true);
                    if (c.hasTemplateArguments()) obj.put("templateArguments", nodes(c.templateArguments()));
                    project.flows().stream().filter(flow -> flow.entry() == c).findFirst().ifPresent(flow -> {
                        if (!flow.complete()) obj.put("flowComplete", false);
                        if (!flow.auxiliaryVertices().isEmpty()) obj.array("flowVertices", a -> flow.auxiliaryVertices().forEach(v -> a.add(auxiliary(v))));
                    });
                    if (!c.bodyComplete()) obj.put("bodyComplete", false);
                    if (c.hasExceptionSpecification()) obj.put("hasExceptionSpecification", true).put("exceptionTypes", nodes(c.exceptionTypes()));
                    if (c.functionTryBlock()) obj.put("functionTryBlock", true);
                    if (!c.handlers().isEmpty()) obj.put("handlers", nodes(c.handlers()));
                    if (c.kind() != CallableDeclaration.Kind.FUNCTION) obj.put("callableKind", c.kind().name());
                    if (!c.initializers().isEmpty()) obj.put("initializers", nodes(c.initializers()));
                    obj.put("name", c.name()).array("modifiers", a -> a.addAll(c.modifiers()));
                    c.returnType().ifPresent(t -> obj.put("returnType", node(t)));
                    c.conversionType().ifPresent(t -> obj.put("conversionType", node(t)));
                    c.writtenType().ifPresent(t -> obj.put("writtenType", node(t)));
                    obj.put("parameters", nodes(c.parameters()));
                    if (!c.templateParameters().isEmpty()) obj.put("templateParameters", nodes(c.templateParameters()));
                    c.body().ifPresent(b -> obj.put("body", node(b)));
                    obj.put("exit", node(c.exit()));
                    if (c.variadic()) obj.put("variadic", true);
                    if (c.isConst()) obj.put("const", true);
                    if (c.isVolatile()) obj.put("volatile", true);
                    if (c.isPureVirtual()) obj.put("pureVirtual", true);
                    if (!historical) {
                        if (InheritanceSemantics.isVirtual(project, c)) obj.put("virtual", true);
                        var overrides = InheritanceSemantics.overrides(project, c);
                        var overriddenBy = InheritanceSemantics.overriddenBy(project, c);
                        if (!overrides.isEmpty()) obj.array("overridesIds", a -> overrides.forEach(method -> a.add(id(method))));
                        if (!overriddenBy.isEmpty()) obj.array("overriddenByIds", a -> overriddenBy.forEach(method -> a.add(id(method))));
                    }
                }
                case BlockStatement b -> { obj.put("items", nodes(b.items())); if (!b.complete()) obj.put("complete", false); }
                case VariableDeclaration v -> {
                    v.constructorTarget().ifPresent(target -> obj.put("constructorTargetId", id(target)));
                    v.destructorTarget().ifPresent(target -> obj.put("destructorTargetId", id(target)));
                    v.bitWidth().ifPresent(value -> obj.put("bitWidth", node(value)));
                    obj.put("name", v.name());
                    if (!v.modifiers().isEmpty()) obj.array("modifiers", a -> a.addAll(v.modifiers()));
                    v.type().ifPresent(t -> obj.put("type", node(t)));
                    v.initializer().ifPresent(i -> obj.put("initializer", node(i)));
                }
                case BaseSpecifier b -> obj.put("access", b.access()).put("virtual", b.isVirtual()).put("type", node(b.type()));
                case InitializationExpression i -> {
                    obj.put("form", i.form().name()).put("arguments", nodes(i.arguments()));
                    i.type().ifPresent(type -> obj.put("type", node(type)));
                }
                case MemberInitializer i -> { obj.put("name", i.name()).put("arguments", nodes(i.arguments())); i.target().ifPresent(target -> obj.put("targetId", id(target))); }
                case ParameterDeclaration p -> {
                    obj.put("name", p.name());
                    if (!p.modifiers().isEmpty()) obj.array("modifiers", a -> a.addAll(p.modifiers()));
                    p.type().ifPresent(t -> obj.put("type", node(t)));
                    p.defaultValue().ifPresent(value -> obj.put("defaultValue", node(value)));
                }
                case OpaqueLibraryType t -> {
                    obj.put("name", t.name()).put("identity", t.identity()).put("category", t.category().name());
                    t.iteratorContainer().ifPresent(container -> obj.put("iteratorContainerId", id(container)));
                    t.iteratorDifferenceType().ifPresent(difference -> obj.put("iteratorDifferenceTypeId", id(difference)));
                    t.iteratorValueType().ifPresent(value -> obj.put("iteratorValueTypeId", id(value)));
                }
                case NamedType t -> {
                    obj.put("name", t.name());
                    if (t.hasTemplateArguments()) obj.put("templateArguments", nodes(t.templateArguments()));
                    t.qualifier().ifPresent(qualifier -> obj.put("qualifier", node(qualifier)));
                    if (t.typenameKeyword()) obj.put("typenameKeyword", true);
                    if (t.templateKeyword()) obj.put("templateKeyword", true);
                    if (t.dependent()) obj.put("dependent", true);
                    t.elaboratedKeyword().ifPresent(keyword -> obj.put("elaboratedKeyword", keyword));
                    t.alias().ifPresent(a -> obj.put("aliasId", id(a)).put("canonicalIdentity", t.canonicalIdentity()));
                    t.declaration().ifPresent(declaration -> obj.put("declarationId", id(declaration))
                            .put("completeAtUse", t.completeAtUse()));
                    if (!t.resolutionSteps().isEmpty()) obj.array("resolutionSteps", steps -> t.resolutionSteps().forEach(step ->
                            steps.object(value -> value.put("kind", step.kind()).put("viaId", id(step.via())).put("targetId", id(step.target())))));
                }
                case TypedefDeclaration d -> obj.put("name", d.name()).put("type", node(d.type()));
                case QualifiedType t -> obj.put("baseType", node(t.baseType())).put("const", t.isConst()).put("volatile", t.isVolatile());
                case PointerType t -> obj.put("pointee", node(t.pointee()));
                case ReferenceType t -> obj.put("referredType", node(t.referredType()));
                case ArrayType t -> { obj.put("elementType", node(t.elementType())); t.bound().ifPresent(b -> obj.put("bound", node(b))); }
                case MemberPointerType t -> obj.put("ownerType", node(t.ownerType())).put("pointee", node(t.pointee()));
                case FunctionType t -> { obj.put("returnType", node(t.returnType())).put("parameters", nodes(t.parameters()))
                        .put("variadic", t.variadic()).put("const", t.isConst()).put("volatile", t.isVolatile());
                    if (t.hasExceptionSpecification()) obj.put("hasExceptionSpecification", true).put("exceptionTypes", nodes(t.exceptionTypes()));
                }
                case OffsetofExpression e -> obj.put("type", node(e.type())).put("steps", nodes(e.steps()));
                case SetjmpExpression e -> obj.put("buffer", node(e.buffer())).put("emission", e.emission()).put("stateRepresentation", "opaque").put("returnsTwice", true);
                case VaListExpression e -> {
                    obj.put("operation", lower(e.kind())).put("list", node(e.list()))
                            .put("emission", e.emission()).put("stateRepresentation", "opaque");
                    e.lastParameter().ifPresent(value -> obj.put("lastParameter", node(value)));
                    e.requestedType().ifPresent(value -> obj.put("requestedType", node(value)));
                }
                case OffsetofExpression.Member member -> {
                    obj.put("name", member.name()); member.target().ifPresent(target -> obj.put("targetId", id(target)));
                }
                case OffsetofExpression.Index index -> obj.put("expression", node(index.expression()));
                case SizeofExpression e -> {
                    e.type().ifPresent(t -> obj.put("type", node(t)));
                    e.operand().ifPresent(operand -> obj.put("operand", node(operand)));
                }
                case TypeidExpression e -> {
                    e.type().ifPresent(t -> obj.put("type", node(t)));
                    e.operand().ifPresent(operand -> obj.put("operand", node(operand)));
                }
                case NewExpression e -> obj.put("type", node(e.type()))
                        .put("placementArguments", nodes(e.placementArguments()))
                        .put("initializerArguments", nodes(e.initializerArguments()))
                        .put("hasInitializer", e.hasInitializer()).put("global", e.isGlobal())
                        .put("parenthesizedType", e.isParenthesizedType());
                case DeleteExpression e -> obj.put("operand", node(e.operand()))
                        .put("array", e.isArray()).put("global", e.isGlobal());
                case NameExpression e -> {
                    obj.put("name", e.name());
                    if (e.hasTemplateArguments()) obj.put("templateArguments", nodes(e.templateArguments()));
                    if (e.templateKeyword()) obj.put("templateKeyword", true);
                }
                case IntegerLiteral i -> { obj.put("value", i.bigValue()); i.cppType().ifPresent(t -> obj.put("cppType", t));
                    i.spelling().ifPresent(spelling -> obj.put("spelling", spelling)); }
                case BooleanLiteral i -> obj.put("value", i.value()).put("spelling", i.spelling());
                case FloatingLiteral i -> obj.put("value", i.value()).put("cppType", i.cppType()).put("spelling", i.spelling());
                case CompilerIntrinsicExpression i -> obj.put("intrinsic", lower(i.kind())).put("cppType", i.cppType())
                        .put("emission", i.emission());
                case CharacterLiteral i -> obj.put("value", i.value()).put("wide", i.wide()).put("cppType", i.cppType()).put("spelling", i.spelling())
                        .put("codeUnitBits", i.codeUnitBits()).put("valueBits", i.valueBits());
                case StringLiteral i -> obj.array("codeUnits", a -> i.codeUnits().forEach(a::add))
                        .put("wide", i.wide()).put("cppType", i.cppType()).put("spelling", i.spelling());
                case ConversionExpression c -> {
                    obj.put("operand", node(c.operand())).put("targetType", node(c.targetType()))
                            .put("explicit", c.isExplicit()).put("conversionKind", lower(c.kind()));
                    c.sourceType().ifPresent(type -> obj.put("sourceType", type));
                }
                case CompoundAssignmentExpression a -> obj.put("target", node(a.target())).put("value", node(a.value())).put("operator", a.operator());
                case ConditionalExpression c -> obj.put("condition", node(c.condition())).put("thenExpression", node(c.thenExpression()))
                        .put("elseExpression", node(c.elseExpression()));
                case ErrorExpression e -> obj.put("problem", problem(e.problem()));
                case IncludeDirective i -> {
                    obj.put("header", i.header()).put("system", i.system());
                    i.libraryHeader().ifPresent(header -> obj.put("libraryHeaderId", id(header)));
                }
                case IfStatement i -> {
                    obj.put("condition", node(i.condition())).put("thenBranch", node(i.thenBranch()));
                    i.elseBranch().ifPresent(b -> obj.put("elseBranch", node(b)));
                }
                case WhileStatement w -> obj.put("condition", node(w.condition())).put("body", node(w.body()));
                case EmptyStatement ignored -> { }
                case BreakStatement ignored -> { }
                case ContinueStatement ignored -> { }
                case GotoStatement g -> obj.put("label", g.label());
                case LabelStatement l -> obj.put("label", l.label()).put("statement", node(l.statement()));
                case DoWhileStatement d -> obj.put("body", node(d.body())).put("condition", node(d.condition()));
                case ForStatement f -> {
                    obj.put("initializers", nodes(f.initializers())).put("body", node(f.body()));
                    f.condition().ifPresent(c -> obj.put("condition", node(c)));
                    f.update().ifPresent(u -> obj.put("update", node(u)));
                }
                case SwitchStatement s -> obj.put("condition", node(s.condition())).put("body", node(s.body()));
                case CaseStatement c -> { c.value().ifPresent(v -> obj.put("value", node(v))); obj.put("default", c.value().isEmpty()).put("statement", node(c.statement())); }
                case TryStatement t -> obj.put("body", node(t.body())).put("handlers", nodes(t.handlers()));
                case CatchClause c -> { c.parameter().ifPresent(p -> obj.put("parameter", node(p))); obj.put("catchAll", c.parameter().isEmpty()).put("body", node(c.body())); }
                case ThrowExpression t -> { obj.put("rethrow", t.value().isEmpty()); t.value().ifPresent(v -> obj.put("value", node(v))); }
                case ConditionDeclarationExpression c -> obj.put("declaration", node(c.declaration()));
                case ReturnStatement r -> r.value().ifPresent(v -> obj.put("value", node(v)));
                case PrintStatement p -> obj.put("value", node(p.value()));
                case ExpressionStatement e -> obj.put("expression", node(e.expression()));
                case AssignmentExpression a -> obj.put("target", node(a.target())).put("value", node(a.value()));
                case CallExpression c -> { obj.put("callee", node(c.callee())).put("arguments", nodes(c.arguments()))
                        .put("resolution", lower(c.resolution()))
                        .array("candidateIds", a -> c.candidates().forEach(candidate -> a.add(id(candidate))));
                    c.target().ifPresent(target -> obj.put("staticTargetId", id(target)));
                    if (c.isVirtualDispatch()) obj.put("virtualDispatch", true)
                            .put("virtualTargetsComplete", c.virtualTargetsComplete())
                            .array("virtualTargetIds", a -> c.virtualTargets().forEach(target -> a.add(id(target))));
                    if (!c.defaultArguments().isEmpty()) obj.array("defaultArguments", a -> c.defaultArguments().forEach(argument -> a.object(value -> value
                                .put("parameterIndex", argument.parameterIndex()).put("declarationId", id(argument.declaration()))
                                .put("parameterId", id(argument.parameter())).put("expressionId", id(argument.expression())))));
                }
                case MemberExpression m -> {
                    obj.put("receiver", node(m.receiver())).put("member", m.member());
                    if (m.hasTemplateArguments()) obj.put("templateArguments", nodes(m.templateArguments()));
                    if (m.templateKeyword()) obj.put("templateKeyword", true);
                    m.target().ifPresent(target -> obj.put("targetId", id(target)));
                }
                case BinaryExpression b -> obj.put("left", node(b.left())).put("right", node(b.right()));
                case UnaryExpression u -> obj.put("operand", node(u.operand()));
                case Exit ignored -> { }
                default -> throw new IllegalStateException("Missing JSON schema for " + n.getClass().getName());
            }
            if (n instanceof Expression expression && expression.dependent()) obj.put("dependent", true);
            if (n instanceof Expression expression && (!expression.operatorCandidates().isEmpty()
                    || expression.operatorResolution() != CallExpression.Resolution.UNRESOLVED)) {
                obj.put("operatorResolution", lower(expression.operatorResolution()))
                        .array("operatorCandidateIds", a -> expression.operatorCandidates().forEach(candidate -> a.add(id(candidate))));
                expression.operatorTarget().ifPresent(target -> obj.put("operatorTargetId", id(target)));
            }
            if (n instanceof Expression expression) expression.semanticInfo().ifPresent(info -> obj.object("semanticInfo", value -> {
                info.resultType().ifPresent(type -> value.put("resultType", type));
                value.put("category", lower(info.category()));
            }));
            if (n instanceof Declaration declaration) {
                declaration.templateOrigin().ifPresent(origin -> obj.put("templateOriginId", id(origin)));
                if (declaration.isFriend()) obj.put("friend", true);
                declaration.access().ifPresent(value -> obj.put("access", value));
                declaration.typeIdentity().ifPresent(identity -> obj.put("typeIdentity", identity));
                if (declaration.linkage() != Declaration.Linkage.NONE) obj.put("linkage", lower(declaration.linkage()));
                if (!declaration.languageLinkage().equals("C++")) obj.put("languageLinkage", declaration.languageLinkage());
                if (!declaration.isDefinition()) obj.put("isDefinition", false);
                declaration.previousDeclaration().ifPresent(previous -> obj.put("previousDeclarationId", id(previous)));
                declaration.definition().ifPresent(definition -> obj.put("definitionId", id(definition)));
            }
            if (n instanceof TypeNode type) {
                if (!(type instanceof NamedType)) obj.put("canonicalIdentity", type.canonicalIdentity());
                type.semanticType().ifPresent(facts -> obj.object("semanticType", value -> {
                    value.put("canonicalIdentity", facts.canonicalIdentity()).put("profileIdentity", facts.profileIdentity());
                    facts.sizeBits().ifPresent(bits -> value.put("sizeBits", bits));
                    facts.sizeBytes().ifPresent(bytes -> value.put("sizeBytes", bytes));
                    facts.alignmentBits().ifPresent(bits -> value.put("alignmentBits", bits));
                    facts.integerRange().ifPresent(range -> value.object("integerRange", limits -> limits.put("minimum", range.min()).put("maximum", range.max())));
                }));
            }
            if (compilationBuild && n instanceof SourceFile file)
                file.variantId().ifPresent(id -> obj.put("variantId", id));
            n.range().ifPresent(r -> obj.put("range", range(r)));
            n.provenance().ifPresent(p -> {
                Integer sourceFileId = units.get(p.unit());
                if (sourceFileId == null && discovering) {
                    sourceContexts.add(p.unit());
                    units.put(p.unit(), -1);
                    sourceFileId = -1;
                }
                if (sourceFileId == null) throw new IllegalStateException("Provenance unit outside export");
                final int sourceId = sourceFileId;
                obj.object("provenance", v -> v.put("sourceFileId", sourceId)
                        .put("startToken", p.startToken()).put("endToken", p.endToken())
                        .array("expansionIds", a -> p.expansions().forEach(e -> a.add(e.id()))));
                if (n instanceof SourceFile) obj.put("preprocessing", preprocessing(p.unit()));
            });
            if (!n.transformationProvenance().isEmpty()) obj.array("transformationProvenance", a ->
                    n.transformationProvenance().forEach(link -> a.object(value -> value
                            .put("passId", link.passId()).put("role", link.role()).put("sourceId", syntaxId(link.source())))));
            if (!n.diagnostics().isEmpty()) {
                obj.array("diagnostics", a -> n.diagnostics().forEach(d -> a.object(o -> {
                    o.put("code", d.code()).put("severity", lower(d.severity())).put("message", d.message());
                    if (d.primaryRange() != null) o.put("primaryRange", range(d.primaryRange()));
                    else n.range().ifPresent(r -> o.put("primaryRange", range(r)));
                    if (!d.relatedRanges().isEmpty()) o.array("relatedRanges", ranges -> d.relatedRanges().forEach(r ->
                            ranges.object(v -> v.put("role", r.role()).put("label", r.label()).put("range", range(r.range())))));
                })));

            }
            var links = outgoing.get(n);
            if (links != null) {
                links.sort(Comparator.comparingInt(Edge::group).thenComparing(Edge::kind).thenComparingInt(Edge::to).thenComparing(e -> e.callSiteId() == null ? -1 : e.callSiteId()));
                obj.array("edges", a -> links.forEach(e -> a.object(o -> { o.put("kind", e.kind()).put("to", e.to()); if (e.callSiteId() != null) o.put("callSiteId", e.callSiteId()); })));
            }
            return obj.build();
        }

        private JsonObject auxiliary(AuxiliaryFlowVertex vertex) {
            var obj = Json.object().put("id", id(vertex)).put("kind", vertex.kind().name())
                    .put("ownerId", id(vertex.owner())).put("index", vertex.index());
            vertex.object().ifPresent(object -> obj.put("objectId", id(object)));
            vertex.destructor().ifPresent(destructor -> obj.put("destructorId", id(destructor)));
            vertex.constructor().ifPresent(constructor -> obj.put("constructorId", id(constructor)));
            var links = outgoing.get(vertex);
            if (links != null) {
                links.sort(Comparator.comparingInt(Edge::group).thenComparing(Edge::kind).thenComparingInt(Edge::to).thenComparing(e -> e.callSiteId() == null ? -1 : e.callSiteId()));
                obj.array("edges", a -> links.forEach(e -> a.object(o -> { o.put("kind", e.kind()).put("to", e.to()); if (e.callSiteId() != null) o.put("callSiteId", e.callSiteId()); })));
            }
            return obj.build();
        }

        private static JsonObject configuration(com.kniazkov.cpg.build.Build.TranslationUnit unit) {
            var obj = Json.object().put("variantId", unit.variantId()).put("source", unit.source().path().toString())
                    .put("directory", unit.directory().toString())
                    .array("includes", a -> unit.includes().forEach(p -> a.object(v ->
                            v.put("kind", p.kind()).put("path", p.path().toString()))))
                    .array("macros", a -> unit.macros().forEach(m -> a.object(v ->
                            v.put("define", m.define()).put("operand", m.operand())
                                    .put("originalArgument", m.originalArgument())
                                    .array("originalArguments", args -> args.addAll(m.originalArguments())))));
            obj.put("platform", settings(unit.platform().toSettings()));
            if (unit.standard() != null) obj.put("standard", unit.standard());
            var source = unit.configurationSource();
            obj.object("configurationSource", o -> {
                o.put("kind", source.kind()).array("arguments", a -> a.addAll(source.arguments()));
                if (source.database() != null) o.put("database", source.database().toString()).put("entryOrdinal", source.entryOrdinal());
                if (source.standardOverrideKind() != null) o.put("standardOverrideKind", source.standardOverrideKind());
            });
            return obj.build();
        }

        private static JsonObject settings(Map<?, ?> settings) {
            var object = Json.object();
            settings.forEach((key, value) -> {
                String name = key.toString();
                if (value == null) object.putNull(name);
                else if (value instanceof Map<?, ?> nested) object.put(name, settings(nested));
                else if (value instanceof String text) object.put(name, text);
                else if (value instanceof Boolean flag) object.put(name, flag);
                else if (value instanceof Number number) object.put(name, number);
                else throw new IllegalArgumentException("Unsupported platform setting " + name);
            });
            return object.build();
        }

        private static JsonObject range(SourceRange r) {
            return Json.object().put("file", r.file()).put("start", position(r.start()))
                    .put("end", position(r.end())).build();
        }

        private static JsonObject problem(ParseProblem p) {
            var obj = Json.object().put("code", p.code()).put("message", p.message())
                    .put("range", range(p.range())).put("skippedText", p.skippedText())
                    .put("recovered", p.recovered()).put("primaryRange", range(p.primaryRange()))
                    .put("reason", p.reason()).array("relatedRanges", a -> p.relatedRanges().forEach(r ->
                            a.object(o -> o.put("role", r.role()).put("label", r.label()).put("range", range(r.range())))));
            if (p.context() != null) obj.put("context", diagnosticContext(p.context()));
            return obj.build();
        }

        private static JsonObject diagnosticGroup(DiagnosticGroup group) {
            return Json.object().put("code", group.code()).put("primaryRange", range(group.primaryRange()))
                    .put("reason", group.reason()).put("message", group.message())
                    .put("translationUnitCount", group.translationUnitCount()).put("variantCount", group.variantCount())
                    .put("occurrenceCount", group.occurrenceCount())
                    .array("occurrences", a -> group.occurrences().forEach(p -> a.add(problem(p)))).build();
        }

        private static JsonObject diagnosticContext(DiagnosticContext context) {
            var obj = Json.object().put("translationUnit", context.translationUnit());
            if (context.variantId() != null) obj.put("variantId", context.variantId());
            return obj.array("includeChain", a -> context.includeChain().forEach(i -> a.object(o -> {
                        o.put("id", i.id()).put("file", i.file());
                        if (i.parentId() != null) o.put("parentId", i.parentId());
                    })))
                    .array("expansionChain", a -> context.expansionChain().forEach(e -> a.object(o -> {
                        o.put("id", e.id()).put("name", e.name()).put("definition", range(e.definition()))
                                .array("replacement", fs -> e.replacement().forEach(f -> fs.add(fragment(f))))
                                .put("invocation", range(e.invocation())).put("includeId", e.includeId())
                                .put("startToken", e.startToken()).put("endToken", e.endToken())
                                .put("definitionId", e.definitionId());
                        if (e.parentId() != null) o.put("parentId", e.parentId());
                        o.array("arguments", args -> e.arguments().forEach(arg -> {
                            var fragments = Json.array();
                            arg.forEach(f -> fragments.add(fragment(f)));
                            args.add(fragments.build());
                        }));
                        o.array("operations", ops -> e.operations().forEach(op -> ops.object(v -> v
                                .put("kind", op.kind()).put("operator", range(op.operator())).put("spelling", op.spelling())
                                .array("fragments", fs -> op.fragments().forEach(f -> fs.add(fragment(f)))))));
                    }))).build();
        }

        private static JsonObject fragment(Preprocessing.Fragment f) {
            return Json.object().put("range", range(f.range())).put("includeId", f.includeId())
                    .put("spelling", f.spelling()).build();
        }

        private JsonObject preprocessing(Preprocessing p) {
            return Json.object().put("translationUnit", p.translationUnit())
                    .array("includeStates", a -> p.includeStates().forEach(s -> a.object(o -> o.put("includeId", s.includeId())
                            .object("activeDefinitions", values -> s.activeDefinitions().forEach(values::put)))))
                    .array("definitions", a -> p.definitions().forEach(d -> a.object(o -> {
                        o.put("id", d.id()).put("name", d.name()).put("functionLike", d.functionLike())
                                .array("parameters", values -> values.addAll(d.parameters()))
                                .put("rawReplacement", d.rawReplacement()).put("range", range(d.range()))
                                .put("includeId", d.includeId()).put("originKind", d.originKind()).put("sourceKey", d.sourceKey());
                        if (d.unavailableReason() != null) o.put("unavailableReason", d.unavailableReason());
                        project.externalInput(d.sourceKey()).ifPresent(input -> o.put("sourceNodeId", id(input)));
                        o.array("expansionIds", values -> p.expansions().stream().filter(e -> e.definitionId() == d.id()).forEach(e -> values.add(e.id())));
                        o.array("presenceCheckIds", values -> p.presenceChecks().stream().filter(c -> c.definitionId() != null && c.definitionId() == d.id()).forEach(c -> values.add(c.id())));
                    })))
                    .array("branches", a -> p.branches().forEach(b -> a.object(o -> {
                        o.put("id", b.id()).put("directive", b.directive()).put("rawSource", b.rawSource())
                                .put("range", range(b.range())).put("directiveRange", range(b.directiveRange()))
                                .put("includeId", b.includeId()).put("parentActive", b.parentActive())
                                .put("evaluated", b.evaluated()).put("conditionResult", b.conditionResult()).put("active", b.active())
                                .put("startToken", b.startToken()).put("endToken", b.endToken());
                        if (p.configuration() != null) o.put("variantId", p.configuration().variantId());
                        if (b.parentId() != null) o.put("parentId", b.parentId());
                    })))
                    .array("presenceChecks", a -> p.presenceChecks().forEach(c -> a.object(o -> {
                        o.put("id", c.id()).put("name", c.name()).put("range", range(c.range()))
                                .put("includeId", c.includeId()).put("present", c.present())
                                .put("conditionResult", c.conditionResult()).put("branchId", c.branchId());
                        if (c.definitionId() != null) o.put("definitionId", c.definitionId());
                    })))
                    .array("directives", a -> p.directives().forEach(d -> a.object(o -> {
                        o.put("kind", d.kind()).put("rawSource", d.rawSource()).put("range", range(d.range()))
                                .put("includeId", d.includeId()).put("active", d.active());
                        if (d.includedHeader() != null) o.put("includedHeader", d.includedHeader()).put("startToken", d.startToken());
                    })))
                    .array("logicalLocations", a -> p.logicalLocations().forEach(l -> a.object(o -> o.put("range", range(l.range()))
                            .put("includeId", l.includeId()).put("file", l.file()).put("line", l.line()))))
                    .array("includes", a -> p.includes().forEach(i -> a.object(o -> {
                        o.put("id", i.id()).put("file", i.file());
                        if (i.parentId() != null) o.put("parentId", i.parentId());
                    })))
                    .array("tokens", a -> p.tokens().forEach(t -> a.object(o -> o
                            .put("index", t.index()).put("spelling", t.spelling())
                            .put("spellingRange", range(t.spellingRange()))
                            .put("expansionRange", range(t.expansionRange())).put("includeId", t.includeId())
                            .array("expansionIds", ids -> t.expansionIds().forEach(ids::add)))))
                    .array("expansions", a -> p.expansions().forEach(e -> a.object(o -> {
                        o.put("id", e.id()).put("name", e.name()).put("definition", range(e.definition()))
                                .array("replacement", fs -> e.replacement().forEach(f -> fs.add(fragment(f))))
                                .put("invocation", range(e.invocation()))
                                .put("includeId", e.includeId()).put("startToken", e.startToken())
                                .put("endToken", e.endToken()).put("definitionId", e.definitionId());
                        if (e.parentId() != null) o.put("parentId", e.parentId());
                        o.array("arguments", args -> e.arguments().forEach(arg -> {
                            var fragments = Json.array();
                            arg.forEach(f -> fragments.add(fragment(f)));
                            args.add(fragments.build());
                        }));
                        o.array("operations", ops -> e.operations().forEach(op -> ops.object(v -> v
                                .put("kind", op.kind()).put("operator", range(op.operator()))
                                .put("spelling", op.spelling())
                                .array("fragments", fs -> op.fragments().forEach(f -> fs.add(fragment(f)))))));
                    }))).build();
        }

        private static JsonObject position(SourcePosition p) {
            return Json.object().put("line", p.line()).put("column", p.column()).put("offset", p.offset()).build();
        }
    }
}
