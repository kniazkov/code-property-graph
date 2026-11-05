/* Code Property Graph: validated physical-source patching. MIT License. */
package com.kniazkov.cpg.generation;

import com.kniazkov.cpg.analysis.ConsoleNormalizer;
import com.kniazkov.cpg.analysis.NameResolver;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.parser.Parser;
import java.nio.file.Path;
import java.util.*;

/** Preserves preprocessing text, expanding only edited, representable invocation boundaries. */
final class PreservedSource {
    private PreservedSource() {}
    private record Pair(Node before, Node after) {}
    private record Patch(String file, int start, int end, String text) {}
    /** A repeated include can disagree with another occurrence even within one TU. */
    private record Context(int variant, long includeId) {}
    private record Edit(Context context, Patch patch) {}
    private record Requirement(Context context, String text) {}

    static boolean applicable(Project project) {
        return !project.files().isEmpty()
                && project.files().stream().allMatch(f -> f.language() == Language.CPP
                    && f.provenance().map(p -> p.unit().configuration() != null).orElse(false))
                && (project.files().stream().map(f -> f.provenance().orElseThrow().unit().translationUnit())
                    .distinct().count() < project.files().size() || project.files().stream().anyMatch(f -> {
                    var unit = f.provenance().orElseThrow().unit();
                    return !unit.branches().isEmpty() || !unit.expansions().isEmpty() || unit.sources().size() > 1 || hasDirectives(unit);
                }));
    }

    private static boolean hasDirectives(Preprocessing unit) {
        var tokens = com.kniazkov.cpg.parser.CppLexer.scan(unit.translationUnit(),
                unit.sources().get(unit.translationUnit())).tokens().stream()
                .filter(t -> !t.spelling().isBlank()).toList();
        for (int i = 0; i + 1 < tokens.size(); i++)
            if (tokens.get(i).canonicalSpelling().equals("#")
                    && !tokens.get(i + 1).canonicalSpelling().equals("include")) return true;
        return false;
    }

    static Map<String, String> files(Project project) {
        var originals = new LinkedHashMap<String, String>();
        var records = new ArrayList<Preprocessing>();
        Path base = null;
        for (SourceFile file : project.files()) {
            Preprocessing unit = file.provenance().orElseThrow().unit();
            records.add(unit);
            Path directory = Path.of(unit.translationUnit()).getParent();
            if (base == null) base = directory;
            else while (!directory.startsWith(base)) base = base.getParent();
            unit.sources().forEach((path, text) -> {
                String old = originals.putIfAbsent(path, text);
                if (old != null && !old.equals(text)) throw unsupported("conflicting captured contents for " + path);
            });
        }
        Project baseline;
        try { baseline = parse(records, originals); }
        catch (RuntimeException e) { throw unsupported("captured source cannot be replayed: " + e.getMessage()); }
        Project desired = normalize(project);
        var patches = new ArrayList<Edit>();
        for (int i = 0; i < baseline.files().size(); i++) {
            var pairs = new ArrayList<Pair>();
            var changes = new ArrayList<Pair>();
            diff(baseline.files().get(i), desired.files().get(i), pairs, changes);
            Preprocessing unit = records.get(i);
            for (Pair change : changes) {
                TokenProvenance span = change.before().provenance().orElseThrow(() -> unsupported("edit has no token span"));
                Pair selected = change;
                // Always expand the outer invocation. Inner expansion ranges can refer to
                // definition text or repeated argument fragments, not an editable call site.
                for (var event : unit.expansions()) {
                    if (event.parentId() != null || event.startToken() == event.endToken()
                            || event.endToken() <= span.startToken() || event.startToken() >= span.endToken()) continue;
                    if (span.startToken() <= event.startToken() && span.endToken() >= event.endToken()) continue;
                    final int start = significantStart(unit, event.startToken(), event.endToken());
                    final int end = significantEnd(unit, event.startToken(), event.endToken());
                    selected = pairs.stream().filter(p -> p.before().provenance().map(s ->
                        s.startToken() == start && s.endToken() == end).orElse(false)).findFirst()
                        .orElseThrow(() -> unsupported("macro " + event.name() + " does not form a whole syntax subtree"));
                    break;
                }
                var selectedSpan = selected.before().provenance().orElseThrow();
                for (var event : unit.expansions()) {
                    if (event.parentId() == null && event.startToken() < event.endToken()
                            && selectedSpan.startToken() <= event.startToken() && selectedSpan.endToken() >= event.endToken()
                            && (selectedSpan.startToken() != significantStart(unit, event.startToken(), event.endToken())
                                || selectedSpan.endToken() != significantEnd(unit, event.startToken(), event.endToken())))
                        throw unsupported("structural edit would flatten other macro calls; use a narrower replacement");
                }
                SourceRange range = physicalRange(unit, selectedSpan);
                long includeId = includeId(unit, unit.tokens().get(selectedSpan.startToken()));
                for (var event : unit.expansions()) {
                    SourceRange call = event.invocation();
                    if (event.parentId() == null && event.startToken() == event.endToken()
                            && event.includeId() == includeId && call.file().equals(range.file()) && call.start().offset() < range.end().offset()
                            && call.end().offset() > range.start().offset())
                        throw unsupported("structural edit would remove an empty macro invocation; use a narrower replacement");
                }
                // Replacing a subtree which straddles a directive would discard
                // inactive text, even if replay happens to accept the active AST.
                for (var directive : unit.directives())
                    if (overlaps(range.file(), range.start().offset(), range.end().offset(), directive.range()))
                        throw unsupported("structural edit crosses preprocessing directive #" + directive.kind()
                                + " in " + range.file() + "; use a narrower replacement");
                patches.add(new Edit(new Context(i, includeId),
                        new Patch(range.file(), range.start().offset(), range.end().offset(),
                                Source.renderFragment(selected.after(), Language.CPP, insideBlock(baseline.files().get(i), selected.before(), false)))));
            }
        }
        // Include unchanged active occurrences in reconciliation, not just edited
        // nodes. Only after all requirements agree is there one patch per fragment.
        List<Patch> reconciled = reconcile(records, originals, patches);
        Map<String, String> emitted = new LinkedHashMap<>(originals);
        for (String path : originals.keySet()) {
            var edits = reconciled.stream().filter(p -> p.file().equals(path)).toList();
            emitted.put(path, apply(originals.get(path), 0, edits));
        }
        // Re-run the original ordered macro options and include search against only
        // the captured, patched files. Retained calls must still mean the desired AST.
        Project checked;
        try { checked = relocated(records, emitted, base); }
        catch (RuntimeException e) { throw unsupported("emitted source failed preprocessing/parsing: " + e.getMessage()); }
        for (int i = 0; i < checked.files().size(); i++)
            if (!render(checked.files().get(i)).equals(render(desired.files().get(i))))
                throw unsupported("emitted source changes the effective expansion of " + records.get(i).translationUnit());
        Map<String, String> result = new LinkedHashMap<>();
        for (var entry : emitted.entrySet()) {
            Path physical = Path.of(entry.getKey());
            if (!physical.startsWith(base)) throw unsupported("header lies outside the output source root: " + physical);
            String relative = base.relativize(physical).toString();
            Source.requireSafeName(relative);
            if (result.putIfAbsent(relative, entry.getValue()) != null) throw unsupported("duplicate output path " + relative);
        }
        return Collections.unmodifiableMap(result);
    }

    /** Compare complete physical requirements for every overlapping patch group. */
    private static List<Patch> reconcile(List<Preprocessing> records, Map<String, String> originals,
            List<Edit> edits) {
        var active = new LinkedHashMap<Context, List<SourceRange>>();
        for (int i = 0; i < records.size(); i++) {
            var unit = records.get(i);
            for (var token : unit.tokens()) if (!token.spelling().isBlank())
                active.computeIfAbsent(new Context(i, includeId(unit, token)), ignored -> new ArrayList<>())
                        .add(token.expansionRange());
            // Even an empty expansion observes its invocation's original spelling.
            for (var event : unit.expansions()) if (event.parentId() == null)
                active.computeIfAbsent(new Context(i, event.includeId()), ignored -> new ArrayList<>())
                        .add(event.invocation());
        }
        var result = new ArrayList<Patch>();
        for (var source : originals.entrySet()) {
            String path = source.getKey();
            var ordered = edits.stream().filter(e -> e.patch().file().equals(path))
                    .sorted(Comparator.comparingInt((Edit e) -> e.patch().start())
                            .thenComparingInt(e -> e.patch().end())).toList();
            for (int cursor = 0; cursor < ordered.size();) {
                int start = ordered.get(cursor).patch().start();
                int end = ordered.get(cursor).patch().end();
                var group = new ArrayList<Edit>();
                group.add(ordered.get(cursor++));
                while (cursor < ordered.size() && ordered.get(cursor).patch().start() < end) {
                    Edit edit = ordered.get(cursor++);
                    end = Math.max(end, edit.patch().end());
                    group.add(edit);
                }
                if (start < 0 || end > source.getValue().length()) throw unsupported("invalid physical edit bounds");
                String original = source.getValue().substring(start, end);
                var requirements = new ArrayList<Requirement>();
                for (var occurrence : active.entrySet()) {
                    boolean observed = false;
                    for (var range : occurrence.getValue())
                        if (overlaps(path, start, end, range)) { observed = true; break; }
                    if (!observed) continue; // An inactive arm imposes no AST requirement.
                    var local = group.stream().filter(e -> e.context().equals(occurrence.getKey()))
                            .map(Edit::patch).distinct().sorted(Comparator.comparingInt(Patch::start)
                                    .thenComparingInt(Patch::end)).toList();
                    for (int i = 1; i < local.size(); i++) {
                        Patch previous = local.get(i - 1), next = local.get(i);
                        if (next.start() < previous.end())
                            throw conflict(records, source.getValue(), path, start, end,
                                    List.of(new Requirement(occurrence.getKey(), previous.text()),
                                            new Requirement(occurrence.getKey(), next.text())));
                    }
                    requirements.add(new Requirement(occurrence.getKey(), apply(original, start, local)));
                }
                if (requirements.isEmpty()) throw unsupported("physical edit has no active source occurrence in " + path);
                String required = requirements.getFirst().text();
                if (requirements.stream().anyMatch(r -> !r.text().equals(required)))
                    throw conflict(records, source.getValue(), path, start, end, requirements);
                result.add(new Patch(path, start, end, required));
            }
        }
        return result;
    }

    private static boolean overlaps(String file, int start, int end, SourceRange range) {
        return file.equals(range.file()) && start < range.end().offset() && end > range.start().offset();
    }

    /** Patches are ordered and disjoint, in coordinates of the original substring. */
    private static String apply(String original, int offset, List<Patch> patches) {
        var text = new StringBuilder(original);
        for (int i = patches.size() - 1; i >= 0; i--) {
            Patch patch = patches.get(i);
            text.replace(patch.start() - offset, patch.end() - offset, patch.text());
        }
        return text.toString();
    }

    private static IllegalArgumentException conflict(List<Preprocessing> records, String original, String path,
            int start, int end, List<Requirement> requirements) {
        var reason = new StringBuilder("conflicting physical requirements for ").append(path).append(':')
                .append(coordinates(original, start)).append('-').append(coordinates(original, end))
                .append(" [offsets ").append(start).append("..").append(end).append(")");
        for (var requirement : requirements) {
            var context = requirement.context();
            var unit = records.get(context.variant());
            var configuration = unit.configuration();
            reason.append("; variant ").append(configuration.variantId()).append(" (").append(unit.translationUnit());
            for (var macro : configuration.macros()) reason.append("; ").append(String.join(" ", macro.originalArguments()));
            reason.append("; include ").append(context.includeId()).append(") requires ")
                    .append(quoted(requirement.text()));
        }
        return unsupported(reason.toString());
    }

    private static String coordinates(String text, int offset) {
        int line = 1, column = 1;
        for (int i = 0; i < offset; i++) {
            char ch = text.charAt(i);
            if (ch == '\r' || ch == '\n' && (i == 0 || text.charAt(i - 1) != '\r')) { line++; column = 1; }
            else if (ch != '\n') column++;
        }
        return line + ":" + column;
    }

    private static String quoted(String text) {
        return "\"" + text.replace("\\", "\\\\").replace("\"", "\\\"").replace("\r", "\\r")
                .replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    /** Validate the actual output layout, not merely the original absolute lookup paths. */
    private static Project relocated(List<Preprocessing> records, Map<String, String> sources, Path base) {
        String virtualName = "__cpg_generated_source__";
        if (base.getFileName() != null && base.getFileName().toString().equals(virtualName)) virtualName += "_relocated";
        Path root = Path.of("/").resolve(virtualName);
        var relocatedSources = new LinkedHashMap<String, String>();
        for (var entry : sources.entrySet()) {
            Path path = Path.of(entry.getKey());
            if (!path.startsWith(base)) throw unsupported("header lies outside the output source root: " + path);
            relocatedSources.put(root.resolve(base.relativize(path)).normalize().toString(), entry.getValue());
        }
        var relocatedRecords = new ArrayList<Preprocessing>();
        for (var record : records) {
            var configuration = record.configuration();
            Path source = root.resolve(base.relativize(configuration.source().path())).normalize();
            var includes = configuration.includes().stream().map(p -> new com.kniazkov.cpg.build.Build.IncludePath(
                    p.kind(), root.resolve(base.relativize(p.path())).normalize())).toList();
            var relocated = new com.kniazkov.cpg.build.Build.TranslationUnit(
                    new com.kniazkov.cpg.build.Build.PhysicalSource(source), root,
                    includes, configuration.macros(), configuration.standard(), configuration.configurationSource(), configuration.platform());
            relocatedRecords.add(new Preprocessing(source.toString(), List.of(), List.of(), List.of(), relocatedSources, relocated,
                    List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), record.translationTime()));
        }
        return parse(relocatedRecords, relocatedSources);
    }

    private static Project parse(List<Preprocessing> records, Map<String, String> sources) {
        var project = Project.create();
        for (var unit : records) project.addFile(Parser.parse(unit.configuration(), sources.get(unit.translationUnit()), sources, unit.translationTime()));
        return normalize(project.build());
    }
    private static Project normalize(Project project) { return ConsoleNormalizer.green(NameResolver.resolve(project)); }
    private static String render(Node node) { return Source.render(node, Language.CPP); }

    private static boolean insideBlock(Node root, Node target, boolean local) {
        if (root == target) return local;
        for (Node child : root.children())
            if (insideBlock(child, target, local || root instanceof BlockStatement)) return true;
        return false;
    }

    private static void diff(Node before, Node after, List<Pair> pairs, List<Pair> changes) {
        Pair pair = new Pair(before, after);
        pairs.add(pair);
        if (before instanceof ClassDeclaration declaration && declaration.implicit()
                && (!(after instanceof ClassDeclaration changed) || !changed.implicit()
                    || !declaration.name().equals(changed.name()) || !declaration.keyword().equals(changed.keyword())
                    || declaration.isDefinition() != changed.isDefinition() || !changed.members().isEmpty()))
            throw unsupported("edit changes an implicit class declaration; edit its written elaborated type together with its declaration");
        if (render(before).equals(render(after))) return;
        // A declarator type can surround its name (int (*name)[3]). Its abstract
        // type rendering cannot replace that noncontiguous written syntax alone.
        // Rewrite the smallest owning declaration and verify replay as usual.
        if (before instanceof Declaration && after instanceof Declaration && changedType(before, after)) {
            if (!(before instanceof CallableDeclaration) && !(before instanceof ParameterDeclaration)) {
                var span = before.provenance().orElseThrow(() -> unsupported("type edit has no declaration token span"));
                int end = significantEnd(span.unit(), span.startToken(), span.endToken());
                if (end == span.startToken() || !span.unit().tokens().get(end - 1).spelling().equals(";"))
                    throw unsupported("type edit belongs to a shared multi-declarator statement; replace the whole statement explicitly");
            }
            changes.add(pair); return;
        }
        if (before.getClass() != after.getClass() || before.children().size() != after.children().size()) {
            changes.add(pair); return;
        }
        int count = changes.size();
        for (int i = 0; i < before.children().size(); i++) diff(before.children().get(i), after.children().get(i), pairs, changes);
        if (count == changes.size()) changes.add(pair);
    }

    private static boolean changedType(Node before, Node after) {
        var oldTypes = before.children().stream().filter(TypeNode.class::isInstance).toList();
        var newTypes = after.children().stream().filter(TypeNode.class::isInstance).toList();
        if (oldTypes.size() != newTypes.size()) return true;
        for (int i = 0; i < oldTypes.size(); i++) if (!render(oldTypes.get(i)).equals(render(newTypes.get(i)))) return true;
        return false;
    }

    private static int significantStart(Preprocessing unit, int start, int end) {
        while (start < end && unit.tokens().get(start).spelling().isBlank()) start++;
        return start;
    }
    private static int significantEnd(Preprocessing unit, int start, int end) {
        while (end > start && unit.tokens().get(end - 1).spelling().isBlank()) end--;
        return end;
    }
    /** Expanded tokens keep their spelling include; edits belong to the outer call's include. */
    private static long includeId(Preprocessing unit, Preprocessing.Token token) {
        for (var event : unit.expansions())
            if (event.parentId() == null && token.expansionIds().contains(event.id())) return event.includeId();
        return token.includeId();
    }
    private static SourceRange physicalRange(Preprocessing unit, TokenProvenance span) {
        if (span.startToken() == span.endToken()) throw unsupported("empty edit span");
        var tokens = unit.tokens().subList(span.startToken(), span.endToken());
        SourceRange first = tokens.getFirst().expansionRange();
        SourceRange last = tokens.getLast().expansionRange();
        if (!first.file().equals(last.file()) || last.end().offset() < first.start().offset()
                || tokens.stream().anyMatch(t -> !t.expansionRange().file().equals(first.file())
                    || includeId(unit, t) != includeId(unit, tokens.getFirst())))
            throw unsupported("edit crosses physical source boundaries");
        if (!unit.sources().containsKey(first.file())) throw unsupported("edit targets uncaptured source");
        return new SourceRange(first.file(), first.start(), last.end());
    }
    private static IllegalArgumentException unsupported(String reason) {
        return new IllegalArgumentException("Cannot preserve C++ source safely: " + reason);
    }
}
