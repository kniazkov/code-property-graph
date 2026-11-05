/* Code Property Graph: Graphviz visualization of typed snapshots. MIT License. */
package com.kniazkov.cpg.visualization;

import com.kniazkov.cpg.generation.Source;
import com.kniazkov.cpg.analysis.InheritanceSemantics;
import com.kniazkov.cpg.model.*;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.IdentityHashMap;
import java.util.Map;

/** Exports syntax ownership and resolved references, using identity for DOT node IDs. */
public final class DotVisualizer {
    private DotVisualizer() {
    }

    /**
     * Traverses the selected subtree in source order. Solid edges are numbered child
     * positions (starting at zero). Dashed edges point to resolved declarations.
     * Targets outside a selected fragment receive a label without expanding their subtree.
     */
    public static String toDot(Node root) {
        return toDot(root, true);
    }

    /** Optionally overlays macro provenance without changing syntax traversal or ownership. */
    public static String toDot(Node root, boolean provenance) {
        Writer writer = new Writer();
        root.walk(writer);
        if (root instanceof Project project) {
            project.flows().forEach(writer::flow);
            project.walk(new NodeVisitor() {
                @Override public void visit(CallableDeclaration method) {
                    InheritanceSemantics.overrides(project, method).forEach(base -> writer.semanticEdge(method, base, "overrides"));
                    InheritanceSemantics.overriddenBy(project, method).forEach(derived -> writer.semanticEdge(method, derived, "overridden by"));
                }
            });
        }
        if (provenance) root.walk(new NodeVisitor() {
            @Override public void visit(Node node) { writer.provenance(node); }
        });
        return writer.dot.append("}\n").toString();
    }

    /** Writes UTF-8 DOT source to the requested path. */
    public static void writeDot(Node root, Path output) throws IOException {
        Files.writeString(output, toDot(root), StandardCharsets.UTF_8);
    }

    /**
     * Runs the Graphviz dot executable from PATH and publishes its SVG on success.
     * A temporary source file avoids pipe deadlocks with large graphs. The renderer's
     * diagnostics are included in an IOException when it exits unsuccessfully.
     * @throws InterruptedException if interrupted while waiting for Graphviz
     * @throws IOException if the files or the renderer cannot be accessed, or rendering fails
     */
    public static void renderSvg(Node root, Path output) throws IOException, InterruptedException {
        renderSvg(root, output, true);
    }

    /** Renders an SVG with the selected provenance visibility. */
    public static void renderSvg(Node root, Path output, boolean provenance) throws IOException, InterruptedException {
        Path directory = output.toAbsolutePath().getParent();
        Path source = Files.createTempFile(directory, "cpg-", ".dot");
        try {
            Files.writeString(source, toDot(root, provenance), StandardCharsets.UTF_8);
            Path svg = Files.createTempFile(directory, "cpg-", ".svg");
            try {
                render(source, svg);
                Files.move(svg, output, StandardCopyOption.REPLACE_EXISTING);
            } finally {
                Files.deleteIfExists(svg);
            }
        } finally {
            Files.deleteIfExists(source);
        }
    }

    private static void render(Path source, Path svg) throws IOException, InterruptedException {
        Path diagnostics = Files.createTempFile(svg.getParent(), "cpg-", ".log");
        try {
            String firstFailure = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                var command = new java.util.ArrayList<String>();
                command.add("dot");
                if (attempt == 1) command.addAll(java.util.List.of("-Grankdir=LR", "-Gsplines=line", "-Gnewrank=true"));
                command.addAll(java.util.List.of("-Tsvg", "-o", svg.toString(), source.toString()));
                Process process = new ProcessBuilder(command).redirectErrorStream(true).redirectOutput(diagnostics.toFile()).start();
                try {
                    int status = process.waitFor();
                    if (status == 0) return;
                    String failure = Files.readString(diagnostics);
                    // Some Graphviz versions fail on large, valid graphs with many
                    // unconstrained semantic edges. Retry the same complete graph with
                    // a different orientation/router; never drop nodes, edges or labels.
                    if (attempt == 0 && (failure.contains("trouble in init_rank") || failure.contains("triangulation failed") || failure.contains("Pshortestpath failed"))) {
                        firstFailure = failure;
                        continue;
                    }
                    throw new IOException("Graphviz exited with status " + status + ": " + failure
                            + (firstFailure == null ? "" : "\nInitial layout failure:\n" + firstFailure));
                } finally {
                    if (process.isAlive()) { process.destroyForcibly(); process.waitFor(); }
                }
            }
        } finally {
            Files.deleteIfExists(diagnostics);
        }
    }

    private static final class Writer implements NodeVisitor {
        private final Map<Node, Integer> ids = new IdentityHashMap<>();
        private final Map<Preprocessing, Integer> units = new IdentityHashMap<>();
        private final java.util.Set<String> events = new java.util.HashSet<>();
        private int fragmentCounter;
        private final Map<String, Node> externalInputs = new java.util.LinkedHashMap<>();

        private final StringBuilder dot = new StringBuilder("""
                digraph CPG {
                  graph [penwidth=1];
                  node [shape=box, style=rounded, penwidth=1, fontsize=10];
                  edge [penwidth=1, fontsize=9];
                """);

        @Override
        public void visit(Node node) {
            int from = id(node);
            if (node instanceof ExternalInput input) externalInputs.put(input.key(), input);
            int index = 0;
            for (Node child : node.children()) {
                int to = id(child);
                dot.append("  n").append(from).append(" -> n").append(to)
                        .append(" [label=<&#160;").append(index++).append(">];\n");
            }
            for (Node target : node.references()) {
                int to = id(target);
                dot.append("  n").append(from).append(" -> n").append(to)
                        .append(" [label=<&#160;ref>, style=dashed, constraint=false];\n");
            }
            for (Comment comment : node.commentReferences()) semanticEdge(node, comment, "HAS_COMMENT");
            if (node instanceof DocumentationEntry entry) for (var binding : entry.bindings()) {
                String context = binding.declaration().provenance().map(p -> " include " + (p.startToken() < p.unit().tokens().size() ? p.unit().tokens().get(p.startToken()).includeId() : -1) + " TU " + p.unit().translationUnit() + (p.unit().configuration() == null ? "" : " variant " + p.unit().configuration().variantId())).orElse("");
                semanticEdge(entry, binding.declaration(), "documents declaration" + context);
                if (binding.target() != null) semanticEdge(entry, binding.target(), "documents parameter" + context);
            }
            if (node instanceof Expression expression) {
                expression.operatorTarget().ifPresent(target -> semanticEdge(expression, target, "operator implementation"));
                if (expression.operatorTarget().isEmpty()) for (var candidate : expression.operatorCandidates())
                    semanticEdge(expression, candidate, "operator candidate (" + expression.operatorResolution().name().toLowerCase(java.util.Locale.ROOT) + ")");
            }
            if (node instanceof OpaqueLibraryType type) {
                type.iteratorContainer().ifPresent(container -> semanticEdge(type, container, "ITERATOR_CONTAINER"));
                type.iteratorDifferenceType().ifPresent(difference -> semanticEdge(type, difference, "ITERATOR_DIFFERENCE_TYPE"));
                type.iteratorValueType().ifPresent(value -> semanticEdge(type, value, "ITERATOR_VALUE_TYPE"));
            }
            if (node instanceof DynamicArrayType type) {
                if (type.concreteClass() != null) semanticEdge(type, type.concreteClass(), "DYNAMIC_ARRAY_CLASS");
                semanticEdge(type, type.elementType(), "ELEMENT_TYPE");
                type.writtenTypes().forEach(site -> semanticEdge(site, type, "COMMON_TYPE"));
            }
            if (node instanceof DynamicArrayOperation operation) {
                semanticEdge(operation.syntax(), operation, "COMMON_OPERATION");
                semanticEdge(operation, operation.type(), "DYNAMIC_ARRAY_TYPE");
                semanticEdge(operation, operation.destination(), "DESTINATION");
                operation.sources().forEach(source -> semanticEdge(operation, source, "SOURCE"));
                semanticEdge(operation, operation.selectedTarget(), "CPP_TARGET");
                operation.elementCopyConstructor().ifPresent(target -> semanticEdge(operation, target, "ELEMENT_COPY_CONSTRUCTOR"));
                operation.elementDestructor().ifPresent(target -> semanticEdge(operation, target, "ELEMENT_DESTRUCTOR"));
                operation.elementAssignment().ifPresent(target -> semanticEdge(operation, target, "ELEMENT_ASSIGNMENT"));
                operation.index().ifPresent(operand -> semanticEdge(operation, operand, "INDEX"));
                operation.mutatedObjects().forEach(target -> semanticEdge(operation, target, "MUTATES"));
                operation.handle().ifPresent(handle -> {
                    semanticEdge(operation, handle.sequence(), "HANDLE_SEQUENCE");
                    handle.position().ifPresent(position -> semanticEdge(operation, position, "HANDLE_POSITION"));
                });
                operation.handleEffects().forEach(effect -> {
                    semanticEdge(operation, effect.producer(), "HANDLE_" + effect.validityAfter().name());
                    effect.sequenceAfter().ifPresent(sequence -> semanticEdge(operation, sequence, "SEQUENCE_AFTER"));
                    effect.positionAfter().ifPresent(position -> semanticEdge(operation, position, "POSITION_AFTER"));
                });
            }
            if (node instanceof IncludeDirective include) include.libraryHeader().ifPresent(header -> semanticEdge(include, header, "LIBRARY_HEADER"));
            if (node instanceof CharacterSequenceType type) {
                type.declaration().ifPresent(value -> semanticEdge(type, value, "CHARACTER_SEQUENCE_CLASS"));
                semanticEdge(type, type.characterType(), "CHARACTER_TYPE");
                type.traitsType().ifPresent(value -> semanticEdge(type, value, "TRAITS_TYPE"));
                type.allocatorType().ifPresent(value -> semanticEdge(type, value, "ALLOCATOR_TYPE"));
                type.writtenTypes().forEach(value -> semanticEdge(value, type, "COMMON_TYPE"));
            }
            if (node instanceof SequenceOperation operation) {
                semanticEdge(operation.syntax(), operation, "COMMON_OPERATION");
                semanticEdge(operation, operation.selectedTarget(), "CPP_TARGET");
                operation.characterType().ifPresent(value -> semanticEdge(operation, value, "CHARACTER_SEQUENCE_TYPE"));
                operation.destination().ifPresent(value -> semanticEdge(operation, value, "DESTINATION"));
                operation.inputSequence().ifPresent(value -> semanticEdge(operation, value, "INPUT_SEQUENCE"));
                operation.outputDestination().ifPresent(value -> semanticEdge(operation, value, "OUTPUT_DESTINATION"));
                operation.first().ifPresent(value -> semanticEdge(operation, value, "RANGE_FIRST"));
                operation.last().ifPresent(value -> semanticEdge(operation, value, "RANGE_LAST"));
                operation.secondFirst().ifPresent(value -> semanticEdge(operation, value, "SECOND_RANGE_FIRST"));
                operation.count().ifPresent(value -> semanticEdge(operation, value, "COUNT"));
                operation.position().ifPresent(value -> semanticEdge(operation, value, "POSITION"));
                operation.initialValue().ifPresent(value -> semanticEdge(operation, value, "INITIAL_VALUE"));
                operation.callable().ifPresent(value -> semanticEdge(operation, value, "CALLABLE"));
                operation.callableTarget().ifPresent(value -> semanticEdge(operation, value, "CALLABLE_TARGET"));
                operation.resultType().ifPresent(value -> semanticEdge(operation, value, "RESULT_TYPE"));
                operation.accumulatorType().ifPresent(value -> semanticEdge(operation, value, "ACCUMULATOR_TYPE"));
                operation.sources().forEach(value -> semanticEdge(operation, value, "SOURCE"));
                operation.mutatedObjects().forEach(value -> semanticEdge(operation, value, "MUTATES"));
                operation.callableEffects().forEach(value -> semanticEdge(operation, value, "CALLABLE_EFFECT"));
            }
            if (node instanceof ContainerType type) {
                if (type.concreteClass() != null) semanticEdge(type, type.concreteClass(), "CONTAINER_CLASS");
                semanticEdge(type, type.elementType(), "ELEMENT_TYPE");
                type.keyType().ifPresent(value -> semanticEdge(type, value, "KEY_TYPE"));
                type.mappedType().ifPresent(value -> semanticEdge(type, value, "MAPPED_TYPE"));
                type.comparatorType().ifPresent(value -> semanticEdge(type, value, "COMPARATOR_TYPE"));
                type.underlyingType().ifPresent(value -> semanticEdge(type, value, "UNDERLYING_TYPE"));
                type.writtenTypes().forEach(site -> semanticEdge(site, type, "COMMON_TYPE"));
            }
            if (node instanceof ContainerOperation operation) {
                semanticEdge(operation.syntax(), operation, "COMMON_OPERATION");
                semanticEdge(operation, operation.type(), "CONTAINER_TYPE");
                semanticEdge(operation, operation.destination(), "DESTINATION");
                operation.sources().forEach(source -> semanticEdge(operation, source, "SOURCE"));
                semanticEdge(operation, operation.selectedTarget(), "CPP_TARGET");
                operation.comparator().ifPresent(value -> semanticEdge(operation, value, "COMPARATOR_TARGET"));
                operation.sourceContainer().ifPresent(value -> semanticEdge(operation, value, "SOURCE_CONTAINER"));
                operation.elementCopyConstructor().ifPresent(target -> semanticEdge(operation, target, "ELEMENT_COPY_CONSTRUCTOR"));
                operation.elementDestructor().ifPresent(target -> semanticEdge(operation, target, "ELEMENT_DESTRUCTOR"));
                operation.elementAssignment().ifPresent(target -> semanticEdge(operation, target, "ELEMENT_ASSIGNMENT"));
                operation.index().ifPresent(operand -> semanticEdge(operation, operand, "INDEX"));
                operation.mutatedObjects().forEach(target -> semanticEdge(operation, target, "MUTATES"));
                operation.handle().ifPresent(handle -> {
                    semanticEdge(operation, handle.sequence(), "HANDLE_SEQUENCE");
                    handle.position().ifPresent(position -> semanticEdge(operation, position, "HANDLE_POSITION"));
                });
                operation.handleEffects().forEach(effect -> {
                    semanticEdge(operation, effect.producer(), "HANDLE_" + effect.validityAfter().name());
                    effect.sequenceAfter().ifPresent(sequence -> semanticEdge(operation, sequence, "SEQUENCE_AFTER"));
                    effect.positionAfter().ifPresent(position -> semanticEdge(operation, position, "POSITION_AFTER"));
                });
            }
            if (node instanceof IncludeDirective include) include.libraryHeader().ifPresent(header -> semanticEdge(include, header, "LIBRARY_HEADER"));
            if (node instanceof VariableDeclaration variable) {
                variable.constructorTarget().ifPresent(target -> semanticEdge(variable, target, "CONSTRUCTOR_TARGET"));
                variable.destructorTarget().ifPresent(target -> semanticEdge(variable, target, "DESTRUCTOR_TARGET"));
            }
            if (node instanceof Declaration declaration) declaration.templateOrigin().ifPresent(origin -> semanticEdge(declaration, origin, "TEMPLATE_ORIGIN"));
            if (node instanceof TemplateInstance instance) for (Node request : instance.requests()) {
                semanticEdge(request, instance, "INSTANTIATION_CAUSE");
                semanticEdge(instance, request, "INSTANTIATION_REQUEST");
            }
            if (node instanceof ClassTemplateInstance instance) for (Node request : instance.requests()) {
                semanticEdge(request, instance, "INSTANTIATION_CAUSE");
                semanticEdge(instance, request, "INSTANTIATION_REQUEST");
            }
            if (node instanceof NamedType type) resolution(type);
            if (node instanceof CallExpression call) {
                call.target().ifPresent(target -> semanticEdge(call, target, "CALL_TARGET (static target)"));
                for (var target : call.virtualTargets()) semanticEdge(call, target,
                        "possible virtual implementation" + (call.virtualTargetsComplete() ? "" : " (incomplete set)"));
            }
            if (node instanceof CallExpression call) for (var argument : call.defaultArguments()) {
                String label = "default argument " + argument.parameterIndex();
                semanticEdge(call, argument.declaration(), label + " declaration");
                semanticEdge(call, argument.parameter(), label + " parameter");
                semanticEdge(call, argument.expression(), label + " expression");
            }
            if (node instanceof Declaration declaration) {
                declaration.previousDeclaration().ifPresent(previous -> semanticEdge(node, previous, "previous declaration"));
                declaration.definition().ifPresent(definition -> semanticEdge(node, definition, "definition"));
            }
        }

        /** Each written type use retains its own path through shared using/typedef nodes. */
        private void resolution(NamedType type) {
            for (int index = 0; index < type.resolutionSteps().size(); index++) {
                var step = type.resolutionSteps().get(index);
                String label = "resolve " + index + " " + step.kind();
                semanticEdge(type, step.via(), label + " via");
                semanticEdge(type, step.target(), label + " target");
            }
        }

        private void semanticEdge(Node from, Node to, String label) {
            dot.append("  n").append(id(from)).append(" -> n").append(id(to))
                    .append(" [label=<&#160;").append(htmlText(label))
                    .append(">, style=dashed, color=\"#455a64\", constraint=false];\n");
        }

        private void provenance(Node node) {
            node.provenance().ifPresent(p -> {
                int unit = units.computeIfAbsent(p.unit(), ignored -> units.size());
                if (node instanceof SourceFile) {
                    for (var definition : p.unit().definitions()) {
                        String key = "p" + unit + "d" + definition.id();
                        provenanceNode(key, "definition " + definition.name() + " = " + definition.rawReplacement());
                        provenanceEdge("n" + id(node), key, "catalogue");
                        Node input = externalInputs.get(definition.sourceKey());
                        if (input != null) provenanceEdge(key, "n" + id(input), "configuration source");
                        else fragment(key, "source definition", definition.range(), definition.rawReplacement());
                    }
                    for (var check : p.unit().presenceChecks()) {
                        String key = "p" + unit + "c" + check.id();
                        provenanceNode(key, "presence check " + check.name() + " = " + check.present());
                        provenanceEdge("n" + id(node), key, "condition");
                        if (check.definitionId() != null) provenanceEdge(key, "p" + unit + "d" + check.definitionId(), "current definition");
                    }
                    for (var branch : p.unit().branches()) {
                        String key = "p" + unit + "b" + branch.id();
                        provenanceNode(key, branch.directive() + " active=" + branch.active() + "\n" + branch.rawSource());
                        provenanceEdge("n" + id(node), key, "conditional branch");
                    }
                }
                var expansions = node instanceof SourceFile ? p.unit().expansions() : p.expansions();
                for (var event : expansions) {
                    String eventId = event(p.unit(), unit, event);
                    provenanceEdge("n" + id(node), eventId, "origin");
                }
            });
        }

        private String event(Preprocessing data, int unit, Preprocessing.Expansion event) {
            String key = "p" + unit + "e" + event.id();
            if (!events.add(key)) return key;
            provenanceNode(key, "macro " + event.name() + " [TU " + unit + ", event " + event.id()
                    + ", include " + event.includeId() + "]");
            if (data.definitions().stream().anyMatch(d -> d.id() == event.definitionId()))
                provenanceEdge(key, "p" + unit + "d" + event.definitionId(), "definition");
            fragment(key, "invocation", event.invocation(), "");
            fragment(key, "definition", event.definition(), "");
            for (var f : event.replacement()) {
                String role = java.util.Set.of("+", "-", "*", "/", "%", "&", "|", "^", "~", "!", "=",
                        "==", "!=", "<", ">", "<=", ">=", "<<", ">>", "&&", "||", "++", "--", "?", ":",
                        "+=", "-=", "*=", "/=", "%=", "&=", "|=", "^=", "<<=", ">>=", "#", "##")
                        .contains(f.spelling()) ? "replacement operator" : "replacement";
                fragment(key, role, f.range(), f.spelling());
            }
            for (int i = 0; i < event.arguments().size(); i++) {
                for (var f : event.arguments().get(i)) fragment(key, "argument " + i, f.range(), f.spelling());
            }
            for (var op : event.operations()) {
                String operation = fragment(key, op.kind(), op.operator(), op.spelling());
                for (var f : op.fragments()) fragment(operation, "operand", f.range(), f.spelling());
            }
            if (event.parentId() != null) {
                data.expansions().stream().filter(e -> e.id() == event.parentId()).findFirst().ifPresent(parent ->
                        provenanceEdge(key, event(data, unit, parent), "parent expansion"));
            }
            return key;
        }

        private String fragment(String parent, String role, SourceRange range, String spelling) {
            String key = "pf" + fragmentCounter++;
            provenanceNode(key, role + (spelling.isEmpty() ? "" : ": " + spelling) + "\n"
                    + range.file() + ":" + range.start().line() + ":" + range.start().column()
                    + "–" + range.end().line() + ":" + range.end().column());
            provenanceEdge(parent, key, role);
            return key;
        }

        private void provenanceNode(String key, String label) {
            dot.append("  ").append(key).append(" [shape=note, style=dashed, color=\"#7b1fa2\", label=<")
                    .append(htmlText(label)).append(">];\n");
        }

        private void provenanceEdge(String from, String to, String label) {
            dot.append("  ").append(from).append(" -> ").append(to)
                    .append(" [style=dashed, color=\"#7b1fa2\", fontcolor=\"#7b1fa2\", constraint=false, label=<")
                    .append(htmlText(label)).append(">];\n");
        }

        /** Draws analysis overlays after syntax IDs have been assigned. */
        private void flow(FunctionFlow flow) {
            if (!flow.complete()) dot.append("  n").append(id(flow.entry())).append(" [xlabel=\"incomplete flow\"];\n");
            for (var vertex : flow.auxiliaryVertices()) {
                String key = flowId(vertex);
                dot.append("  ").append(key).append(" [shape=ellipse, style=dashed, label=<")
                        .append(vertex.kind().name()).append(">];\n");
                vertex.object().ifPresent(object -> auxiliaryReference(key, object, "object ref"));
                vertex.destructor().ifPresent(destructor -> auxiliaryReference(key, destructor, "destructor ref"));
                vertex.constructor().ifPresent(constructor -> auxiliaryReference(key, constructor, "constructor ref"));
            }
            for (var edge : flow.control()) {
                String from = flowId(edge.from());
                String to = flowId(edge.to());
                flowEdge(from, to, "CFG " + edge.kind().name().toLowerCase(java.util.Locale.ROOT), "#1565c0");
            }
            for (var edge : flow.data()) {
                String label = edge.kind() == FunctionFlow.Dependency.VALUE ? "DFG value" : "DFG def";
                flowEdge("n" + id(edge.from()), "n" + id(edge.to()), label, "#aa3377");
            }
        }

        private void auxiliaryReference(String from, Node to, String label) {
            dot.append("  ").append(from).append(" -> n").append(id(to))
                    .append(" [label=<&#160;").append(label)
                    .append(">, style=dashed, color=\"#455a64\", constraint=false];\n");
        }

        private String flowId(FlowVertex vertex) {
            if (vertex instanceof Node node) return "n" + id(node);
            var auxiliary = (AuxiliaryFlowVertex) vertex;
            return "f" + id(auxiliary.owner()) + "v" + auxiliary.index();
        }

        private void flowEdge(String from, String to, String label, String color) {
            dot.append("  ").append(from).append(" -> ").append(to)
                    .append(" [label=<&#160;").append(label).append(">, color=\"").append(color)
                    .append("\", fontcolor=\"").append(color).append("\", constraint=false];\n");
        }

        private int id(Node node) {
            Integer known = ids.get(node);
            if (known != null) {
                return known;
            }
            int id = ids.size();
            ids.put(node, id);
            Label label = new Label();
            node.accept(label);
            if (node instanceof Comment comment) label.detail = comment.kind() + " " + comment.text();
            if (node instanceof DocumentationEntry entry) label.detail = entry.kind() + " " + entry.name() + " " + entry.description();
            if (node instanceof TemplateParameterDeclaration parameter) label.detail = parameter.kind() + " " + parameter.keyword() + " " + parameter.name();
            if (node instanceof TemplateArgument argument) label.detail = argument.kind().name();
            if (node instanceof NamedType type) {
                if (type.typenameKeyword()) label.detail = "typename " + label.detail;
                if (type.templateKeyword()) label.detail += " [template]";
                if (type.hasTemplateArguments()) label.detail += " [template-id]";
            }
            if (node instanceof NameExpression name && name.templateKeyword()
                    || node instanceof MemberExpression member && member.templateKeyword()) label.detail += " [template]";
            if (node instanceof ClassTemplateInstance instance) label.detail = instance.origin().name() + " " + instance.identity();
            if (node instanceof TemplateInstance instance) label.detail = instance.origin().name() + " " + instance.status();
            if (node instanceof SourceFile && com.kniazkov.cpg.analysis.LibraryDeclarations.isLibrary(node)) label.detail += " [library declarations only]";
            if (node instanceof NamedType type && type.dependent() || node instanceof Expression expression && expression.dependent()) label.detail += " [dependent]";
            String border = node.languages().equals(Source.SUPPORTED_TARGETS) ? "#008000" : "#ff0000";
            dot.append("  n").append(id).append(" [color=\"").append(border).append("\", label=<")
                    .append(node.getClass().getSimpleName());
            if (!label.detail.isEmpty()) {
                dot.append("<BR/><FONT COLOR=\"#000080\">").append(htmlText(label.detail)).append("</FONT>");
            }
            for (Diagnostic diagnostic : node.diagnostics()) {
                dot.append("<BR/><FONT COLOR=\"#ad6800\">⚠ ")
                        .append(htmlText(diagnostic.code())).append("</FONT>");
            }
            dot.append(">];\n");
            return id;
        }
    }

    /** Escapes data for a Graphviz HTML label and preserves its line breaks. */
    private static String htmlText(String text) {
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\\", "\\\\").replace("\r\n", "\n").replace('\r', '\n').replace("\n", "<BR/>");
    }

    private static final class Label implements NodeVisitor {
        private String detail = "";

        @Override
        public void visit(Project node) {
            if (node.hasErrors()) detail = "ERROR: project contains parse errors";
        }

        @Override
        public void visit(ErrorExpression node) {
            detail = "ERROR: " + node.problem().message();
        }

        @Override
        public void visit(IntegerLiteral node) {
            detail = node.bigValue().toString();
        }

        @Override public void visit(GotoStatement node) { detail = node.label(); }
        @Override public void visit(LabelStatement node) { detail = node.label(); }
        @Override public void visit(CaseStatement node) { detail = node.value().isEmpty() ? "default" : "case"; }
        @Override public void visit(CatchClause node) { detail = node.parameter().isEmpty() ? "catch (...)" : "typed catch"; }
        @Override public void visit(ThrowExpression node) { detail = node.value().isEmpty() ? "rethrow" : "throw"; }

        @Override public void visit(BooleanLiteral node) { detail = node.spelling(); }
        @Override public void visit(FloatingLiteral node) { detail = node.spelling() + " : " + node.cppType(); }
        @Override public void visit(CharacterLiteral node) { detail = node.spelling() + " : " + node.cppType(); }
        @Override public void visit(StringLiteral node) { detail = node.spelling() + " : " + node.cppType(); }
        @Override public void visit(CompoundAssignmentExpression node) { detail = node.operator(); }
        @Override public void visit(TypeidExpression node) { detail = node.type().isPresent() ? "type operand" : "expression operand"; }
        @Override public void visit(NewExpression node) {
            detail = (node.isGlobal() ? "::" : "") + "new " + node.type().canonicalIdentity()
                    + (node.placementArguments().isEmpty() ? "" : " (placement)")
                    + (node.hasInitializer() ? " (initialized)" : "");
        }
        @Override public void visit(DeleteExpression node) { detail = (node.isGlobal() ? "::" : "") + (node.isArray() ? "delete[]" : "delete"); }
        @Override public void visit(ConversionExpression node) {
            detail = (node.isExplicit() ? "explicit " : "implicit ") + node.kind().name().toLowerCase(java.util.Locale.ROOT)
                    + " to " + node.targetType().canonicalIdentity();
        }

        @Override
        public void visit(NameExpression node) {
            detail = node.name();
        }

        @Override
        public void visit(MemberExpression node) {
            detail = node.member();
        }

        @Override
        public void visit(CallableDeclaration node) {
            detail = node.name() + (node.bodyComplete() ? "" : " (incomplete body)")
                    + (node.hasExceptionSpecification() ? " throw(" + node.exceptionTypes().stream().map(TypeNode::canonicalIdentity).collect(java.util.stream.Collectors.joining(", ")) + ")" : "")
                    + (node.functionTryBlock() ? " (function try)" : "") + (node.isPureVirtual() ? " (pure virtual)" : node.modifiers().contains("virtual") ? " (virtual)" : "");
        }

        @Override
        public void visit(VariableDeclaration node) {
            detail = node.name();
        }

        @Override
        public void visit(ParameterDeclaration node) {
            detail = node.name();
        }

        @Override
        public void visit(ClassDeclaration node) {
            detail = node.name() + (node.implicit() ? " (implicit declaration)" : node.isDefinition() ? "" : " (forward declaration)");
        }

        @Override
        public void visit(NamedType node) {
            detail = node.elaboratedKeyword().map(keyword -> keyword + " ").orElse("") + node.name() + (node.completeAtUse() ? "" : " (incomplete at use)");
        }

        @Override
        public void visit(IncludeDirective node) {
            detail = node.header();
        }

        @Override public void visit(CharacterSequenceType node) { detail = node.characterType().canonicalIdentity(); }
        @Override public void visit(SequenceOperation node) { detail = node.kind().name() + " / " + node.resultKind().name(); }
        @Override public void visit(ContainerType node) { detail = node.kind().name() + " / " + node.elementType().canonicalIdentity(); }
        @Override public void visit(ContainerOperation node) { detail = node.kind().name() + " / " + node.resultKind().name(); }
        @Override public void visit(DynamicArrayType node) { detail = "sequence of " + node.elementType().canonicalIdentity(); }
        @Override public void visit(DynamicArrayOperation node) { detail = node.kind().name() + " / " + node.resultKind().name(); }

        @Override
        public void visit(StandardLibrary node) {
            detail = "Demand-materialized standard library";
        }

        @Override
        public void visit(SourceFile node) {
            detail = node.path();
            if (node.hasErrors()) detail += "\nERROR: incomplete source";
            for (ParseProblem problem : node.parseProblems()) {
                SourceRange range = problem.range();
                detail += "\n" + range.file() + ":" + range.start().line() + ":" + range.start().column()
                        + ": " + problem.message();
            }
            if (node.stoppedAt().isPresent()) {
                SourceRange stopped = node.stoppedAt().orElseThrow();
                detail += "\nstopped at " + stopped.file() + ":" + stopped.start().line() + ":" + stopped.start().column();
            }
        }

        @Override public void visit(NamespaceDeclaration node) { detail = node.anonymous() ? "(anonymous)" : node.name(); }
        @Override public void visit(NamespaceAliasDeclaration node) { detail = node.name() + " = " + node.targetName(); }
        @Override public void visit(UsingDirective node) { detail = "using namespace " + node.namespaceName(); }
        @Override public void visit(UsingDeclaration node) { detail = "using " + node.qualifiedName(); }
        @Override public void visit(TypedefDeclaration node) { detail = node.name(); }
        @Override public void visit(EnumDeclaration node) { detail = node.name().isEmpty() ? "(anonymous)" : node.name(); }
        @Override public void visit(EnumeratorDeclaration node) { detail = node.name(); }

        @Override public void visit(ExternalInput node) { detail = node.role() + ": " + node.text(); }

    }
}
