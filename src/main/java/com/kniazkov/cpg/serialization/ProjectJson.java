/* Code Property Graph: deterministic JSON snapshot export. MIT License. */
package com.kniazkov.cpg.serialization;

import com.kniazkov.cpg.model.*;
import com.kniazkov.json.*;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

/** Writes syntax ownership as nested fields and non-owning edges as destination IDs. */
public final class ProjectJson {
    private ProjectJson() {}

    /** Numbers syntax nodes in preorder before encoding any edge, including forward and cyclic references. */
    public static JsonObject encode(Project project) {
        return new Export(project).node(project);
    }

    /** Creates a UTF-8 file containing the complete snapshot. Existing files are protected. */
    public static void write(Project project, Path file) throws IOException {
        String text = encode(project).toText("  ") + "\n";
        Files.writeString(file, text, StandardOpenOption.CREATE_NEW);
    }

    private record Edge(String kind, int to) {
        int group() {
            return kind.equals("ref") ? 0 : kind.startsWith("cfg.") ? 1 : 2;
        }
    }

    private static final class Export {
        private final Map<Node,Integer> ids = new IdentityHashMap<>();
        private final Map<Node,List<Edge>> outgoing = new IdentityHashMap<>();

        Export(Project project) {
            project.walk(new NodeVisitor() {
                @Override public void visit(Node n) { ids.put(n, ids.size()); }
            });
            for (Node n : ids.keySet()) for (Node target : n.references()) edge(n, target, "ref");
            for (var flow : project.flows()) {
                for (var e : flow.control()) edge((Node)e.from(), (Node)e.to(), "cfg." + lower(e.kind()));
                for (var e : flow.data()) edge(e.from(), e.to(), "dfg." + lower(e.kind()));
            }
        }

        private static String lower(Enum<?> kind) { return kind.name().toLowerCase(Locale.ROOT); }

        private void edge(Node from, Node to, String kind) {
            if (!ids.containsKey(from) || !ids.containsKey(to)) throw new IllegalStateException("Edge outside project");
            outgoing.computeIfAbsent(from, ignored -> new ArrayList<>()).add(new Edge(kind, ids.get(to)));
        }

        private JsonArray nodes(List<? extends Node> children) {
            var array = Json.array();
            children.forEach(child -> array.add(node(child)));
            return array.build();
        }

        private JsonObject node(Node n) {
            var obj = Json.object().put("id", ids.get(n)).put("kind", n.getClass().getSimpleName());
            switch (n) {
                case Project p -> obj.put("files", nodes(p.files()));
                case SourceFile f -> obj.put("path", f.range().map(SourceRange::file)
                                .orElseGet(() -> Path.of(f.path()).toAbsolutePath().normalize().toString()))
                        .put("language", f.language().name()).put("items", nodes(f.items()));
                case ClassDeclaration c -> obj.put("name", c.name()).array("modifiers", a -> a.addAll(c.modifiers()))
                        .put("members", nodes(c.members()));
                case CallableDeclaration c -> {
                    obj.put("name", c.name()).array("modifiers", a -> a.addAll(c.modifiers()));
                    c.returnType().ifPresent(t -> obj.put("returnType", node(t)));
                    obj.put("parameters", nodes(c.parameters()));
                    c.body().ifPresent(b -> obj.put("body", node(b)));
                    obj.put("exit", node(c.exit()));
                }
                case BlockStatement b -> obj.put("items", nodes(b.items()));
                case VariableDeclaration v -> {
                    obj.put("name", v.name());
                    v.type().ifPresent(t -> obj.put("type", node(t)));
                    v.initializer().ifPresent(i -> obj.put("initializer", node(i)));
                }
                case ParameterDeclaration p -> {
                    obj.put("name", p.name());
                    p.type().ifPresent(t -> obj.put("type", node(t)));
                }
                case NamedType t -> obj.put("name", t.name());
                case NameExpression e -> obj.put("name", e.name());
                case IntegerLiteral i -> obj.put("value", i.value());
                case IncludeDirective i -> obj.put("header", i.header()).put("system", i.system());
                case IfStatement i -> {
                    obj.put("condition", node(i.condition())).put("thenBranch", node(i.thenBranch()));
                    i.elseBranch().ifPresent(b -> obj.put("elseBranch", node(b)));
                }
                case WhileStatement w -> obj.put("condition", node(w.condition())).put("body", node(w.body()));
                case ReturnStatement r -> r.value().ifPresent(v -> obj.put("value", node(v)));
                case PrintStatement p -> obj.put("value", node(p.value()));
                case ExpressionStatement e -> obj.put("expression", node(e.expression()));
                case AssignmentExpression a -> obj.put("target", node(a.target())).put("value", node(a.value()));
                case CallExpression c -> obj.put("callee", node(c.callee())).put("arguments", nodes(c.arguments()));
                case MemberExpression m -> obj.put("receiver", node(m.receiver())).put("member", m.member());
                case BinaryExpression b -> obj.put("left", node(b.left())).put("right", node(b.right()));
                case UnaryExpression u -> obj.put("operand", node(u.operand()));
                case Exit ignored -> { }
                default -> throw new IllegalStateException("Missing JSON schema for " + n.getClass().getName());
            }
            n.range().ifPresent(r -> obj.object("range", range ->
                    range.put("start", position(r.start())).put("end", position(r.end()))));
            var links = outgoing.get(n);
            if (links != null) {
                links.sort(Comparator.comparingInt(Edge::group).thenComparing(Edge::kind).thenComparingInt(Edge::to));
                obj.array("edges", a -> links.forEach(e -> a.object(o -> o.put("kind", e.kind()).put("to", e.to()))));
            }
            return obj.build();
        }

        private static JsonObject position(SourcePosition p) {
            return Json.object().put("line", p.line()).put("column", p.column()).put("offset", p.offset()).build();
        }
    }
}
