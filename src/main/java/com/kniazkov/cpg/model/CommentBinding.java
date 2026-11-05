/* Conservative physical comment association. MIT License. */
package com.kniazkov.cpg.model;

import java.util.ArrayList;
import java.util.List;

/** Internal binding pass: uncertain or macro-generated targets remain file-only. */
final class CommentBinding {
    private CommentBinding() {}
    static SourceFile.Builder bind(SourceFile.Builder input) {
        SourceFile file = input.build();
        var context = new EditContext();
        var result = context.edit(file, SourceFile.Builder.class);
        var declarations = new ArrayList<Node>();
        var arguments = new ArrayList<Node>();
        file.walk(new NodeVisitor() {
            @Override public void visit(Node node) {
                if (node instanceof Declaration && !(node instanceof ParameterDeclaration)) declarations.add(node);
                if (node instanceof CallExpression call) arguments.addAll(call.arguments());
            }
        });
        var sources = file.provenance().orElseThrow().unit().sources();
        for (Comment comment : file.comments()) {
            SourceRange cr = comment.range().orElseThrow();
            String source = sources.get(cr.file());
            if (source == null) continue;
            var trailing = new ArrayList<Node>();
            int last = -1;
            for (Node node : declarations) {
                if (!eligible(node, cr.file())) continue;
                SourceRange nr = node.range().orElseThrow();
                int end = nr.end().offset();
                if (nr.end().line() != cr.start().line() || end > cr.start().offset()
                        || !source.substring(end, cr.start().offset()).matches("[\\s;]*")) continue;
                if (end > last) { trailing.clear(); last = end; }
                if (end == last) trailing.add(node);
            }
            Node target = trailing.size() == 1 ? trailing.getFirst() : null;
            if (trailing.isEmpty()) {
                var leading = new ArrayList<Node>();
                for (Node node : arguments) {
                    if (!eligible(node, cr.file())) continue;
                    int before = cr.start().offset() - 1;
                    while (before >= 0 && Character.isWhitespace(source.charAt(before))) before--;
                    if (before >= 0 && source.charAt(before) == ',' && adjacent(source, cr.end().offset(),
                            node.range().orElseThrow().start().offset(), file.comments(), cr.file())) leading.add(node);
                }
                // A declaration comment starts on its own line, or follows only whitespace.
                int line = cr.start().offset();
                while (line > 0 && source.charAt(line - 1) != '\n' && source.charAt(line - 1) != '\r') line--;
                if (source.substring(line, cr.start().offset()).isBlank()) for (Node node : declarations) {
                    if (eligible(node, cr.file()) && adjacent(source, cr.end().offset(),
                            node.range().orElseThrow().start().offset(), file.comments(), cr.file())) leading.add(node);
                }
                if (leading.size() == 1) target = leading.getFirst();
            }
            if (target != null) context.copy(target).addCommentReference(context.copy(comment));
        }
        return result;
    }
    private static boolean eligible(Node node, String file) {
        if (node.range().isEmpty() || !node.range().orElseThrow().file().equals(file)) return false;
        return node.provenance().map(p -> p.unit().tokens().subList(p.startToken(), p.endToken())
                .stream().noneMatch(t -> !t.expansionIds().isEmpty())).orElse(true);
    }
    /** Walks trivia gaps individually, so adjacent // lines stay together but blank lines detach. */
    private static boolean adjacent(String source, int from, int to, List<Comment> comments, String file) {
        if (to < from || to > source.length()) return false;
        int cursor = from;
        for (Comment next : comments) {
            SourceRange range = next.range().orElseThrow();
            if (!range.file().equals(file) || range.start().offset() < cursor || range.end().offset() > to) continue;
            if (!gap(source.substring(cursor, range.start().offset()))) return false;
            cursor = range.end().offset();
        }
        return gap(source.substring(cursor, to));
    }
    private static boolean gap(String text) {
        if (!text.isBlank() && !text.isEmpty()) return false;
        return text.replace("\r\n", "\n").replace('\r', '\n').chars().filter(c -> c == '\n').count() <= 1;
    }
}
