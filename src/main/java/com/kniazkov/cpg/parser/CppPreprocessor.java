/* Code Property Graph: C++03 translation phase 4. MIT License. */
package com.kniazkov.cpg.parser;

import com.kniazkov.cpg.build.Build;
import com.kniazkov.cpg.model.SourceRange;
import com.kniazkov.cpg.model.SourcePosition;
import com.kniazkov.cpg.model.Preprocessing;
import com.kniazkov.cpg.model.DiagnosticContext;
import com.kniazkov.cpg.model.DiagnosticRelatedRange;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import static com.kniazkov.cpg.parser.PreprocessingToken.Kind.*;

/**
 * Translation-unit-local macro expansion and recursive header inclusion. Input
 * files always pass through CppLexer phases 1–3. Conditional groups retain their
 * original source, including skipped groups and expression fragments.
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
            List<List<Fragment>> arguments, List<Operation> operations, int startToken, int endToken, long definitionId) {
        public ExpansionEvent(long id, String name, SourceRange definition, List<Fragment> replacement,
                SourceRange invocation, Build.IncludeInstance include, Long parentId,
                List<List<Fragment>> arguments, List<Operation> operations, int startToken, int endToken) {
            this(id, name, definition, replacement, invocation, include, parentId, arguments, operations, startToken, endToken, 0);
        }
        public ExpansionEvent {
            replacement = List.copyOf(replacement);
            arguments = arguments.stream().map(List::copyOf).toList();
            operations = List.copyOf(operations);
        }
    }
    /** Every executed definition, including unused definitions and repeated identical definitions. */
    public record Definition(long id, String name, boolean functionLike, List<String> parameters,
            String rawReplacement, SourceRange range, Build.IncludeInstance include,
            String originKind, String sourceKey, String unavailableReason) {
        public Definition(long id, String name, boolean functionLike, List<String> parameters,
                String rawReplacement, SourceRange range, Build.IncludeInstance include, String originKind, String sourceKey) {
            this(id, name, functionLike, parameters, rawReplacement, range, include, originKind, sourceKey, null);
        }
        public Definition { parameters = List.copyOf(parameters); }
    }
    /** A conditional arm's physical text, and its contribution to the expanded token stream. */
    public record Branch(long id, String directive, String rawSource, SourceRange range,
            SourceRange directiveRange, Build.IncludeInstance include, Long parentId,
            boolean parentActive, boolean evaluated, boolean conditionResult, boolean active,
            int startToken, int endToken) {}
    /** Presence is a query, never a fabricated value expansion. */
    public record PresenceCheck(long id, String name, SourceRange range, Build.IncludeInstance include,
            boolean present, boolean conditionResult, Long definitionId, long branchId) {}
    /** Unknown pragmas are retained and ignored; skipped directives are retained without evaluation. */
    public record Directive(String kind, String rawSource, SourceRange range,
            Build.IncludeInstance include, boolean active, String includedHeader, int startToken) {
        public Directive(String kind, String rawSource, SourceRange range, Build.IncludeInstance include, boolean active) {
            this(kind, rawSource, range, include, active, null, -1);
        }
    }
    /** Presumed filename/line is separate from the immutable physical source span. */
    public record LogicalLocation(SourceRange range, Build.IncludeInstance include, String file, int line) {}
    public record IncludeState(Build.IncludeInstance include, Map<String, Long> activeDefinitions) {
        public IncludeState { activeDefinitions = Collections.unmodifiableMap(new LinkedHashMap<>(activeDefinitions)); }
    }
    public record Result(List<ExpandedToken> tokens, List<Build.IncludeInstance> includes,
            List<ExpansionEvent> expansions, Map<String, String> sources, List<Definition> definitions,
            List<Branch> branches, List<PresenceCheck> presenceChecks, List<Directive> directives,
            List<LogicalLocation> logicalLocations, List<IncludeState> includeStates, Instant translationTime) {
        public Result {
            tokens = List.copyOf(tokens); includes = List.copyOf(includes); expansions = List.copyOf(expansions);
            sources = Collections.unmodifiableMap(new LinkedHashMap<>(sources)); definitions = List.copyOf(definitions);
            branches = List.copyOf(branches); presenceChecks = List.copyOf(presenceChecks);
            directives = List.copyOf(directives); logicalLocations = List.copyOf(logicalLocations); includeStates = List.copyOf(includeStates);
        }
        public Result(List<ExpandedToken> tokens, List<Build.IncludeInstance> includes,
                List<ExpansionEvent> expansions, Map<String, String> sources, List<Definition> definitions,
                List<Branch> branches, List<PresenceCheck> presenceChecks, List<Directive> directives,
                List<LogicalLocation> logicalLocations, List<IncludeState> includeStates) {
            this(tokens, includes, expansions, sources, definitions, branches, presenceChecks, directives,
                    logicalLocations, includeStates, Instant.EPOCH);
        }
        public Result(List<ExpandedToken> tokens, List<Build.IncludeInstance> includes) { this(tokens, includes, List.of(), Map.of()); }
        public Result(List<ExpandedToken> tokens, List<Build.IncludeInstance> includes, List<ExpansionEvent> expansions) {
            this(tokens, includes, expansions, Map.of());
        }
        public Result(List<ExpandedToken> tokens, List<Build.IncludeInstance> includes, List<ExpansionEvent> expansions, Map<String, String> sources) {
            this(tokens, includes, expansions, sources, List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), Instant.EPOCH);
        }
    }

    /** Preprocess supplied root contents using this TU's ordered -D/-U and search paths. */
    public static Result preprocess(Build.TranslationUnit unit, String source) {
        com.kniazkov.cpg.build.CppStandard.require(unit.standard(), unit.source().path().toString());
        return new Processor(unit, null).run(source);
    }

    /** Trusted declaration catalogues may retain an unavailable default-argument name;
     * this does not make its value available to user code or directive evaluation. */
    static Result preprocessLibrary(Build.TranslationUnit unit, String source) {
        com.kniazkov.cpg.build.CppStandard.require(unit.standard(), unit.source().path().toString());
        var processor = new Processor(unit, Map.of(unit.source().path().toString(), source), Instant.EPOCH);
        processor.declarationOnly = true;
        return processor.run(source);
    }

    /** Replays a captured translation unit without consulting the filesystem, including existence checks. */
    public static Result preprocess(Build.TranslationUnit unit, String source, Map<String, String> sources) {
        com.kniazkov.cpg.build.CppStandard.require(unit.standard(), unit.source().path().toString());
        return new Processor(unit, Map.copyOf(sources)).run(source);
    }

    /** Explicit UTC translation time for reproducible captures; defaults use the actual start time. */
    public static Result preprocess(Build.TranslationUnit unit, String source, Map<String, String> sources, Instant translationTime) {
        com.kniazkov.cpg.build.CppStandard.require(unit.standard(), unit.source().path().toString());
        return new Processor(unit, sources == null ? null : Map.copyOf(sources), translationTime).run(source);
    }

    /** Partial preprocessing and its terminal error, for diagnostic parsing only. */
    record Recovery(Result result, ParseException problem) {}
    static Recovery preprocessRecovering(Build.TranslationUnit unit, String source, Map<String, String> sources, Instant time) {
        var processor = new Processor(unit, sources == null ? null : Map.copyOf(sources), time == null ? Instant.now() : time);
        processor.recovering = true;
        try { return new Recovery(processor.run(source), null); }
        catch (ParseException error) {
            SourceRange range = error.range().orElseGet(() -> new SourceRange(unit.source().path().toString(),
                    new SourcePosition(1, 1, 0), new SourcePosition(1, 1, 0)));
            Long occurrence = error.context() == null || error.context().includeChain().isEmpty() ? null
                    : error.context().includeChain().getLast().id();
            var include = processor.includes.stream().filter(i -> occurrence != null ? i.occurrence() == occurrence
                    : i.source().path().toString().equals(range.file())).findFirst().orElse(processor.includes.getFirst());
            var end = new PreprocessingToken(EOF, "", "", new SourceRange(range.file(), range.start(), range.start()), 0, 0);
            processor.output.add(new Work(new ExpandedToken(end, include, List.of(), end.range())));
            for (var arm : processor.branches) if (arm.end < 0) {
                arm.end = arm.source.length(); arm.endOutput = processor.output.size() - 1;
            }
            Result result = processor.finish();
            var context = error.context();
            if (context != null) {
                var expanded = context.expansionChain().stream().map(e -> result.expansions().stream()
                        .filter(event -> event.id() == e.id()).findFirst().map(processor::diagnosticExpansion).orElse(e)).toList();
                error = error.withContext(new DiagnosticContext(context.translationUnit(), context.variantId(), context.includeChain(), expanded));
            }
            return new Recovery(result, error);
        }
    }

    // Deliberately only the library header currently modeled by the parser.
    private static final Set<String> OPAQUE_HEADERS = com.kniazkov.cpg.analysis.StandardLibraryCatalogue.headers();
    private record Macro(long id, String name, List<String> parameters, List<Work> replacement, SourceRange definition, SourceRange fullDefinition) {}
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
    private record Conditional(boolean parentActive, boolean taken, boolean seenElse, Arm arm) {
        boolean active() { return arm.active; }
    }
    private static final class Arm {
        long id; String directive; SourceRange directiveRange; Build.IncludeInstance include;
        Long parent; boolean parentActive, evaluated, condition, active;
        String source; int start, end = -1, startOutput, endOutput;
        Branch freeze(int[] bounds) {
            SourceRange range = physicalRange(directiveRange.file(), source, start, end);
            return new Branch(id, directive, source.substring(start, end), range, directiveRange,
                    include, parent, parentActive, evaluated, condition, active, bounds[startOutput], bounds[endOutput]);
        }
    }
    private static SourceRange physicalRange(String file, String source, int start, int end) {
        int line = 1, column = 1; SourcePosition first = null, last = null;
        for (int i = 0; i <= end; i++) {
            if (i == start) first = new SourcePosition(line, column, i);
            if (i == end) { last = new SourcePosition(line, column, i); break; }
            char ch = source.charAt(i);
            if (ch == '\r' || ch == '\n' && (i == 0 || source.charAt(i - 1) != '\r')) { line++; column = 1; }
            else if (ch != '\n') column++;
        }
        return new SourceRange(file, first, last);
    }
    private static final class Processor {
        private final Build.TranslationUnit unit;
        private final Map<String, String> captured = new LinkedHashMap<>();
        private final Map<String, String> virtualSources;
        private final Map<String, String> definitionSources = new HashMap<>();
        private final Map<String, Macro> macros = new LinkedHashMap<>();
        private final List<Build.IncludeInstance> includes = new ArrayList<>();
        private final List<Work> output = new ArrayList<>();
        private final List<Definition> definitions = new ArrayList<>();
        private final List<Arm> branches = new ArrayList<>();
        private final List<PresenceCheck> presenceChecks = new ArrayList<>();
        private final List<Directive> directives = new ArrayList<>();
        private final List<LogicalLocation> logicalLocations = new ArrayList<>();
        private final List<IncludeState> includeStates = new ArrayList<>();
        private final Instant translationTime;
        private boolean recovering, declarationOnly;
        private static final Set<String> BUILTINS = Set.of("__LINE__", "__FILE__", "__DATE__", "__TIME__", "__cplusplus");
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
                        call, include, parent, arguments, operations, start, end, macro.id);
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
            int[] bounds = new int[output.size() + 1]; int outputIndex = 0;
            for (Work token : output) {
                bounds[outputIndex++] = tokens.size();
                for (long id : token.value.expansionIds()) {
                    Event event = events.get((int) id - 1);
                    if (token.anchor) { if (event.anchor < 0) event.anchor = tokens.size(); }
                    else { if (event.start < 0) event.start = tokens.size(); event.end = tokens.size() + 1; }
                }
                if (!token.anchor) tokens.add(token.value);
            }
            bounds[output.size()] = tokens.size();
            // Consumed anchors (e.g. an empty argument of a later invocation) use
            // their enclosing expansion's boundary, never a fabricated source offset.
            for (Event event : events) if (event.start < 0) {
                Event parent = event.parent == null ? null : events.get(event.parent.intValue() - 1);
                event.start = event.anchor >= 0 ? event.anchor : parent != null ? parent.start : 0;
                event.end = event.start;
            }
            return new Result(tokens, includes, events.stream().map(Event::freeze).toList(), captured, definitions,
                    branches.stream().map(arm -> arm.freeze(bounds)).toList(), presenceChecks, directives, logicalLocations, includeStates, translationTime);
        }
        Processor(Build.TranslationUnit unit, Map<String, String> sources) { this(unit, sources, Instant.now()); }
        Processor(Build.TranslationUnit unit, Map<String, String> sources, Instant time) {
            this.unit = unit; this.virtualSources = sources; this.translationTime = Objects.requireNonNull(time);
        }

        Result run(String source) {
            try { return runSource(source); }
            catch (ParseException error) {
                if (error.context() != null) throw error;
                var include = includes.stream().filter(i -> error.range().map(r -> r.file().equals(i.source().path().toString())).orElse(false))
                        .reduce((first, last) -> last).orElse(includes.getFirst());
                throw withChain(error, include);
            }
        }
        Result runSource(String source) {
            var root = instance(unit.source().path(), null);
            if (recovering) captured.put(unit.source().path().toString(), source);
            predefined(root);
            int operationOrdinal = 0;
            for (var operation : unit.macros()) {
                // Build options have their own synthetic text buffers, not offsets
                // into the physical translation unit. Ordinals include -U options.
                String optionSource = unit.directory().resolve("<command-line:" + ++operationOrdinal + ">").toString();
                String operand = operation.operand();
                if (operation.define()) {
                    int equals = operand.indexOf('=');
                    String definition = equals < 0 ? operand + " 1" : operand.substring(0, equals) + " " + operand.substring(equals + 1);
                    definitionSources.put(optionSource, definition);
                    define(wrap(CppLexer.scan(optionSource, definition).tokens(), root), "command-line", Integer.toString(operationOrdinal));
                } else {
                    var name = significant(wrap(CppLexer.scan(optionSource, operand).tokens(), root));
                    if (name.size() != 1 || !identifier(name.get(0))) throw error(root, "Invalid -U operand: " + operand);
                    rejectReserved(name.get(0));
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
            String file = include.source().path().toString();
            String previous = captured.putIfAbsent(file, source);
            if (previous != null && !previous.equals(source)) throw error(include, "Physical source changed while preprocessing");
            if (depth > 128) throw error(include, "Include nesting limit exceeded (possible unguarded cycle)");
            List<Work> input; SourcePosition eof; ParseException lexicalProblem = null;
            try {
                var scan = recovering ? CppLexer.scanRecovering(file, source) : new CppLexer.Recovery(CppLexer.scan(file, source), null);
                input = wrap(scan.result().tokens(), include); eof = scan.result().eof(); lexicalProblem = scan.problem();
            }
            catch (ParseException e) { throw withChain(e, include); }
            var conditions = new ArrayDeque<Conditional>();
            var pending = new ArrayList<Work>();
            String logicalFile = file; int lineDelta = 0;
            for (int i = 0; i < input.size();) {
                int end = i;
                while (end < input.size() && input.get(end).value.token().kind() != NEWLINE) end++;
                var line = input.subList(i, end);
                var sig = significant(line);
                int lineStart = input.get(i).value.token().range().start().offset();
                int lineEnd = end < input.size() ? input.get(end).value.token().range().end().offset() : source.length();
                var lineRange = new SourceRange(file, input.get(i).value.token().range().start(),
                        end < input.size() ? input.get(end).value.token().range().end() : eof);
                logicalLocations.add(new LogicalLocation(lineRange, include, logicalFile, lineRange.start().line() + lineDelta));
                boolean active = conditions.isEmpty() || conditions.peek().active();
                if (!sig.isEmpty() && sig.get(0).text().equals("#")) {
                    output.addAll(expandOutput(pending)); pending.clear();
                    String directive = sig.size() == 1 ? "" : sig.get(1).text();
                    directives.add(new Directive(directive, source.substring(lineStart, lineEnd), lineRange, include, active));
                    if (sig.size() > 1) {
                        var args = sig.subList(2, sig.size()); Work location = sig.get(1);
                        var rawArgs = line.subList(line.indexOf(location) + 1, line.size());
                        switch (directive) {
                            case "ifdef", "ifndef", "if" -> {
                                Arm arm = arm(directive, lineRange, include, conditions.isEmpty() ? null : conditions.peek().arm.id,
                                        active, source, lineStart);
                                if (active) {
                                    arm.evaluated = true;
                                    if (directive.equals("if")) arm.condition = condition(rawArgs, arm, location);
                                    else {
                                        if (args.size() != 1 || !identifier(args.get(0))) throw error(location, "Expected one macro name after #" + directive);
                                        arm.condition = presence(args.get(0), arm.id, directive.equals("ifndef"));
                                    }
                                }
                                arm.active = active && arm.condition;
                                conditions.push(new Conditional(active, arm.active, false, arm));
                            }
                            case "elif", "else" -> {
                                if (conditions.isEmpty() || conditions.peek().seenElse) throw error(location, "Unmatched or malformed #" + directive);
                                var old = conditions.pop(); close(old.arm, lineStart);
                                if (directive.equals("else") && old.parentActive && !args.isEmpty()) throw error(location, "Unmatched or malformed #else");
                                Arm arm = arm(directive, lineRange, include, old.arm.parent, old.parentActive, source, lineStart);
                                arm.evaluated = old.parentActive && !old.taken;
                                if (arm.evaluated) arm.condition = directive.equals("else") || condition(rawArgs, arm, location);
                                arm.active = arm.evaluated && arm.condition;
                                conditions.push(new Conditional(old.parentActive, old.taken || arm.active, directive.equals("else"), arm));
                            }
                            case "endif" -> {
                                if (conditions.isEmpty() || conditions.peek().parentActive && !args.isEmpty()) throw error(location, "Unmatched or malformed #endif");
                                close(conditions.pop().arm, lineStart);
                            }
                            default -> {
                                if (active) switch (directive) {
                                    case "define" -> define(rawArgs, "source", file);
                                    case "undef" -> {
                                        if (args.size() != 1 || !identifier(args.get(0))) throw error(location, "Expected one macro name after #undef");
                                        rejectReserved(args.get(0)); macros.remove(args.get(0).text());
                                    }
                                    case "include" -> include(include, rawArgs, location, depth);
                                    case "error" -> throw error(location, "#error " + spelling(rawArgs));
                                    case "pragma" -> { /* No implementation-specific pragmas are recognized. */ }
                                    case "line" -> {
                                        int firstEvent = events.size();
                                        var expanded = significant(expandDirective(rawArgs));
                                        if (expanded.isEmpty() || expanded.size() > 2 || !expanded.getFirst().text().matches("[0-9]+"))
                                            throw expandedError(location, "Invalid #line directive", expanded, firstEvent);
                                        int number;
                                        try { number = Integer.parseInt(expanded.getFirst().text()); }
                                        catch (NumberFormatException e) { throw expandedError(location, "#line number outside C++03 range 1..32767", expanded, firstEvent); }
                                        if (number < 1 || number > 32767) throw expandedError(location, "#line number outside C++03 range 1..32767", expanded, firstEvent);
                                        if (expanded.size() == 2) {
                                            String quoted = expanded.get(1).text();
                                            if (expanded.get(1).value.token().kind() != STRING_LITERAL || !quoted.startsWith("\""))
                                                throw expandedError(location, "Expected character string literal after #line number", expanded, firstEvent);
                                            try { logicalFile = lineFilename(quoted, location); }
                                            catch (ParseException error) { throw withExpandedCause(error, location, expanded, firstEvent); }
                                        }
                                        lineDelta = number - lineRange.end().line();
                                    }
                                    default -> throw error(location, "Unsupported preprocessing directive #" + directive);
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
            output.addAll(expandOutput(pending));
            if (lexicalProblem != null) throw withChain(lexicalProblem, include);
            if (!conditions.isEmpty()) throw withChain(new ParseException(file, eof.line(), eof.column(),
                    "Unterminated conditional directive", new SourceRange(file, eof, eof)), include);
            var active = new LinkedHashMap<String, Long>(); macros.forEach((name, macro) -> active.put(name, macro.id));
            includeStates.add(new IncludeState(include, active));
        }
        Arm arm(String directive, SourceRange range, Build.IncludeInstance include, Long parent,
                boolean parentActive, String source, int start) {
            Arm arm = new Arm(); arm.id = branches.size() + 1L; arm.directive = directive;
            arm.directiveRange = range; arm.include = include; arm.parent = parent;
            arm.parentActive = parentActive; arm.source = source; arm.start = start; arm.startOutput = output.size();
            branches.add(arm); return arm;
        }
        void close(Arm arm, int end) { arm.end = end; arm.endOutput = output.size(); }
        boolean presence(Work name, long branch, boolean negate) {
            Macro macro = lookup(name);
            boolean present = macro != null, result = negate ? !present : present;
            presenceChecks.add(new PresenceCheck(presenceChecks.size() + 1L, name.text(), name.value.token().range(),
                    name.value.include(), present, result, macro == null ? null : macro.id, branch));
            return result;
        }
        boolean condition(List<Work> raw, Arm arm, Work location) {
            var operand = new ArrayList<Work>();
            for (int i = 0; i < raw.size(); i++) {
                Work token = raw.get(i);
                if (!token.text().equals("defined")) { operand.add(token); continue; }
                int nameIndex = next(raw, i + 1);
                boolean paren = nameIndex < raw.size() && raw.get(nameIndex).text().equals("(");
                if (paren) nameIndex = next(raw, nameIndex + 1);
                if (nameIndex >= raw.size() || !identifier(raw.get(nameIndex))) throw error(token, "Expected macro name in defined operator");
                Work name = raw.get(nameIndex); i = nameIndex;
                if (paren) {
                    i = next(raw, i + 1);
                    if (i >= raw.size() || !raw.get(i).text().equals(")")) throw error(token, "Expected ')' after defined operand");
                }
                operand.add(synthetic(token, PP_NUMBER, presence(name, arm.id, false) ? "1" : "0"));
            }
            int firstEvent = events.size();
            var expanded = significant(expandDirective(operand));
            if (expanded.isEmpty()) throw expandedError(location, "Empty #if expression", expanded, firstEvent);
            if (expanded.stream().anyMatch(t -> t.text().equals("defined")))
                throw expandedError(location, "Macro-generated defined has undefined behavior in C++03", expanded, firstEvent);
            try { return CppConditionalExpression.evaluate(expanded.stream().map(t -> t.value.token()).toList(), unit.platform()); }
            catch (ParseException e) {
                Work fault = expanded.stream().filter(t -> {
                    SourceRange range = t.value.token().range();
                    return range.file().equals(e.file()) && range.start().line() == e.line() && range.start().column() == e.column();
                }).findFirst().orElse(location);
                var error = expandedError(fault, e.reason(), expanded, firstEvent);
                throw error.withDiagnostic("preprocessor-conditional", error.causeReason(),
                        error.range().orElse(null), error.relatedRanges(), error.context());
            }
        }
        List<Work> expandDirective(List<Work> input) {
            int first = events.size(); var result = expand(input);
            int boundary = (int) output.stream().filter(t -> !t.anchor).count();
            for (int i = first; i < events.size(); i++) events.get(i).anchor = boundary;
            return result;
        }
        void predefined(Build.IncludeInstance root) {
            for (String name : List.of("__cplusplus", "__FILE__", "__LINE__", "__DATE__", "__TIME__")) {
                String file = unit.directory().resolve("<predefined:" + name + ">").toString();
                var tokens = wrap(CppLexer.scan(file, name).tokens(), root);
                SourceRange range = tokens.getFirst().value.token().range();
                long id = definitions.size() + 1L;
                var macro = new Macro(id, name, null, List.of(), range, range);
                definitions.add(new Definition(id, name, false, List.of(), name.equals("__cplusplus") ? "199711L" : "",
                        range, root, "predefined", name));
                macros.put(name, macro);
            }
            for (var entry : unit.platform().predefinedMacros().entrySet()) {
                String name = entry.getKey(), value = entry.getValue();
                String file = unit.directory().resolve("<predefined:" + name + ">").toString();
                String source = name + " " + value;
                definitionSources.put(file, source);
                var tokens = wrap(CppLexer.scan(file, source).tokens(), root);
                SourceRange range = tokens.getFirst().value.token().range();
                long id = definitions.size() + 1L;
                macros.put(name, new Macro(id, name, null, trim(tokens.subList(1, tokens.size())), range, range));
                definitions.add(new Definition(id, name, false, List.of(), value, range, root, "predefined", name));
            }
        }
        Macro lookup(Work invocation) {
            Macro macro = macros.get(invocation.text());
            if (macro == null || !BUILTINS.contains(macro.name)) return macro;
            Build.IncludeInstance include = invocation.value.include();
            if (!invocation.value.expansionIds().isEmpty())
                include = events.get(invocation.value.expansionIds().getLast().intValue() - 1).include;
            SourceRange call = invocation.value.expansionRange();
            String file = call.file(); int line = call.start().line();
            for (int i = logicalLocations.size() - 1; i >= 0; i--) {
                LogicalLocation location = logicalLocations.get(i);
                if (location.include().equals(include) && location.range().file().equals(call.file())
                        && call.start().offset() >= location.range().start().offset()
                        && call.start().offset() < location.range().end().offset()) {
                    file = location.file(); line = location.line() + call.start().line() - location.range().start().line(); break;
                }
            }
            var time = translationTime.atZone(ZoneOffset.UTC);
            String value = switch (macro.name) {
                case "__cplusplus" -> "199711L";
                case "__LINE__" -> Integer.toString(line);
                case "__FILE__" -> quote(file);
                case "__DATE__" -> quote(time.format(DateTimeFormatter.ofPattern("MMM", Locale.US)) + " "
                        + String.format(Locale.ROOT, "%2d", time.getDayOfMonth()) + " " + time.getYear());
                case "__TIME__" -> quote(time.format(DateTimeFormatter.ofPattern("HH:mm:ss", Locale.ROOT)));
                default -> throw new AssertionError(macro.name);
            };
            var kind = value.startsWith("\"") ? STRING_LITERAL : PP_NUMBER;
            var token = new PreprocessingToken(kind, value, value, macro.definition, 0, value.length());
            var replacement = new Work(new ExpandedToken(token, include, List.of(), call));
            return new Macro(macro.id, macro.name, null, List.of(replacement), macro.definition, macro.fullDefinition);
        }
        String lineFilename(String quoted, Work location) {
            StringBuilder result = new StringBuilder();
            for (int i = 1; i < quoted.length() - 1; i++) {
                char ch = quoted.charAt(i);
                if (ch != '\\') { result.append(ch); continue; }
                char escape = quoted.charAt(++i);
                String keys = "'\"?\\abfnrtv", values = "'\"?\\\u0007\b\f\n\r\t\u000b";
                int key = keys.indexOf(escape);
                if (key >= 0) { result.append(values.charAt(key)); continue; }
                if (escape >= '0' && escape <= '7') {
                    int value = escape - '0', count = 1;
                    while (count < 3 && i + 1 < quoted.length() - 1 && quoted.charAt(i + 1) >= '0' && quoted.charAt(i + 1) <= '7') {
                        value = value * 8 + quoted.charAt(++i) - '0'; count++;
                    }
                    result.append((char) value); continue;
                }
                if (escape == 'x' || escape == 'u' || escape == 'U') {
                    int start = i + 1, limit = escape == 'u' ? 4 : escape == 'U' ? 8 : Integer.MAX_VALUE;
                    while (i + 1 < quoted.length() - 1 && i + 1 - start < limit && Character.digit(quoted.charAt(i + 1), 16) >= 0) i++;
                    try {
                        if (i < start || escape != 'x' && i + 1 - start != limit) throw new NumberFormatException();
                        int value = Integer.parseInt(quoted.substring(start, i + 1), 16);
                        if (!Character.isValidCodePoint(value)) throw new NumberFormatException();
                        result.appendCodePoint(value); continue;
                    } catch (NumberFormatException e) { throw error(location, "Invalid filename escape in #line directive"); }
                }
                throw error(location, "Invalid filename escape in #line directive");
            }
            return result.toString();
        }
        static String quote(String text) {
            var result = new StringBuilder("\"");
            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '\\' || ch == '"') result.append('\\').append(ch);
                else if (ch < 32 || ch == 127) result.append(String.format(Locale.ROOT, "\\%03o", (int) ch));
                else result.append(ch);
            }
            return result.append('\"').toString();
        }
        String rawReplacement(List<Work> replacement) {
            if (replacement.isEmpty()) return "";
            SourceRange first = replacement.getFirst().value.token().range(), last = replacement.getLast().value.token().range();
            String source = captured.getOrDefault(first.file(), definitionSources.get(first.file()));
            return source != null && first.file().equals(last.file())
                    ? source.substring(first.start().offset(), last.end().offset()) : spelling(replacement);
        }
        void rejectReserved(Work name) {
            if (BUILTINS.contains(name.text()) || unit.platform().predefinedMacros().containsKey(name.text())
                    || Set.of("__CHAR_UNSIGNED__", "__WCHAR_UNSIGNED__").contains(name.text()) || name.text().equals("__STDC__") || name.text().equals("defined"))
                throw error(name, "Redefining or undefining reserved preprocessing name has undefined behavior in C++03: " + name.text());
        }
        void define(List<Work> line, String originKind, String sourceKey) {
            var raw = trim(line);
            if (raw.isEmpty() || !identifier(raw.get(0))) throw raw.isEmpty()
                    ? error(unit.source().path().toString(), "Missing macro name") : error(raw.get(0), "Expected macro name");
            Work name = raw.get(0);
            rejectReserved(name);
            int index = 1;
            List<String> parameters = null;
            if (index < raw.size() && raw.get(index).text().equals("(")) {
                parameters = new ArrayList<>(); index++;
                index = next(raw, index);
                if (index >= raw.size()) throw error(name, "Unterminated macro parameter list");
                if (!raw.get(index).text().equals(")")) {
                    while (true) {
                        if (index >= raw.size() || !identifier(raw.get(index))) throw error(index < raw.size() ? raw.get(index) : name,
                                "Expected macro parameter (variadic macros are not C++03)");
                        String parameter = raw.get(index++).text();
                        if (parameters.contains(parameter)) throw error(name, "Duplicate macro parameter: " + parameter);
                        parameters.add(parameter); index = next(raw, index);
                        if (index >= raw.size()) throw error(name, "Unterminated macro parameter list");
                        if (!raw.get(index).text().equals(",")) break;
                        index = next(raw, index + 1);
                    }
                }
                if (raw.get(index).text().equals("...")) throw error(raw.get(index), "Variadic macros are not C++03");
                if (!raw.get(index).text().equals(")")) throw error(name, "Expected ')' in macro parameters");
                index++;
            }
            var replacement = trim(raw.subList(index, raw.size()));
            var sig = significant(replacement);
            if (!sig.isEmpty() && (sig.getFirst().text().equals("##") || sig.getLast().text().equals("##"))) throw error(name, "## cannot begin or end a macro replacement");
            if (parameters != null) for (int i = 0; i < sig.size(); i++) {
                if (sig.get(i).text().equals("#") && (i + 1 == sig.size() || !parameters.contains(sig.get(i + 1).text()))) throw error(sig.get(i), "# must precede a macro parameter");
            }
            var macro = new Macro(definitions.size() + 1L, name.text(), parameters == null ? null : List.copyOf(parameters), List.copyOf(replacement), name.value.token().range(), new SourceRange(name.value.token().range().file(),
                    name.value.token().range().start(), raw.getLast().value.token().range().end()));
            Macro old = macros.get(name.text());
            if (old != null && (!Objects.equals(old.parameters, macro.parameters) || !spelling(old.replacement).equals(spelling(macro.replacement)))) {
                var error = error(name, "Incompatible redefinition of macro " + name.text());
                String cause = "Incompatible redefinition of macro " + name.text() + " [previous parameters: " + old.parameters
                        + "; previous replacement: " + spelling(old.replacement) + "; new parameters: " + macro.parameters
                        + "; new replacement: " + spelling(macro.replacement) + "]";
                throw error.withDiagnostic("macro-redefinition", cause, error.range().orElse(null), List.of(
                        new DiagnosticRelatedRange("previous-definition", "Previous macro definition", old.fullDefinition),
                        new DiagnosticRelatedRange("new-definition", "New macro definition", macro.fullDefinition)), error.context());
            }
            definitions.add(new Definition(macro.id, macro.name, parameters != null,
                    parameters == null ? List.of() : parameters, rawReplacement(replacement), macro.fullDefinition,
                    name.value.include(), originKind, sourceKey));
            macros.put(name.text(), macro);
        }
        void include(Build.IncludeInstance parent, List<Work> raw, Work location, int depth) {
            int directiveIndex = directives.size() - 1;
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
                throw expandedError(location, "Expected quoted or angle header name after #include", expanded, firstEvent);
            String header = spec.substring(1, spec.length() - 1);
            if (virtualSources != null && Path.of(header).isAbsolute())
                throw expandedError(location, "Cannot preserve C++ source safely: absolute include is not relocatable: " + header, expanded, firstEvent);
            var candidates = new ArrayList<Path>();
            if (quote) candidates.add(parent.source().path().getParent().resolve(header));
            if (quote) for (var path : unit.includes()) if (path.kind().equals("-iquote")) candidates.add(path.path().resolve(header));
            for (String kind : List.of("-I", "-isystem")) for (var path : unit.includes()) if (path.kind().equals(kind)) candidates.add(path.path().resolve(header));
            for (Path rawCandidate : candidates) {
                Path candidate = rawCandidate.toAbsolutePath().normalize();
                if (!(virtualSources == null ? Files.isRegularFile(candidate) : virtualSources.containsKey(candidate.toString()))) continue;
                var included = instance(candidate, parent);
                try { process(included, virtualSources == null ? Files.readString(candidate, java.nio.charset.Charset.forName(unit.platform().sourceEncoding())) : virtualSources.get(candidate.toString()), depth + 1); }
                catch (IOException e) { throw expandedError(location, "Cannot read header " + candidate + ": " + e.getMessage(), expanded, firstEvent); }
                recordIncludedHeader(directiveIndex, header, anchorIndex);
                return;
            }
            if (angle && OPAQUE_HEADERS.contains(header)) {
                libraryMacros(parent, header, location);
                recordIncludedHeader(directiveIndex, header, anchorIndex);
                PreprocessingToken token = new PreprocessingToken(HEADER_NAME, spec, spec, location.value.token().range(), 0, spec.length());
                output.add(new Work(new ExpandedToken(token, parent, location.value.origins(), location.value.expansionRange())));
                return;
            }
            throw expandedError(location, "Header not found: " + header, expanded, firstEvent);
        }

        void libraryMacros(Build.IncludeInstance parent, String header, Work location) {
            if (header.equals("cassert") || header.equals("assert.h")) {
                boolean disabled = macros.containsKey("NDEBUG");
                String mode = disabled ? "disabled" : "enabled";
                String unavailable = !disabled && !unit.platform().cRuntime().equals("glibc")
                        ? "Enabled assert requires an explicitly selected supported C runtime (cRuntime=glibc)" : null;
                String replacement = disabled ? "((void)0)" : unavailable != null ? ""
                        : "(static_cast<bool>(expression) ? (void)0 : ::__assert_fail(#expression, __FILE__, __LINE__, 0))";
                String file = unit.directory().resolve("<cpg-library:" + header + ":assert:" + mode + ">").toString();
                String source = "assert(expression) " + replacement;
                definitionSources.put(file, source);
                // Unlike ordinary redefinition, every inclusion must reselect assert.
                macros.remove("assert");
                define(wrap(CppLexer.scan(file, source).tokens(), parent), "library",
                        "profile:platform:" + unit.platform().identityHash() + ":library:" + header + ":assert:" + mode);
                if (unavailable != null) {
                    Definition d = definitions.getLast();
                    definitions.set(definitions.size() - 1, new Definition(d.id(), d.name(), d.functionLike(), d.parameters(),
                            d.rawReplacement(), d.range(), d.include(), d.originKind(), d.sourceKey(), unavailable));
                }
            }
            String canonical = com.kniazkov.cpg.analysis.StandardLibraryCatalogue.canonicalHeader(header);
            if (canonical.equals("ctime")) {
                var model = unit.platform().clockScale();
                String file = unit.directory().resolve("<cpg-library:" + header + ":CLOCKS_PER_SEC>").toString();
                String source = "CLOCKS_PER_SEC " + model.map(m -> m.replacement()).orElse("");
                definitionSources.put(file, source);
                define(wrap(CppLexer.scan(file, source).tokens(), parent), "library",
                        "profile:platform:" + unit.platform().identityHash() + ":library:" + header + ":CLOCKS_PER_SEC");
                if (model.isEmpty()) {
                    Definition d = definitions.getLast();
                    definitions.set(definitions.size() - 1, new Definition(d.id(), d.name(), false, List.of(),
                            d.rawReplacement(), d.range(), d.include(), d.originKind(), d.sourceKey(),
                            "Library macro CLOCKS_PER_SEC requires an explicit clockScale target model"));
                }
            }
            if (canonical.equals("cwchar") || canonical.equals("cwctype")) {
                var model = unit.platform().wideInteger();
                String file = unit.directory().resolve("<cpg-library:" + header + ":WEOF>").toString();
                String source = "WEOF " + model.map(m -> m.replacement(unit.platform())).orElse("");
                definitionSources.put(file, source);
                define(wrap(CppLexer.scan(file, source).tokens(), parent), "library",
                        "profile:platform:" + unit.platform().identityHash() + ":library:" + header + ":WEOF");
                if (model.isEmpty()) {
                    Definition d = definitions.getLast();
                    definitions.set(definitions.size() - 1, new Definition(d.id(), d.name(), false, List.of(),
                            d.rawReplacement(), d.range(), d.include(), d.originKind(), d.sourceKey(),
                            "Library macro WEOF requires an explicit wideInteger target model"));
                }
            }
            if (canonical.equals("cerrno")) {
                boolean available = unit.platform().cRuntime().equals("glibc");
                String file = unit.directory().resolve("<cpg-library:" + header + ":errno>").toString();
                String source = "errno " + (available ? "(*::__errno_location())" : "");
                definitionSources.put(file, source);
                define(wrap(CppLexer.scan(file, source).tokens(), parent), "library",
                        "profile:platform:" + unit.platform().identityHash() + ":library:" + header + ":errno");
                if (!available) {
                    Definition d = definitions.getLast();
                    definitions.set(definitions.size() - 1, new Definition(d.id(), d.name(), false, List.of(),
                            d.rawReplacement(), d.range(), d.include(), d.originKind(), d.sourceKey(),
                            "Library macro errno requires an explicitly selected supported C runtime (cRuntime=glibc)"));
                }
            }
            if (canonical.equals("cstdio")) for (String name : List.of("stdin", "stdout", "stderr")) {
                boolean available = !unit.platform().standardStreamsBinding().equals("unavailable");
                String file = unit.directory().resolve("<cpg-library:" + header + ":" + name + ">").toString();
                // Real glibc self-reference preserves qualified and ordinary lookup.
                String source = name + " " + (available ? name : "");
                definitionSources.put(file, source);
                define(wrap(CppLexer.scan(file, source).tokens(), parent), "library",
                        "profile:platform:" + unit.platform().identityHash() + ":library:" + header + ":" + name);
                if (!available) {
                    Definition d = definitions.getLast();
                    definitions.set(definitions.size() - 1, new Definition(d.id(), d.name(), false, List.of(),
                            d.rawReplacement(), d.range(), d.include(), d.originKind(), d.sourceKey(),
                            "Library macro " + name + " requires explicit standardStreamsBinding=glibc-x86_64-lp64"));
                }
            }
            if (canonical.equals("cstdlib")) {
                boolean available = !unit.platform().mbCurMaxBinding().equals("unavailable");
                String file = unit.directory().resolve("<cpg-library:" + header + ":MB_CUR_MAX>").toString();
                String source = "MB_CUR_MAX " + (available ? "(::__ctype_get_mb_cur_max())" : "");
                definitionSources.put(file, source);
                define(wrap(CppLexer.scan(file, source).tokens(), parent), "library",
                        "profile:platform:" + unit.platform().identityHash() + ":library:" + header + ":MB_CUR_MAX");
                if (!available) {
                    Definition d = definitions.getLast();
                    definitions.set(definitions.size() - 1, new Definition(d.id(), d.name(), false, List.of(),
                            d.rawReplacement(), d.range(), d.include(), d.originKind(), d.sourceKey(),
                            "Library macro MB_CUR_MAX requires explicit mbCurMaxBinding=glibc-x86_64-lp64-mb16"));
                }
            }
            for (String name : com.kniazkov.cpg.build.RuntimeIntegerMacros.namesForHeader(canonical)) {
                var fact = unit.platform().runtimeIntegerMacros().get(name);
                String replacement = fact == null ? "" : fact.replacement(unit.platform());
                String file = unit.directory().resolve("<cpg-library:" + header + ":" + name + ">").toString();
                String source = name + " " + replacement;
                definitionSources.put(file, source);
                define(wrap(CppLexer.scan(file, source).tokens(), parent), "library",
                        "profile:platform:" + unit.platform().identityHash() + ":library:" + header + ":" + name);
                if (fact == null) {
                    Definition d = definitions.getLast();
                    definitions.set(definitions.size() - 1, new Definition(d.id(), d.name(), false, List.of(),
                            d.rawReplacement(), d.range(), d.include(), d.originKind(), d.sourceKey(),
                            "Library macro " + name + " requires an explicit runtimeIntegerMacros." + name + " target fact"));
                }
            }
            Map<String, String> library;
            try { library = StandardLibraryMacros.definitions(header, unit.platform()); }
            catch (IllegalArgumentException error) { throw error(location, error.getMessage()); }
            for (var entry : library.entrySet()) {
                String name = entry.getKey();
                String file = unit.directory().resolve("<cpg-library:" + header + ":" + name + ">").toString();
                String source = name + " " + entry.getValue();
                definitionSources.put(file, source);
                define(wrap(CppLexer.scan(file, source).tokens(), parent), "library",
                        "profile:platform:" + unit.platform().identityHash() + ":library:" + header + ":" + name);
            }
        }

        void recordIncludedHeader(int index, String header, int startToken) {
            Directive directive = directives.get(index);
            directives.set(index, new Directive(directive.kind(), directive.rawSource(), directive.range(),
                    directive.include(), directive.active(), header, startToken));
        }

        List<Work> expandOutput(List<Work> input) { return expand(input, null, recovering, declarationOnly); }
        List<Work> expand(List<Work> input) { return expand(input, null); }
        List<Work> expand(List<Work> input, Long context) { return expand(input, context, false); }
        List<Work> expand(List<Work> input, Long context, boolean preservePrefix) {
            return expand(input, context, preservePrefix, false);
        }
        List<Work> expand(List<Work> input, Long context, boolean preservePrefix, boolean catalogueOutput) {
            var work = new ArrayList<>(input);
            int index = 0, firstEvent = events.size();
            try {
                while (index < work.size()) {
                    Work invocation = work.get(index);
                    Macro macro = lookup(invocation);
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
                    String unavailable = definitions.get((int) macro.id - 1).unavailableReason();
                    if (unavailable != null && catalogueOutput) { index++; continue; }
                    if (unavailable != null) throw error(invocation, unavailable).withDiagnostic("unavailable-library-macro",
                            unavailable, invocation.value.expansionRange(), List.of(new DiagnosticRelatedRange(
                                    "macro-definition", "Unavailable library macro binding", macro.fullDefinition)),
                            context(invocation.value.include(), invocation.value.expansionIds()));
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
            } catch (ParseException error) {
                // Only ordinary output may commit a prefix. Argument/directive prescans cannot.
                if (preservePrefix) {
                    output.addAll(work.subList(0, index));
                    int boundary = (int) output.stream().filter(t -> !t.anchor).count();
                    // Failed expansions have no emitted replacement. Their zero-length
                    // boundary is the end of the committed prefix, not token zero.
                    for (int i = firstEvent; i < events.size(); i++) {
                        Event event = events.get(i);
                        if (event.anchor < 0 && output.stream().noneMatch(t -> t.value.expansionIds().contains(event.id)))
                            event.anchor = boundary;
                    }
                }
                throw error;
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
                    var fragments = new ArrayList<>(a.fragments); fragments.addAll(b.fragments);
                    var operation = new Operation("paste", replacement.get(i).value.token().range(), fragments, joined);
                    // Failed operations are evidence too: retain them before attempting synthetic lexing.
                    event.operations.add(operation);
                    List<PreprocessingToken> scanned;
                    try { scanned = CppLexer.scan(invocation.value.token().range().file(), joined).tokens(); }
                    catch (ParseException e) { throw invalidPaste(invocation, event, operation, a, b); }
                    if (scanned.size() != 2 || scanned.get(0).kind() == WHITESPACE || scanned.get(0).kind() == NEWLINE || scanned.get(0).kind() == OTHER || !scanned.get(0).spelling().equals(joined))
                        throw invalidPaste(invocation, event, operation, a, b);
                    PreprocessingToken lexical = scanned.getFirst();
                    var token = new PreprocessingToken(lexical.kind(), joined, lexical.canonicalSpelling(), a.value.token().range(), 0, joined.length());
                    var origins = new ArrayList<>(a.value.origins()); origins.addAll(b.value.origins());
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
        ParseException expandedError(Work location, String reason, List<Work> operands, int firstEvent) {
            return withExpandedCause(error(location, reason), location, operands, firstEvent);
        }
        ParseException withExpandedCause(ParseException error, Work location, List<Work> operands, int firstEvent) {
            var ids = new LinkedHashSet<Long>(location.value.expansionIds());
            // Empty replacements emit no operand tokens. Their executed events still
            // caused this directive's result and must survive trimming/significance.
            for (int i = firstEvent; i < events.size(); i++) ids.add(events.get(i).id);
            for (Work operand : operands) ids.addAll(operand.value.expansionIds());
            var expansionContext = context(location.value.include(), List.copyOf(ids));
            var base = error.context();
            var occurrence = new DiagnosticContext(unit.source().path().toString(), unit.variantId(),
                    base == null ? expansionContext.includeChain() : base.includeChain(), expansionContext.expansionChain());
            var related = new ArrayList<DiagnosticRelatedRange>();
            for (var event : occurrence.expansionChain())
                related.add(new DiagnosticRelatedRange("macro-invocation", "Expansion of macro " + event.name(), event.invocation()));
            var origins = new LinkedHashSet<SourceRange>();
            for (Work operand : operands) if (!operand.value.expansionIds().isEmpty()) origins.add(operand.value.token().range());
            for (SourceRange origin : origins)
                related.add(new DiagnosticRelatedRange("macro-definition", "Expanded directive operand originates here", origin));
            return error.withDiagnostic(error.code(), error.causeReason() + " [expanded operands: " + spelling(operands) + "]",
                    error.range().orElse(null), related, occurrence);
        }
        ParseException invalidPaste(Work invocation, Event event, Operation operation, Work left, Work right) {
            var related = new ArrayList<DiagnosticRelatedRange>();
            for (var fragment : left.fragments) related.add(new DiagnosticRelatedRange("macro-argument-left", "Left operand of ##", fragment.range()));
            for (var fragment : right.fragments) related.add(new DiagnosticRelatedRange("macro-argument-right", "Right operand of ##", fragment.range()));
            related.add(new DiagnosticRelatedRange("macro-paste-operator", "Token-pasting operator", operation.operator()));
            var cause = "Invalid token paste: " + operation.spelling() + " [left: " + left.value.token().spelling()
                    + ", right: " + right.value.token().spelling() + "]";
            return error(invocation, "Invalid token paste: " + operation.spelling()).withDiagnostic("invalid-token-paste", cause,
                    event.call, related, context(event.include, List.of(event.id)));
        }
        ParseException error(Work location, String reason) {
            SourceRange range = location.value.expansionRange();
            Build.IncludeInstance include = location.value.include();
            if (!location.value.expansionIds().isEmpty())
                include = events.get(location.value.expansionIds().getLast().intValue() - 1).include;
            String file = range.file(); int line = range.start().line();
            for (int i = logicalLocations.size() - 1; i >= 0; i--) {
                LogicalLocation logical = logicalLocations.get(i);
                if (logical.include().equals(include) && logical.range().file().equals(range.file())
                        && range.start().offset() >= logical.range().start().offset()
                        && range.start().offset() < logical.range().end().offset()) {
                    file = logical.file(); line = logical.line() + range.start().line() - logical.range().start().line(); break;
                }
            }
            var context = context(include, location.value.expansionIds());
            var related = new ArrayList<DiagnosticRelatedRange>();
            for (var expansion : context.expansionChain())
                related.add(new DiagnosticRelatedRange("macro-invocation", "Expansion of macro " + expansion.name(), expansion.invocation()));
            if (!context.expansionChain().isEmpty())
                related.add(new DiagnosticRelatedRange("macro-definition", "Expanded token originates here", location.value.token().range()));
            return withChain(new ParseException(file, line, range.start().column(), reason, range, "preprocessor-error",
                    reason + " [token: " + location.text() + "]", related, context), include);
        }
        ParseException error(Build.IncludeInstance include, String reason) {
            return withChain(error(include.source().path().toString(), reason), include);
        }
        ParseException error(String file, String reason) {
            var start = new SourcePosition(1, 1, 0);
            return new ParseException(file, 1, 1, reason, new SourceRange(file, start, start));
        }
        ParseException withChain(ParseException error, Build.IncludeInstance include) {
            var chain = new ArrayList<String>();
            for (var cursor = include; cursor != null; cursor = cursor.parent()) chain.add(cursor.source().path().toString());
            Collections.reverse(chain);
            return (error.context() == null ? error.withContext(context(include, List.of())) : error)
                    .withReason(error.reason() + " [include chain: " + String.join(" -> ", chain) + "]");
        }
        DiagnosticContext context(Build.IncludeInstance include, List<Long> expansionIds) {
            var chain = new ArrayList<Preprocessing.Include>();
            for (var cursor = include; cursor != null; cursor = cursor.parent())
                chain.add(new Preprocessing.Include(cursor.occurrence(), cursor.source().path().toString(),
                        cursor.parent() == null ? null : cursor.parent().occurrence()));
            Collections.reverse(chain);
            var ancestry = new LinkedHashSet<Long>();
            for (long id : expansionIds) addExpansionAncestry(id, ancestry);
            var expanded = ancestry.stream().map(id -> diagnosticExpansion(events.get(id.intValue() - 1))).toList();
            return new DiagnosticContext(unit.source().path().toString(), unit.variantId(), chain, expanded);
        }
        void addExpansionAncestry(long id, Set<Long> result) {
            if (result.contains(id)) return;
            Event event = events.get((int) id - 1);
            if (event.parent != null) addExpansionAncestry(event.parent, result);
            result.add(id);
        }
        Preprocessing.Fragment diagnosticFragment(Fragment fragment) {
            return new Preprocessing.Fragment(fragment.range(), fragment.include().occurrence(), fragment.spelling());
        }
        Preprocessing.Expansion diagnosticExpansion(Event event) {
            int boundary = (int) output.stream().filter(t -> !t.anchor).count();
            int start = event.start >= 0 ? event.start : event.anchor >= 0 ? event.anchor : boundary;
            int end = event.end >= 0 ? event.end : start;
            return diagnosticExpansion(new ExpansionEvent(event.id, event.macro.name, event.macro.fullDefinition,
                    fragments(event.macro.replacement), event.call, event.include, event.parent,
                    event.arguments, event.operations, start, end, event.macro.id));
        }
        Preprocessing.Expansion diagnosticExpansion(ExpansionEvent event) {
            return new Preprocessing.Expansion(event.id(), event.name(), event.definition(),
                    event.replacement().stream().map(this::diagnosticFragment).toList(),
                    event.invocation(), event.include().occurrence(), event.parentId(),
                    event.arguments().stream().map(arg -> arg.stream().map(this::diagnosticFragment).toList()).toList(),
                    event.operations().stream().map(op -> new Preprocessing.Operation(op.kind(), op.operator(),
                            op.fragments().stream().map(this::diagnosticFragment).toList(), op.spelling())).toList(),
                    event.startToken(), event.endToken(), event.definitionId());
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
