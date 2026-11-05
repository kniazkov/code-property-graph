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
        joinLineDescriptions(file, context);
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
            List<Node> targets = samePhysicalOccurrence(trailing) ? trailing : List.of();
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
                if (samePhysicalOccurrence(leading)) targets = leading;
            }
            for (Node target : targets) context.copy(target).addCommentReference(context.copy(comment));
        }
        return result;
    }
    private static boolean samePhysicalOccurrence(List<Node> nodes) {
        return !nodes.isEmpty() && nodes.stream().allMatch(node -> node.getClass() == nodes.getFirst().getClass()
                && node.range().equals(nodes.getFirst().range()));
    }
    private static boolean eligible(Node node, String file) {
        if (node.range().isEmpty() || !node.range().orElseThrow().file().equals(file)) return false;
        return node.provenance().map(p -> {
            var safeTokens = new java.util.HashSet<Integer>();
            var identityTokens = new java.util.HashSet<Integer>();
            if (node instanceof CallableDeclaration callable) {
                // Compound type spans may contain their declarator's identifier. An
                // expanded token with an identity spelling is therefore never safe there.
                var names = new java.util.HashSet<String>();
                names.add(callable.name());
                if (callable.name().contains("::")) names.add(callable.name().substring(callable.name().lastIndexOf("::") + 2));
                callable.parameters().forEach(parameter -> { if (!parameter.name().isEmpty()) names.add(parameter.name()); });
                var writtenTypes = new ArrayList<TypeNode>();
                callable.returnType().ifPresent(writtenTypes::add);
                callable.parameters().forEach(parameter -> parameter.type().ifPresent(writtenTypes::add));
                for (TypeNode type : writtenTypes) type.provenance().ifPresent(span -> {
                    if (span.unit() == p.unit()) for (var token : span.tokens())
                        if (!token.expansionIds().isEmpty() && names.contains(token.spelling())) identityTokens.add(token.index());
                });
                // Expansion in types or executable/default expressions cannot replace the
                // physically written callable/parameter identifiers. Keep those tokens excluded.
                var regions = new ArrayList<Node>();
                callable.returnType().ifPresent(regions::add);
                callable.parameters().forEach(parameter -> {
                    parameter.type().ifPresent(regions::add);
                    parameter.defaultValue().ifPresent(regions::add);
                });
                callable.body().ifPresent(regions::add);
                regions.addAll(callable.handlers());
                regions.addAll(callable.initializers());
                regions.addAll(callable.exceptionTypes());
                for (Node region : regions) region.provenance().ifPresent(span -> {
                    if (span.unit() == p.unit()) for (int i = span.startToken(); i < span.endToken(); i++) safeTokens.add(i);
                });
            }
            return p.tokens().stream().allMatch(t -> t.expansionIds().isEmpty() || safeTokens.contains(t.index()) && !identityTokens.contains(t.index()));
        }).orElse(true);
    }
    /** Consecutive Doxygen line comments are logical prose, but remain separately owned text. */
    private static void joinLineDescriptions(SourceFile file, EditContext context) {
        DocumentationEntry current = null;
        Comment previous = null;
        String description = "";
        for (Comment comment : file.comments()) {
            boolean line = comment.kind() == Comment.Kind.LINE
                    && (comment.text().startsWith("///") || comment.text().startsWith("//!"));
            boolean consecutive = line && previous != null && previous.range().isPresent() && comment.range().isPresent()
                    && previous.range().orElseThrow().file().equals(comment.range().orElseThrow().file())
                    && previous.range().orElseThrow().end().line() + 1 == comment.range().orElseThrow().start().line()
                    && file.provenance().orElseThrow().unit().sources().get(comment.range().orElseThrow().file())
                        .substring(previous.range().orElseThrow().end().offset(), comment.range().orElseThrow().start().offset()).isBlank();
            if (!consecutive) current = null;
            if (!line) { previous = null; continue; }
            if (!comment.documentationEntries().isEmpty()) {
                current = comment.documentationEntries().getLast();
                // An unknown command after the last recognized one starts a different entry.
                if (current.tagRange().isEmpty() || current.tagRange().orElseThrow().end().offset()
                        != comment.range().orElseThrow().end().offset()) current = null;
                if (current != null) description = current.description();
            } else if (java.util.regex.Pattern.compile("[@\\\\][A-Za-z]+").matcher(comment.text()).find()) {
                current = null;
            } else if (current != null) {
                String continuation = comment.text().substring(3).strip();
                description += "\n" + continuation;
                context.edit(current, DocumentationEntry.Builder.class).setDescription(description);
            }
            previous = comment;
        }
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
