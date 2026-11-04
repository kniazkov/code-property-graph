/* Code Property Graph: C++03 translation phase 4. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.SourceRange;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;

/**
 * Translation-unit-local macro expansion and recursive header inclusion. Input
 * files always pass through CppLexer phases 1–3. Conditional support is limited
 * to ifdef/ifndef/else/endif (include guards); #if expressions are a later stage.
 * Empty actual arguments and invalid pastes are diagnosed rather than assigning
 * meaning to C++03 undefined behavior. Zero-parameter invocations and arguments
 * that become empty after expansion are supported; variadic macros are not.
 */
public final class CppPreprocessor {
    private CppPreprocessor() {}

    /** One expansion step; physical definition and invocation remain distinct. */
    public record MacroExpansion(String name, SourceRange definition, SourceRange invocation) {}
    /**
     * token.range is the physical spelling origin. expansionRange locates the
     * outer invocation for parser diagnostics; include identifies the physical
     * spelling occurrence. origins preserves nested substitution/expansion steps.
     * HEADER_NAME output denotes an opaque modeled standard include only.
     */
    public record ExpandedToken(PreprocessingToken token, Build.IncludeInstance include,
            List<MacroExpansion> origins, SourceRange expansionRange, List<Long> expansionIds) {
        public ExpandedToken { origins = List.copyOf(origins); expansionIds = List.copyOf(expansionIds); }
        public ExpandedToken(PreprocessingToken token, Build.IncludeInstance include,
                List<MacroExpansion> origins, SourceRange expansionRange) {
            this(token, include, origins, expansionRange, List.of());
        }
    }
    /** A physical source contribution, including its particular include occurrence. */
    public record Fragment(SourceRange range, Build.IncludeInstance include, String spelling) {}
    /** # or ## operation, retaining every operand fragment even after synthetic lexing. */
    public record Operation(String kind, SourceRange operator, List<Fragment> fragments, String spelling) {
        public Operation { fragments = List.copyOf(fragments); }
    }
    /**
     * One executed macro invocation. IDs start at one and are TU-local preorder IDs.
     * Bounds are half-open indices in Result.tokens (including whitespace, excluding
     * internal anchors). Empty or directive-only expansions have equal bounds.
     * A prescanned argument reused twice has one event and multiple output tokens.
     */
    public record ExpansionEvent(long id, String name, SourceRange definition, List<Fragment> replacement,
            SourceRange invocation, Build.IncludeInstance include, Long parentId,
            List<List<Fragment>> arguments, List<Operation> operations, int startToken, int endToken) {
        public ExpansionEvent {
            replacement = List.copyOf(replacement);
            arguments = arguments.stream().map(List::copyOf).toList();
            operations = List.copyOf(operations);
        }
    }
    public record Result(List<ExpandedToken> tokens, List<Build.IncludeInstance> includes,
            List<ExpansionEvent> expansions) {
        public Result { tokens = List.copyOf(tokens); includes = List.copyOf(includes); expansions = List.copyOf(expansions); }
        public Result(List<ExpandedToken> tokens, List<Build.IncludeInstance> includes) { this(tokens, includes, List.of()); }
    }

    /** Preprocess supplied root contents using this TU's ordered -D/-U and search paths. */
    public static Result preprocess(Build.TranslationUnit unit, String source) {
        com.kniazkov.cpg.build.CppStandard.require(unit.standard(), unit.source().path().toString());
        return new Processor(unit).run(source);
    }

    // Deliberately only the library header currently modeled by the parser.
    private static final Set<String> OPAQUE_HEADERS = Set.of("iostream");
    private record Macro(String name, List<String> parameters, List<Work> replacement, SourceRange definition, SourceRange fullDefinition) {}
    private record Work(ExpandedToken value, Set<String> hidden, boolean paste,
            boolean anchor, List<Fragment> fragments) {
        Work(ExpandedToken value) { this(value, Set.of(), false); }
        Work(ExpandedToken value, Set<String> hidden, boolean paste) {
            this(value, hidden, paste, false,
                    List.of(new Fragment(value.token().range(), value.include(), value.token().spelling())));
        }
        String text() { return value.token().canonicalSpelling(); }
        boolean space() { return anchor || value.token().kind() == WHITESPACE || value.token().kind() == NEWLINE; }
    }
    private record Conditional(boolean parentActive, boolean condition, boolean seenElse) {
        boolean active() { return parentActive && (seenElse ? !condition : condition); }
    }
    private static final class Processor {
        private final Build.TranslationUnit unit;
        private final Map<String, Macro> macros = new LinkedHashMap<>();
        private final List<Build.IncludeInstance> includes = new ArrayList<>();
        private final List<Work> output = new ArrayList<>();
        private long occurrence;
        private long expansions;
        private final List<Event> events = new ArrayList<>();
        private final class Event {
            final long id;
            final Macro macro;
            final SourceRange call;
            final Build.IncludeInstance include;
            final Long parent;
            final List<List<Fragment>> arguments;
            final List<Operation> operations = new ArrayList<>();
            int anchor = -1, start = -1, end = -1;
            Event(Macro macro, SourceRange call, Work invocation, List<List<Work>> args, Long parent) {
                this.id = events.size() + 1L; this.macro = macro; this.call = call;
                this.include = parent == null ? invocation.value.include() : events.get(parent.intValue() - 1).include;
                this.parent = parent;
                this.arguments = args.stream().map(Processor::fragments).toList();
                events.add(this);
            }
            ExpansionEvent freeze() {
                return new ExpansionEvent(id, macro.name, macro.fullDefinition, fragments(macro.replacement),
                        call, include, parent, arguments, operations, start, end);
            }
        }
        static List<Fragment> fragments(List<Work> tokens) {
            return tokens.stream().filter(t -> !t.anchor).flatMap(t -> t.fragments.stream()).toList();
        }
        Work anchor(Event event) {
            var token = new PreprocessingToken(WHITESPACE, "", "", event.call, 0, 0);
            return new Work(new ExpandedToken(token, event.include, List.of(), event.call, List.of(event.id)),
                    Set.of(), false, true, List.of());
        }
        Result finish() {
            var tokens = new ArrayList<ExpandedToken>();
            for (Work token : output) {
                for (long id : token.value.expansionIds()) {
                    Event event = events.get((int) id - 1);
                    if (token.anchor) { if (event.anchor < 0) event.anchor = tokens.size(); }
                    else { if (event.start < 0) event.start = tokens.size(); event.end = tokens.size() + 1; }
                }
                if (!token.anchor) tokens.add(token.value);
            }
            // Consumed anchors (e.g. an empty argument of a later invocation) use
            // their enclosing expansion's boundary, never a fabricated source offset.
            for (Event event : events) if (event.start < 0) {
                Event parent = event.parent == null ? null : events.get(event.parent.intValue() - 1);
                event.start = event.anchor >= 0 ? event.anchor : parent != null ? parent.start : 0;
                event.end = event.start;
            }
            return new Result(tokens, includes, events.stream().map(Event::freeze).toList());
        }
        Processor(Build.TranslationUnit unit) { this.unit = unit; }

        Result run(String source) {
            var root = instance(unit.source().path(), null);
            int operationOrdinal = 0;
            for (var operation : unit.macros()) {
                // Build options have their own synthetic text buffers, not offsets
                // into the physical translation unit. Ordinals include -U options.
                String optionSource = unit.directory().resolve("<command-line:" + ++operationOrdinal + ">").toString();
                String operand = operation.operand();
                if (operation.define()) {
                    int equals = operand.indexOf('=');
                    String definition = equals < 0 ? operand + " 1" : operand.substring(0, equals) + " " + operand.substring(equals + 1);
                    define(wrap(CppLexer.scan(optionSource, definition).tokens(), root));
                } else {
                    var name = significant(wrap(CppLexer.scan(optionSource, operand).tokens(), root));
                    if (name.size() != 1 || !identifier(name.get(0))) throw error(root, "Invalid -U operand: " + operand);
                    macros.remove(name.get(0).text());
                }
            }
            process(root, source, 0);
            var end = CppLexer.scan(unit.source().path().toString(), source).tokens().getLast();
            output.add(new Work(new ExpandedToken(end, root, List.of(), end.range())));
            return finish();
        }
        Build.IncludeInstance instance(Path path, Build.IncludeInstance parent) {
            var result = new Build.IncludeInstance(unit, new Build.PhysicalSource(path), parent, occurrence++);
            includes.add(result); return result;
        }
        List<Work> wrap(List<PreprocessingToken> tokens, Build.IncludeInstance include) {
            return tokens.stream().filter(t -> t.kind() != EOF).map(t -> new Work(new ExpandedToken(t, include, List.of(), t.range()))).toList();
        }
        void process(Build.IncludeInstance include, String source, int depth) {
            if (depth > 128) throw error(include, "Include nesting limit exceeded (possible unguarded cycle)");
            List<Work> input;
            try { input = wrap(CppLexer.scan(include.source().path().toString(), source).tokens(), include); }
            catch (ParseException e) { throw withChain(e, include); }
            var conditions = new ArrayDeque<Conditional>();
            var pending = new ArrayList<Work>();
            for (int i = 0; i < input.size();) {
                int end = i;
                while (end < input.size() && input.get(end).value.token().kind() != NEWLINE) end++;
                var line = input.subList(i, end);
                var significant = significant(line);
                boolean active = conditions.isEmpty() || conditions.peek().active();
                if (!significant.isEmpty() && significant.get(0).text().equals("#")) {
                    output.addAll(expand(pending)); pending.clear();
                    if (significant.size() > 1) {
                        String directive = significant.get(1).text();
                        var args = significant.subList(2, significant.size());
                        Work location = significant.get(1);
                        switch (directive) {
                            case "ifdef", "ifndef" -> {
                                // In a skipped group only the directive name is processed
                                // ([cpp.cond]/6); even nested conditional operands are ignored.
                                if (!active) {
                                    conditions.push(new Conditional(false, false, false));
                                    break;
                                }
                                if (args.size() != 1 || !identifier(args.get(0))) throw error(location, "Expected one macro name after #" + directive);
                                boolean defined = macros.containsKey(args.get(0).text());
                                conditions.push(new Conditional(active, directive.equals("ifdef") ? defined : !defined, false));
                            }
                            case "if" -> {
                                if (active) throw error(location, "#if expressions are not supported yet");
                                conditions.push(new Conditional(false, false, false));
                            }
                            case "else" -> {
                                if (conditions.isEmpty() || conditions.peek().seenElse || conditions.peek().parentActive && !args.isEmpty()) throw error(location, "Unmatched or malformed #else");
                                var old = conditions.pop(); conditions.push(new Conditional(old.parentActive, old.condition, true));
                            }
                            case "endif" -> {
                                if (conditions.isEmpty() || conditions.peek().parentActive && !args.isEmpty()) throw error(location, "Unmatched or malformed #endif");
                                conditions.pop();
                            }
                            default -> {
                                if (active) {
                                    int after = line.indexOf(location) + 1;
                                    var rawArgs = line.subList(after, line.size());
                                    switch (directive) {
                                        case "define" -> define(rawArgs);
                                        case "undef" -> {
                                            if (args.size() != 1 || !identifier(args.get(0))) throw error(location, "Expected one macro name after #undef");
                                            macros.remove(args.get(0).text());
                                        }
                                        case "include" -> include(include, rawArgs, location, depth);
                                        case "error" -> throw error(location, "#error " + spelling(rawArgs));
                                        default -> throw error(location, "Unsupported preprocessing directive #" + directive);
                                    }
                                }
                            }
                        }
                    }
                } else if (active) {
                    pending.addAll(line);
                    if (end < input.size()) pending.add(input.get(end));
                }
                i = end + 1;
            }
            output.addAll(expand(pending));
            if (!conditions.isEmpty()) throw error(include, "Unterminated conditional directive");
        }
        void define(List<Work> line) {
            var raw = trim(line);
            if (raw.isEmpty() || !identifier(raw.get(0))) throw raw.isEmpty()
                    ? error(unit.source().path().toString(), "Missing macro name") : error(raw.get(0), "Expected macro name");
            Work name = raw.get(0);
            int index = 1;
            List<String> parameters = null;
            if (index < raw.size() && raw.get(index).text().equals("(")) {
                parameters = new ArrayList<>(); index++;
                index = next(raw, index);
                if (index >= raw.size()) throw error(name, "Unterminated macro parameter list");
                if (!raw.get(index).text().equals(")")) {
                    while (true) {
                        if (index >= raw.size() || !identifier(raw.get(index))) throw error(name, "Expected macro parameter (variadic macros are not C++03)");
                        String parameter = raw.get(index++).text();
                        if (parameters.contains(parameter)) throw error(name, "Duplicate macro parameter: " + parameter);
                        parameters.add(parameter); index = next(raw, index);
                        if (index >= raw.size()) throw error(name, "Unterminated macro parameter list");
                        if (!raw.get(index).text().equals(",")) break;
                        index = next(raw, index + 1);
                    }
                }
                if (!raw.get(index).text().equals(")")) throw error(name, "Expected ')' in macro parameters");
                index++;
            }
            var replacement = trim(raw.subList(index, raw.size()));
            var sig = significant(replacement);
            if (!sig.isEmpty() && (sig.getFirst().text().equals("##") || sig.getLast().text().equals("##"))) throw error(name, "## cannot begin or end a macro replacement");
            if (parameters != null) for (int i = 0; i < sig.size(); i++) {
                if (sig.get(i).text().equals("#") && (i + 1 == sig.size() || !parameters.contains(sig.get(i + 1).text()))) throw error(sig.get(i), "# must precede a macro parameter");
            }
            var macro = new Macro(name.text(), parameters == null ? null : List.copyOf(parameters), List.copyOf(replacement), name.value.token().range(), new SourceRange(name.value.token().range().file(),
                    name.value.token().range().start(), raw.getLast().value.token().range().end()));
            Macro old = macros.get(name.text());
            if (old != null && (!Objects.equals(old.parameters, macro.parameters) || !spelling(old.replacement).equals(spelling(macro.replacement)))) throw error(name, "Incompatible redefinition of macro " + name.text());
            macros.put(name.text(), macro);
        }
        void include(Build.IncludeInstance parent, List<Work> raw, Work location, int depth) {
            int firstEvent = events.size();
            var expanded = trim(expand(raw));
            // Include operands are not emitted as C++ tokens, but their events
            // remain visible at the insertion point immediately before the header.
            int anchorIndex = (int) output.stream().filter(t -> !t.anchor).count();
            for (int e = firstEvent; e < events.size(); e++) events.get(e).anchor = anchorIndex;
            var args = significant(expanded);
            String spec = spelling(expanded);
            boolean quote = spec.startsWith("\"") && spec.endsWith("\"");
            boolean angle = spec.startsWith("<") && spec.endsWith(">");
            if ((!quote && !angle) || spec.length() <= 2 || quote && args.size() != 1)
                throw error(location, "Expected quoted or angle header name after #include");
            String header = spec.substring(1, spec.length() - 1);
            var candidates = new ArrayList<Path>();
            if (quote) candidates.add(parent.source().path().getParent().resolve(header));
            if (quote) for (var path : unit.includes()) if (path.kind().equals("-iquote")) candidates.add(path.path().resolve(header));
            for (String kind : List.of("-I", "-isystem")) for (var path : unit.includes()) if (path.kind().equals(kind)) candidates.add(path.path().resolve(header));
            for (Path candidate : candidates) if (Files.isRegularFile(candidate)) {
                var included = instance(candidate, parent);
                try { process(included, Files.readString(candidate), depth + 1); }
                catch (IOException e) { throw error(location, "Cannot read header " + candidate + ": " + e.getMessage()); }
                return;
            }
            if (angle && OPAQUE_HEADERS.contains(header)) {
                PreprocessingToken token = new PreprocessingToken(HEADER_NAME, spec, spec, location.value.token().range(), 0, spec.length());
                output.add(new Work(new ExpandedToken(token, parent, location.value.origins(), location.value.expansionRange())));
                return;
            }
            throw error(location, "Header not found: " + header);
        }

        List<Work> expand(List<Work> input) { return expand(input, null); }
        List<Work> expand(List<Work> input, Long context) {
            var work = new ArrayList<>(input);
            for (int index = 0; index < work.size();) {
                Work invocation = work.get(index);
                Macro macro = macros.get(invocation.text());
                if (macro == null || !identifier(invocation) || invocation.hidden.contains(macro.name)) { index++; continue; }
                int end = index + 1;
                List<List<Work>> arguments = List.of(), rawArguments = List.of();
                Set<String> hide = new HashSet<>(invocation.hidden);
                if (macro.parameters != null) {
                    int open = next(work, end);
                    if (open >= work.size() || !work.get(open).text().equals("(")) { index++; continue; }
                    var collected = new ArrayList<List<Work>>();
                    var rawCollected = new ArrayList<List<Work>>();
                    int level = 0, start = open + 1, close = start;
                    for (; close < work.size(); close++) {
                        String text = work.get(close).text();
                        if (text.equals("(")) level++;
                        else if (text.equals(")")) { if (level == 0) break; level--; }
                        else if (text.equals(",") && level == 0) { collected.add(trim(work.subList(start, close))); rawCollected.add(new ArrayList<>(work.subList(start, close))); start = close + 1; }
                    }
                    if (close == work.size()) throw error(invocation, "Unterminated invocation of macro " + macro.name);
                    var finalArgument = trim(work.subList(start, close));
                    if (!finalArgument.isEmpty() || !collected.isEmpty() || !macro.parameters.isEmpty()) {
                        collected.add(finalArgument); rawCollected.add(new ArrayList<>(work.subList(start, close)));
                    }
                    if (collected.size() != macro.parameters.size()) throw error(invocation, "Macro " + macro.name + " expects " + macro.parameters.size() + " arguments, got " + collected.size());
                    if (collected.stream().anyMatch(List::isEmpty)) throw error(invocation, "Empty macro argument has undefined behavior in C++03");
                    hide.retainAll(work.get(close).hidden);
                    arguments = collected; rawArguments = rawCollected; end = close + 1;
                }
                if (++expansions > 100000) throw error(invocation, "Macro expansion limit exceeded");
                hide.add(macro.name);
                SourceRange call = invocation.value.expansionRange();
                SourceRange last = work.get(end - 1).value.expansionRange();
                if (call.file().equals(last.file()) && last.end().offset() >= call.start().offset()) call = new SourceRange(call.file(), call.start(), last.end());
                var step = new MacroExpansion(macro.name, macro.definition, call);
                List<Long> ancestry = invocation.value.expansionIds();
                Long parent = ancestry.isEmpty() ? context : ancestry.getLast();
                // Argument tokens may already carry an enclosing replacement's
                // ancestry. The invocation doing this prescan is their new parent.
                if (context != null && (parent == null || context > parent)) parent = context;
                var event = new Event(macro, call, invocation, rawArguments, parent);
                List<Work> replacement = substitute(macro, arguments, invocation, step, hide, event);
                replacement.addFirst(anchor(event));
                work.subList(index, end).clear(); work.addAll(index, replacement);
            }
            return work;
        }
        List<Work> substitute(Macro macro, List<List<Work>> args, Work invocation, MacroExpansion step, Set<String> hide, Event event) {
            var replacement = new ArrayList<Work>();
            var expandedArgs = new HashMap<String, List<Work>>();
            for (int i = 0; i < macro.replacement.size(); i++) {
                Work token = macro.replacement.get(i);
                if (macro.parameters != null && token.text().equals("#")) {
                    i = next(macro.replacement, i + 1);
                    int parameter = macro.parameters.indexOf(macro.replacement.get(i).text());
                    String spelling = stringize(args.get(parameter));
                    List<Fragment> fragments = fragments(args.get(parameter));
                    event.operations.add(new Operation("stringize", token.value.token().range(), fragments, spelling));
                    Work synthetic = synthetic(token, STRING_LITERAL, spelling);
                    replacement.add(new Work(synthetic.value, synthetic.hidden, false, false, fragments));
                } else if (token.text().equals("##")) {
                    replacement.add(new Work(token.value, token.hidden, true));
                } else {
                    int parameter = macro.parameters == null ? -1 : macro.parameters.indexOf(token.text());
                    if (parameter < 0) replacement.add(token);
                    else {
                        int before = previous(macro.replacement, i - 1), after = next(macro.replacement, i + 1);
                        boolean pasted = before >= 0 && macro.replacement.get(before).text().equals("##")
                                || after < macro.replacement.size() && macro.replacement.get(after).text().equals("##");
                        List<Work> argument = args.get(parameter);
                        if (!pasted) {
                            final List<Work> raw = argument;
                            argument = expandedArgs.computeIfAbsent(token.text(), key -> expand(raw, event.id));
                        }
                        replacement.addAll(argument);
                    }
                }
            }
            for (int i = 0; i < replacement.size();) {
                if (!replacement.get(i).paste) { i++; continue; }
                int left = previous(replacement, i - 1), right = next(replacement, i + 1);
                if (left < 0 || right >= replacement.size() || replacement.get(left).paste || replacement.get(right).paste) throw error(invocation, "Missing operand for ##");
                Work a = replacement.get(left), b = replacement.get(right), merged;
                {
                    String joined = a.value.token().spelling() + b.value.token().spelling();
                    List<PreprocessingToken> scanned;
                    try { scanned = CppLexer.scan(invocation.value.token().range().file(), joined).tokens(); }
                    catch (ParseException e) { throw error(invocation, "Invalid token paste: " + joined); }
                    if (scanned.size() != 2 || scanned.get(0).kind() == WHITESPACE || scanned.get(0).kind() == NEWLINE || scanned.get(0).kind() == OTHER || !scanned.get(0).spelling().equals(joined)) throw error(invocation, "Invalid token paste: " + joined);
                    PreprocessingToken lexical = scanned.getFirst();
                    var token = new PreprocessingToken(lexical.kind(), joined, lexical.canonicalSpelling(), a.value.token().range(), 0, joined.length());
                    var origins = new ArrayList<>(a.value.origins()); origins.addAll(b.value.origins());
                    var fragments = new ArrayList<>(a.fragments); fragments.addAll(b.fragments);
                    event.operations.add(new Operation("paste", replacement.get(i).value.token().range(), fragments, joined));
                    var ids = new LinkedHashSet<>(a.value.expansionIds()); ids.addAll(b.value.expansionIds());
                    var bothHidden = new HashSet<>(a.hidden); bothHidden.retainAll(b.hidden);
                    merged = new Work(new ExpandedToken(token, a.value.include(), origins, a.value.expansionRange(), List.copyOf(ids)), Set.copyOf(bothHidden), false, false, List.copyOf(fragments));
                }
                replacement.subList(left, right + 1).clear(); replacement.add(left, merged); i = left;
            }
            var result = new ArrayList<Work>();
            for (Work token : replacement) {
                var hidden = new HashSet<>(token.hidden); hidden.addAll(hide);
                var origins = new ArrayList<>(invocation.value.origins()); origins.add(step); origins.addAll(token.value.origins());
                var ids = new LinkedHashSet<>(invocation.value.expansionIds()); ids.add(event.id); ids.addAll(token.value.expansionIds());
                result.add(new Work(new ExpandedToken(token.value.token(), token.value.include(), origins, step.invocation(), List.copyOf(ids)),
                        Set.copyOf(hidden), false, token.anchor, token.fragments));
            }
            return result;
        }
        Work synthetic(Work source, PreprocessingToken.Kind kind, String text) {
            var token = new PreprocessingToken(kind, text, text, source.value.token().range(), 0, text.length());
            return new Work(new ExpandedToken(token, source.value.include(), source.value.origins(), source.value.expansionRange()), source.hidden, false);
        }
        ParseException error(Work location, String reason) {
            SourceRange range = location.value.expansionRange();
            return withChain(new ParseException(range.file(), range.start().line(), range.start().column(), reason), location.value.include());
        }
        ParseException error(Build.IncludeInstance include, String reason) {
            return withChain(error(include.source().path().toString(), reason), include);
        }
        ParseException error(String file, String reason) { return new ParseException(file, 1, 1, reason); }
        ParseException withChain(ParseException error, Build.IncludeInstance include) {
            var chain = new ArrayList<String>();
            for (var cursor = include; cursor != null; cursor = cursor.parent()) chain.add(cursor.source().path().toString());
            Collections.reverse(chain);
            return new ParseException(error.file(), error.line(), error.column(), error.reason() + " [include chain: " + String.join(" -> ", chain) + "]");
        }
    }
    private static boolean identifier(Work token) {
        // new/delete are keywords rather than alternative word operators and can be macro names.
        return token.value.token().kind() == IDENTIFIER || token.text().equals("new") || token.text().equals("delete");
    }
    private static int next(List<Work> tokens, int index) { while (index < tokens.size() && tokens.get(index).space()) index++; return index; }
    private static int previous(List<Work> tokens, int index) { while (index >= 0 && tokens.get(index).space()) index--; return index; }
    private static List<Work> trim(List<Work> tokens) { int start = next(tokens, 0), end = previous(tokens, tokens.size() - 1) + 1; return new ArrayList<>(tokens.subList(start, Math.max(start, end))); }
    private static List<Work> significant(List<Work> tokens) { return tokens.stream().filter(t -> !t.space()).toList(); }
    private static String spelling(List<Work> tokens) {
        var result = new StringBuilder(); boolean space = false;
        for (Work token : trim(tokens)) {
            if (token.anchor) continue;
            if (token.space()) space = true;
            else { if (space && !result.isEmpty()) result.append(' '); result.append(token.value.token().spelling()); space = false; }
        }
        return result.toString();
    }
    private static String stringize(List<Work> tokens) {
        var result = new StringBuilder("\""); boolean space = false;
        for (Work token : trim(tokens)) {
            if (token.anchor) continue;
            if (token.space()) space = true;
            else {
                if (space) result.append(' '); space = false;
                String text = token.value.token().spelling();
                if (token.value.token().kind() == STRING_LITERAL || token.value.token().kind() == CHARACTER_LITERAL) text = text.replace("\\", "\\\\").replace("\"", "\\\"");
                result.append(text);
            }
        }
        return result.append('"').toString();
    }
}
