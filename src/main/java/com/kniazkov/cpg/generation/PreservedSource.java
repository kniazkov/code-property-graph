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

    static boolean applicable(Project project) {
        return !project.files().isEmpty()
                && project.files().stream().allMatch(f -> f.language() == Language.CPP
                    && f.provenance().map(p -> p.unit().configuration() != null).orElse(false))
                && project.files().stream().anyMatch(f -> {
                    var unit = f.provenance().orElseThrow().unit();
                    return !unit.expansions().isEmpty() || unit.sources().size() > 1 || hasDirectives(unit);
                });
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
        var patches = new ArrayList<Patch>();
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
                for (var event : unit.expansions()) {
                    SourceRange call = event.invocation();
                    if (event.parentId() == null && event.startToken() == event.endToken()
                            && call.file().equals(range.file()) && call.start().offset() < range.end().offset()
                            && call.end().offset() > range.start().offset())
                        throw unsupported("structural edit would remove an empty macro invocation; use a narrower replacement");
                }
                patches.add(new Patch(range.file(), range.start().offset(), range.end().offset(), render(selected.after())));
            }
        }
        Map<String, String> emitted = new LinkedHashMap<>(originals);
        for (String path : originals.keySet()) {
            var edits = patches.stream().filter(p -> p.file().equals(path))
                    .distinct().sorted(Comparator.comparingInt(Patch::start).thenComparing(Comparator.comparingInt(Patch::end).reversed())).toList();
            var accepted = new ArrayList<Patch>();
            for (Patch patch : edits) {
                if (!accepted.isEmpty()) {
                    Patch previous = accepted.getLast();
                    if (patch.start() < previous.end()) {
                        if (patch.start() == previous.start() && patch.end() == previous.end() && patch.text().equals(previous.text())) continue;
                        // Nested edits are rendered by the encompassing edited subtree.
                        if (patch.end() <= previous.end() && patch.start() != previous.start()) continue;
                        throw unsupported("overlapping physical edits in " + path);
                    }
                }
                accepted.add(patch);
            }
            StringBuilder text = new StringBuilder(originals.get(path));
            for (int i = accepted.size() - 1; i >= 0; i--) {
                Patch p = accepted.get(i);
                if (p.start() < 0 || p.end() > text.length()) throw unsupported("invalid physical edit bounds");
                text.replace(p.start(), p.end(), p.text());
            }
            emitted.put(path, text.toString());
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
                    includes, configuration.macros(), configuration.standard());
            relocatedRecords.add(new Preprocessing(source.toString(), List.of(), List.of(), List.of(), relocatedSources, relocated));
        }
        return parse(relocatedRecords, relocatedSources);
    }

    private static Project parse(List<Preprocessing> records, Map<String, String> sources) {
        var project = Project.create();
        for (var unit : records) project.addFile(Parser.parse(unit.configuration(), sources.get(unit.translationUnit()), sources));
        return normalize(project.build());
    }
    private static Project normalize(Project project) { return ConsoleNormalizer.green(NameResolver.resolve(project)); }
    private static String render(Node node) { return Source.render(node, Language.CPP); }

    private static void diff(Node before, Node after, List<Pair> pairs, List<Pair> changes) {
        Pair pair = new Pair(before, after);
        pairs.add(pair);
        if (render(before).equals(render(after))) return;
        if (before.getClass() != after.getClass() || before.children().size() != after.children().size()) {
            changes.add(pair); return;
        }
        int count = changes.size();
        for (int i = 0; i < before.children().size(); i++) diff(before.children().get(i), after.children().get(i), pairs, changes);
        if (count == changes.size()) changes.add(pair);
    }

    private static int significantStart(Preprocessing unit, int start, int end) {
        while (start < end && unit.tokens().get(start).spelling().isBlank()) start++;
        return start;
    }
    private static int significantEnd(Preprocessing unit, int start, int end) {
        while (end > start && unit.tokens().get(end - 1).spelling().isBlank()) end--;
        return end;
    }
    private static SourceRange physicalRange(Preprocessing unit, TokenProvenance span) {
        if (span.startToken() == span.endToken()) throw unsupported("empty edit span");
        var tokens = unit.tokens().subList(span.startToken(), span.endToken());
        SourceRange first = tokens.getFirst().expansionRange();
        SourceRange last = tokens.getLast().expansionRange();
        if (!first.file().equals(last.file()) || last.end().offset() < first.start().offset()
                || tokens.stream().anyMatch(t -> !t.expansionRange().file().equals(first.file())))
            throw unsupported("edit crosses physical source boundaries");
        if (!unit.sources().containsKey(first.file())) throw unsupported("edit targets uncaptured source");
        return new SourceRange(first.file(), first.start(), last.end());
    }
    private static IllegalArgumentException unsupported(String reason) {
        return new IllegalArgumentException("Cannot preserve C++ source safely: " + reason);
    }
}
