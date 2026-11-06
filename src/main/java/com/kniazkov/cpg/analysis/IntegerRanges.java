/* Opt-in conservative scalar integer range analysis. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.lattice.IntegerDomain;
import com.kniazkov.cpg.lattice.IntegerValue;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.ControlEdge;
import com.kniazkov.cpg.passes.*;
import java.math.BigInteger;
import java.util.*;

/**
 * Opt-in intraprocedural interval analysis, registered explicitly with {@link PassRunner}.
 * This pass is not part of parsing, building, or any ordinary normalization pipeline.
 * It joins paths without refining conditions; in particular [1,10] includes 5 even when
 * the only concrete incoming assignments are 1 and 10. Growing CFG states widen to top.
 *
 * <p>The precise subset consists of automatic nonvolatile int/bool locals and by-value
 * parameters, integer/boolean literals, pure +, -, *, unary +, -, !, comparisons,
 * int/bool conversions, standalone assignments and increments/decrements, and structured
 * blocks, if, while, do, for, break, continue and return. C++ int uses the source target
 * profile; Java int uses 32 bits. A possibly overflowing operation yields top, never a
 * mathematical result mistaken for a machine value. Calls, aliasing, globals, other
 * types, short-circuit/conditional/comma expressions and other unsupported syntax make
 * the entire callable conservative (all published values top). No interprocedural or
 * memory analysis is claimed.</p>
 *
 * <p>{@link #RANGE} is published on body expressions, local declarations, parameters and
 * return statements. An expression denotes values when evaluated; a local declaration
 * denotes its initializer, not its value at function exit. A plain assignment's target
 * name denotes the written value; an increment's operand denotes the value read. Return
 * statements denote their expression before any function-return conversion. Uninitialized
 * ints and incoming int parameters are top, booleans are [0,1]. Supported nodes not
 * evaluated on any reachable CFG path are bottom. Unsupported callables use top rather
 * than claiming unreachable behavior. Results are immutable and producer-owned.</p>
 */
public final class IntegerRanges implements AnalysisPass {
    public static final PassId ID = new PassId("IntegerRanges");
    public static final ResultKey<IntegerValue> RANGE = new ResultKey<>(ID, "range", IntegerValue.class);
    private static final IntegerDomain DOMAIN = IntegerDomain.INSTANCE;
    private static final int WIDEN_AFTER = 8;

    public IntegerRanges() {}
    @Override public PassId id() { return ID; }
    @Override public Set<PassId> requires() { return Set.of(BuiltinPasses.CONTROL_FLOW); }

    @Override public void analyze(PassContext context, AnalysisResults results) {
        var sources = SourceContexts.index(context.project());
        var flows = new IdentityHashMap<CallableDeclaration, FunctionFlow>();
        context.project().flows().forEach(flow -> flows.put(flow.entry(), flow));
        for (Node node : context.nodes()) if (node instanceof CallableDeclaration callable && callable.body().isPresent()) {
            var analysis = new FunctionAnalysis(callable, flows.get(callable), SourceContexts.require(sources, callable));
            analysis.run().forEach((target, value) -> results.put(target, RANGE, value));
        }
    }

    private static final class FunctionAnalysis {
        private final CallableDeclaration function;
        private final FunctionFlow flow;
        private final PlatformProfile profile;
        private final Language language;
        private final BigInteger minimum, maximum;
        private final Map<Declaration, TypeNode> variables = new LinkedHashMap<>();
        private final Map<Node, IntegerValue> values = new LinkedHashMap<>();

        FunctionAnalysis(CallableDeclaration function, FunctionFlow flow, SourceFile source) {
            this.function = function; this.flow = flow; profile = TypeSemantics.profile(source); language = source.language();
            var bounds = source.language() == Language.JAVA
                    ? new PlatformProfile.IntegerRange(BigInteger.valueOf(Integer.MIN_VALUE), BigInteger.valueOf(Integer.MAX_VALUE))
                    : profile.integerRange("int").orElseThrow();
            minimum = bounds.min(); maximum = bounds.max();
            for (var parameter : function.parameters()) {
                values.put(parameter, DOMAIN.bottom());
                parameter.type().ifPresent(type -> variables.put(parameter, type));
            }
            function.body().orElseThrow().walk(new NodeVisitor() {
                @Override public void visit(Node node) {
                    if (node instanceof Expression || node instanceof VariableDeclaration || node instanceof ReturnStatement)
                        values.put(node, DOMAIN.bottom());
                    if (node instanceof VariableDeclaration variable)
                        variable.type().ifPresent(type -> variables.put(variable, type));
                }
            });
        }

        Map<Node, IntegerValue> run() {
            if (flow == null || !flow.complete() || !supported()) {
                values.replaceAll((node, value) -> DOMAIN.top());
                return values;
            }
            Map<FlowVertex, List<ControlEdge>> successors = new IdentityHashMap<>();
            for (var edge : flow.control()) successors.computeIfAbsent(edge.from(), ignored -> new ArrayList<>()).add(edge);
            Map<FlowVertex, Map<Declaration, IntegerValue>> inputs = new LinkedHashMap<>();
            Map<FlowVertex, Map<Declaration, Integer>> growth = new IdentityHashMap<>();
            var initial = new LinkedHashMap<Declaration, IntegerValue>();
            variables.forEach((variable, type) -> initial.put(variable, unknown(type)));
            inputs.put(function, initial);
            var queue = new ArrayDeque<FlowVertex>();
            var queued = Collections.newSetFromMap(new IdentityHashMap<FlowVertex, Boolean>());
            queue.add(function); queued.add(function);
            while (!queue.isEmpty()) {
                var vertex = queue.remove(); queued.remove(vertex);
                var output = new LinkedHashMap<>(inputs.get(vertex));
                transfer(vertex, output, false);
                for (var edge : successors.getOrDefault(vertex, List.of())) {
                    var input = inputs.get(edge.to());
                    boolean changed = input == null;
                    if (input == null) inputs.put(edge.to(), new LinkedHashMap<>(output));
                    else {
                        var changes = growth.computeIfAbsent(edge.to(), ignored -> new IdentityHashMap<>());
                        for (var variable : variables.keySet()) {
                            var old = input.get(variable);
                            var joined = DOMAIN.join(old, output.get(variable));
                            if (!joined.equals(old)) {
                                int count = changes.merge(variable, 1, Integer::sum);
                                input.put(variable, count > WIDEN_AFTER ? DOMAIN.top() : joined);
                                changed = true;
                            }
                        }
                    }
                    if (changed && queued.add(edge.to())) queue.add(edge.to());
                }
            }
            // Publish only after convergence, never the temporary underapproximations.
            function.parameters().forEach(parameter -> record(parameter, initial.get(parameter), true));
            inputs.forEach((vertex, state) -> transfer(vertex, new LinkedHashMap<>(state), true));
            // CFG splits pure ! conditions (and their conversion wrappers). Recover their
            // summaries from the evaluated children without executing any statement twice.
            for (Node node : values.keySet()) if (node instanceof Expression expression) completePure(expression);
            return values;
        }

        private boolean supported() {
            if (function.kind() != CallableDeclaration.Kind.FUNCTION || !function.bodyComplete()
                    || function.functionTryBlock() || !function.handlers().isEmpty()
                    || !function.initializers().isEmpty() || ConstantEvaluator.hasSemanticErrors(function)) return false;
            if (function.parameters().stream().anyMatch(parameter -> parameter.type().filter(this::scalar).isEmpty()
                    || parameter.modifiers().contains("volatile"))) return false;
            return statement(function.body().orElseThrow());
        }

        private boolean statement(Node node) {
            if (node instanceof BlockStatement block) return block.complete() && block.items().stream().allMatch(this::statement);
            if (node instanceof VariableDeclaration variable) return variable.type().filter(this::scalar).isPresent()
                    && Collections.disjoint(variable.modifiers(), Set.of("static", "extern", "volatile", "thread_local"))
                    && variable.constructorTarget().isEmpty() && variable.destructorTarget().isEmpty()
                    && variable.bitWidth().isEmpty() && variable.initializer().map(this::pure).orElse(true);
            if (node instanceof ExpressionStatement expression) return instruction(expression.expression());
            if (node instanceof ReturnStatement result) return result.value().map(this::pure).orElse(true);
            if (node instanceof IfStatement branch) return pure(branch.condition()) && statement(branch.thenBranch())
                    && branch.elseBranch().map(this::statement).orElse(true);
            if (node instanceof WhileStatement loop) return pure(loop.condition()) && statement(loop.body());
            if (node instanceof DoWhileStatement loop) return pure(loop.condition()) && statement(loop.body());
            if (node instanceof ForStatement loop) return loop.initializers().stream().allMatch(this::statement)
                    && loop.condition().map(this::pure).orElse(true) && loop.update().map(this::instruction).orElse(true)
                    && statement(loop.body());
            return node instanceof EmptyStatement || node instanceof BreakStatement || node instanceof ContinueStatement;
        }

        private boolean instruction(Expression expression) {
            if (OperatorSemantics.mayInvokeUserCode(expression) || expression.dependent()) return false;
            if (expression instanceof AssignmentExpression assignment) return local(assignment.target()) && pure(assignment.value());
            if (increment(expression)) return local(((UnaryExpression) expression).operand())
                    && TypeSemantics.expressionType(expression, profile).filter(this::integer).isPresent();
            return pure(expression);
        }

        private boolean pure(Expression expression) {
            if (OperatorSemantics.mayInvokeUserCode(expression) || expression.dependent()) return false;
            if (expression instanceof NameExpression) return local(expression);
            if (expression instanceof IntegerLiteral literal) return literal.cppType().map("int"::equals).orElse(true)
                    && minimum.compareTo(literal.bigValue()) <= 0 && maximum.compareTo(literal.bigValue()) >= 0;
            if (expression instanceof BooleanLiteral) return true;
            if (expression instanceof ConversionExpression conversion) return scalar(conversion.targetType())
                    && Set.of(ConversionExpression.Kind.STATIC_CAST, ConversionExpression.Kind.C_STYLE_CAST,
                        ConversionExpression.Kind.LVALUE_TO_RVALUE, ConversionExpression.Kind.INTEGRAL_PROMOTION,
                        ConversionExpression.Kind.ARITHMETIC_CONVERSION, ConversionExpression.Kind.QUALIFICATION).contains(conversion.kind())
                    && pure(conversion.operand());
            if (expression instanceof UnaryPlusExpression || expression instanceof NegateExpression || expression instanceof LogicalNotExpression)
                return pure(((UnaryExpression) expression).operand());
            if (expression instanceof AddExpression || expression instanceof SubtractExpression || expression instanceof MultiplyExpression
                    || comparison(expression)) {
                var binary = (BinaryExpression) expression;
                return pure(binary.left()) && pure(binary.right());
            }
            return false;
        }

        private boolean local(Expression expression) {
            return expression instanceof NameExpression name && name.target().filter(variables::containsKey)
                    .map(variables::get).filter(this::scalar).isPresent();
        }

        private boolean scalar(TypeNode type) {
            if ((TypeSemantics.qualifiers(type) & 2) != 0) return false;
            var plain = TypeSemantics.unqualified(type);
            return plain instanceof NamedType named && (named.fundamentalKind().filter(kind ->
                    kind == FundamentalKind.INT || kind == FundamentalKind.BOOL).isPresent()
                    || language == Language.JAVA && named.name().equals("boolean") && named.declaration().isEmpty());
        }
        private boolean integer(TypeNode type) {
            return scalar(type) && TypeSemantics.unqualified(type) instanceof NamedType named
                    && named.fundamentalKind().orElse(null) == FundamentalKind.INT;
        }
        private boolean bool(TypeNode type) { return scalar(type) && !integer(type); }
        private IntegerValue unknown(TypeNode type) { return bool(type) ? DOMAIN.interval(0, 1) : DOMAIN.top(); }

        private void transfer(FlowVertex vertex, Map<Declaration, IntegerValue> state, boolean publish) {
            if (vertex instanceof VariableDeclaration variable) {
                var value = variable.initializer().map(expression -> evaluate(expression, state, publish)).orElseGet(() -> unknown(variables.get(variable)));
                value = convert(value, variables.get(variable));
                state.put(variable, value); record(variable, value, publish);
            } else if (vertex instanceof ExpressionStatement statement) evaluate(statement.expression(), state, publish);
            else if (vertex instanceof ReturnStatement statement)
                record(statement, statement.value().map(expression -> evaluate(expression, state, publish)).orElseGet(DOMAIN::top), publish);
            else if (vertex instanceof Expression expression) evaluate(expression, state, publish);
        }

        private IntegerValue evaluate(Expression expression, Map<Declaration, IntegerValue> state, boolean publish) {
            IntegerValue result;
            if (expression instanceof IntegerLiteral literal) result = DOMAIN.constant(literal.bigValue());
            else if (expression instanceof BooleanLiteral literal) result = DOMAIN.constant(literal.value() ? 1 : 0);
            else if (expression instanceof NameExpression name) result = state.get(name.target().orElseThrow());
            else if (expression instanceof AssignmentExpression assignment) {
                var target = ((NameExpression) assignment.target()).target().orElseThrow();
                result = convert(evaluate(assignment.value(), state, publish), variables.get(target));
                state.put(target, result); record(assignment.target(), result, publish);
            } else if (increment(expression)) {
                var operand = ((UnaryExpression) expression).operand();
                var target = ((NameExpression) operand).target().orElseThrow();
                var before = evaluate(operand, state, publish);
                int delta = expression instanceof PrefixIncrementExpression || expression instanceof PostfixIncrementExpression ? 1 : -1;
                var after = add(before, DOMAIN.constant(delta));
                state.put(target, after);
                result = expression instanceof PostfixIncrementExpression || expression instanceof PostfixDecrementExpression ? before : after;
            } else if (expression instanceof ConversionExpression conversion)
                result = convert(evaluate(conversion.operand(), state, publish), conversion.targetType());
            else if (expression instanceof UnaryExpression unary)
                result = unary(unary, evaluate(unary.operand(), state, publish));
            else if (expression instanceof BinaryExpression binary)
                result = binary(binary, evaluate(binary.left(), state, publish), evaluate(binary.right(), state, publish));
            else result = DOMAIN.top();
            record(expression, result, publish);
            return result;
        }

        private IntegerValue completePure(Expression expression) {
            var existing = values.getOrDefault(expression, DOMAIN.bottom());
            if (!existing.isBottom()) return existing;
            IntegerValue value = existing;
            if (expression instanceof ConversionExpression conversion)
                value = convert(completePure(conversion.operand()), conversion.targetType());
            else if (expression instanceof UnaryPlusExpression || expression instanceof NegateExpression || expression instanceof LogicalNotExpression)
                value = unary((UnaryExpression) expression, completePure(((UnaryExpression) expression).operand()));
            else if (expression instanceof BinaryExpression binary && pure(binary))
                value = binary(binary, completePure(binary.left()), completePure(binary.right()));
            values.put(expression, value);
            return value;
        }

        private void record(Node node, IntegerValue value, boolean publish) {
            if (publish) values.put(node, DOMAIN.join(values.getOrDefault(node, DOMAIN.bottom()), value));
        }
        private IntegerValue convert(IntegerValue value, TypeNode type) {
            return bool(type) ? truth(value) : bounded(value);
        }
        private IntegerValue bounded(IntegerValue value) {
            if (value.isBottom()) return value;
            return finite(value) && value.lowerBound().orElseThrow().compareTo(minimum) >= 0
                    && value.upperBound().orElseThrow().compareTo(maximum) <= 0 ? value : DOMAIN.top();
        }
        private IntegerValue truth(IntegerValue value) {
            if (value.isBottom()) return value;
            if (!value.contains(0)) return DOMAIN.constant(1);
            return value.isConstant() ? DOMAIN.constant(0) : DOMAIN.interval(0, 1);
        }
        private IntegerValue unary(UnaryExpression expression, IntegerValue value) {
            if (value.isBottom()) return value;
            if (expression instanceof LogicalNotExpression) {
                var truth = truth(value);
                return truth.isConstant() ? DOMAIN.constant(truth.contains(0) ? 1 : 0) : truth;
            }
            if (expression instanceof UnaryPlusExpression) return bounded(value);
            return finite(value) ? bounded(DOMAIN.interval(value.upperBound().orElseThrow().negate(), value.lowerBound().orElseThrow().negate())) : DOMAIN.top();
        }
        private IntegerValue add(IntegerValue left, IntegerValue right) {
            if (left.isBottom() || right.isBottom()) return DOMAIN.bottom();
            if (!finite(left) || !finite(right)) return DOMAIN.top();
            return bounded(DOMAIN.interval(left.lowerBound().orElseThrow().add(right.lowerBound().orElseThrow()),
                    left.upperBound().orElseThrow().add(right.upperBound().orElseThrow())));
        }
        private IntegerValue binary(BinaryExpression expression, IntegerValue left, IntegerValue right) {
            if (left.isBottom() || right.isBottom()) return DOMAIN.bottom();
            if (comparison(expression)) {
                if (!finite(left) || !finite(right)) return DOMAIN.interval(0, 1);
                var lo = left.lowerBound().orElseThrow(); var hi = left.upperBound().orElseThrow();
                var low = right.lowerBound().orElseThrow(); var high = right.upperBound().orElseThrow();
                boolean always, never;
                if (expression instanceof EqualExpression || expression instanceof NotEqualExpression) {
                    always = left.isConstant() && left.equals(right);
                    never = hi.compareTo(low) < 0 || high.compareTo(lo) < 0;
                    if (expression instanceof NotEqualExpression) { boolean swap = always; always = never; never = swap; }
                } else if (expression instanceof LessExpression || expression instanceof LessOrEqualExpression) {
                    boolean equal = expression instanceof LessOrEqualExpression;
                    always = equal ? hi.compareTo(low) <= 0 : hi.compareTo(low) < 0;
                    never = equal ? lo.compareTo(high) > 0 : lo.compareTo(high) >= 0;
                } else {
                    boolean equal = expression instanceof GreaterOrEqualExpression;
                    always = equal ? lo.compareTo(high) >= 0 : lo.compareTo(high) > 0;
                    never = equal ? hi.compareTo(low) < 0 : hi.compareTo(low) <= 0;
                }
                return always ? DOMAIN.constant(1) : never ? DOMAIN.constant(0) : DOMAIN.interval(0, 1);
            }
            if (!finite(left) || !finite(right)) return DOMAIN.top();
            if (expression instanceof AddExpression) return add(left, right);
            var a = left.lowerBound().orElseThrow(); var b = left.upperBound().orElseThrow();
            var c = right.lowerBound().orElseThrow(); var d = right.upperBound().orElseThrow();
            if (expression instanceof SubtractExpression) return bounded(DOMAIN.interval(a.subtract(d), b.subtract(c)));
            var products = List.of(a.multiply(c), a.multiply(d), b.multiply(c), b.multiply(d));
            return bounded(DOMAIN.interval(Collections.min(products), Collections.max(products)));
        }
        private static boolean finite(IntegerValue value) { return value.lowerBound().isPresent() && value.upperBound().isPresent(); }
        private static boolean increment(Expression expression) {
            return expression instanceof PrefixIncrementExpression || expression instanceof PrefixDecrementExpression
                    || expression instanceof PostfixIncrementExpression || expression instanceof PostfixDecrementExpression;
        }
        private static boolean comparison(Expression expression) {
            return expression instanceof EqualExpression || expression instanceof NotEqualExpression || expression instanceof LessExpression
                    || expression instanceof LessOrEqualExpression || expression instanceof GreaterExpression || expression instanceof GreaterOrEqualExpression;
        }
    }
}
