/* Conservative outcome summaries for known C++03 unexpected handlers. MIT License. */
package com.kniazkov.cpg.analysis;

import com.kniazkov.cpg.build.PlatformProfile;
import com.kniazkov.cpg.model.*;
import com.kniazkov.cpg.model.FunctionFlow.ControlEdge;
import java.util.*;

/** Narrows boundary alternatives only when every candidate has a supported body. */
final class UnexpectedHandlerOutcomes {
    private UnexpectedHandlerOutcomes() {}
    private record Outcome(boolean next, boolean returned, boolean terminated, boolean unknown, Set<String> thrown) {
        Outcome { thrown = Set.copyOf(thrown); }
        Outcome union(Outcome other) {
            var types = new HashSet<>(thrown); types.addAll(other.thrown);
            return new Outcome(next || other.next, returned || other.returned,
                    terminated || other.terminated, unknown || other.unknown, types);
        }
        Outcome then(Outcome other) {
            if (!next) return this;
            return new Outcome(false, returned, terminated, unknown, thrown).union(other);
        }
    }
    private static final Outcome NEXT = new Outcome(true, false, false, false, Set.of());
    private static final Outcome UNKNOWN = new Outcome(true, false, false, true, Set.of());

    static List<FunctionFlow> refine(Project project, List<FunctionFlow> flows, Map<Node, SourceFile> contexts) {
        var summaries = new Summaries(project, flows, contexts);
        var result = new ArrayList<FunctionFlow>();
        for (var flow : flows) {
            Set<ControlEdge> removed = new HashSet<>();
            for (var event : flow.auxiliaryVertices()) {
                if (event.kind() != AuxiliaryFlowVertex.Kind.UNEXPECTED_CALL || event.unknownUnexpectedHandler()
                        || event.unexpectedHandlers().isEmpty()) continue;
                summaries.remaining = 2000;
                Outcome outcomes = new Outcome(false, false, false, false, Set.of());
                for (var target : event.unexpectedHandlers()) outcomes = outcomes.union(summaries.function(target, new HashSet<>()));
                // Returning from an unexpected_handler violates its contract. Keep conservative
                // alternatives instead of treating host termination as a portable positive oracle.
                if (outcomes.unknown() || outcomes.next() || outcomes.returned()) continue;
                final var thrownTypes = outcomes.thrown();
                boolean rejected = thrownTypes.stream().anyMatch(type -> flow.entry().exceptionTypes().stream()
                        .noneMatch(allowed -> ExceptionMatching.catches(project, type, allowed.canonicalIdentity())));
                boolean fallback = flow.entry().exceptionTypes().stream().anyMatch(UnexpectedHandlerOutcomes::badException);
                for (var edge : flow.control()) if (edge.from() == event && edge.to() instanceof AuxiliaryFlowVertex destination) {
                    boolean keep = switch (destination.kind()) {
                        case TERMINATE_EXIT -> outcomes.terminated() || rejected && !fallback;
                        case BAD_EXCEPTION_REPLACEMENT -> rejected;
                        case EXCEPTION_REPLACEMENT -> destination.object().filter(TypeNode.class::isInstance).map(TypeNode.class::cast)
                                .filter(allowed -> thrownTypes.stream().anyMatch(type -> ExceptionMatching.catches(project, type, allowed.canonicalIdentity()))).isPresent();
                        default -> true;
                    };
                    if (!keep) removed.add(edge);
                }
            }
            if (removed.isEmpty()) { result.add(flow); continue; }
            var edges = flow.control().stream().filter(e -> !removed.contains(e)).toList();
            Map<FlowVertex, List<FlowVertex>> outgoing = new IdentityHashMap<>();
            for (var edge : edges) outgoing.computeIfAbsent(edge.from(), k -> new ArrayList<>()).add(edge.to());
            Set<FlowVertex> reached = Collections.newSetFromMap(new IdentityHashMap<>());
            var work = new ArrayDeque<FlowVertex>(); work.add(flow.entry());
            while (!work.isEmpty()) { var vertex = work.remove(); if (reached.add(vertex)) work.addAll(outgoing.getOrDefault(vertex, List.of())); }
            result.add(new FunctionFlow(flow.entry(), flow.exit(), edges.stream().filter(e -> reached.contains(e.from())).toList(), flow.data(), flow.complete()));
        }
        return List.copyOf(result);
    }
    private static boolean badException(TypeNode type) {
        return ClassSemantics.objectClass(type).filter(LibraryDeclarations::isLibrary).filter(c -> c.name().equals("bad_exception")).isPresent();
    }
    private static final class Summaries {
        private int remaining;
        private final Project project;
        private final Map<CallableDeclaration, FunctionFlow> functions = new IdentityHashMap<>();
        private final Map<Node, SourceFile> contexts;
        Summaries(Project project, List<FunctionFlow> flows, Map<Node, SourceFile> contexts) {
            this.project = project; flows.forEach(f -> functions.put(f.entry(), f)); this.contexts = contexts;
        }
        Outcome function(CallableDeclaration declaration, Set<CallableDeclaration> active) {
            var target = declaration;
            if (!functions.containsKey(target)) target = target.definition().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).orElse(target);
            var flow = functions.get(target);
            if (--remaining < 0 || flow == null || !flow.complete() || target.functionTryBlock() || active.size() >= 8 || active.contains(target)) return UNKNOWN;
            if (target.kind() == CallableDeclaration.Kind.CONSTRUCTOR && !target.initializers().isEmpty()) return UNKNOWN;
            var stack = new HashSet<>(active); stack.add(target);
            var profile = TypeSemantics.profile(SourceContexts.require(contexts, target));
            if (target.parameters().stream().anyMatch(p -> p.type().flatMap(ClassSemantics::objectClass).isPresent())) return UNKNOWN;
            var result = statement(target.body().orElseThrow(), profile, stack, Set.of());
            if (target.hasExceptionSpecification() && result.thrown().stream().anyMatch(type -> flow.entry().exceptionTypes().stream()
                    .noneMatch(allowed -> ExceptionMatching.catches(project, type, allowed.canonicalIdentity())))) return UNKNOWN;
            return result;
        }
        Outcome statement(Node node, PlatformProfile profile, Set<CallableDeclaration> active, Set<String> caught) {
            if (--remaining < 0) return UNKNOWN;
            if (node instanceof BlockStatement block) return sequence(block.items(), profile, active, caught);
            if (node instanceof ExpressionStatement expression) return expression(expression.expression(), profile, active, caught);
            if (node instanceof ReturnStatement returned) {
                var value = returned.value().map(e -> expression(e, profile, active, caught)).orElse(NEXT);
                return new Outcome(false, value.next() || value.returned(), value.terminated(), value.unknown(), value.thrown());
            }
            if (node instanceof IfStatement branch) return expression(branch.condition(), profile, active, caught).then(
                    statement(branch.thenBranch(), profile, active, caught).union(branch.elseBranch()
                            .map(other -> statement(other, profile, active, caught)).orElse(NEXT)));
            if (node instanceof TryStatement attempt) {
                var body = statement(attempt.body(), profile, active, caught);
                if (body.unknown()) return UNKNOWN;
                var remaining = new HashSet<>(body.thrown());
                Outcome result = new Outcome(body.next(), body.returned(), body.terminated(), false, Set.of());
                for (var handler : attempt.handlers()) {
                    var type = handler.parameter().flatMap(ParameterDeclaration::type);
                    if (type.flatMap(ClassSemantics::objectClass).isPresent()) return UNKNOWN;
                    var matching = new HashSet<String>();
                    for (var thrown : remaining) if (type.isEmpty() || ExceptionMatching.catches(project, thrown, type.orElseThrow().canonicalIdentity())) matching.add(thrown);
                    if (!matching.isEmpty()) { result = result.union(statement(handler.body(), profile, active, matching)); remaining.removeAll(matching); }
                }
                return result.union(new Outcome(false, false, false, false, remaining));
            }
            if (node instanceof VariableDeclaration variable) {
                if (variable.type().flatMap(ClassSemantics::objectClass).isPresent())
                    return local(variable, NEXT, profile, active, caught);
                if (variable.type().map(TypeSemantics::unqualified).filter(ArrayType.class::isInstance).isPresent()) return UNKNOWN;
                return variable.initializer().map(e -> expression(e, profile, active, caught)).orElse(NEXT);
            }
            if (node instanceof EmptyStatement || node instanceof ClassDeclaration || node instanceof CallableDeclaration) return NEXT;
            return UNKNOWN; // loops, goto, lifetime operations and unsupported statements are not proofs
        }
        /** Fold scopes from the end so a failed construction never destroys that object. */
        Outcome sequence(List<? extends Node> items, PlatformProfile profile, Set<CallableDeclaration> active, Set<String> caught) {
            Outcome result = NEXT;
            for (int i = items.size() - 1; i >= 0; i--) {
                if (--remaining < 0) return UNKNOWN;
                Node item = items.get(i);
                if (item instanceof VariableDeclaration variable && variable.type().flatMap(ClassSemantics::objectClass).isPresent())
                    result = local(variable, result, profile, active, caught);
                else result = statement(item, profile, active, caught).then(result);
            }
            return result;
        }
        /** Selected automatic, default-initialized complete objects; no copies or hidden subobjects. */
        Outcome local(VariableDeclaration variable, Outcome following, PlatformProfile profile,
                Set<CallableDeclaration> active, Set<String> caught) {
            if (variable.initializer().isPresent() || variable.modifiers().contains("static")
                    || variable.modifiers().contains("extern")) return UNKNOWN;
            TypeNode type = variable.type().map(TypeSemantics::unqualified).orElse(null);
            int count = 1;
            if (type instanceof ArrayType array) {
                var bound = array.bound().flatMap(e -> ConstantEvaluator.evaluate(e, profile)).map(v -> v.number());
                if (TypeSemantics.unqualified(array.elementType()) instanceof ArrayType || bound.isEmpty()
                        || bound.get().signum() <= 0 || bound.get().compareTo(java.math.BigInteger.valueOf(64)) > 0) return UNKNOWN;
                count = bound.get().intValue(); type = TypeSemantics.unqualified(array.elementType());
            }
            if (type == null || type instanceof ReferenceType) return UNKNOWN;
            var owner = ClassSemantics.objectClass(type).orElse(null);
            if (owner == null || LibraryDeclarations.isLibrary(owner) || !owner.bases().isEmpty()
                    || ClassSemantics.fields(owner).stream().anyMatch(field -> field.type().map(TypeSemantics::unqualified)
                        .filter(t -> t instanceof ArrayType || t instanceof ReferenceType || ClassSemantics.objectClass(t).isPresent()).isPresent())) return UNKNOWN;
            Outcome construct = special(owner, ClassSemantics.SpecialKind.DEFAULT_CONSTRUCTOR, active);
            Outcome destroy = special(owner, ClassSemantics.SpecialKind.DESTRUCTOR, active);
            // Each layer represents one successfully constructed element. A later
            // constructor failure unwinds earlier layers only, in reverse order.
            Outcome result = following;
            for (int i = 0; i < count; i++) result = construct.then(cleanup(result, destroy));
            return result;
        }
        Outcome special(ClassDeclaration owner, ClassSemantics.SpecialKind kind, Set<CallableDeclaration> active) {
            var member = ClassSemantics.specialMembers(owner).stream().filter(m -> m.kind() == kind).findFirst().orElseThrow();
            if (member.origin() == ClassSemantics.Origin.IMPLICIT) return NEXT;
            if (member.origin() != ClassSemantics.Origin.USER_DECLARED || member.declarations().size() != 1) return UNKNOWN;
            var target = member.declarations().getFirst();
            // Default arguments, member/base initialization and function try blocks
            // need their own evaluation/lifetime contract, not just the written body.
            if (!target.parameters().isEmpty() || !target.initializers().isEmpty()) return UNKNOWN;
            var outcome = function(target, active);
            return new Outcome(outcome.next() || outcome.returned(), false, outcome.terminated(), outcome.unknown(), outcome.thrown());
        }
        /** Cleanup throws replace normal/return completion, but terminate during unwinding. */
        Outcome cleanup(Outcome body, Outcome destructor) {
            if (!body.next() && !body.returned() && body.thrown().isEmpty()) return body;
            if (body.unknown() || destructor.unknown()) return UNKNOWN;
            boolean completes = destructor.next();
            var thrown = new HashSet<String>();
            if (completes) thrown.addAll(body.thrown());
            if (body.next() || body.returned()) thrown.addAll(destructor.thrown());
            return new Outcome(completes && body.next(), completes && body.returned(),
                    body.terminated() || destructor.terminated() || !body.thrown().isEmpty() && !destructor.thrown().isEmpty(),
                    false, thrown);
        }
        Outcome expression(Expression expression, PlatformProfile profile, Set<CallableDeclaration> active, Set<String> caught) {
            if (expression instanceof SizeofExpression || expression instanceof OffsetofExpression
                    || expression instanceof TypeidExpression typeid && TypeSemantics.typeidOperandUnevaluated(typeid, profile)) return NEXT;
            if (expression.operatorTarget().isPresent() || expression instanceof NewExpression || expression instanceof DeleteExpression
                    || TypeSemantics.expressionType(expression, profile).map(TypeSemantics::decay).flatMap(ClassSemantics::objectClass).isPresent()) return UNKNOWN;
            if (expression instanceof ThrowExpression thrown) {
                if (thrown.value().isEmpty()) return caught.isEmpty() ? UNKNOWN : new Outcome(false, false, false, false, caught);
                var value = thrown.value().orElseThrow();
                var evaluated = expression(value, profile, active, caught);
                var type = TypeSemantics.expressionType(value, profile).map(TypeSemantics::decay).map(TypeNode::canonicalIdentity);
                return evaluated.then(type.map(t -> new Outcome(false, false, false, false, Set.of(t))).orElse(UNKNOWN));
            }
            if (expression instanceof CallExpression call) {
                if (!(call.callee() instanceof NameExpression || call.callee() instanceof ScopeMemberExpression)
                        || call.isVirtualDispatch() || !call.defaultArguments().isEmpty() || call.target().isEmpty()) return UNKNOWN;
                Outcome arguments = NEXT;
                // No parameter values are used to prune branches. Union argument exceptions
                // conservatively; do not infer a particular C++03 evaluation order.
                for (var argument : call.arguments()) arguments = arguments.union(expression(argument, profile, active, caught));
                if (arguments.unknown()) return UNKNOWN;
                var target = call.target().orElseThrow();
                Outcome result;
                if (LibraryDeclarations.isLibrary(target)) result = target.name().equals("terminate")
                        ? new Outcome(false, false, true, false, Set.of()) : UNKNOWN;
                else result = function(target, active);
                var invocation = new Outcome(result.next() || result.returned(), false, result.terminated(), result.unknown(), result.thrown());
                return new Outcome(false, false, arguments.terminated(), arguments.unknown(), arguments.thrown()).union(invocation);
            }
            Outcome result = NEXT;
            for (var child : expression.children()) if (child instanceof Expression operand) result = result.union(expression(operand, profile, active, caught));
            return result;
        }
    }
}
