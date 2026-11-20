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
            return function(declaration, active, false);
        }
        Outcome function(CallableDeclaration declaration, Set<CallableDeclaration> active, boolean lifetimeBody) {
            var target = declaration;
            if (!functions.containsKey(target)) target = target.definition().filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).orElse(target);
            var flow = functions.get(target);
            if (--remaining < 0 || flow == null || !flow.complete() && !lifetimeBody || !target.bodyComplete() || target.functionTryBlock() || active.size() >= 8 || active.contains(target)) return UNKNOWN;
            if (target.kind() == CallableDeclaration.Kind.CONSTRUCTOR && !lifetimeBody && !target.initializers().isEmpty()) return UNKNOWN;
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
        /** Automatic construction, including selected user-declared copies from named lvalues. */
        Outcome local(VariableDeclaration variable, Outcome following, PlatformProfile profile,
                Set<CallableDeclaration> active, Set<String> caught) {
            if (variable.modifiers().contains("static")
                    || variable.modifiers().contains("extern")) return UNKNOWN;
            List<Expression> arguments = List.of();
            if (variable.initializer().isPresent()) {
                var value = variable.initializer().orElseThrow();
                if (value instanceof InitializationExpression init && init.form() == InitializationExpression.Form.DIRECT) {
                    arguments = init.arguments();
                } else {
                    // Same-class lvalue copy initialization has no conversion temporary
                    // or optional copy-elision choice. Other copy forms remain unknown.
                    var source = TypeSemantics.expressionType(value, profile).flatMap(ClassSemantics::objectClass);
                    var destination = variable.type().flatMap(ClassSemantics::objectClass);
                    if (!namedLvalue(value) || source.isEmpty() || destination.isEmpty()
                            || !ClassSemantics.sameClass(source.orElseThrow(), destination.orElseThrow())) return UNKNOWN;
                    var copies = ClassSemantics.specialMembers(destination.orElseThrow()).stream()
                            .filter(m -> m.kind() == ClassSemantics.SpecialKind.COPY_CONSTRUCTOR)
                            .flatMap(m -> m.declarations().stream()).toList();
                    var copy = OverloadResolution.select(copies, List.of(value), profile).selected();
                    if (copy.isEmpty() || copy.orElseThrow().modifiers().contains("explicit")) return UNKNOWN;
                    arguments = List.of(value);
                }
            }
            var lifetime = lifetime(variable.type().orElse(null), profile, active, new HashSet<>(), true, arguments);
            return lifetime.construct().then(cleanup(following, lifetime.destroy()));
        }
        private record Lifetime(Outcome construct, Outcome destroy) {}
        private static final Lifetime UNKNOWN_LIFETIME = new Lifetime(UNKNOWN, UNKNOWN);

        /** Complete objects own virtual bases; base subobjects omit their shared virtual bases. */
        Lifetime lifetime(TypeNode written, PlatformProfile profile, Set<CallableDeclaration> active,
                Set<ClassDeclaration> owners, boolean completeObject) {
            return lifetime(written, profile, active, owners, completeObject, List.of());
        }
        Lifetime lifetime(TypeNode written, PlatformProfile profile, Set<CallableDeclaration> active,
                Set<ClassDeclaration> owners, boolean completeObject, List<Expression> arguments) {
            if (--remaining < 0 || written == null) return UNKNOWN_LIFETIME;
            TypeNode type = TypeSemantics.unqualified(written);
            if (type instanceof ReferenceType) return UNKNOWN_LIFETIME;
            if (type instanceof ArrayType array) {
                if (!arguments.isEmpty()) return UNKNOWN_LIFETIME;
                var bound = array.bound().flatMap(e -> ConstantEvaluator.evaluate(e, profile)).map(v -> v.number());
                if (TypeSemantics.unqualified(array.elementType()) instanceof ArrayType || bound.isEmpty()
                        || bound.get().signum() <= 0 || bound.get().compareTo(java.math.BigInteger.valueOf(64)) > 0) return UNKNOWN_LIFETIME;
                var element = lifetime(array.elementType(), profile, active, owners, true);
                return compose(Collections.nCopies(bound.get().intValue(), element), NEXT, NEXT);
            }
            var owner = ClassSemantics.objectClass(type).orElse(null);
            if (owner == null) return TypeSemantics.scalar(type, profile) || type instanceof MemberPointerType
                    ? new Lifetime(NEXT, NEXT) : UNKNOWN_LIFETIME;
            if (LibraryDeclarations.isLibrary(owner) || owner.keyword().equals("union")
                    || owners.size() >= 8 || owners.contains(owner) || !ClassSemantics.hasCompleteHierarchy(owner))
                return UNKNOWN_LIFETIME;
            var nested = new HashSet<>(owners); nested.add(owner);
            var parts = new ArrayList<Lifetime>();
            var constructors = owner.members().stream().filter(CallableDeclaration.class::isInstance)
                    .map(CallableDeclaration.class::cast).filter(c -> c.kind() == CallableDeclaration.Kind.CONSTRUCTOR).toList();
            CallableDeclaration selected = OverloadResolution.select(constructors, arguments, profile).selected().orElse(null);
            if (selected == null && (!constructors.isEmpty() || !arguments.isEmpty())) return UNKNOWN_LIFETIME;
            CallableDeclaration constructor = selected == null ? null : selected.definition()
                    .filter(CallableDeclaration.class::isInstance).map(CallableDeclaration.class::cast).orElse(selected);
            var initializers = constructor == null ? List.<MemberInitializer>of() : constructor.initializers();
            var fields = ClassSemantics.fields(owner);
            var initialized = new HashSet<Declaration>();
            var initializedBases = new ArrayList<ClassDeclaration>();
            var virtualBases = ClassSemantics.virtualBases(owner);
            for (var initializer : initializers) {
                var target = initializer.target().orElse(null);
                var base = ClassSemantics.initializedBase(initializer);
                if (base.isPresent()) {
                    var cls = base.orElseThrow();
                    boolean direct = owner.bases().stream().filter(b -> !b.isVirtual())
                            .anyMatch(b -> ClassSemantics.objectClass(b.type()).filter(c -> ClassSemantics.sameClass(c, cls)).isPresent());
                    boolean virtual = virtualBases.stream().anyMatch(b -> ClassSemantics.objectClass(b.type())
                            .filter(c -> ClassSemantics.sameClass(c, cls)).isPresent());
                    if ((!direct && !virtual) || direct && virtual || initializedBases.stream().anyMatch(c -> ClassSemantics.sameClass(c, cls)))
                        return UNKNOWN_LIFETIME;
                    initializedBases.add(cls);
                } else if (!(target instanceof VariableDeclaration field) || !fields.contains(field)
                        || !initialized.add(field) || !initializer.arguments().isEmpty()
                        && !field.type().map(t -> TypeSemantics.scalar(t, profile)
                            || TypeSemantics.unqualified(t) instanceof MemberPointerType
                            || !(TypeSemantics.unqualified(t) instanceof ArrayType) && ClassSemantics.objectClass(t).isPresent()).orElse(false))
                    return UNKNOWN_LIFETIME;
            }
            // Construction order, not initializer-list order. Virtual base arguments
            // are omitted entirely for base-subobject invocations.
            var bases = new ArrayList<BaseSpecifier>();
            if (completeObject) bases.addAll(virtualBases);
            owner.bases().stream().filter(b -> !b.isVirtual()).forEach(bases::add);
            for (var base : bases) {
                var cls = ClassSemantics.objectClass(base.type()).orElseThrow();
                var args = initializers.stream().filter(i -> ClassSemantics.initializedBase(i)
                        .filter(c -> ClassSemantics.sameClass(c, cls)).isPresent()).findFirst()
                        .map(MemberInitializer::arguments).orElse(List.of());
                parts.add(lifetime(base.type(), profile, active, nested, false, args));
            }
            for (var field : fields) {
                if (field.initializer().isPresent()) return UNKNOWN_LIFETIME;
                var initializer = initializers.stream().filter(i -> i.target().orElse(null) == field).findFirst();
                var args = initializer.map(MemberInitializer::arguments).orElse(List.of());
                Lifetime part;
                if (field.type().flatMap(ClassSemantics::objectClass).isPresent()) {
                    part = lifetime(field.type().orElseThrow(), profile, active, nested, true, args);
                } else {
                    if (args.size() > 1) return UNKNOWN_LIFETIME;
                    part = lifetime(field.type().orElse(null), profile, active, nested, true);
                    Outcome evaluated = NEXT;
                    for (var argument : args) evaluated = evaluated.then(expression(argument, profile, active, Set.of()));
                    part = new Lifetime(evaluated.then(part.construct()), part.destroy());
                }
                parts.add(part);
            }
            var result = compose(parts, selected == null ? NEXT : constructorBody(selected, active),
                    special(owner, ClassSemantics.SpecialKind.DESTRUCTOR, active));
            // A written specification governs subobject operations as well as the body.
            return new Lifetime(invocationArguments(selected, arguments, profile, active).then(
                    selected == null ? result.construct() : constrain(selected, result.construct())),
                    constrain(owner, ClassSemantics.SpecialKind.DESTRUCTOR, result.destroy()));
        }
        /** Arguments are caller-side expressions: before subobjects and outside the callee specification. */
        Outcome invocationArguments(CallableDeclaration target, List<Expression> written,
                PlatformProfile profile, Set<CallableDeclaration> active) {
            if (target == null) return written.isEmpty() ? NEXT : UNKNOWN;
            if (written.size() > target.parameters().size() || !target.templateParameters().isEmpty()) return UNKNOWN;
            Outcome result = NEXT;
            for (int i = 0; i < target.parameters().size(); i++) {
                var parameter = target.parameters().get(i);
                var value = i < written.size() ? written.get(i) : parameter.defaultValue().orElse(null);
                if (value == null) return UNKNOWN;
                var parameterType = parameter.type().map(TypeSemantics::unqualified).orElse(null);
                Outcome argument;
                if (parameterType instanceof ReferenceType reference) {
                    var source = TypeSemantics.expressionType(referenceOperand(value), profile).map(TypeSemantics::unqualified);
                    var referred = TypeSemantics.unqualified(reference.referredType());
                    // A named lvalue binds without construction, destruction or a read.
                    // Reject temporaries, derived-to-base binding and user conversions.
                    if (!namedLvalue(value) || source.isEmpty() || referred instanceof ArrayType
                            || !source.orElseThrow().canonicalIdentity().equals(referred.canonicalIdentity())) return UNKNOWN;
                    argument = NEXT;
                } else {
                    if (parameterType == null || !(TypeSemantics.scalar(parameterType, profile)
                            || parameterType instanceof MemberPointerType)) return UNKNOWN;
                    if (!TypeSemantics.expressionType(value, profile).map(t -> TypeSemantics.scalar(TypeSemantics.decay(t), profile)
                            || TypeSemantics.unqualified(TypeSemantics.decay(t)) instanceof MemberPointerType).orElse(false)) return UNKNOWN;
                    argument = expression(value, profile, active, Set.of());
                }
                var union = result.union(argument);
                // C++03 does not specify argument order. Any argument may throw,
                // but the invocation is reachable only if all can complete.
                result = new Outcome(result.next() && argument.next(), false, union.terminated(), union.unknown(), union.thrown());
            }
            return result;
        }
        /** Only stable names are supported; member/index/call expressions need evaluation summaries. */
        boolean namedLvalue(Expression value) {
            return referenceOperand(value) instanceof NameExpression name && name.target()
                    .filter(t -> t instanceof VariableDeclaration || t instanceof ParameterDeclaration).isPresent();
        }
        /** Strip only semantic reference binding, never a cast or value conversion. */
        Expression referenceOperand(Expression value) {
            while (value instanceof ConversionExpression conversion && !conversion.isExplicit()
                    && conversion.kind() == ConversionExpression.Kind.REFERENCE_BINDING) value = conversion.operand();
            return value;
        }
        Lifetime compose(List<Lifetime> parts, Outcome constructor, Outcome destructor) {
            Outcome construct = constructor, destroy = destructor;
            for (int i = parts.size() - 1; i >= 0; i--) {
                var part = parts.get(i);
                construct = part.construct().then(failedConstruction(construct, part.destroy()));
                destroy = cleanup(destroy, part.destroy());
            }
            return new Lifetime(construct, destroy);
        }
        /** Successful construction retains subobjects; only an escaping exception cleans them. */
        Outcome failedConstruction(Outcome body, Outcome destructor) {
            if (body.unknown()) return UNKNOWN;
            var failure = cleanup(new Outcome(false, false, false, false, body.thrown()), destructor);
            return new Outcome(body.next(), body.returned(), body.terminated(), false, Set.of()).union(failure);
        }
        Outcome constrain(CallableDeclaration target, Outcome outcome) {
            if (target.hasExceptionSpecification() && outcome.thrown().stream().anyMatch(type -> target.exceptionTypes().stream()
                    .noneMatch(allowed -> ExceptionMatching.catches(project, type, allowed.canonicalIdentity())))) return UNKNOWN;
            return outcome;
        }
        Outcome constrain(ClassDeclaration owner, ClassSemantics.SpecialKind kind, Outcome outcome) {
            var member = ClassSemantics.specialMembers(owner).stream().filter(m -> m.kind() == kind).findFirst().orElseThrow();
            for (var target : member.declarations()) outcome = constrain(target, outcome);
            return outcome;
        }
        Outcome constructorBody(CallableDeclaration target, Set<CallableDeclaration> active) {
            var outcome = function(target, active, true);
            return new Outcome(outcome.next() || outcome.returned(), false, outcome.terminated(), outcome.unknown(), outcome.thrown());
        }
        Outcome special(ClassDeclaration owner, ClassSemantics.SpecialKind kind, Set<CallableDeclaration> active) {
            var member = ClassSemantics.specialMembers(owner).stream().filter(m -> m.kind() == kind).findFirst().orElseThrow();
            if (member.origin() == ClassSemantics.Origin.IMPLICIT) return NEXT;
            if (member.origin() != ClassSemantics.Origin.USER_DECLARED || member.declarations().size() != 1) return UNKNOWN;
            var target = member.declarations().getFirst();
            // Default arguments, member/base initialization and function try blocks
            // need their own evaluation/lifetime contract, not just the written body.
            if (target.parameters().stream().anyMatch(p -> p.defaultValue().isEmpty())) return UNKNOWN;
            // Standalone CFGs deliberately leave virtual special members incomplete:
            // their hidden operations depend on complete-object/base invocation.
            // Here those operations are composed explicitly; summarize only the
            // supported written body, without changing its standalone CFG.
            var outcome = function(target, active, true);
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
                        || call.isVirtualDispatch() || call.target().isEmpty()) return UNKNOWN;
                var target = call.target().orElseThrow();
                var values = new ArrayList<>(call.arguments());
                for (var value : call.defaultArguments()) {
                    if (value.parameterIndex() != values.size() || value.parameter().defaultValue().isEmpty()) return UNKNOWN;
                    values.add(value.parameter().defaultValue().orElseThrow());
                }
                var arguments = invocationArguments(target, values, profile, active);
                if (arguments.unknown()) return UNKNOWN;
                Outcome result;
                if (LibraryDeclarations.isLibrary(target)) result = target.name().equals("terminate")
                        ? new Outcome(false, false, true, false, Set.of()) : UNKNOWN;
                else result = function(target, active);
                var invocation = new Outcome(result.next() || result.returned(), false, result.terminated(), result.unknown(), result.thrown());
                return arguments.then(invocation);
            }
            Outcome result = NEXT;
            for (var child : expression.children()) if (child instanceof Expression operand) result = result.union(expression(operand, profile, active, caught));
            return result;
        }
    }
}
